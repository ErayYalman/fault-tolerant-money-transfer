package com.fintech.payment.b.application.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.fintech.payment.b.application.messaging.command.CreditRequestedCommand;

public final class CommandFingerprint {

    private CommandFingerprint() {
    }

    public static String forCredit(CreditRequestedCommand command) {
        String canonicalContent = String.join(
                "\n",
                "credit-requested",
                command.transferId().toString(),
                command.correlationId().toString(),
                command.accountId().toString(),
                command.amount().stripTrailingZeros().toPlainString(),
                command.currency().strip().toUpperCase(Locale.ROOT));

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