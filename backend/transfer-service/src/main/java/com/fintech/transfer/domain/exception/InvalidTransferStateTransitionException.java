package com.fintech.transfer.domain.exception;

import com.fintech.transfer.domain.model.TransferStatus;

public class InvalidTransferStateTransitionException
        extends RuntimeException {

    public InvalidTransferStateTransitionException(
            TransferStatus current,
            TransferStatus target
    ) {
        super(
                "Invalid transfer state transition: "
                        + current
                        + " -> "
                        + target
        );
    }
}