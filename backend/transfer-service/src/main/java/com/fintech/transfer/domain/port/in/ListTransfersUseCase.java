package com.fintech.transfer.domain.port.in;

import com.fintech.transfer.domain.model.TransferPage;

public interface ListTransfersUseCase {

    TransferPage listByOwner(
            String ownerSubject,
            int page,
            int size
    );
}
