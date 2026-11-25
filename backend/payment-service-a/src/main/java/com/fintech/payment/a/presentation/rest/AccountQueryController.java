package com.fintech.payment.a.presentation.rest;

import com.fintech.payment.a.application.service.AccountQueryService;
import com.fintech.payment.a.presentation.dto.AccountPageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(
        name = "Accounts",
        description = "Customer-owned Bank A accounts"
)
public class AccountQueryController {

    private final AccountQueryService accountQueryService;

    public AccountQueryController(
            AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    @Operation(
            summary = "List customer accounts",
            description = """
                    Returns paginated Bank A accounts owned by the authenticated
                    customer. Ownership is derived from the JWT subject claim.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account page returned"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "CUSTOMER role required")
    })
    @GetMapping
    public ResponseEntity<AccountPageResponse> findOwnAccounts(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of accounts per page", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        try {
            var accountPage = accountQueryService.findOwnedAccounts(
                    jwt.getSubject(),
                    page,
                    size
            );

            return ResponseEntity.ok(
                    AccountPageResponse.from(accountPage)
            );

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception
            );
        }
    }
}
