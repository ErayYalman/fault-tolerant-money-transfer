package com.fintech.reconciliation.presentation.dto;

import java.util.List;

import com.fintech.reconciliation.domain.model.ReconciliationRunPage;

public record ReconciliationRunsPageResponse(
        List<ReconciliationRunSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ReconciliationRunsPageResponse {
        content = List.copyOf(content);
    }

    public static ReconciliationRunsPageResponse from(
            ReconciliationRunPage result) {

        return new ReconciliationRunsPageResponse(
                result.content()
                        .stream()
                        .map(ReconciliationRunSummaryResponse::from)
                        .toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
