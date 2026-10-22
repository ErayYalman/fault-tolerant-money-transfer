package com.fintech.reconciliation.presentation.rest;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.reconciliation.application.service.ReconciliationExecutionException;
import com.fintech.reconciliation.application.service.ReconciliationRunNotFoundException;
import com.fintech.reconciliation.application.service.ReconciliationRunQueryService;
import com.fintech.reconciliation.application.service.ReconciliationRunService;
import com.fintech.reconciliation.presentation.dto.ManualReconciliationRequest;
import com.fintech.reconciliation.presentation.dto.ReconciliationExecutionError;
import com.fintech.reconciliation.presentation.dto.ReconciliationRunDetailsResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationRunService runService;
    private final ReconciliationRunQueryService queryService;

    public ReconciliationController(
            ReconciliationRunService runService,
            ReconciliationRunQueryService queryService) {
        this.runService = runService;
        this.queryService = queryService;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runManual(
            @Valid @RequestBody ManualReconciliationRequest request) {

        try {
            ReconciliationRunDetailsResponse response = runService.runManual(request.businessDate());

            return ResponseEntity.ok(response);

        } catch (ReconciliationExecutionException exception) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(new ReconciliationExecutionError(
                            exception.getRunId(),
                            "Reconciliation failed. Inspect the run details "
                                    + "using the returned runId."));
        }
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<?> getRun(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(queryService.getById(id));

        } catch (ReconciliationRunNotFoundException exception) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", exception.getMessage()));
        }
    }
}