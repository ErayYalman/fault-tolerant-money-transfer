package com.fintech.transfer.domain.port.in;

import java.util.UUID;

import com.fintech.transfer.domain.model.Transfer;

public interface GetTransferUseCase {

    Transfer getById(UUID transferId, String ownerSubject);
}