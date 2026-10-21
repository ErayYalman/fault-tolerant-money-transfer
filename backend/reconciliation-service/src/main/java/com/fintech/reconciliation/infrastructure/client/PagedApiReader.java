package com.fintech.reconciliation.infrastructure.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

public final class PagedApiReader {

    private final int pageSize;

    public PagedApiReader(int pageSize) {
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException(
                    "pageSize must be between 1 and 1000");
        }

        this.pageSize = pageSize;
    }

    public <T> List<T> readAll(IntFunction<List<T>> fetchPage) {
        List<T> results = new ArrayList<>();

        for (int page = 0;; page++) {
            List<T> currentPage = fetchPage.apply(page);

            if (currentPage == null) {
                throw new IllegalStateException(
                        "Remote API returned an empty response body");
            }

            results.addAll(currentPage);

            if (currentPage.size() < pageSize) {
                return List.copyOf(results);
            }

            if (page == Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "Remote API pagination exceeded the supported range");
            }
        }
    }
}