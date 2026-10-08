package com.fintech.transfer.presentation.rest;

import com.fintech.transfer.domain.exception.TransferNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class TransferExceptionHandler {

    @ExceptionHandler(TransferNotFoundException.class)
    public ProblemDetail handleTransferNotFound(
            TransferNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.NOT_FOUND
        );

        problem.setTitle("Transfer not found");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
            MethodArgumentNotValidException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        problem.setTitle("Validation failed");

        Map<String, String> errors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null
                                ? "Invalid value"
                                : error.getDefaultMessage(),
                        (first, second) -> first
                ));

        problem.setProperty("errors", errors);

        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        problem.setTitle("Invalid request");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        problem.setTitle("Constraint violation");
        problem.setDetail(exception.getMessage());

        return problem;
    }
}