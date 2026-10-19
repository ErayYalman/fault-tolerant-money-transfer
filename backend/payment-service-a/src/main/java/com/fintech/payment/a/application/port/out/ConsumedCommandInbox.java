package com.fintech.payment.a.application.port.out;

import com.fintech.payment.a.application.messaging.ConsumedCommand;

public interface ConsumedCommandInbox {

    /**
     * Returns true for a newly registered command and false for an identical
     * logical operation that was registered previously.
     *
     * Throws when a key or logical operation is reused with different content.
     */
    boolean tryRegister(ConsumedCommand command);
}