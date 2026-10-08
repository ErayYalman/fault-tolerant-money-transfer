package com.fintech.transfer.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class TransferApiIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("transfer_api_test_db")
                    .withUsername("transfer_api_test_user")
                    .withPassword("transfer_api_test_password");

    @DynamicPropertySource
    static void registerDataSourceProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCreateTransfer() {
        String idempotencyKey =
                "integration-" + UUID.randomUUID();

        String request = """
                {
                  "amount": 1500.00,
                  "currency": "TRY",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "22222222-2222-2222-2222-222222222222",
                  "idempotencyKey": "%s"
                }
                """.formatted(idempotencyKey);

        ResponseEntity<String> response =
                postTransfer(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .contains("\"status\":\"PENDING\"")
                .contains("\"idempotencyKey\":\""
                        + idempotencyKey + "\"");
    }

    @Test
    void shouldReturnExistingTransferForDuplicateIdempotencyKey() {
        String idempotencyKey =
                "duplicate-" + UUID.randomUUID();

        String request = """
                {
                  "amount": 1000.00,
                  "currency": "TRY",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "22222222-2222-2222-2222-222222222222",
                  "idempotencyKey": "%s"
                }
                """.formatted(idempotencyKey);

        ResponseEntity<String> first =
                postTransfer(request);

        ResponseEntity<String> second =
                postTransfer(request);

        assertThat(first.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(second.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        DocumentContext firstContext = JsonPath.parse(first.getBody());
        DocumentContext secondContext = JsonPath.parse(second.getBody());

        assertThat(secondContext.read("$.id", String.class))
                .isEqualTo(firstContext.read("$.id", String.class));

        assertThat(secondContext.read("$.status", String.class))
                .isEqualTo("PENDING");
    }

    @Test
    void shouldRejectSameSourceAndTargetAccount() {
        String request = """
                {
                  "amount": 1000.00,
                  "currency": "TRY",
                  "fromAccountId": "11111111-1111-1111-1111-111111111111",
                  "toAccountId": "11111111-1111-1111-1111-111111111111",
                  "idempotencyKey": "same-account-%s"
                }
                """.formatted(UUID.randomUUID());

        ResponseEntity<String> response =
                postTransfer(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> postTransfer(
            String body
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return restTemplate.postForEntity(
                "http://localhost:" + port
                        + "/api/v1/transfers",
                new HttpEntity<>(body, headers),
                String.class
        );
    }
}