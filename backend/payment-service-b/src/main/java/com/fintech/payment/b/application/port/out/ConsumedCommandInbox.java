package com.fintech.payment.b.application.port.out;

import com.fintech.payment.b.application.messaging.ConsumedCommand;

public interface ConsumedCommandInbox {

    /**
     * true: yeni mantıksal işlem kaydedildi.
     * false: aynı içerikte mantıksal işlem daha önce işlendi.
     *
     * Bir anahtar veya mantıksal işlem farklı içerikle kullanılırsa
     * IdempotencyConflictException fırlatılır.
     */
    boolean tryRegister(ConsumedCommand command);
}