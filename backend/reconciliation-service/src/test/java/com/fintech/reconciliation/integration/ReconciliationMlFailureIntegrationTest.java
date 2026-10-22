package com.fintech.reconciliation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
import com.fintech.reconciliation.domain.model.AnomalyFeatures;
import com.fintech.reconciliation.domain.model.LedgerEntryRecord;
import com.fintech.reconciliation.domain.model.TransferRecord;
import com.fintech.reconciliation.domain.port.out.AnomalyScoringProvider;
import com.fintech.reconciliation.domain.port.out.PaymentALedgerDataProvider;
import com.fintech.reconciliation.domain.port.out.PaymentBLedgerDataProvider;
import com.fintech.reconciliation.domain.port.out.TransferDataProvider;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReconciliationMlFailureIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reconciliation_ml_failure_test_db")
            .withUsername("reconciliation_ml_failure_test_user")
            .withPassword("reconciliation_ml_failure_test_password");

    private static final Instant STARTED_AT = Instant.parse("2026-10-20T10:00:00Z");

    private static final UUID TRANSFER_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_A_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_B_ID = UUID.randomUUID();

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
    private TransferDataProvider transferDataProvider;

    @MockitoBean
    private PaymentALedgerDataProvider paymentALedgerDataProvider;

    @MockitoBean
    private PaymentBLedgerDataProvider paymentBLedgerDataProvider;

    @MockitoBean
    private AnomalyScoringProvider anomalyScoringProvider;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM anomaly_scores");
        jdbcTemplate.update("DELETE FROM reconciliation_discrepancies");
        jdbcTemplate.update("DELETE FROM reconciliation_runs");
    }

    @Test
    void shouldCompleteReconciliationAndReportMlUnavailableWhenScoringIsUnavailable()
            throws Exception {

        TransferRecord transfer = new TransferRecord(
                TRANSFER_ID,
                CORRELATION_ID,
                new BigDecimal("250.00"),
                "TRY",
                "COMPLETED",
                STARTED_AT,
                STARTED_AT.plusMillis(20),
                STARTED_AT.plusMillis(100),
                STARTED_AT.plusMillis(110),
                STARTED_AT.plusMillis(500),
                STARTED_AT.plusMillis(500));

        LedgerEntryRecord debit = new LedgerEntryRecord(
                TRANSFER_ID,
                CORRELATION_ID,
                ACCOUNT_A_ID,
                new BigDecimal("250.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("750.00"),
                "DEBIT",
                "COMPLETED",
                STARTED_AT.plusMillis(100));

        LedgerEntryRecord credit = new LedgerEntryRecord(
                TRANSFER_ID,
                CORRELATION_ID,
                ACCOUNT_B_ID,
                new BigDecimal("250.00"),
                new BigDecimal("500.00"),
                new BigDecimal("750.00"),
                "CREDIT",
                "COMPLETED",
                STARTED_AT.plusMillis(500));

        when(transferDataProvider.findTransfers(
                any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(transfer));

        when(paymentALedgerDataProvider.findLedgerEntries(
                any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(debit));

        when(paymentBLedgerDataProvider.findLedgerEntries(
                any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(credit));

        when(anomalyScoringProvider.score(any(AnomalyFeatures.class)))
                .thenThrow(new IllegalStateException("ML service unavailable"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = """
                {
                  "businessDate": "2026-10-20"
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/v1/reconciliation/run",
                new HttpEntity<>(requestBody, headers),
                String.class);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode result = objectMapper.readTree(response.getBody());
        UUID runId = UUID.fromString(result.path("runId").asText());

        assertThat(result.path("status").asText())
                .isEqualTo("COMPLETED");

        assertThat(result.path("transfersChecked").asInt())
                .isEqualTo(1);

        ResponseEntity<String> detailsResponse = restTemplate.getForEntity(
                baseUrl() + "/api/v1/reconciliation/runs/" + runId,
                String.class);

        assertThat(detailsResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode details = objectMapper.readTree(detailsResponse.getBody());

        assertThat(details.path("status").asText())
                .isEqualTo("COMPLETED");

        assertThat(details.path("errorMessage").isNull())
                .isTrue();

        assertThat(details.at("/anomalySummary/status").asText())
                .isEqualTo("UNAVAILABLE");

        assertThat(details.at("/anomalySummary/attemptedCount").asInt())
                .isEqualTo(1);

        assertThat(details.at("/anomalySummary/failedCount").asInt())
                .isEqualTo(1);

        assertThat(details.at("/anomalySummary/scoredCount").asInt())
                .isZero();

        assertThat(details.at("/anomalySummary/errorMessage").asText())
                .contains("ML scoring failed for 1 of 1 eligible transfer(s)");

        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM reconciliation_runs WHERE id = ?",
                String.class,
                runId);

        String scoringStatus = jdbcTemplate.queryForObject(
                "SELECT anomaly_scoring_status FROM reconciliation_runs WHERE id = ?",
                String.class,
                runId);

        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT anomaly_scoring_failed_count FROM reconciliation_runs WHERE id = ?",
                Integer.class,
                runId);

        assertThat(persistedStatus).isEqualTo("COMPLETED");
        assertThat(scoringStatus).isEqualTo("UNAVAILABLE");
        assertThat(failedCount).isEqualTo(1);

        verify(anomalyScoringProvider)
                .score(any(AnomalyFeatures.class));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}