package com.fintech.reconciliation.infrastructure.client;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fintech.reconciliation.infrastructure.client.dto.TransferSnapshot;

@Component
public class TransferServiceClient {

    private static final ParameterizedTypeReference<List<TransferSnapshot>> RESPONSE_TYPE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final PagedApiReader pagedApiReader;
    private final int pageSize;

    public TransferServiceClient(
            RestClient.Builder builder,
            @Value("${reconciliation.clients.transfer-base-url}") String baseUrl,
            @Value("${reconciliation.clients.page-size:500}") int pageSize) {

        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .build();

        this.pagedApiReader = new PagedApiReader(pageSize);
        this.pageSize = pageSize;
    }

    public List<TransferSnapshot> findTransfers(
            Instant fromInclusive,
            Instant toExclusive) {

        validateRange(fromInclusive, toExclusive);

        return pagedApiReader.readAll(page -> fetchPage(fromInclusive, toExclusive, page));
    }

    private List<TransferSnapshot> fetchPage(
            Instant fromInclusive,
            Instant toExclusive,
            int page) {

        List<TransferSnapshot> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/transfers")
                        .queryParam("from", fromInclusive.toString())
                        .queryParam("to", toExclusive.toString())
                        .queryParam("page", page)
                        .queryParam("size", pageSize)
                        .build())
                .retrieve()
                .body(RESPONSE_TYPE);

        if (response == null) {
            throw new IllegalStateException(
                    "Transfer Service returned no response body");
        }

        return response;
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException(
                    "from must be earlier than to");
        }
    }
}