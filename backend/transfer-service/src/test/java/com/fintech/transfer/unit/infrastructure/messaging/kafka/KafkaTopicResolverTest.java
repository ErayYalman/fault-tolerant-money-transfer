package com.fintech.transfer.unit.infrastructure.messaging.kafka;

import com.fintech.transfer.infrastructure.messaging.kafka.KafkaTopicResolver;
import com.fintech.transfer.infrastructure.messaging.kafka.TransferKafkaTopics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaTopicResolverTest {

    private final KafkaTopicResolver resolver =
            new KafkaTopicResolver();

    @Test
    void shouldResolveAllCanonicalMessageTypes() {
        assertThat(resolver.resolve("debit-requested"))
                .isEqualTo(TransferKafkaTopics.DEBIT_REQUESTED);

        assertThat(resolver.resolve("credit-requested"))
                .isEqualTo(TransferKafkaTopics.CREDIT_REQUESTED);

        assertThat(resolver.resolve("debit-compensate"))
                .isEqualTo(TransferKafkaTopics.DEBIT_COMPENSATE);

        assertThat(resolver.resolve("debit-completed"))
                .isEqualTo(TransferKafkaTopics.DEBIT_COMPLETED);

        assertThat(resolver.resolve("debit-failed"))
                .isEqualTo(TransferKafkaTopics.DEBIT_FAILED);

        assertThat(resolver.resolve("credit-completed"))
                .isEqualTo(TransferKafkaTopics.CREDIT_COMPLETED);

        assertThat(resolver.resolve("credit-failed"))
                .isEqualTo(TransferKafkaTopics.CREDIT_FAILED);

        assertThat(resolver.resolve("debit-compensated"))
                .isEqualTo(TransferKafkaTopics.DEBIT_COMPENSATED);
    }

    @Test
    void shouldRejectUnknownMessageType() {
        assertThatThrownBy(() -> resolver.resolve("unknown-message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported outbox message type");
    }
}