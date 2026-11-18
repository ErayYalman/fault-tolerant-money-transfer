package com.fintech.transfer.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import com.fintech.transfer.domain.port.in.InitiateTransferUseCase.InitiateTransferCommand;

public final class TransferRequestFingerprint {

    private TransferRequestFingerprint() {
    }

    public static String from(InitiateTransferCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        String canonicalContent = String.join(
                "\n",
                "transfer-create-v1",
                command.amount().stripTrailingZeros().toPlainString(),
                command.currency().toUpperCase(Locale.ROOT),
                command.fromAccountId().toString(),
                command.toAccountNumber()
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonicalContent.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }
}
