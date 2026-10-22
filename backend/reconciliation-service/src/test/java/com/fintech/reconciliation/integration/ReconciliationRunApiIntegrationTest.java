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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
import com.fintech.reconciliation.domain.model.DiscrepancyCode;
import com.fintech.reconciliation.domain.model.ReconciliationAnalysisResult;
import com.fintech.reconciliation.domain.model.ReconciliationDiscrepancy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

        ResponseEntity<String> detailsResponse = restTemplate.getForEntity(
                baseUrl() + "/api/v1/reconciliation/runs/" + runId,
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
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/reconciliation/runs/" + UUID.randomUUID(),
                String.class);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> postRun(String businessDate) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

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

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}