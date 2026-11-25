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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reconciliation")
@Tag(
        name = "Reconciliation",
        description = "T+1 reconciliation and anomaly analysis operations"
)
public class ReconciliationController {

    private final ReconciliationRunService runService;
    private final ReconciliationRunQueryService queryService;

    public ReconciliationController(
            ReconciliationRunService runService,
            ReconciliationRunQueryService queryService) {
        this.runService = runService;
        this.queryService = queryService;
    }

    @Operation(
            summary = "Execute manual reconciliation",
            description = """
                    Executes reconciliation for the supplied business date.
                    Persists the run, discrepancy records and available anomaly
                    scores, then returns the reconciliation details.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Reconciliation completed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ReconciliationRunDetailsResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid reconciliation request"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "OPERATIONS or ADMIN role required"
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Reconciliation execution failed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ReconciliationExecutionError.class
                            )
                    )
            )
    })
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

    @Operation(
            summary = "Get reconciliation run details",
            description = """
                    Returns the persisted details of a reconciliation run,
                    including discrepancies, individual anomaly scores
                    and the anomaly scoring summary.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Reconciliation run found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ReconciliationRunDetailsResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "OPERATIONS or ADMIN role required"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Reconciliation run not found"
            )
    })
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