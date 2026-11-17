package com.fintech.payment.b.application.service;

import java.util.Optional;

import com.fintech.payment.b.domain.model.Account;
import com.fintech.payment.b.domain.port.out.AccountRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RecipientAccountQueryService {

    private static final int MAX_ACCOUNT_NUMBER_LENGTH = 50;

    private final AccountRepository accountRepository;

    public RecipientAccountQueryService(
            AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Optional<Account> findByAccountNumber(String accountNumber) {

        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "accountNumber must not be blank"
            );
        }

        String normalized = accountNumber.strip();

        if (normalized.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new IllegalArgumentException(
                    "accountNumber must not exceed 50 characters"
            );
        }

        return accountRepository.findByAccountNumber(normalized);
    }
}
