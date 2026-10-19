package com.fintech.payment.a.domain.exception;

public final class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException() {
        super("Account is not active");
    }
}