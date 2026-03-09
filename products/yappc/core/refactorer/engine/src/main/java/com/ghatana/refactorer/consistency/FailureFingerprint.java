package com.ghatana.refactorer.consistency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Generates and compares fingerprints for actions to detect regressions.
 * Uses SHA-256 for fingerprint generation.
 
 * @doc.type class
 * @doc.purpose Handles failure fingerprint operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class FailureFingerprint {
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * Generates a fingerprint for the given content, rule, and context.
     *
     * @param content The content to fingerprint
     * @param rule    The rule ID that triggered the action
     * @param context Additional context about the action
     * @return A base64-encoded fingerprint string
     */
    public String generateFingerprint(String content, String rule, String context) {
        Objects.requireNonNull(content, "Content cannot be null");
        Objects.requireNonNull(rule, "Rule cannot be null");
        
        String input = String.format("%s:%s:%s", 
            rule, 
            context != null ? context : "", 
            normalizeContent(content));
        
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return BASE64_ENCODER.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // Should not happen as SHA-256 is required to be available
            throw new RuntimeException("Failed to generate fingerprint", e);
        }
    }

    /**
     * Checks if the given fingerprint matches any known failure.
     *
     * @param fingerprint The fingerprint to check
     * @param maxAgeHours Maximum age of the failure in hours (0 for any age)
     * @return true if the fingerprint matches a known failure within the specified age
     */
    public boolean matchesKnownFailure(String fingerprint, long maxAgeHours) {
        // In a real implementation, this would check against a persistent store
        // For now, we'll just return false as a placeholder
        return false;
    }

    /**
     * Normalizes the content to ensure consistent fingerprinting.
     * Removes whitespace differences and normalizes line endings.
     */
    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("\\s+", " ").trim();
    }

    /**
     * Creates a fingerprint record that can be stored and checked later.
     */
    public static class FingerprintRecord {
        private final String fingerprint;
        private final Instant timestamp;
        private final String filePath;
        private final String ruleId;

        public FingerprintRecord(String fingerprint, Instant timestamp, String filePath, String ruleId) {
            this.fingerprint = Objects.requireNonNull(fingerprint, "Fingerprint cannot be null");
            this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null");
            this.filePath = Objects.requireNonNull(filePath, "File path cannot be null");
            this.ruleId = Objects.requireNonNull(ruleId, "Rule ID cannot be null");
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getRuleId() {
            return ruleId;
        }

        public boolean isExpired(long maxAgeHours) {
            if (maxAgeHours <= 0) {
                return false;
            }
            long seconds = Math.multiplyExact(maxAgeHours, 3600L);
            return timestamp.plusSeconds(seconds).isBefore(Instant.now());
        }
    }
}
