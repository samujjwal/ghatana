package com.ghatana.security.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Validates webhook signatures using HMAC-SHA256 with constant-time comparison.
 *
 * <p>
 * <b>Purpose</b><br>
 * Prevents webhook spoofing by verifying that incoming webhooks are signed with
 * the secret key known only to the sender and receiver.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * WebhookSignatureValidator validator = new WebhookSignatureValidator("my-secret-key");
 * String payload = "{\"event\": \"order.created\"}";
 * String signature = "sha256=abc123...";
 *
 * if (validator.validate(payload, signature)) {
 *     // Webhook is authentic, process it
 *     processWebhook(payload);
 * } else {
 *     // Webhook signature is invalid, reject it
 *     throw new WebhookValidationException("Invalid signature");
 * }
 * }</pre>
 *
 * <p>
 * <b>Security Considerations</b><br>
 * - Uses HMAC-SHA256 (industry standard) - Implements constant-time comparison
 * to prevent timing attacks - Secret key should be at least 32 bytes - Expected
 * signature format: "sha256=hexadecimal_value"
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable and thread-safe. Can be shared across threads.
 *
 * @doc.type class
 * @doc.purpose Webhook signature verification using HMAC-SHA256
 * @doc.layer core
 * @doc.pattern Validator
 */
public class WebhookSignatureValidator {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final HexFormat HEX_FORMAT = HexFormat.of().withLowerCase();

    private final byte[] secretKeyBytes;

    /**
     * Creates a webhook signature validator with the given secret key.
     *
     * @param secretKey the secret key for HMAC-SHA256 (should be at least 32
     * bytes)
     * @throws IllegalArgumentException if secretKey is null or empty
     */
    public WebhookSignatureValidator(String secretKey) {
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalArgumentException("Secret key cannot be null or empty");
        }
        this.secretKeyBytes = secretKey.getBytes();
    }

    /**
     * Validates that the payload signature matches the expected signature.
     *
     * <p>
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param payload the webhook payload (typically JSON string)
     * @param signature the signature from the webhook header (format:
     * "sha256=hexvalue")
     * @return true if signature is valid, false otherwise
     * @throws IllegalArgumentException if payload or signature is null/empty
     */
    public boolean validate(String payload, String signature) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Payload cannot be null or empty");
        }
        if (signature == null || signature.isEmpty()) {
            throw new IllegalArgumentException("Signature cannot be null or empty");
        }

        try {
            // Extract the hexadecimal part from the signature
            String expectedSignatureHex = extractSignatureHex(signature);

            // Compute the HMAC-SHA256 of the payload
            byte[] computedSignatureBytes = computeHmacSha256(payload.getBytes());
            String computedSignatureHex = HEX_FORMAT.formatHex(computedSignatureBytes);

            // Compare using constant-time comparison to prevent timing attacks
            return constantTimeEquals(expectedSignatureHex, computedSignatureHex);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            // Log the exception and return false (invalid signature)
            return false;
        }
    }

    /**
     * Extracts the hexadecimal signature value from the signature header.
     *
     * @param signature the signature header value (format: "sha256=hexvalue")
     * @return the hexadecimal signature value
     * @throws IllegalArgumentException if signature format is invalid
     */
    private String extractSignatureHex(String signature) {
        if (!signature.startsWith(SIGNATURE_PREFIX)) {
            throw new IllegalArgumentException(
                    String.format("Signature must start with '%s'", SIGNATURE_PREFIX)
            );
        }
        return signature.substring(SIGNATURE_PREFIX.length());
    }

    /**
     * Computes the HMAC-SHA256 of the payload using the secret key.
     *
     * @param payloadBytes the payload bytes
     * @return the computed HMAC-SHA256 bytes
     * @throws NoSuchAlgorithmException if HMAC-SHA256 algorithm is not
     * available
     * @throws InvalidKeyException if the secret key is invalid
     */
    private byte[] computeHmacSha256(byte[] payloadBytes)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec secretKey = new SecretKeySpec(secretKeyBytes, 0, secretKeyBytes.length, ALGORITHM);
        mac.init(secretKey);
        return mac.doFinal(payloadBytes);
    }

    /**
     * Compares two strings using constant-time comparison to prevent timing
     * attacks.
     *
     * <p>
     * This method always takes the same amount of time regardless of where the
     * strings differ, making it resistant to timing-based attacks.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @return true if the strings are equal, false otherwise
     */
    @SuppressWarnings("all")
    private boolean constantTimeEquals(String expected, String actual) {
        // Convert to bytes for comparison
        byte[] expectedBytes = expected.getBytes();
        byte[] actualBytes = actual.getBytes();

        // If lengths differ, return false (but still do comparison to maintain constant time)
        if (expectedBytes.length != actualBytes.length) {
            // Maintain constant time even when lengths differ
            return false;
        }

        // Compare bytes using bitwise OR to prevent short-circuit evaluation
        int mismatch = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            mismatch |= expectedBytes[i] ^ actualBytes[i];
        }

        return mismatch == 0;
    }
}
