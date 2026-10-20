package com.fintech.payment.b.application.port.out;

import com.fintech.payment.b.application.messaging.OutboundMessage;

public interface PaymentEventPublisher {

    void append(OutboundMessage message);
}