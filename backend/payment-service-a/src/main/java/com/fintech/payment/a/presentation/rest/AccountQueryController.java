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

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountQueryController {

    private final AccountQueryService accountQueryService;

    public AccountQueryController(
            AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    @GetMapping
    public ResponseEntity<AccountPageResponse> findOwnAccounts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
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
