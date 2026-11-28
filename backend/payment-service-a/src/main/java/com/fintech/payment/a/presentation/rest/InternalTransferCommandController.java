package com.fintech.payment.a.presentation.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.payment.a.application.service.InternalTransferApplicationService;
import com.fintech.payment.a.application.service.InternalTransferCommand;
import com.fintech.payment.a.application.service.InternalTransferResult;
import com.fintech.payment.a.presentation.dto.CreateInternalTransferRequest;
import com.fintech.payment.a.presentation.dto.InternalTransferResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/accounts/internal-transfers")
@Tag(
        name = "Internal Transfers",
        description = "Transfers between customer-owned Bank A accounts"
)
public class InternalTransferCommandController {

    private final InternalTransferApplicationService applicationService;

    public InternalTransferCommandController(
            InternalTransferApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Operation(
            summary = "Transfer money between own Bank A accounts",
            description = """
                    Transfers money atomically between two active Bank A accounts
                    owned by the authenticated customer.

                    The source and destination accounts must use the same currency.
                    A repeated idempotency key with the same request content returns
                    the previously completed transfer.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Internal transfer completed"),
            @ApiResponse(
                    responseCode = "200",
                    description = "Previously completed transfer returned"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"),
            @ApiResponse(
                    responseCode = "403",
                    description = "CUSTOMER role required"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found or not owned by customer"),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency key reused with different content"),
            @ApiResponse(
                    responseCode = "422",
                    description = "Inactive account, insufficient funds or currency mismatch")
    })
    @PostMapping
    public ResponseEntity<InternalTransferResponse> createInternalTransfer(
            @Valid @RequestBody CreateInternalTransferRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        InternalTransferCommand command = new InternalTransferCommand(
                request.amount(),
                request.currency(),
                request.fromAccountId(),
                request.toAccountId(),
                request.idempotencyKey());

        InternalTransferResult result = applicationService.execute(
                jwt.getSubject(),
                command);

        InternalTransferResponse response = InternalTransferResponse.from(
                result.transfer(),
                result.replayed());

        if (result.replayed()) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
