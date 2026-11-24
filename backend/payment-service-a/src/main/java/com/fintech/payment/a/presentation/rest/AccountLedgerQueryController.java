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

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountLedgerQueryController {

    private final AccountLedgerQueryService accountLedgerQueryService;

    public AccountLedgerQueryController(
            AccountLedgerQueryService accountLedgerQueryService) {
        this.accountLedgerQueryService = accountLedgerQueryService;
    }

    @GetMapping("/{accountId}/ledger")
    public ResponseEntity<LedgerEntryPageResponse> findOwnAccountLedger(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
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
