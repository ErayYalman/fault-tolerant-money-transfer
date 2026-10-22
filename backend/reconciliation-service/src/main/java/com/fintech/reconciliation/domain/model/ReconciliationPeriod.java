package com.fintech.reconciliation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record ReconciliationPeriod(
        LocalDate businessDate,
        Instant fromInclusive,
        Instant toExclusive) {

    public ReconciliationPeriod {
        Objects.requireNonNull(businessDate, "businessDate must not be null");
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");
        Objects.requireNonNull(toExclusive, "toExclusive must not be null");

        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "fromInclusive must be earlier than toExclusive");
        }
    }
}