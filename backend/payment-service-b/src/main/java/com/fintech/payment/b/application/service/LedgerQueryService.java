package com.fintech.payment.b.application.service;

import java.time.Instant;
import java.util.List;

import com.fintech.payment.b.domain.model.LedgerEntry;
import com.fintech.payment.b.domain.port.out.LedgerEntryRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class LedgerQueryService {

    private static final int MAX_PAGE_SIZE = 1000;

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerQueryService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public List<LedgerEntry> findByCreatedAtRange(
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size) {

        if (fromInclusive == null || toExclusive == null) {
            throw new IllegalArgumentException(
                    "from and to must not be null");
        }

        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "from must be earlier than to");
        }

        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must not be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        return ledgerEntryRepository.findByCreatedAtRange(
                fromInclusive,
                toExclusive,
                page,
                size);
    }
}