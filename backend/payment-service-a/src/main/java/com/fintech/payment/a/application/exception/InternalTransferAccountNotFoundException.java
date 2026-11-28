package com.fintech.payment.a.application.exception;

public final class InternalTransferAccountNotFoundException
        extends RuntimeException {

    public InternalTransferAccountNotFoundException() {
        super("Account not found or not owned by authenticated customer");
    }
}
