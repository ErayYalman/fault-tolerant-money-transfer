package com.fintech.transfer.presentation.rest;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.GetTransferUseCase;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.presentation.dto.Request.CreateTransferRequest;
import com.fintech.transfer.presentation.dto.Response.TransferResponse;
import com.fintech.transfer.presentation.mapper.TransferApiMapper;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final InitiateTransferUseCase initiateTransferUseCase;
    private final GetTransferUseCase getTransferUseCase;
    private final TransferApiMapper mapper;

    public TransferController(
            InitiateTransferUseCase initiateTransferUseCase,
            GetTransferUseCase getTransferUseCase,
            TransferApiMapper mapper
    ) {
        this.initiateTransferUseCase = initiateTransferUseCase;
        this.getTransferUseCase = getTransferUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @Valid @RequestBody CreateTransferRequest request
    ) {
        InitiateTransferUseCase.CreateTransferResult result =
                initiateTransferUseCase.execute(
                        mapper.toCommand(request)
                );

        TransferResponse response =
                mapper.toResponse(result.transfer());

        if (!result.created()) {
            return ResponseEntity.ok(response);
        }

        URI location = URI.create(
                "/api/v1/transfers/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable UUID id
    ) {
        Transfer transfer = getTransferUseCase.getById(id);

        return ResponseEntity.ok(
                mapper.toResponse(transfer)
        );
    }
}
