package com.ghatana.refactorer.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * Core memory models for tracking diagnostic outcomes, confidence scores, and blocklisted items.
 *
 * <p>These models support the memory and consistency layer by providing:
 *
 * <ul>
 *   <li>Outcome tracking for applied fixes
 *   <li>Confidence scoring for diagnostic rules
 *   <li>Blocklist management with TTL
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Track an outcome
 * Outcome outcome = Outcome.APPLIED;
 *
 * // Create confidence score
 * Confidence confidence = new Confidence("F401", 0.95);
 *
 * // Create blocklist entry
 * Blocklisted blocked = new Blocklisted("fingerprint123", Instant.now().plusDays(30));
 * }</pre>
 
 * @doc.type class
 * @doc.purpose Handles memory models operations
 * @doc.layer core
 * @doc.pattern Enum
* @doc.gaa.memory episodic
*/
public final class MemoryModels {

    private MemoryModels() {
        // Utility class
    }

    /**
 * Represents the outcome of applying a diagnostic fix. */
    public enum Outcome {
        /**
 * Fix was successfully applied */
        APPLIED,

        /**
 * Fix was rolled back due to issues */
        ROLLED_BACK,

        /**
 * Fix caused a regression and was reverted */
        REGRESSED,

        /**
 * Fix was skipped (e.g., due to blocklist) */
        SKIPPED
    }

    /**
     * Represents confidence in a diagnostic rule's effectiveness.
     *
     * @param ruleId The diagnostic rule identifier
     * @param score Confidence score between 0.0 and 1.0
     */
    public record Confidence(String ruleId, double score) {
        @JsonCreator
        public Confidence(
                @JsonProperty("ruleId") String ruleId, @JsonProperty("score") double score) {
            this.ruleId = Objects.requireNonNull(ruleId, "ruleId cannot be null");
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException(
                        "Score must be between 0.0 and 1.0, got: " + score);
            }
            this.score = score;
        }

        /**
 * Creates a high confidence score (0.9). */
        public static Confidence high(String ruleId) {
            return new Confidence(ruleId, 0.9);
        }

        /**
 * Creates a medium confidence score (0.6). */
        public static Confidence medium(String ruleId) {
            return new Confidence(ruleId, 0.6);
        }

        /**
 * Creates a low confidence score (0.3). */
        public static Confidence low(String ruleId) {
            return new Confidence(ruleId, 0.3);
        }

        /**
 * Returns true if this is a high confidence score (>= 0.8). */
        public boolean isHigh() {
            return score >= 0.8;
        }

        /**
 * Returns true if this is a low confidence score (<= 0.4). */
        public boolean isLow() {
            return score <= 0.4;
        }
    }

    /**
     * Represents a blocklisted diagnostic fingerprint with expiration.
     *
     * @param fingerprint The diagnostic fingerprint to block
     * @param expiresAt When this blocklist entry expires
     */
    public record Blocklisted(String fingerprint, Instant expiresAt) {
        @JsonCreator
        public Blocklisted(
                @JsonProperty("fingerprint") String fingerprint,
                @JsonProperty("expiresAt") Instant expiresAt) {
            this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        }

        /**
 * Creates a blocklist entry that expires in the specified number of days. */
        public static Blocklisted forDays(String fingerprint, int days) {
            return new Blocklisted(fingerprint, Instant.now().plusSeconds(days * 24L * 3600L));
        }

        /**
 * Creates a blocklist entry that expires in 30 days (default TTL). */
        public static Blocklisted withDefaultTtl(String fingerprint) {
            return forDays(fingerprint, 30);
        }

        /**
 * Returns true if this blocklist entry has expired. */
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        /**
 * Returns true if this blocklist entry is still active. */
        public boolean isActive() {
            return !isExpired();
        }
    }

    /**
     * Represents a diagnostic fingerprint for memory tracking.
     *
     * @param tool The tool that generated the diagnostic (e.g., "ruff", "shellcheck")
     * @param rule The rule ID (e.g., "F401", "SC2086")
     * @param message The diagnostic message
     * @param symbol The symbol or identifier involved (optional)
     * @param pathSuffix The file path suffix for context
     * @param contextWindow A hash of the surrounding context
     */
    public record DiagnosticFingerprint(
            String tool,
            String rule,
            String message,
            String symbol,
            String pathSuffix,
            String contextWindow) {
        @JsonCreator
        public DiagnosticFingerprint(
                @JsonProperty("tool") String tool,
                @JsonProperty("rule") String rule,
                @JsonProperty("message") String message,
                @JsonProperty("symbol") String symbol,
                @JsonProperty("pathSuffix") String pathSuffix,
                @JsonProperty("contextWindow") String contextWindow) {
            this.tool = Objects.requireNonNull(tool, "tool cannot be null");
            this.rule = Objects.requireNonNull(rule, "rule cannot be null");
            this.message = Objects.requireNonNull(message, "message cannot be null");
            this.symbol = symbol; // nullable
            this.pathSuffix = Objects.requireNonNull(pathSuffix, "pathSuffix cannot be null");
            this.contextWindow =
                    Objects.requireNonNull(contextWindow, "contextWindow cannot be null");
        }

        /**
 * Generates a deterministic hash for this fingerprint. */
        public String toHash() {
            return Integer.toHexString(
                    Objects.hash(tool, rule, message, symbol, pathSuffix, contextWindow));
        }
    }
}
