package com.fintech.reconciliation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReconciliationDatabaseMigrationTest {

        @SuppressWarnings("resource")
        @Container
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("reconciliation_test_db")
                        .withUsername("reconciliation_test_user")
                        .withPassword("reconciliation_test_password");

        @DynamicPropertySource
        static void configureDataSource(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES::getUsername);
                registry.add("spring.datasource.password", POSTGRES::getPassword);
        }

        @Autowired
        JdbcTemplate jdbcTemplate;

        @Test
        void shouldCreateAllReconciliationTables() {
                List<String> tables = jdbcTemplate.queryForList(
                                """
                                                SELECT table_name
                                                FROM information_schema.tables
                                                WHERE table_schema = 'public'
                                                """,
                                String.class);

                assertThat(tables).contains(
                                "reconciliation_runs",
                                "reconciliation_discrepancies",
                                "anomaly_scores",
                                "flyway_schema_history");
        }

        @Test
        void shouldCreateIndexesForRunAndTransferLookups() {
                List<String> indexes = jdbcTemplate.queryForList(
                                """
                                                SELECT indexname
                                                FROM pg_indexes
                                                WHERE schemaname = 'public'
                                                """,
                                String.class);

                assertThat(indexes).contains(
                                "idx_reconciliation_runs_business_date",
                                "idx_discrepancies_run_id",
                                "idx_discrepancies_correlation_id",
                                "idx_anomaly_scores_run_id",
                                "idx_anomaly_scores_correlation_id");
        }

        @Test
        void shouldCreateAnomalyScoringStatusColumns() {
                List<String> columns = jdbcTemplate.queryForList(
                                """
                                                SELECT column_name
                                                FROM information_schema.columns
                                                WHERE table_schema = 'public'
                                                  AND table_name = 'reconciliation_runs'
                                                """,
                                String.class);

                assertThat(columns).contains(
                                "anomaly_scoring_status",
                                "anomaly_scoring_attempted_count",
                                "anomaly_scoring_failed_count",
                                "anomaly_scoring_error_message");
        }
}