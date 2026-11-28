package com.fintech.payment.a.presentation.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request for transferring money between own Bank A accounts")
public record CreateInternalTransferRequest(

        @Schema(
                description = "Transfer amount",
                example = "250.00",
                minimum = "0.0001")
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @Schema(
                description = "ISO 4217 currency code",
                example = "TRY")
        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$")
        String currency,

        @Schema(
                description = "Source Bank A account UUID",
                example = "11111111-1111-1111-1111-111111111111")
        @NotNull
        UUID fromAccountId,

        @Schema(
                description = "Destination Bank A account UUID",
                example = "22222222-2222-2222-2222-222222222222")
        @NotNull
        UUID toAccountId,

        @Schema(
                description = "Client-generated idempotency key",
                example = "bank-a-internal-transfer-001",
                maxLength = 100)
        @NotBlank
        @Size(max = 100)
        String idempotencyKey) {
}
