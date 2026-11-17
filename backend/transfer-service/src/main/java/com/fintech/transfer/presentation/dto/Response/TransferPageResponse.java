package com.fintech.transfer.presentation.dto.Response;

import java.util.List;

public record TransferPageResponse(
        List<TransferResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public TransferPageResponse {
        content = List.copyOf(content);
    }
}
