package com.fintech.payment.a.domain.model;

import java.util.List;

public record LedgerEntryPage(
        List<LedgerEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public LedgerEntryPage {
        content = List.copyOf(content);
    }
}
