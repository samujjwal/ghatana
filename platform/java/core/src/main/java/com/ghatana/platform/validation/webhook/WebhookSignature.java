/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation.webhook;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * HMAC-SHA256 based webhook signature generator and verifier.
 *
 * @doc.type class
 * @doc.purpose HMAC-SHA256 based webhook signature generation and constant-time verification
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class WebhookSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private final String signature;

    private WebhookSignature(@NotNull String signature) {
        this.signature = Objects.requireNonNull(signature, "Signature cannot be null");
    }

    @NotNull
    public static WebhookSignature generate(@NotNull byte[] payload, @NotNull String secret) {
        Objects.requireNonNull(payload, "Payload cannot be null");
        if (secret.isEmpty()) {
            throw new IllegalArgumentException("Webhook secret cannot be empty");
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    0,
                    secret.length(),
                    ALGORITHM
            );
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(payload);
            String hexSignature = toHexString(hmac);
            return new WebhookSignature(hexSignature);
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("HMAC-SHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new SecurityException("Invalid webhook secret key", e);
        }
    }

    @NotNull
    public static WebhookSignature generate(@NotNull String payload, @NotNull String secret) {
        Objects.requireNonNull(payload, "Payload cannot be null");
        return generate(payload.getBytes(StandardCharsets.UTF_8), secret);
    }

    public boolean verify(@NotNull String receivedSignature) {
        if (receivedSignature.isEmpty()) {
            return false;
        }
        String expected = this.signature.toLowerCase(Locale.ROOT);
        String received = receivedSignature.toLowerCase(Locale.ROOT);
        return constantTimeEquals(expected, received);
    }

    @NotNull
    public String getSignature() {
        return signature;
    }

    @NotNull
    public String asHexString() {
        return signature;
    }

    private static String toHexString(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            String hexByte = Integer.toHexString(0xff & b);
            if (hexByte.length() == 1) {
                hex.append('0');
            }
            hex.append(hexByte);
        }
        return hex.toString();
    }

    private static boolean constantTimeEquals(@NotNull String a, @NotNull String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}
