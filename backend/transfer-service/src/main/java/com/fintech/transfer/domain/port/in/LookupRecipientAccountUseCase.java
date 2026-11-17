package com.fintech.transfer.domain.port.in;

import com.fintech.transfer.domain.model.RecipientAccount;

public interface LookupRecipientAccountUseCase {

    RecipientAccount findByAccountNumber(String accountNumber);
}
