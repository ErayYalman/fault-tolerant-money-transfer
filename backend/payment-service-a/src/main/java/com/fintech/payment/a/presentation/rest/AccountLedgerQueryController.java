package com.fintech.payment.a.presentation.rest;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fintech.payment.a.application.service.AccountLedgerQueryService;
import com.fintech.payment.a.presentation.dto.LedgerEntryPageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(
        name = "Account Ledger",
        description = "Customer account transaction history"
)
public class AccountLedgerQueryController {

    private final AccountLedgerQueryService accountLedgerQueryService;

    public AccountLedgerQueryController(
            AccountLedgerQueryService accountLedgerQueryService) {
        this.accountLedgerQueryService = accountLedgerQueryService;
    }

    @Operation(
            summary = "Get account ledger",
            description = """
                    Returns paginated ledger entries for a Bank A account.
                    The account must belong to the authenticated customer.
                    Unknown and non-owned accounts both return 404.
                    Entries are ordered from newest to oldest.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account ledger returned"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters or account ID"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "CUSTOMER role required"),
            @ApiResponse(responseCode = "404", description = "Account not found or not owned by authenticated customer")
    })
    @GetMapping("/{accountId}/ledger")
    public ResponseEntity<LedgerEntryPageResponse> findOwnAccountLedger(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(
                    description = "Bank A account UUID",
                    example = "11111111-1111-1111-1111-111111111111",
                    required = true
            )
            @PathVariable UUID accountId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of ledger entries per page", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        try {
            var ledgerPage = accountLedgerQueryService.findOwnedAccountLedger(
                    jwt.getSubject(),
                    accountId,
                    page,
                    size);

            if (ledgerPage.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(
                    LedgerEntryPageResponse.from(ledgerPage.get()));

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception);
        }
    }
}
