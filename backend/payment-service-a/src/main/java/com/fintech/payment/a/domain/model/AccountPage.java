package com.fintech.payment.a.domain.model;

import java.util.List;

public record AccountPage(
        List<Account> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public AccountPage {
        content = List.copyOf(content);
    }
}
