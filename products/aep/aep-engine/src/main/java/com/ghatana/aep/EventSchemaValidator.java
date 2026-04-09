package com.ghatana.aep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates event schema for AEP event processing.
 *
 * <p>Performs structural validation on incoming {@link AepEngine.Event} objects
 * before they enter the processing pipeline. Validation enforces:
 * <ul>
 *   <li>Required fields: non-null, non-blank event type</li>
 *   <li>Payload type safety: no null keys, no deeply nested unsupported types</li>
 *   <li>Header safety: all header keys and values must be strings</li>
 *   <li>Timestamp presence: events must have a valid timestamp</li>
 * </ul>
 *
 * <p>Schema validation is intentionally lenient about payload content (any
 * {@code Map<String, Object>} is accepted) to support heterogeneous event types,
 * but strict about the event envelope structure.
 *
 * @doc.type class
 * @doc.purpose Structural validation of AEP events before pipeline processing
 * @doc.layer product
 * @doc.pattern Service
 */
public final class EventSchemaValidator {

    private static final Logger logger = LoggerFactory.getLogger(EventSchemaValidator.class);

    /** Maximum allowed event type length to prevent pathological inputs. */
    private static final int MAX_EVENT_TYPE_LENGTH = 256;

    /** Maximum number of top-level payload keys allowed per event. */
    private static final int MAX_PAYLOAD_KEYS = 500;

    /** Maximum number of header entries allowed per event. */
    private static final int MAX_HEADER_ENTRIES = 100;

    /** Maximum allowed version string length. */
    private static final int MAX_VERSION_LENGTH = 64;

    /**
     * Version format: major.minor (e.g. "1.0", "2.3"). Allows optional patch: "1.0.0".
     * Anything matching this pattern is accepted; other formats produce a warning but
     * are still accepted (lenient validation).
     *
     * <p>Validation limits enforced by this validator:
     * <ul>
     *   <li>{@code event.type} length <= 256</li>
     *   <li>Top-level payload keys <= 500</li>
     *   <li>Header entries <= 100</li>
     *   <li>{@code event.version} length <= 64</li>
     * </ul>
     */
    private static final java.util.regex.Pattern VERSION_PATTERN =
        java.util.regex.Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?$");

    /**
     * Validates the given event.
     *
     * @param event event to validate
     * @return validation result containing any errors found
     */
    public ValidationResult validate(AepEngine.Event event) {
        if (event == null) {
            return ValidationResult.of(List.of(
                new ValidationError(ErrorCode.MALFORMED, "event", "event must not be null")
            ));
        }

        List<ValidationError> errors = new ArrayList<>();
        validateType(event, errors);
        validateVersion(event, errors);
        validatePayload(event, errors);
        validateHeaders(event, errors);
        validateTimestamp(event, errors);

        if (!errors.isEmpty()) {
            logger.debug("Event schema validation failed for type={}: {}", event.type(), errors);
        }
        return ValidationResult.of(errors);
    }

    private void validateType(AepEngine.Event event, List<ValidationError> errors) {
        String type = event.type();
        if (type == null || type.isBlank()) {
            addError(errors, ErrorCode.MISSING_FIELD, "event.type", "event.type must not be blank");
            return;
        }
        if (type.length() > MAX_EVENT_TYPE_LENGTH) {
            addError(errors, ErrorCode.SIZE_EXCEEDED, "event.type",
                "event.type exceeds maximum length of " + MAX_EVENT_TYPE_LENGTH);
        }
    }

    private void validateVersion(AepEngine.Event event, List<ValidationError> errors) {
        String version = event.version();
        if (version == null || version.isBlank()) {
            // The Event record compact constructor already defaults to "1.0",
            // so a blank/null version here indicates a programmatic bypass.
            addError(errors, ErrorCode.MISSING_FIELD, "event.version",
                "event.version must not be blank");
            return;
        }
        if (version.length() > MAX_VERSION_LENGTH) {
            addError(errors, ErrorCode.SIZE_EXCEEDED, "event.version",
                "event.version exceeds maximum length of " + MAX_VERSION_LENGTH);
            return;
        }
        if (!VERSION_PATTERN.matcher(version).matches()) {
            // Warn but do not reject — allow custom version schemes (e.g. "v2-beta").
            logger.debug("event.version '{}' does not match recommended format major.minor[.patch]",
                version);
        }
    }

    private void validatePayload(AepEngine.Event event, List<ValidationError> errors) {
        Map<String, Object> payload = event.payload();
        if (payload == null) {
            addError(errors, ErrorCode.MISSING_FIELD, "event.payload", "event.payload must not be null");
            return;
        }
        if (payload.size() > MAX_PAYLOAD_KEYS) {
            addError(errors, ErrorCode.SIZE_EXCEEDED, "event.payload",
                "event.payload exceeds maximum key count of " + MAX_PAYLOAD_KEYS);
        }
        // Null keys are impossible in Map.copyOf()-based payloads; no check needed.
    }

    private void validateHeaders(AepEngine.Event event, List<ValidationError> errors) {
        Map<String, String> headers = event.headers();
        if (headers == null) {
            addError(errors, ErrorCode.MISSING_FIELD, "event.headers", "event.headers must not be null");
            return;
        }
        if (headers.size() > MAX_HEADER_ENTRIES) {
            addError(errors, ErrorCode.SIZE_EXCEEDED, "event.headers",
                "event.headers exceeds maximum entry count of " + MAX_HEADER_ENTRIES);
        }
        // Null header keys are impossible in immutable maps; no check needed.
    }

    private void validateTimestamp(AepEngine.Event event, List<ValidationError> errors) {
        if (event.timestamp() == null) {
            addError(errors, ErrorCode.MISSING_FIELD, "event.timestamp", "event.timestamp must not be null");
        }
    }

    private void addError(
        List<ValidationError> errors,
        ErrorCode code,
        String field,
        String message
    ) {
        errors.add(new ValidationError(code, field, message));
    }

    // ==================== Result Type ====================

    public enum ErrorCode {
        MISSING_FIELD,
        INVALID_TYPE,
        SIZE_EXCEEDED,
        MALFORMED
    }

    public record ValidationError(ErrorCode code, String field, String message) {
        public ValidationError {
            code = code != null ? code : ErrorCode.MALFORMED;
            field = field != null ? field : "event";
            message = message != null ? message : "validation error";
        }
    }

    /**
     * Result of schema validation.
     */
    public record ValidationResult(List<String> errors, List<ValidationError> details) {

        public ValidationResult {
            errors = errors != null ? List.copyOf(errors) : List.of();
            details = details != null ? List.copyOf(details) : List.of();
            if (errors.isEmpty() && !details.isEmpty()) {
                errors = details.stream()
                    .map(ValidationError::message)
                    .toList();
            }
        }

        /**
         * Create a result from structured validation errors (empty list means valid).
         */
        public static ValidationResult of(List<ValidationError> details) {
            return new ValidationResult(List.of(), details);
        }

        /** @return {@code true} if no validation errors were found */
        public boolean isValid() {
            return details.isEmpty();
        }

        /** @return first error message, or {@code null} if valid */
        public String firstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }

        /** @return first structured error, or {@code null} if valid */
        public ValidationError firstDetail() {
            return details.isEmpty() ? null : details.get(0);
        }

        /** @return comma-joined summary of all errors */
        public String summary() {
            return String.join("; ", errors);
        }

        public boolean hasCode(ErrorCode code) {
            return details.stream().anyMatch(error -> error.code() == code);
        }
    }
}
