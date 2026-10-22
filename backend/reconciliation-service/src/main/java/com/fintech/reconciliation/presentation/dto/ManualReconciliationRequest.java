package com.fintech.reconciliation.presentation.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record ManualReconciliationRequest(
        @NotNull LocalDate businessDate) {
}