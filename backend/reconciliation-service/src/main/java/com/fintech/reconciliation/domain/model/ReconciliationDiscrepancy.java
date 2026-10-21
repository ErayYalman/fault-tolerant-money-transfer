package com.fintech.reconciliation.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ReconciliationDiscrepancy(
        UUID transferId,
        UUID correlationId,
        DiscrepancyCode ruleCode,
        Map<String, String> details) {

    public ReconciliationDiscrepancy {
        Objects.requireNonNull(transferId, "transferId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(ruleCode, "ruleCode must not be null");
        details = Map.copyOf(
                Objects.requireNonNull(details, "details must not be null"));
    }
}