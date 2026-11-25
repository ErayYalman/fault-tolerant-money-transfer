package com.fintech.transfer.presentation.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.transfer.domain.port.in.LookupRecipientAccountUseCase;
import com.fintech.transfer.presentation.dto.RecipientLookupResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/recipients")
@Tag(
        name = "Recipients",
        description = "Recipient account verification"
)
public class RecipientLookupController {

    private final LookupRecipientAccountUseCase lookupRecipientAccountUseCase;

    public RecipientLookupController(
            LookupRecipientAccountUseCase lookupRecipientAccountUseCase) {
        this.lookupRecipientAccountUseCase = lookupRecipientAccountUseCase;
    }

    @Operation(
            summary = "Verify recipient account",
            description = """
                    Looks up a recipient account in Bank B using its
                    account number. Returns the account number, currency
                    and current account status.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipient account found"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "CUSTOMER role required"),
            @ApiResponse(responseCode = "404", description = "Recipient account not found"),
            @ApiResponse(responseCode = "503", description = "Recipient lookup service unavailable")
    })
    @GetMapping("/{accountNumber}")
    public ResponseEntity<RecipientLookupResponse> findRecipient(
            @Parameter(
                    description = "Bank B recipient account number",
                    example = "BANK-B-RECIPIENT-001",
                    required = true
            )
            @PathVariable String accountNumber) {

        var recipient = lookupRecipientAccountUseCase
                .findByAccountNumber(accountNumber);

        return ResponseEntity.ok(
                RecipientLookupResponse.from(recipient));
    }
}
