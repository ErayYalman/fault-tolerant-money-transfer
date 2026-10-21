package com.fintech.payment.a.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.payment.a.domain.model.LedgerEntry;
import com.fintech.payment.a.domain.port.out.LedgerEntryRepository;

@Service
public class LedgerQueryService {

    private static final int MAX_PAGE_SIZE = 1000;

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerQueryService(
            LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> findByCreatedAtRange(
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size) {
        Objects.requireNonNull(fromInclusive, "from must not be null");
        Objects.requireNonNull(toExclusive, "to must not be null");

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