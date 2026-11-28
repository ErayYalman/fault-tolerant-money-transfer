package com.fintech.payment.a.application.service;

import com.fintech.payment.a.domain.model.InternalTransfer;

public record InternalTransferResult(
        InternalTransfer transfer,
        boolean replayed) {
}
