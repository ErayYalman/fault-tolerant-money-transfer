package com.fintech.payment.a.application.service;

import com.fintech.payment.a.domain.model.AccountPage;
import com.fintech.payment.a.domain.port.out.AccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;

    public AccountQueryService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public AccountPage findOwnedAccounts(
            String ownerSubject,
            int page,
            int size) {

        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "ownerSubject must not be blank");
        }

        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must not be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        return accountRepository.findAllByOwnerSubject(
                ownerSubject,
                page,
                size
        );
    }
}
