package com.fintech.payment.a.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public final class InternalTransferRequestFingerprint {

    private InternalTransferRequestFingerprint() {
    }

    public static String from(InternalTransferCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        String canonicalContent = String.join(
                "\n",
                "bank-a-internal-transfer-v1",
                command.amount().stripTrailingZeros().toPlainString(),
                command.currency().strip().toUpperCase(Locale.ROOT),
                command.fromAccountId().toString(),
                command.toAccountId().toString()
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonicalContent.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception);
        }
    }
}
