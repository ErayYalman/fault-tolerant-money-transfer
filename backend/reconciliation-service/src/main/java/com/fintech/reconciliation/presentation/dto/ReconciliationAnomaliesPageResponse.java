package com.fintech.reconciliation.presentation.dto;

import java.time.LocalDate;
import java.util.List;

import com.fintech.reconciliation.domain.model.ReconciliationAnomalyPage;

public record ReconciliationAnomaliesPageResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Boolean isAnomaly,
        List<ReconciliationAnomalyResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public ReconciliationAnomaliesPageResponse {
        content = List.copyOf(content);
    }

    public static ReconciliationAnomaliesPageResponse from(
            LocalDate fromDate,
            LocalDate toDate,
            Boolean isAnomaly,
            ReconciliationAnomalyPage result) {

        List<ReconciliationAnomalyResponse> content = result.content()
                .stream()
                .map(ReconciliationAnomalyResponse::from)
                .toList();

        return new ReconciliationAnomaliesPageResponse(
                fromDate,
                toDate,
                isAnomaly,
                content,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
