package com.fintech.transfer.application.service;

import org.springframework.stereotype.Service;

import com.fintech.transfer.domain.exception.RecipientAccountNotFoundException;
import com.fintech.transfer.domain.model.RecipientAccount;
import com.fintech.transfer.domain.port.in.LookupRecipientAccountUseCase;
import com.fintech.transfer.domain.port.out.RecipientAccountLookupPort;

@Service
public class RecipientAccountQueryService
        implements LookupRecipientAccountUseCase {

    private static final int MAX_ACCOUNT_NUMBER_LENGTH = 50;

    private final RecipientAccountLookupPort recipientAccountLookupPort;

    public RecipientAccountQueryService(
            RecipientAccountLookupPort recipientAccountLookupPort) {
        this.recipientAccountLookupPort = recipientAccountLookupPort;
    }

    @Override
    public RecipientAccount findByAccountNumber(String accountNumber) {

        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "accountNumber must not be blank");
        }

        String normalized = accountNumber.strip();

        if (normalized.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new IllegalArgumentException(
                    "accountNumber must not exceed 50 characters");
        }

        return recipientAccountLookupPort.findByAccountNumber(normalized)
                .orElseThrow(
                        () -> new RecipientAccountNotFoundException(normalized));
    }
}
