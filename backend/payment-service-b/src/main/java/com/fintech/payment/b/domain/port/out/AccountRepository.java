package com.fintech.payment.b.domain.port.out;

import com.fintech.payment.b.domain.model.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Optional<Account> findById(UUID accountId);

    void update(Account account);
}