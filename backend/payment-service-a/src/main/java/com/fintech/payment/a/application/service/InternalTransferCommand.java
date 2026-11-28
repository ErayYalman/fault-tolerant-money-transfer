package com.fintech.payment.a.application.service;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalTransferCommand(
        BigDecimal amount,
        String currency,
        UUID fromAccountId,
        UUID toAccountId,
        String idempotencyKey) {
}
