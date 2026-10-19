package com.fintech.payment.b.support;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class PaymentBPostgresTestResource
        implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer postgres;

    @SuppressWarnings("resource")
    @Override
    public Map<String, String> start() {
        postgres = new PostgreSQLContainer(
                DockerImageName.parse("postgres:16-alpine")
        )
                .withDatabaseName("payment_b_test_db")
                .withUsername("payment_b_test_user")
                .withPassword("payment_b_test_password");

        postgres.start();

        return Map.of(
                "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword()
        );
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }
}