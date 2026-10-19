package com.fintech.payment.a.infrastructure.messaging.outbox;

import com.fintech.payment.a.application.outbox.OutboxRelay;
import com.fintech.payment.a.application.port.out.OutboxMessageTransport;
import com.fintech.payment.a.application.port.out.OutboxRelayStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "app.kafka.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxRelayConfiguration {

    @Bean
    public OutboxRelay outboxRelay(
            OutboxRelayStore outboxStore,
            OutboxMessageTransport transport,
            Clock clock
    ) {
        return new OutboxRelay(outboxStore, transport, clock);
    }
}