package com.fintech.transfer.application.port.out;

import com.fintech.transfer.application.messaging.OutboundMessage;

public interface DomainEventPublisher {

    void publish(OutboundMessage message);
}