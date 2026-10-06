package com.fintech.transfer.domain.port.in;

import com.fintech.transfer.domain.model.Transfer;

import java.util.UUID;

public interface GetTransferUseCase {

    Transfer getById(UUID transferId);
}