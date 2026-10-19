package com.fintech.payment.a.infrastructure.messaging.kafka;

public final class PaymentKafkaTopics {

        private PaymentKafkaTopics() {
        }

        public static final String DEBIT_REQUESTED = "transfer.commands.debit-requested";

        public static final String DEBIT_COMPLETED = "transfer.events.debit-completed";

        public static final String DEBIT_FAILED = "transfer.events.debit-failed";

        public static final String DEBIT_COMPENSATE = "transfer.commands.debit-compensate";

        public static final String DEBIT_COMPENSATED = "transfer.events.debit-compensated";
}