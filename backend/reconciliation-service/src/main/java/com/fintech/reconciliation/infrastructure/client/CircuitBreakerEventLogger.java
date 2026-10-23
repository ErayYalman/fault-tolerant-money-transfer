package com.fintech.reconciliation.infrastructure.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Component
public class CircuitBreakerEventLogger {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerEventLogger.class);

    public CircuitBreakerEventLogger(
            CircuitBreakerRegistry circuitBreakerRegistry) {

        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> register(event.getAddedEntry()));

        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(this::register);
    }

    private void register(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "Circuit Breaker '{}' state transition: {}",
                        event.getCircuitBreakerName(),
                        event.getStateTransition()));
    }
}