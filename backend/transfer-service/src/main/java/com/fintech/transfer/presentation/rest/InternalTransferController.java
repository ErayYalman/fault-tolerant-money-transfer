package com.fintech.transfer.presentation.rest;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fintech.transfer.application.service.TransferLifecycleQueryService;
import com.fintech.transfer.presentation.dto.Response.InternalTransferResponse;

@RestController
@RequestMapping("/internal/transfers")
public class InternalTransferController {

    private final TransferLifecycleQueryService queryService;

    public InternalTransferController(
            TransferLifecycleQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<InternalTransferResponse> findTransfers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "500") int size) {

        try {
            return queryService.findByLifecycleRange(
                    from,
                    to,
                    page,
                    size)
                    .stream()
                    .map(InternalTransferResponse::from)
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception);
        }
    }
}