package com.fintech.payment.a.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.payment.a.domain.model.LedgerEntryPage;
import com.fintech.payment.a.domain.port.out.AccountRepository;
import com.fintech.payment.a.domain.port.out.LedgerEntryRepository;

@Service
public class AccountLedgerQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountLedgerQueryService(
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public Optional<LedgerEntryPage> findOwnedAccountLedger(
            String ownerSubject,
            UUID accountId,
            int page,
            int size) {

        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "ownerSubject must not be blank");
        }

        if (accountId == null) {
            throw new IllegalArgumentException(
                    "accountId must not be null");
        }

        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must not be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        boolean accountOwnedByCustomer = accountRepository
                .findById(accountId)
                .map(account -> account.isOwnedBy(ownerSubject))
                .orElse(false);

        if (!accountOwnedByCustomer) {
            return Optional.empty();
        }

        return Optional.of(
                ledgerEntryRepository.findPageByAccountId(
                        accountId,
                        page,
                        size));
    }
}
