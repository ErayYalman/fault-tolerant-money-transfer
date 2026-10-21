package com.fintech.payment.b.presentation.rest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.fintech.payment.b.application.service.LedgerQueryService;
import com.fintech.payment.b.presentation.dto.LedgerEntryResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/internal/ledger")
@Produces(MediaType.APPLICATION_JSON)
public class LedgerQueryResource {

    private final LedgerQueryService ledgerQueryService;

    @Inject
    public LedgerQueryResource(LedgerQueryService ledgerQueryService) {
        this.ledgerQueryService = ledgerQueryService;
    }

    @GET
    public List<LedgerEntryResponse> findLedgerEntries(
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("500") int size) {

        if (from == null || from.isBlank()
                || to == null || to.isBlank()) {
            throw new BadRequestException(
                    "from and to query parameters are required");
        }

        Instant fromInclusive = parseTimestamp(from, "from");
        Instant toExclusive = parseTimestamp(to, "to");

        try {
            return ledgerQueryService.findByCreatedAtRange(
                    fromInclusive,
                    toExclusive,
                    page,
                    size)
                    .stream()
                    .map(LedgerEntryResponse::from)
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage());
        }
    }

    private Instant parseTimestamp(String value, String parameterName) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(
                    parameterName
                            + " must be an ISO-8601 timestamp with timezone");
        }
    }
}