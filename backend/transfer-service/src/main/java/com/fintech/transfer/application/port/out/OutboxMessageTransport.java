package com.fintech.transfer.application.port.out;

import com.fintech.transfer.application.messaging.PendingOutboxMessage;

public interface OutboxMessageTransport {

    void publish(PendingOutboxMessage message);
}