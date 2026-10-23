package com.fintech.reconciliation.integration;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CircuitBreakerActuatorIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("circuit_breaker_actuator_test_db")
            .withUsername("circuit_breaker_actuator_test_user")
            .withPassword("circuit_breaker_actuator_test_password");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldExposeAllConfiguredCircuitBreakers() throws Exception {
        registerCircuitBreakers();

        mockMvc.perform(get("/actuator/circuitbreakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circuitBreakers", hasKey("transferService")))
                .andExpect(jsonPath("$.circuitBreakers", hasKey("paymentAService")))
                .andExpect(jsonPath("$.circuitBreakers", hasKey("paymentBService")))
                .andExpect(jsonPath("$.circuitBreakers", hasKey("mlService")));
    }

    @Test
    void shouldExposeCircuitBreakerCallMetrics() throws Exception {
        registerCircuitBreakers();

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "resilience4j.circuitbreaker.calls")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "resilience4j.circuitbreaker.state")));

        mockMvc.perform(
                get("/actuator/metrics/resilience4j.circuitbreaker.calls")
                        .param("tag", "name:transferService"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "resilience4j.circuitbreaker.calls")));

        mockMvc.perform(
                get("/actuator/metrics/resilience4j.circuitbreaker.state")
                        .param("tag", "name:transferService"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "resilience4j.circuitbreaker.state")));
    }

    private void registerCircuitBreakers() {
        circuitBreakerRegistry.circuitBreaker("transferService");
        circuitBreakerRegistry.circuitBreaker("paymentAService");
        circuitBreakerRegistry.circuitBreaker("paymentBService");
        circuitBreakerRegistry.circuitBreaker("mlService");
    }
}