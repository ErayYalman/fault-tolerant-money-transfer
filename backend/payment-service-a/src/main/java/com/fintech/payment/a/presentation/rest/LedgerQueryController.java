package com.fintech.payment.a.presentation.rest;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fintech.payment.a.application.service.LedgerQueryService;
import com.fintech.payment.a.presentation.dto.LedgerEntryResponse;

@RestController
@RequestMapping("/internal/ledger")
public class LedgerQueryController {

    private final LedgerQueryService ledgerQueryService;

    public LedgerQueryController(
            LedgerQueryService ledgerQueryService) {
        this.ledgerQueryService = ledgerQueryService;
    }

    @GetMapping
    public List<LedgerEntryResponse> findLedgerEntries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "500") int size) {
        try {
            return ledgerQueryService.findByCreatedAtRange(
                    from,
                    to,
                    page,
                    size).stream()
                    .map(LedgerEntryResponse::from)
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception);
        }
    }
}