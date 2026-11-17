package com.fintech.transfer.domain.model;

import java.util.List;

public record TransferPage(
        List<Transfer> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public TransferPage {
        content = List.copyOf(content);
    }
}
