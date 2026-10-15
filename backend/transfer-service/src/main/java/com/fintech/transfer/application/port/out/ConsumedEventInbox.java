package com.fintech.transfer.application.port.out;

import java.util.UUID;

public interface ConsumedEventInbox {

    boolean tryRegister(
            UUID eventId,
            String eventType,
            UUID correlationId
    );
}