package com.fintech.reconciliation.domain.model;

import java.util.List;

public record ReconciliationAnomalyPage(
        List<ReconciliationAnomalyRecord> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ReconciliationAnomalyPage {
        content = List.copyOf(content);
    }
}
