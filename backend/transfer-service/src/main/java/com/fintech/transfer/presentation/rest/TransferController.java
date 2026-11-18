package com.fintech.transfer.presentation.rest;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.model.TransferPage;
import com.fintech.transfer.domain.port.in.GetTransferUseCase;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.domain.port.in.ListTransfersUseCase;
import com.fintech.transfer.presentation.dto.Request.CreateTransferRequest;
import com.fintech.transfer.presentation.dto.Response.TransferPageResponse;
import com.fintech.transfer.presentation.dto.Response.TransferResponse;
import com.fintech.transfer.presentation.mapper.TransferApiMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Transfer lifecycle management endpoints")
public class TransferController {

        private final InitiateTransferUseCase initiateTransferUseCase;
        private final GetTransferUseCase getTransferUseCase;
        private final ListTransfersUseCase listTransfersUseCase;
        private final TransferApiMapper mapper;

        public TransferController(
                        InitiateTransferUseCase initiateTransferUseCase,
                        GetTransferUseCase getTransferUseCase,
                        ListTransfersUseCase listTransfersUseCase,
                        TransferApiMapper mapper) {
                this.initiateTransferUseCase = initiateTransferUseCase;
                this.getTransferUseCase = getTransferUseCase;
                this.listTransfersUseCase = listTransfersUseCase;
                this.mapper = mapper;
        }

        @Operation(summary = "Create a transfer", description = """
                        Creates a transfer and atomically persists its initial
                        debit command in the Outbox. The initial lifecycle
                        state after command persistence is DEBIT_REQUESTED.
                        An idempotency key can only be reused with the same
                        request content. A repeated request with the same key
                        and the same content returns the existing transfer.
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Transfer successfully created"),
                        @ApiResponse(responseCode = "200", description = "Existing transfer returned for duplicate idempotency key"),
                        @ApiResponse(responseCode = "400", description = "Invalid request"),
                        @ApiResponse(responseCode = "409", description = "Idempotency key was already used with a different request")
        })
        @PostMapping
        public ResponseEntity<TransferResponse> createTransfer(
                        @Valid @RequestBody CreateTransferRequest request,
                        @AuthenticationPrincipal Jwt jwt) {

                InitiateTransferUseCase.CreateTransferResult result = initiateTransferUseCase.execute(
                                mapper.toCommand(request, jwt.getSubject()));

                TransferResponse response = mapper.toResponse(result.transfer());

                if (!result.created()) {
                        return ResponseEntity.ok(response);
                }

                URI location = URI.create(
                                "/api/v1/transfers/" + response.transferId());

                return ResponseEntity
                                .created(location)
                                .body(response);
        }

        @Operation(summary = "Get transfer by ID", description = "Returns a transfer and its current lifecycle state.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Transfer found"),
                        @ApiResponse(responseCode = "404", description = "Transfer not found")
        })
        @GetMapping("/{id}")
        public ResponseEntity<TransferResponse> getTransfer(
                        @Parameter(description = "Transfer UUID", required = true) @PathVariable UUID id,
                        @AuthenticationPrincipal Jwt jwt) {

                Transfer transfer = getTransferUseCase.getById(
                                id,
                                jwt.getSubject());

                return ResponseEntity.ok(
                                mapper.toResponse(transfer));
        }

        @GetMapping
        public ResponseEntity<TransferPageResponse> listTransfers(
                        @AuthenticationPrincipal Jwt jwt,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {

                TransferPage transferPage = listTransfersUseCase.listByOwner(
                                jwt.getSubject(),
                                page,
                                size);

                TransferPageResponse response = new TransferPageResponse(
                                transferPage.content()
                                                .stream()
                                                .map(mapper::toResponse)
                                                .toList(),
                                transferPage.page(),
                                transferPage.size(),
                                transferPage.totalElements(),
                                transferPage.totalPages());

                return ResponseEntity.ok(response);
        }
}