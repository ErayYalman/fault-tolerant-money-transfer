package com.fintech.transfer.infrastructure.messaging.kafka;

public final class TransferKafkaTopics {

    private TransferKafkaTopics() {
    }

    // Commands: transfer-service -> payment services
    public static final String DEBIT_REQUESTED =
            "transfer.commands.debit-requested";

    public static final String CREDIT_REQUESTED =
            "transfer.commands.credit-requested";

    public static final String DEBIT_COMPENSATE =
            "transfer.commands.debit-compensate";

    // Events: payment services -> transfer-service
    public static final String DEBIT_COMPLETED =
            "transfer.events.debit-completed";

    public static final String DEBIT_FAILED =
            "transfer.events.debit-failed";

    public static final String CREDIT_COMPLETED =
            "transfer.events.credit-completed";

    public static final String CREDIT_FAILED =
            "transfer.events.credit-failed";

    public static final String DEBIT_COMPENSATED =
            "transfer.events.debit-compensated";
}