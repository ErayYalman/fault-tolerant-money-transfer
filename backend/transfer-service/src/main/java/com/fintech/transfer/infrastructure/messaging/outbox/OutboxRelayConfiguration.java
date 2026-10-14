package com.fintech.transfer.infrastructure.messaging.outbox;

import com.fintech.transfer.application.outbox.OutboxRelay;
import com.fintech.transfer.application.port.out.OutboxMessageTransport;
import com.fintech.transfer.application.port.out.OutboxRelayStore;
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
            OutboxRelayStore outboxRelayStore,
            OutboxMessageTransport transport,
            Clock clock
    ) {
        return new OutboxRelay(
                outboxRelayStore,
                transport,
                clock
        );
    }
}