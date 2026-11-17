package com.fintech.transfer.presentation.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.transfer.domain.port.in.LookupRecipientAccountUseCase;
import com.fintech.transfer.presentation.dto.RecipientLookupResponse;

@RestController
@RequestMapping("/api/v1/recipients")
public class RecipientLookupController {

    private final LookupRecipientAccountUseCase lookupRecipientAccountUseCase;

    public RecipientLookupController(
            LookupRecipientAccountUseCase lookupRecipientAccountUseCase) {
        this.lookupRecipientAccountUseCase = lookupRecipientAccountUseCase;
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<RecipientLookupResponse> findRecipient(
            @PathVariable String accountNumber) {

        var recipient = lookupRecipientAccountUseCase
                .findByAccountNumber(accountNumber);

        return ResponseEntity.ok(
                RecipientLookupResponse.from(recipient));
    }
}
