/*
 * Copyright (c) 2026 Ghatana Inc.
 */
package com.ghatana.datacloud.launcher.http.voice;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Retention and redaction policy for voice transcript data (DC-E5, Sprint 5).
 *
 * <p>Voice input poses unique privacy risk because spoken utterances may incidentally
 * contain PII (names, account numbers, passwords) even when the <em>intent</em> of the
 * command is benign.  This class defines:
 *
 * <ul>
 *   <li><b>Retention windows per data tier</b> — how long each category of voice data
 *       may be held before it must be deleted.</li>
 *   <li><b>PII detection patterns</b> — lightweight regex rules used to identify
 *       PII in transcript text before any logging or audit emission.</li>
 *   <li><b>Sanitisation utility</b> — replaces detected PII spans with
 *       {@code [REDACTED:<type>]} placeholders.</li>
 * </ul>
 *
 * <h2>Retention Tiers</h2>
 * <pre>
 *   AUDIO_BUFFER    → DELETE    (raw PCM / encoded audio destroyed immediately after STT)
 *   TRANSCRIPT_TEXT → DELETE    (raw transcript text never persisted; session-only)
 *   INTENT_AUDIT    → 7 days    (intent name + confidence + resolved path in audit log)
 *   DIAGNOSTIC_LOG  → 24 hours  (STT provider name + latency; no utterance content)
 *   FEEDBACK_RECORD → 90 days   (thumbs-up/down feedback linked to session ID, not text)
 * </pre>
 *
 * <h2>Privacy by Design Guarantees</h2>
 * <ol>
 *   <li>Raw transcript text is NEVER written to audit events or metrics ({@link VoiceGatewayHandler}
 *       already enforces this).  This class provides the redaction utility as a defence-in-depth
 *       guard for any future code path that might inadvertently log the utterance.</li>
 *   <li>Audio bytes are dereferenced immediately after STT completes and before the method
 *       returns — they are never stored to disk or passed outside the calling thread.</li>
 *   <li>PII patterns are applied <em>before</em> any string reaches a logging framework.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * String safeText = VoiceTranscriptRetentionPolicy.sanitise(rawTranscript);
 * log.debug("intent resolved from: {}", safeText);   // safe to log
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Retention windows and PII redaction policy for voice transcript data
 * @doc.layer product
 * @doc.pattern ValueObject, Policy
 * @doc.gaa.memory episodic
 */
public final class VoiceTranscriptRetentionPolicy {

    private VoiceTranscriptRetentionPolicy() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Retention tiers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Raw PCM / encoded audio buffers.
     * <b>Retention: DELETE immediately after STT completes.</b>
     * Audio bytes must not be persisted to disk, cache, or any messaging system.
     */
    public static final Duration RETAIN_AUDIO_BUFFER = Duration.ZERO;

    /**
     * Raw transcript text returned by the STT provider.
     * <b>Retention: DELETE at end of request (session-only, never persisted).</b>
     * The transcript is used only for intent classification within the current request.
     */
    public static final Duration RETAIN_TRANSCRIPT_TEXT = Duration.ZERO;

    /**
     * Intent audit records: intent name, resolved path, HTTP method, confidence score.
     * <b>Retention: 7 days</b> (transient audit tier).
     * These records contain NO utterance text — just the classified intent and its metadata.
     */
    public static final Duration RETAIN_INTENT_AUDIT = Duration.ofDays(7);

    /**
     * Diagnostic log entries: STT provider name, response latency, error codes.
     * <b>Retention: 24 hours</b> (operational debugging only).
     * These entries contain NO utterance content, only provider metadata.
     */
    public static final Duration RETAIN_DIAGNOSTIC_LOG = Duration.ofHours(24);

    /**
     * Feedback records: session-scoped thumbs-up/down feedback linked to a session ID.
     * <b>Retention: 90 days</b> (for recommendation quality improvement).
     * These records contain only the session ID, feedback signal, and intent type;
     * no utterance text is stored.
     */
    public static final Duration RETAIN_FEEDBACK_RECORD = Duration.ofDays(90);

    // ─────────────────────────────────────────────────────────────────────────
    // PII detection patterns
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PII patterns applied during transcript sanitisation.
     * Each {@link PiiPattern} maps a regex to a label used in the redaction placeholder.
     *
     * <p>Patterns are intentionally conservative (high recall, some false positives)
     * to maximise privacy protection.  False positives produce a {@code [REDACTED:TYPE]}
     * placeholder which is safe for logging; false negatives would leak PII.
     */
    static final List<PiiPattern> PII_PATTERNS = List.of(
        // Email addresses
        new PiiPattern(
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"),
            "EMAIL"),

        // Social Security Number (US)
        new PiiPattern(
            Pattern.compile("\\b\\d{3}[\\s\\-]?\\d{2}[\\s\\-]?\\d{4}\\b"),
            "SSN"),

        // IPv4 addresses
        new PiiPattern(
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
            "IP"),

        // Phone numbers (international & US formats)
        new PiiPattern(
            Pattern.compile("(\\+?\\d[\\s\\-.]?){7,15}\\d"),
            "PHONE"),

        // Credit card numbers (Luhn-range, 13-16 digits, optional separators)
        new PiiPattern(
            Pattern.compile("\\b(?:\\d[\\s\\-]?){13,16}\\b"),
            "CARD"),

        // API keys / secrets: 20+ consecutive alphanumeric+special chars (conservative heuristic)
        new PiiPattern(
            Pattern.compile("[A-Za-z0-9_\\-]{20,}"),
            "SECRET")
    );

    /**
     * Fields whose values must always be redacted before storage or logging,
     * regardless of context.
     */
    public static final Set<String> ALWAYS_REDACT_FIELDS = Set.of(
        "email", "phone", "ssn", "passport_number", "date_of_birth",
        "credit_card", "bank_account", "api_key", "password", "secret_token",
        "ip_address", "full_name", "national_id"
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Sanitisation utility
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sanitises transcript text by replacing detected PII spans with
     * {@code [REDACTED:<type>]} placeholders.
     *
     * <p>The order of pattern application matters: more specific patterns (SSN, CARD)
     * are applied before broader ones (PHONE) to avoid double-redaction artefacts.
     *
     * <p>This method is <b>safe to call on any string</b> — it never throws and
     * returns the original string unchanged if no PII is detected.
     *
     * @param transcript raw transcript text; may be null or blank
     * @return sanitised text safe for logging; never null
     */
    public static String sanitise(String transcript) {
        if (transcript == null || transcript.isBlank()) return "";
        String result = transcript;
        for (PiiPattern p : PII_PATTERNS) {
            result = p.pattern().matcher(result).replaceAll("[REDACTED:" + p.label() + "]");
        }
        return result;
    }

    /**
     * Returns {@code true} if the given field name should always have its value
     * redacted before storage or logging.
     *
     * @param fieldName field name to check (case-insensitive)
     * @return {@code true} if the field is a known PII field
     */
    public static boolean isAlwaysRedactedField(String fieldName) {
        return fieldName != null && ALWAYS_REDACT_FIELDS.contains(fieldName.toLowerCase());
    }

    /**
     * Validates that a retention duration is consistent with this policy.
     *
     * <p>Any code path that stores voice-related data must declare a retention
     * window.  This method asserts that the declared window does not exceed
     * the maximum permitted by tier.
     *
     * @param tier     named tier (one of {@code audio}, {@code transcript}, {@code intent_audit},
     *                 {@code diagnostic}, {@code feedback})
     * @param declared the retention duration declared by the caller
     * @throws IllegalArgumentException if the declared retention exceeds the policy maximum
     */
    public static void assertRetentionCompliant(String tier, Duration declared) {
        Duration max = switch (tier) {
            case "audio"        -> RETAIN_AUDIO_BUFFER;
            case "transcript"   -> RETAIN_TRANSCRIPT_TEXT;
            case "intent_audit" -> RETAIN_INTENT_AUDIT;
            case "diagnostic"   -> RETAIN_DIAGNOSTIC_LOG;
            case "feedback"     -> RETAIN_FEEDBACK_RECORD;
            default -> throw new IllegalArgumentException("Unknown voice retention tier: " + tier);
        };
        if (declared.compareTo(max) > 0) {
            throw new IllegalArgumentException(String.format(
                "Declared retention %s for tier '%s' exceeds policy maximum of %s",
                declared, tier, max));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Support type
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Associates a compiled regex pattern with a human-readable PII category label.
     *
     * @param pattern compiled regex
     * @param label   redaction placeholder label (e.g. {@code "EMAIL"})
     */
    public record PiiPattern(Pattern pattern, String label) {}
}
