/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Input validation and sanitization utilities for AEP API boundaries.
 *
 * <p>Enforces OWASP-aligned validation rules at all external entry points,
 * preventing injection attacks (SQL, XSS, command injection) and enforcing
 * size constraints to guard against denial-of-service payloads.
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li>Tenant IDs: alphanumeric, hyphens, underscores only; max 64 chars</li>
 *   <li>Agent / pipeline / pattern IDs: alphanumeric, hyphens, underscores, dots; max 128 chars</li>
 *   <li>Event type identifiers: lowercase alphanumeric + dots + hyphens; max 128 chars</li>
 *   <li>Payload maps: max {@value #MAX_PAYLOAD_KEYS} top-level keys; values must not contain
 *       script-injection patterns</li>
 *   <li>Free-form string values: max {@value #MAX_STRING_VALUE_LENGTH} characters</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Input validation and sanitization for AEP API layer
 * @doc.layer product
 * @doc.pattern Utility, SecurityGuard
 */
public final class AepInputValidator {

    private static final Logger log = LoggerFactory.getLogger(AepInputValidator.class);

    /** Maximum allowed length for tenant / resource identifiers. */
    public static final int MAX_ID_LENGTH = 128;

    /** Maximum allowed length for free-form string payload values. */
    public static final int MAX_STRING_VALUE_LENGTH = 65_536; // 64 KiB

    /** Maximum allowed number of top-level keys in an event payload map. */
    public static final int MAX_PAYLOAD_KEYS = 256;

    /** Maximum allowed batch size (number of events per batch request). */
    public static final int MAX_BATCH_SIZE = 1_000;

    /** Maximum allowed request body size in bytes (16 MiB). */
    public static final int MAX_REQUEST_BODY_BYTES = 16 * 1024 * 1024;

    /** Allowed tenant ID pattern: 1–64 chars, alphanumeric + hyphen + underscore. */
    private static final Pattern TENANT_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_\\-]{1,64}$");

    /** Allowed resource ID pattern: alphanumeric + hyphen + underscore + dot; 1–128 chars. */
    private static final Pattern RESOURCE_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_\\-.]{1,128}$");

    /** Allowed event-type identifier: lowercase + dots + hyphens; 1–128 chars. */
    private static final Pattern EVENT_TYPE_PATTERN =
            Pattern.compile("^[A-Za-z0-9._\\-]{1,128}$");

    /**
     * Script/injection detection — flags patterns commonly used in XSS / script injection.
     * Applied to all string values extracted from untrusted JSON payloads.
     */
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "<\\s*script|javascript\\s*:|vbscript\\s*:|<\\s*iframe|<\\s*object|" +
            "<\\s*embed|on\\w+\\s*=|expression\\s*\\(|url\\s*\\(\\s*javascript",
            Pattern.CASE_INSENSITIVE);

    /** SQL injection keyword detection (basic heuristic for inline-query risk). */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "\\b(union\\s+select|insert\\s+into|drop\\s+table|delete\\s+from|" +
            "exec\\s+xp_|execute\\s+sp_|1\\s*=\\s*1|';\\s*--|;\\s*drop)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Singleton — no state needed. */
    private AepInputValidator() {}

    // ==================== ID Validators ====================

    /**
     * Validates a tenant identifier.
     *
     * @param tenantId raw tenant ID from the request
     * @return validated tenant ID (same reference if valid)
     * @throws ValidationException if the tenant ID is null, blank, or contains illegal characters
     */
    public static String validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ValidationException("tenantId must not be blank");
        }
        if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            log.warn("Rejected invalid tenantId: '{}'", sanitizeForLog(tenantId));
            throw new ValidationException(
                    "tenantId contains illegal characters — only alphanumeric, hyphen, and underscore are allowed");
        }
        return tenantId;
    }

    /**
     * Validates a resource identifier (agent ID, pipeline ID, pattern ID, etc.).
     *
     * @param id    raw ID from the request
     * @param label field name used in error messages (e.g., "agentId", "pipelineId")
     * @return validated ID
     * @throws ValidationException if the ID is null, blank, or contains illegal characters
     */
    public static String validateResourceId(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new ValidationException(label + " must not be blank");
        }
        if (id.length() > MAX_ID_LENGTH) {
            throw new ValidationException(label + " exceeds maximum length of " + MAX_ID_LENGTH);
        }
        if (!RESOURCE_ID_PATTERN.matcher(id).matches()) {
            log.warn("Rejected invalid {}: '{}'", label, sanitizeForLog(id));
            throw new ValidationException(label + " contains illegal characters — only alphanumeric, hyphen, underscore, and dot are allowed");
        }
        return id;
    }

    /**
     * Validates an event-type identifier.
     *
     * @param eventType raw event type from the request
     * @return validated event type
     * @throws ValidationException if the event type is invalid
     */
    public static String validateEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new ValidationException("event type must not be blank");
        }
        if (!EVENT_TYPE_PATTERN.matcher(eventType).matches()) {
            log.warn("Rejected invalid event type: '{}'", sanitizeForLog(eventType));
            throw new ValidationException("event type contains illegal characters");
        }
        return eventType;
    }

    // ==================== Payload Validators ====================

    /**
     * Validates and sanitizes an event payload map.
     *
     * <p>Checks:
     * <ul>
     *   <li>Map is not null</li>
     *   <li>Top-level key count ≤ {@value #MAX_PAYLOAD_KEYS}</li>
     *   <li>All string values are free of script/SQL injection patterns</li>
     *   <li>All string values are within {@value #MAX_STRING_VALUE_LENGTH} chars</li>
     * </ul>
     *
     * @param payload event payload map to validate
     * @return the same payload if valid (no mutation — data is not sanitized by stripping,
     *         only validated; rejected payloads throw an exception)
     * @throws ValidationException if the payload violates any rule
     */
    public static Map<String, Object> validatePayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new ValidationException("payload must not be null");
        }
        if (payload.size() > MAX_PAYLOAD_KEYS) {
            throw new ValidationException(
                    "payload exceeds maximum of " + MAX_PAYLOAD_KEYS + " top-level keys");
        }
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            validatePayloadValue(entry.getKey(), entry.getValue(), 0);
        }
        return payload;
    }

    /**
     * Validates that a batch does not exceed the maximum allowed size.
     *
     * @param count number of events in the batch
     * @throws ValidationException if the batch is too large
     */
    public static void validateBatchSize(int count) {
        if (count <= 0) {
            throw new ValidationException("batch must contain at least one event");
        }
        if (count > MAX_BATCH_SIZE) {
            throw new ValidationException(
                    "batch exceeds maximum of " + MAX_BATCH_SIZE + " events");
        }
    }

    /**
     * Validates that a string field is present and within size limits.
     *
     * @param value value to check
     * @param field field name for the error message
     * @throws ValidationException if the value is null, blank, or too long
     */
    public static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required and must not be blank");
        }
        if (value.length() > MAX_STRING_VALUE_LENGTH) {
            throw new ValidationException(field + " exceeds maximum length");
        }
        return value;
    }

    // ==================== Private Helpers ====================

    @SuppressWarnings("unchecked")
    private static void validatePayloadValue(String key, Object value, int depth) {
        if (depth > 8) {
            throw new ValidationException("payload nesting exceeds maximum depth of 8");
        }
        if (value instanceof String str) {
            if (str.length() > MAX_STRING_VALUE_LENGTH) {
                throw new ValidationException(
                        "payload field '" + key + "' exceeds maximum string length");
            }
            if (INJECTION_PATTERN.matcher(str).find()) {
                log.warn("Rejected payload field '{}' containing script injection pattern", key);
                throw new ValidationException(
                        "payload field '" + key + "' contains disallowed content");
            }
            if (SQL_INJECTION_PATTERN.matcher(str).find()) {
                log.warn("Rejected payload field '{}' containing SQL injection pattern", key);
                throw new ValidationException(
                        "payload field '" + key + "' contains disallowed SQL pattern");
            }
        } else if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_PAYLOAD_KEYS) {
                throw new ValidationException(
                        "nested map at key '" + key + "' exceeds " + MAX_PAYLOAD_KEYS + " keys");
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                validatePayloadValue(String.valueOf(e.getKey()), e.getValue(), depth + 1);
            }
        } else if (value instanceof List<?> list) {
            if (list.size() > MAX_BATCH_SIZE) {
                throw new ValidationException(
                        "list at key '" + key + "' exceeds " + MAX_BATCH_SIZE + " items");
            }
            for (Object item : list) {
                validatePayloadValue(key + "[]", item, depth + 1);
            }
        } else if (value instanceof Collection<?> col) {
            if (col.size() > MAX_BATCH_SIZE) {
                throw new ValidationException(
                        "collection at key '" + key + "' exceeds " + MAX_BATCH_SIZE + " items");
            }
        }
        // Number, Boolean, null — OK, no further validation needed
    }

    /**
     * Truncates a raw value for safe logging (prevents log injection).
     * Replaces newlines and limits length to 80 chars.
     */
    private static String sanitizeForLog(String raw) {
        if (raw == null) return "(null)";
        return raw.replace('\n', '_').replace('\r', '_')
                   .substring(0, Math.min(raw.length(), 80));
    }

    // ==================== Validation Exception ====================

    /**
     * Signals that an AEP API input failed validation.
     *
     * @doc.type class
     * @doc.purpose Typed validation failure for AEP input boundaries
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public static final class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}
