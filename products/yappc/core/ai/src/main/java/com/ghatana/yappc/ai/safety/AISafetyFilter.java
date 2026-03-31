/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — AI Safety Filter
 */
package com.ghatana.yappc.ai.safety;

import com.ghatana.yappc.ai.router.AIResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Content safety filter applied to all AI model responses before they are returned to callers.
 *
 * <p>Enforces three safety categories:
 * <ol>
 *   <li><b>HARMFUL_CONTENT</b> — responses containing instructions for violence, self-harm, or
 *       illegal activity are rejected.</li>
 *   <li><b>PII_LEAKAGE</b> — responses that appear to echo back personal identifiable information
 *       patterns (SSNs, card numbers, credentials) are redacted and flagged.</li>
 *   <li><b>PROMPT_INJECTION</b> — responses containing injection-style markers (e.g.
 *       {@code ignore previous instructions}) are rejected before reaching the caller.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AISafetyFilter filter = new AISafetyFilter(AISafetyFilter.Config.defaults());
 * filter.check(aiResponse)
 *       .whenResult(checked -> renderSuggestions(checked))
 *       .whenException(SafetyViolationException.class, ex -> log.warn("Blocked: {}", ex.category()));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Content safety gate for all AI model responses
 * @doc.layer product
 * @doc.pattern Filter, Guard
 */
public final class AISafetyFilter {

    private static final Logger log = LoggerFactory.getLogger(AISafetyFilter.class);

    // ── PII patterns ─────────────────────────────────────────────────────────

    /** US Social Security Number: 123-45-6789 or 123456789 */
    private static final Pattern SSN_PATTERN =
            Pattern.compile("\\b\\d{3}[- ]?\\d{2}[- ]?\\d{4}\\b");

    /** Payment card (13-19 consecutive digits, simplified Luhn check excluded for pattern speed) */
    private static final Pattern CARD_PATTERN =
            Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

    /** API key / secret heuristic: high-entropy alphanumeric 32+ chars */
    private static final Pattern SECRET_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9+/]{32,}(?![A-Za-z0-9])");

    // ── Injection markers ─────────────────────────────────────────────────────

    private static final List<String> INJECTION_MARKERS = List.of(
            "ignore previous instructions",
            "ignore all prior instructions",
            "disregard the above",
            "forget your instructions",
            "new instructions:",
            "you are now",
            "act as jailbreak",
            "developer mode enabled",
            "dan mode"
    );

    // ── Harmful content keywords (minimal, non-exhaustive block-list) ─────────

    private static final List<Pattern> HARMFUL_PATTERNS = List.of(
            Pattern.compile("\\b(make|build|construct|synthesize)\\s+(a\\s+)?(bomb|weapon|malware|ransomware|exploit)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(step[- ]by[- ]step|how to)\\s+(kill|poison|hack|crack)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bself[- ]?harm\\s+(method|technique|instruction)\\b",
                    Pattern.CASE_INSENSITIVE)
    );

    private final Config config;

    /** Create a safety filter with the given configuration. */
    public AISafetyFilter(Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Checks an AI response for safety violations.
     *
     * <p>If the response is safe, a {@link Promise} resolving to the (potentially
     * PII-redacted) response text is returned. If a {@code HARMFUL_CONTENT} or
     * {@code PROMPT_INJECTION} violation is detected, the promise fails with a
     * {@link SafetyViolationException}.
     *
     * @param response the raw AI model response
     * @return promise of the cleaned response text, or failure if blocked
     */
    public Promise<String> check(AIResponse response) {
        Objects.requireNonNull(response, "response");
        return check(response.getContent());
    }

    /**
     * Checks a plain text string (e.g. a suggestion body) instead of a full {@link AIResponse}.
     *
     * @param text raw text to evaluate
     * @return promise of the cleaned text, or failure if blocked
     */
    public Promise<String> check(String text) {
        if (text == null || text.isBlank()) {
            return Promise.of("");
        }

        // 1. Prompt injection check
        if (config.blockPromptInjection() && containsInjectionMarker(text)) {
            log.warn("AI safety BLOCKED: prompt injection detected");
            return Promise.ofException(new SafetyViolationException(
                    SafetyCategory.PROMPT_INJECTION,
                    "Response contains prompt-injection markers"));
        }

        // 2. Harmful content check
        if (config.blockHarmfulContent() && containsHarmfulContent(text)) {
            log.warn("AI safety BLOCKED: harmful content detected");
            return Promise.ofException(new SafetyViolationException(
                    SafetyCategory.HARMFUL_CONTENT,
                    "Response contains harmful or dangerous content"));
        }

        // 3. PII redaction (warn + redact, do not block)
        String sanitized = text;
        if (config.redactPii()) {
            sanitized = redactPii(sanitized);
            if (!sanitized.equals(text)) {
                log.warn("AI safety REDACTED: PII detected and removed from response");
            }
        }

        return Promise.of(sanitized);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean containsInjectionMarker(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return INJECTION_MARKERS.stream().anyMatch(lower::contains);
    }

    private boolean containsHarmfulContent(String text) {
        return HARMFUL_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    private String redactPii(String text) {
        text = SSN_PATTERN  .matcher(text).replaceAll("[REDACTED-SSN]");
        text = CARD_PATTERN .matcher(text).replaceAll("[REDACTED-CARD]");
        text = SECRET_PATTERN.matcher(text).replaceAll("[REDACTED-SECRET]");
        return text;
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /** Safety violation categories. */
    public enum SafetyCategory {
        HARMFUL_CONTENT,
        PII_LEAKAGE,
        PROMPT_INJECTION
    }

    /** Thrown when an AI response is blocked by the safety filter. */
    public static final class SafetyViolationException extends RuntimeException {
        private final SafetyCategory category;

        public SafetyViolationException(SafetyCategory category, String message) {
            super(message);
            this.category = category;
        }

        /** Returns the safety category that triggered the block. */
        public SafetyCategory category() {
            return category;
        }
    }

    /**
     * Filter configuration.
     *
     * @doc.type class
     * @doc.purpose Configuration record for AISafetyFilter
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record Config(
            boolean blockHarmfulContent,
            boolean blockPromptInjection,
            boolean redactPii
    ) {
        /** Default safe configuration: all checks enabled. */
        public static Config defaults() {
            return new Config(true, true, true);
        }

        /** Permissive configuration: only block harmful content. */
        public static Config permissive() {
            return new Config(true, false, false);
        }
    }
}
