package com.fintech.payment.a.presentation.dto;

import java.util.List;

import com.fintech.payment.a.domain.model.AccountPage;

public record AccountPageResponse(
        List<AccountSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public AccountPageResponse {
        content = List.copyOf(content);
    }

    public static AccountPageResponse from(AccountPage accountPage) {
        return new AccountPageResponse(
                accountPage.content()
                        .stream()
                        .map(AccountSummaryResponse::from)
                        .toList(),
                accountPage.page(),
                accountPage.size(),
                accountPage.totalElements(),
                accountPage.totalPages()
        );
    }
}
