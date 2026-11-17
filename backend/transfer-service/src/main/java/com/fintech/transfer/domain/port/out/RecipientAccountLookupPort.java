package com.fintech.transfer.domain.port.out;

import java.util.Optional;

import com.fintech.transfer.domain.model.RecipientAccount;

public interface RecipientAccountLookupPort {

    Optional<RecipientAccount> findByAccountNumber(String accountNumber);
}
