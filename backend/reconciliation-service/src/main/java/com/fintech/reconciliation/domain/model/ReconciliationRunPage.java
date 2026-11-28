package com.fintech.reconciliation.domain.model;

import java.util.List;

public record ReconciliationRunPage(
        List<ReconciliationRunRecord> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ReconciliationRunPage {
        content = List.copyOf(content);
    }
}
