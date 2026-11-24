package com.fintech.payment.a.presentation.dto;

import java.util.List;

import com.fintech.payment.a.domain.model.LedgerEntryPage;

public record LedgerEntryPageResponse(
        List<LedgerEntryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public LedgerEntryPageResponse {
        content = List.copyOf(content);
    }

    public static LedgerEntryPageResponse from(
            LedgerEntryPage ledgerPage) {

        return new LedgerEntryPageResponse(
                ledgerPage.content()
                        .stream()
                        .map(LedgerEntryResponse::from)
                        .toList(),
                ledgerPage.page(),
                ledgerPage.size(),
                ledgerPage.totalElements(),
                ledgerPage.totalPages()
        );
    }
}
