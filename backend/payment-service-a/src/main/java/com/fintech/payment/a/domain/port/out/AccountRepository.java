package com.fintech.payment.a.domain.port.out;

import com.fintech.payment.a.domain.model.Account;
import com.fintech.payment.a.domain.model.AccountPage;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Optional<Account> findById(UUID accountId);

    void update(Account account);

    AccountPage findAllByOwnerSubject(
            String ownerSubject,
            int page,
            int size
    );
}
