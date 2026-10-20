package com.fintech.payment.b.infrastructure.messaging.kafka;

public final class PaymentBKafkaTopics {

    private PaymentBKafkaTopics() {
    }

    public static final String CREDIT_REQUESTED = "transfer.commands.credit-requested";

    public static final String CREDIT_COMPLETED = "transfer.events.credit-completed";

    public static final String CREDIT_FAILED = "transfer.events.credit-failed";
}