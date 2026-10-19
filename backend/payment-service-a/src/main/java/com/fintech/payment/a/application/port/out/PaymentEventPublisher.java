package com.fintech.payment.a.application.port.out;

import com.fintech.payment.a.application.messaging.OutboundMessage;

public interface PaymentEventPublisher {

    void append(OutboundMessage message);
}