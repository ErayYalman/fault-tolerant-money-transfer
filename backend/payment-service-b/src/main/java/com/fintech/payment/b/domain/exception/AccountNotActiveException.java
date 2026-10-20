package com.fintech.payment.b.domain.exception;

public final class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException() {
        super("Account is not active");
    }
}