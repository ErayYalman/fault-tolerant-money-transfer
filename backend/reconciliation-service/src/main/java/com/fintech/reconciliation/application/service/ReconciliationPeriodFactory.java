package com.fintech.reconciliation.application.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fintech.reconciliation.domain.model.ReconciliationPeriod;

@Component
public class ReconciliationPeriodFactory {

    private final ZoneId zoneId;

    public ReconciliationPeriodFactory(
            @Value("${reconciliation.time-zone:Europe/Istanbul}") String timeZone) {
        this.zoneId = ZoneId.of(timeZone);
    }

    public ReconciliationPeriod forBusinessDate(LocalDate businessDate) {
        if (businessDate == null) {
            throw new IllegalArgumentException(
                    "businessDate must not be null");
        }

        return new ReconciliationPeriod(
                businessDate,
                businessDate.atStartOfDay(zoneId).toInstant(),
                businessDate.plusDays(1).atStartOfDay(zoneId).toInstant());
    }
}