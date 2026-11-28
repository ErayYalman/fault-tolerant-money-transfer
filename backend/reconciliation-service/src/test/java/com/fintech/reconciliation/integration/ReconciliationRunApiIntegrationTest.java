package com.fintech.reconciliation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.reconciliation.application.service.ReconciliationAnalysisService;
import com.fintech.reconciliation.domain.model.AnomalyScoreRecord;
import com.fintech.reconciliation.domain.model.DiscrepancyCode;
import com.fintech.reconciliation.domain.model.ReconciliationAnalysisResult;
import com.fintech.reconciliation.domain.model.ReconciliationDiscrepancy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ReconciliationTestSecurityConfiguration.class)
class ReconciliationRunApiIntegrationTest {

        @SuppressWarnings("resource")
        @Container
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("reconciliation_run_api_test_db")
                        .withUsername("reconciliation_run_api_test_user")
                        .withPassword("reconciliation_run_api_test_password");

        @DynamicPropertySource
        static void configureDataSource(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES::getUsername);
                registry.add("spring.datasource.password", POSTGRES::getPassword);
        }

        @LocalServerPort
        private int port;

        @Autowired
        private TestRestTemplate restTemplate;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private ReconciliationAnalysisService analysisService;

        @BeforeEach
        void cleanDatabase() {
                jdbcTemplate.update("DELETE FROM anomaly_scores");
                jdbcTemplate.update("DELETE FROM reconciliation_discrepancies");
                jdbcTemplate.update("DELETE FROM reconciliation_runs");
        }

        @Test
        void shouldPersistCompletedRunAndReturnItsDetails() throws Exception {
                UUID transferId = UUID.randomUUID();
                UUID correlationId = UUID.randomUUID();

                ReconciliationDiscrepancy discrepancy = new ReconciliationDiscrepancy(
                                transferId,
                                correlationId,
                                DiscrepancyCode.MISSING_CREDIT,
                                Map.of(
                                                "reason",
                                                "Completed debit has no credit or reversal.",
                                                "transferStatus",
                                                "COMPLETED"));

                when(analysisService.analyze(
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(new ReconciliationAnalysisResult(
                                                4,
                                                List.of(discrepancy)));

                ResponseEntity<String> response = postRun("2026-10-20");

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

                JsonNode runResponse = objectMapper.readTree(response.getBody());
                UUID runId = UUID.fromString(runResponse.path("runId").asText());

                assertThat(runResponse.path("status").asText())
                                .isEqualTo("COMPLETED");
                assertThat(runResponse.path("runType").asText())
                                .isEqualTo("MANUAL");
                assertThat(runResponse.path("transfersChecked").asInt())
                                .isEqualTo(4);
                assertThat(runResponse.path("discrepanciesFound").asInt())
                                .isEqualTo(1);
                assertThat(runResponse.path("discrepancies").size())
                                .isEqualTo(1);
                assertThat(runResponse.at("/discrepancies/0/ruleCode").asText())
                                .isEqualTo("MISSING_CREDIT");
                assertThat(runResponse.path("anomalyScores")).isEmpty();

                String status = jdbcTemplate.queryForObject(
                                "SELECT status FROM reconciliation_runs WHERE id = ?",
                                String.class,
                                runId);

                Integer discrepancyCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM reconciliation_discrepancies WHERE run_id = ?",
                                Integer.class,
                                runId);

                assertThat(status).isEqualTo("COMPLETED");
                assertThat(discrepancyCount).isEqualTo(1);

                HttpHeaders detailHeaders = new HttpHeaders();
                detailHeaders.setBearerAuth("operations-test-token");

                ResponseEntity<String> detailsResponse = restTemplate.exchange(
                                baseUrl() + "/api/v1/reconciliation/runs/" + runId,
                                HttpMethod.GET,
                                new HttpEntity<>(detailHeaders),
                                String.class);

                assertThat(detailsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

                JsonNode details = objectMapper.readTree(detailsResponse.getBody());
                assertThat(details.path("runId").asText()).isEqualTo(runId.toString());
                assertThat(details.at("/anomalySummary/scoredCount").asLong())
                                .isZero();
        }

        @Test
        void shouldMarkRunFailedWhenAnalysisFails() throws Exception {
                doThrow(new IllegalStateException("Remote service unavailable"))
                                .when(analysisService)
                                .analyze(any(Instant.class), any(Instant.class));

                ResponseEntity<String> response = postRun("2026-10-19");

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_GATEWAY);

                JsonNode error = objectMapper.readTree(response.getBody());
                UUID runId = UUID.fromString(error.path("runId").asText());

                String status = jdbcTemplate.queryForObject(
                                "SELECT status FROM reconciliation_runs WHERE id = ?",
                                String.class,
                                runId);

                String errorMessage = jdbcTemplate.queryForObject(
                                "SELECT error_message FROM reconciliation_runs WHERE id = ?",
                                String.class,
                                runId);

                assertThat(status).isEqualTo("FAILED");
                assertThat(errorMessage).contains("Remote service unavailable");
        }

        @Test
        void shouldReturnNotFoundForUnknownRun() {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth("operations-test-token");

                ResponseEntity<String> response = restTemplate.exchange(
                                baseUrl() + "/api/v1/reconciliation/runs/" + UUID.randomUUID(),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                String.class);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldExposeOpenApiDocumentToAuthenticatedOperationsUser()
                        throws Exception {

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth("operations-test-token");

                ResponseEntity<String> response = restTemplate.exchange(
                                baseUrl() + "/v3/api-docs",
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                String.class);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                JsonNode openApi = objectMapper.readTree(response.getBody());

                assertThat(openApi.path("paths")
                                .has("/api/v1/reconciliation/run"))
                                .isTrue();

                assertThat(openApi.path("paths")
                                .has("/api/v1/reconciliation/runs/{id}"))
                                .isTrue();

                assertThat(openApi.path("paths")
                                .has("/api/v1/reconciliation/runs"))
                                .isTrue();

                assertThat(openApi.path("paths")
                                .has("/api/v1/reconciliation/reports/summary"))
                                .isTrue();

                assertThat(openApi.at(
                                "/components/schemas/ReconciliationRunDetailsResponse/properties")
                                .has("anomalyScores"))
                                .isTrue();

                assertThat(openApi.at(
                                "/components/securitySchemes/bearerAuth/type").asText())
                                .isEqualTo("http");

                assertThat(openApi.at(
                                "/components/securitySchemes/bearerAuth/scheme").asText())
                                .isEqualTo("bearer");
        }

        @Test
        void shouldRejectOpenApiDocumentRequestWithoutBearerToken() {

                ResponseEntity<String> response = restTemplate.getForEntity(
                                baseUrl() + "/v3/api-docs",
                                String.class);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldRejectManualRunWithoutBearerToken() {
                ResponseEntity<String> response = postRun(
                                "2026-10-20",
                                null);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldRejectCustomerFromManualReconciliation() {
                ResponseEntity<String> response = postRun(
                                "2026-10-20",
                                "customer-test-token");

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void shouldRejectOperationsRoleFromAnotherClient() {
                ResponseEntity<String> response = postRun(
                                "2026-10-20",
                                "foreign-operations-test-token");

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void shouldAllowAdminToAccessReconciliationRunEndpoint() {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth("admin-test-token");

                ResponseEntity<String> response = restTemplate.exchange(
                                baseUrl() + "/api/v1/reconciliation/runs/" + UUID.randomUUID(),
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                String.class);

                // 404 means authentication and authorization succeeded,
                // but the requested run does not exist.
                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void shouldListAllRunsWithinInclusiveDateRangeWithPagination()
                        throws Exception {

                when(analysisService.analyze(
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(new ReconciliationAnalysisResult(
                                                1,
                                                List.of()));

                assertThat(postRun("2026-10-18").getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                assertThat(postRun("2026-10-19").getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                assertThat(postRun("2026-10-19").getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                ResponseEntity<String> firstPageResponse = getRuns(
                                "2026-10-18",
                                "2026-10-20",
                                0,
                                2,
                                "operations-test-token");

                assertThat(firstPageResponse.getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                JsonNode firstPage = objectMapper.readTree(
                                firstPageResponse.getBody());

                assertThat(firstPage.path("totalElements").asLong())
                                .isEqualTo(3);

                assertThat(firstPage.path("totalPages").asInt())
                                .isEqualTo(2);

                assertThat(firstPage.path("page").asInt())
                                .isZero();

                assertThat(firstPage.path("size").asInt())
                                .isEqualTo(2);

                JsonNode firstContent = firstPage.path("content");

                assertThat(firstContent).hasSize(2);

                // Aynı iş gününde yapılan iki manuel koşu kaybolmamalı.
                assertThat(firstContent.get(0).path("businessDate").asText())
                                .isEqualTo("2026-10-19");

                assertThat(firstContent.get(1).path("businessDate").asText())
                                .isEqualTo("2026-10-19");

                assertThat(firstContent.get(0).path("runType").asText())
                                .isEqualTo("MANUAL");

                assertThat(firstContent.get(0).path("status").asText())
                                .isEqualTo("COMPLETED");

                ResponseEntity<String> secondPageResponse = getRuns(
                                "2026-10-18",
                                "2026-10-20",
                                1,
                                2,
                                "operations-test-token");

                assertThat(secondPageResponse.getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                JsonNode secondPage = objectMapper.readTree(
                                secondPageResponse.getBody());

                assertThat(secondPage.path("content")).hasSize(1);

                assertThat(secondPage.at("/content/0/businessDate").asText())
                                .isEqualTo("2026-10-18");

                assertThat(secondPage.at("/content/0/runId").asText())
                                .isNotEqualTo(
                                                firstContent.get(0).path("runId").asText());

                // Tarih filtresinin iki ucu da dahil edilmeli.
                ResponseEntity<String> singleDayResponse = getRuns(
                                "2026-10-19",
                                "2026-10-19",
                                0,
                                10,
                                "operations-test-token");

                JsonNode singleDay = objectMapper.readTree(
                                singleDayResponse.getBody());

                assertThat(singleDay.path("totalElements").asLong())
                                .isEqualTo(2);
        }

        @Test
        void shouldRejectInvalidReconciliationRunDateRange() {

                ResponseEntity<String> response = getRuns(
                                "2026-10-20",
                                "2026-10-19",
                                0,
                                20,
                                "operations-test-token");

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldRejectReconciliationRunListWithoutBearerToken() {

                ResponseEntity<String> response = getRuns(
                                "2026-10-01",
                                "2026-10-31",
                                0,
                                20,
                                null);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldRejectCustomerFromReconciliationRunList() {

                ResponseEntity<String> response = getRuns(
                                "2026-10-01",
                                "2026-10-31",
                                0,
                                20,
                                "customer-test-token");

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void shouldAggregateOnlyLatestCompletedRunForEachBusinessDate()
                        throws Exception {

                AnomalyScoreRecord firstScore = new AnomalyScoreRecord(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                0.92,
                                "iforest-synthetic-v2",
                                true,
                                Instant.parse("2026-10-20T00:00:00Z"));

                AnomalyScoreRecord secondScore = new AnomalyScoreRecord(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                0.15,
                                "iforest-synthetic-v2",
                                false,
                                Instant.parse("2026-10-20T00:00:00Z"));

                when(analysisService.analyze(
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(new ReconciliationAnalysisResult(
                                                3,
                                                List.of(),
                                                List.of(firstScore)))
                                .thenReturn(new ReconciliationAnalysisResult(
                                                7,
                                                List.of(),
                                                List.of(secondScore)))
                                .thenThrow(new IllegalStateException(
                                                "Remote service unavailable"));

                ResponseEntity<String> firstRunResponse = postRun("2026-10-20");

                assertThat(firstRunResponse.getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                UUID firstRunId = UUID.fromString(
                                objectMapper.readTree(firstRunResponse.getBody())
                                                .path("runId").asText());

                // Ensure the second successful run is unambiguously newer.
                jdbcTemplate.update(
                                """
                                UPDATE reconciliation_runs
                                SET started_at = TIMESTAMPTZ '2026-10-20 00:00:00+00'
                                WHERE id = ?
                                """,
                                firstRunId);

                ResponseEntity<String> secondRunResponse = postRun("2026-10-20");

                assertThat(secondRunResponse.getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                UUID secondRunId = UUID.fromString(
                                objectMapper.readTree(secondRunResponse.getBody())
                                                .path("runId").asText());

                // A later failed run must not replace the latest completed run.
                ResponseEntity<String> failedSameDayResponse = postRun("2026-10-20");

                assertThat(failedSameDayResponse.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_GATEWAY);

                // A day with only a failed run must not appear in the summary.
                ResponseEntity<String> failedOnlyResponse = postRun("2026-10-19");

                assertThat(failedOnlyResponse.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_GATEWAY);

                ResponseEntity<String> summaryResponse = getDailySummary(
                                "2026-10-18",
                                "2026-10-20",
                                "operations-test-token");

                assertThat(summaryResponse.getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                JsonNode summary = objectMapper.readTree(summaryResponse.getBody());

                assertThat(summary.path("completedDays").asInt()).isEqualTo(1);
                assertThat(summary.path("transfersChecked").asLong()).isEqualTo(7);
                assertThat(summary.path("discrepanciesFound").asLong()).isZero();
                assertThat(summary.path("anomaliesFound").asLong()).isZero();

                assertThat(summary.path("daily").size()).isEqualTo(1);

                JsonNode daily = summary.path("daily").get(0);

                assertThat(daily.path("runId").asText())
                                .isEqualTo(secondRunId.toString());

                assertThat(daily.path("businessDate").asText())
                                .isEqualTo("2026-10-20");

                assertThat(daily.path("transfersChecked").asInt())
                                .isEqualTo(7);

                assertThat(daily.path("anomaliesFound").asInt())
                                .isZero();

                assertThat(daily.path("anomalyScoringStatus").asText())
                                .isEqualTo("COMPLETED");

                assertThat(summary.path("incompleteAnomalyScoringDays").asInt())
                                .isZero();
        }

        @Test
        void shouldRejectDailySummaryWithInvalidDateRange() {

                ResponseEntity<String> response = getDailySummary(
                                "2026-10-20",
                                "2026-10-19",
                                "operations-test-token");

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void shouldRejectDailySummaryWithoutBearerToken() {

                ResponseEntity<String> response = getDailySummary(
                                "2026-10-01",
                                "2026-10-31",
                                null);

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldRejectCustomerFromDailySummary() {

                ResponseEntity<String> response = getDailySummary(
                                "2026-10-01",
                                "2026-10-31",
                                "customer-test-token");

                assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN);
        }

        private ResponseEntity<String> postRun(String businessDate) {
                return postRun(businessDate, "operations-test-token");
        }

        private ResponseEntity<String> postRun(
                        String businessDate,
                        String token) {

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                if (token != null) {
                        headers.setBearerAuth(token);
                }

                String body = """
                                {
                                  "businessDate": "%s"
                                }
                                """.formatted(businessDate);

                return restTemplate.postForEntity(
                                baseUrl() + "/api/v1/reconciliation/run",
                                new HttpEntity<>(body, headers),
                                String.class);
        }

        private ResponseEntity<String> getRuns(
                        String fromDate,
                        String toDate,
                        int page,
                        int size,
                        String token) {

                HttpHeaders headers = new HttpHeaders();

                if (token != null) {
                        headers.setBearerAuth(token);
                }

                String url = baseUrl()
                                + "/api/v1/reconciliation/runs"
                                + "?fromDate=" + fromDate
                                + "&toDate=" + toDate
                                + "&page=" + page
                                + "&size=" + size;

                return restTemplate.exchange(
                                url,
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                String.class);
        }

        private ResponseEntity<String> getDailySummary(
                        String fromDate,
                        String toDate,
                        String token) {

                HttpHeaders headers = new HttpHeaders();

                if (token != null) {
                        headers.setBearerAuth(token);
                }

                String url = baseUrl()
                                + "/api/v1/reconciliation/reports/summary"
                                + "?fromDate=" + fromDate
                                + "&toDate=" + toDate;

                return restTemplate.exchange(
                                url,
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                String.class);
        }

        private String baseUrl() {
                return "http://localhost:" + port;
        }

        @Test
        void shouldPersistAnomalyScoresAndUpdateRunSummary() throws Exception {
                UUID transfer1 = UUID.randomUUID();
                UUID transfer2 = UUID.randomUUID();
                UUID correlation1 = UUID.randomUUID();
                UUID correlation2 = UUID.randomUUID();

                Instant firstScoredAt =
                                Instant.parse("2026-10-20T03:00:00Z");

                Instant secondScoredAt =
                                Instant.parse("2026-10-20T03:01:00Z");

                List<AnomalyScoreRecord> scores = List.of(
                                new AnomalyScoreRecord(
                                                transfer1,
                                                correlation1,
                                                0.91,
                                                "iforest-test-v1",
                                                true,
                                                firstScoredAt),
                                new AnomalyScoreRecord(
                                                transfer2,
                                                correlation2,
                                                0.12,
                                                "iforest-test-v1",
                                                false,
                                                secondScoredAt));

                when(analysisService.analyze(
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(new ReconciliationAnalysisResult(
                                                2,
                                                List.of(),
                                                scores));

                ResponseEntity<String> response = postRun("2026-10-20");

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

                JsonNode result = objectMapper.readTree(response.getBody());
                UUID runId = UUID.fromString(result.path("runId").asText());

                assertThat(result.path("anomaliesFound").asInt()).isEqualTo(1);
                assertThat(result.at("/anomalySummary/scoredCount").asLong()).isEqualTo(2);
                assertThat(result.at("/anomalySummary/anomalyCount").asLong()).isEqualTo(1);

                JsonNode anomalyScores = result.path("anomalyScores");

                assertThat(anomalyScores).hasSize(2);

                // İlk transfer: anomali olarak işaretlenmiş.
                assertThat(anomalyScores.get(0).path("transferId").asText())
                                .isEqualTo(transfer1.toString());

                assertThat(anomalyScores.get(0).path("correlationId").asText())
                                .isEqualTo(correlation1.toString());

                assertThat(anomalyScores.get(0).path("anomalyScore").asDouble())
                                .isEqualTo(0.91);

                assertThat(anomalyScores.get(0).path("modelVersion").asText())
                                .isEqualTo("iforest-test-v1");

                assertThat(anomalyScores.get(0).path("isAnomaly").asBoolean())
                                .isTrue();

                assertThat(anomalyScores.get(0).path("scoredAt").asText())
                                .isEqualTo(firstScoredAt.toString());

                // İkinci transfer: anomali olarak işaretlenmemiş.
                assertThat(anomalyScores.get(1).path("transferId").asText())
                                .isEqualTo(transfer2.toString());

                assertThat(anomalyScores.get(1).path("anomalyScore").asDouble())
                                .isEqualTo(0.12);

                assertThat(anomalyScores.get(1).path("isAnomaly").asBoolean())
                                .isFalse();

                Integer savedScores = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM anomaly_scores WHERE run_id = ?",
                                Integer.class,
                                runId);

                Integer savedAnomalies = jdbcTemplate.queryForObject(
                                """
                                                SELECT COUNT(*)
                                                FROM anomaly_scores
                                                WHERE run_id = ? AND is_anomaly = TRUE
                                                """,
                                Integer.class,
                                runId);

                assertThat(savedScores).isEqualTo(2);
                assertThat(savedAnomalies).isEqualTo(1);

                HttpHeaders detailHeaders = new HttpHeaders();
                detailHeaders.setBearerAuth("operations-test-token");

                ResponseEntity<String> detailsResponse = restTemplate.exchange(
                                baseUrl() + "/api/v1/reconciliation/runs/" + runId,
                                HttpMethod.GET,
                                new HttpEntity<>(detailHeaders),
                                String.class);

                assertThat(detailsResponse.getStatusCode())
                                .isEqualTo(HttpStatus.OK);

                JsonNode details = objectMapper.readTree(detailsResponse.getBody());

                assertThat(details.path("anomalyScores")).hasSize(2);

                assertThat(details.at("/anomalySummary/scoredCount").asLong())
                                .isEqualTo(2);

                assertThat(details.at("/anomalySummary/anomalyCount").asLong())
                                .isEqualTo(1);
        }
}