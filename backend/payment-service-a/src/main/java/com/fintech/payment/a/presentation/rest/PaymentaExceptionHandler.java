package com.fintech.payment.a.presentation.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fintech.payment.a.application.exception.IdempotencyConflictException;
import com.fintech.payment.a.application.exception.InternalTransferAccountNotFoundException;
import com.fintech.payment.a.domain.exception.AccountNotActiveException;
import com.fintech.payment.a.domain.exception.CurrencyMismatchException;
import com.fintech.payment.a.domain.exception.InsufficientFundsException;

@RestControllerAdvice
public class PaymentaExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ProblemDetail handleBadRequest(Exception exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
    }

    @ExceptionHandler(InternalTransferAccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(
            InternalTransferAccountNotFoundException exception) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(
            IdempotencyConflictException exception) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The idempotency key was already used with different request content.");
    }

    @ExceptionHandler({
            AccountNotActiveException.class,
            CurrencyMismatchException.class,
            InsufficientFundsException.class
    })
    public ProblemDetail handleUnprocessableTransfer(Exception exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getMessage());
    }
}
