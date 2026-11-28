package com.fintech.payment.a.domain.port.out;

import com.fintech.payment.a.domain.model.Account;
import com.fintech.payment.a.domain.model.AccountPage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Optional<Account> findById(UUID accountId);

    /**
     * Loads the requested accounts while acquiring pessimistic write locks.
     * The persistence implementation must lock rows in ascending ID order.
     */
    List<Account> findAllByIdsForUpdate(List<UUID> accountIds);

    void update(Account account);

    AccountPage findAllByOwnerSubject(
            String ownerSubject,
            int page,
            int size
    );
}
