package com.fintech.transfer.presentation.dto.Request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request for creating a money transfer")
public record CreateTransferRequest(

        @Schema(
                description = "Transfer amount",
                example = "1500.00",
                minimum = "0.0001"
        )
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @Schema(
                description = "ISO 4217 currency code. MVP supports TRY.",
                example = "TRY"
        )
        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$")
        String currency,

        @Schema(
                description = "Source account UUID belonging to Bank A",
                example = "11111111-1111-1111-1111-111111111111"
        )
        @NotNull
        UUID fromAccountId,

        @Schema(
                description = "Target account UUID belonging to Bank B",
                example = "22222222-2222-2222-2222-222222222222"
        )
        @NotNull
        UUID toAccountId,

        @Schema(
                description = "Client-generated idempotency key",
                example = "demo-transfer-001"
        )
        @NotBlank
        String idempotencyKey
) {
}