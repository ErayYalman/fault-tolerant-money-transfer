package com.fintech.payment.b.presentation.rest;

import com.fintech.payment.b.application.service.RecipientAccountQueryService;
import com.fintech.payment.b.presentation.dto.RecipientAccountLookupResponse;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/internal/recipient-accounts")
@Produces(MediaType.APPLICATION_JSON)
public class RecipientAccountLookupResource {

    private final RecipientAccountQueryService queryService;

    @Inject
    public RecipientAccountLookupResource(
            RecipientAccountQueryService queryService) {
        this.queryService = queryService;
    }

    @GET
    @Path("/{accountNumber}")
    @RolesAllowed("recipient-lookup")
    public RecipientAccountLookupResponse findByAccountNumber(
            @PathParam("accountNumber") String accountNumber) {

        try {
            return queryService.findByAccountNumber(accountNumber)
                    .map(RecipientAccountLookupResponse::from)
                    .orElseThrow(NotFoundException::new);

        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage());
        }
    }
}
