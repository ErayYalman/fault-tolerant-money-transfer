package com.fintech.payment.b;

import com.fintech.payment.b.support.PaymentBPostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(PaymentBPostgresTestResource.class)
class PaymentBDatabaseMigrationTest {

    @Inject
    DataSource dataSource;

    @Test
    void shouldCreateAllPaymentBTablesAndApplyV1() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name IN (
                          'accounts',
                          'ledger_entries',
                          'inbox_messages',
                          'outbox_events'
                      )
                    """
            );
                 ResultSet resultSet = statement.executeQuery()) {

                resultSet.next();
                assertEquals(
                        4,
                        resultSet.getInt(1),
                        "All four Bank B tables must exist"
                );
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE version = '1'
                      AND success = TRUE
                    """
            );
                 ResultSet resultSet = statement.executeQuery()) {

                resultSet.next();
                assertEquals(
                        1,
                        resultSet.getInt(1),
                        "Flyway V1 migration must be applied successfully"
                );
            }
        }
    }
}