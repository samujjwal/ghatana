package com.ghatana.datacloud.launcher.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Centralised API boundary input validator for Data-Cloud HTTP endpoints.
 *
 * <p>Every public method returns an {@link Optional} that is empty when the
 * input is valid, or contains a human-readable error message when it is not.
 * Call sites can turn a non-empty Optional into an HTTP 400 or 422 response.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>No external dependencies — pure JDK.</li>
 *   <li>Stateless; all methods are static — no instantiation required.</li>
 *   <li>Fail-accumulate: {@link #validateAll} collects every violation before returning.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Centralised, stateless input validation at all HTTP API boundaries
 * @doc.layer product
 * @doc.pattern Validator
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public final class ApiInputValidator {

    // -----------------------------------------------------------------------
    // Structural limits
    // -----------------------------------------------------------------------

    /** Maximum allowed length for tenantId. */
    static final int MAX_TENANT_ID_LEN = 128;

    /** Maximum allowed length for collection name. */
    static final int MAX_COLLECTION_LEN = 128;

    /** Maximum allowed length for entity/event ID. */
    static final int MAX_ID_LEN = 255;

    /** Maximum allowed length for free-text search query. */
    static final int MAX_SEARCH_QUERY_LEN = 2_048;

    /** Maximum allowed value for {@code limit} query parameter. */
    static final int MAX_LIMIT = 1_000;

    /** Minimum allowed value for {@code limit} query parameter. */
    static final int MIN_LIMIT = 1;

    /** Maximum allowed batch size for bulk-save/delete endpoints. */
    static final int MAX_BATCH_SIZE = 500;

    /** Maximum allowed JSON body size in bytes (10 MB). */
    static final long MAX_BODY_BYTES = 10 * 1024 * 1024L;

    /** Maximum allowed depth of nested maps in entity payload. */
    static final int MAX_NESTING_DEPTH = 20;

    /** Maximum number of keys allowed in a map at any nesting level. */
    static final int MAX_MAP_KEYS = 500;

    // -----------------------------------------------------------------------
    // The safe patterns — allow only characters we can reason about
    // -----------------------------------------------------------------------

    /**
     * Safe pattern for identifiers (tenantId, collection, entityId).
     * Allows: alphanumeric, hyphen, underscore, period, colon.
     * Explicitly disallows path separators, null bytes, and shell metacharacters.
     */
    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("^[a-zA-Z0-9._\\-:]{1,255}$");

    /** Disallow null bytes and raw control chars in string field values. */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    private ApiInputValidator() {
        // utility class — do not instantiate
    }

    // -----------------------------------------------------------------------
    // Individual field validators
    // -----------------------------------------------------------------------

    /**
     * Validates a {@code tenantId} path/query parameter.
     *
     * @param tenantId value to validate; must not be null or blank
     * @return empty Optional when valid; error message when invalid
     */
    public static Optional<String> validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.of("tenantId must not be null or blank");
        }
        if (tenantId.length() > MAX_TENANT_ID_LEN) {
            return Optional.of("tenantId must not exceed " + MAX_TENANT_ID_LEN + " characters");
        }
        if (!SAFE_IDENTIFIER.matcher(tenantId).matches()) {
            return Optional.of("tenantId contains illegal characters; only [a-zA-Z0-9._-:] are allowed");
        }
        return Optional.empty();
    }

    /**
     * Validates a {@code collection} path parameter.
     *
     * @param collection collection name; must not be null or blank
     * @return empty Optional when valid; error message when invalid
     */
    public static Optional<String> validateCollection(String collection) {
        if (collection == null || collection.isBlank()) {
            return Optional.of("collection must not be null or blank");
        }
        if (collection.length() > MAX_COLLECTION_LEN) {
            return Optional.of("collection must not exceed " + MAX_COLLECTION_LEN + " characters");
        }
        if (!SAFE_IDENTIFIER.matcher(collection).matches()) {
            return Optional.of("collection contains illegal characters; only [a-zA-Z0-9._-:] are allowed");
        }
        return Optional.empty();
    }

    /**
     * Validates an entity or event {@code id} path parameter.
     *
     * @param id entity/event identifier; must not be null or blank
     * @return empty Optional when valid; error message when invalid
     */
    public static Optional<String> validateId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.of("id must not be null or blank");
        }
        if (id.length() > MAX_ID_LEN) {
            return Optional.of("id must not exceed " + MAX_ID_LEN + " characters");
        }
        if (!SAFE_IDENTIFIER.matcher(id).matches()) {
            return Optional.of("id contains illegal characters; only [a-zA-Z0-9._-:] are allowed");
        }
        return Optional.empty();
    }

    /**
     * Validates the {@code limit} query parameter.
     *
     * @param limitStr raw string value (may be null — returns default)
     * @param defaultValue value to use when {@code limitStr} is null
     * @return a {@link LimitResult} with the parsed value or error
     */
    public static LimitResult validateLimit(String limitStr, int defaultValue) {
        if (limitStr == null || limitStr.isBlank()) {
            return LimitResult.ok(defaultValue);
        }
        int value;
        try {
            value = Integer.parseInt(limitStr.strip());
        } catch (NumberFormatException e) {
            return LimitResult.error("limit must be a valid integer, got: " + sanitizeForMessage(limitStr));
        }
        if (value < MIN_LIMIT) {
            return LimitResult.error("limit must be >= " + MIN_LIMIT);
        }
        if (value > MAX_LIMIT) {
            return LimitResult.error("limit must be <= " + MAX_LIMIT);
        }
        return LimitResult.ok(value);
    }

    /**
     * Validates a full-text search query string.
     *
     * @param query the search query; must not be null or blank
     * @return empty Optional when valid; error message when invalid
     */
    public static Optional<String> validateSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return Optional.of("query parameter 'q' must not be null or blank");
        }
        if (query.length() > MAX_SEARCH_QUERY_LEN) {
            return Optional.of("search query must not exceed " + MAX_SEARCH_QUERY_LEN + " characters");
        }
        if (CONTROL_CHARS.matcher(query).find()) {
            return Optional.of("search query contains illegal control characters");
        }
        return Optional.empty();
    }

    /**
     * Validates the raw request body size.
     *
     * @param bodyBytes body content
     * @return empty Optional when valid; error message when payload exceeds limit
     */
    public static Optional<String> validateBodySize(byte[] bodyBytes) {
        if (bodyBytes == null) {
            return Optional.of("request body must not be null");
        }
        if (bodyBytes.length > MAX_BODY_BYTES) {
            return Optional.of("request body exceeds maximum size of " + (MAX_BODY_BYTES / (1024 * 1024)) + " MB");
        }
        return Optional.empty();
    }

    /**
     * Validates a batch of entity maps for the bulk save endpoint.
     *
     * @param entities list of entity payloads; must not be null, empty, or &gt; 500
     * @return empty Optional when valid; error message when invalid
     */
    public static Optional<String> validateBatchSize(List<?> entities) {
        if (entities == null || entities.isEmpty()) {
            return Optional.of("'entities' array must not be null or empty");
        }
        if (entities.size() > MAX_BATCH_SIZE) {
            return Optional.of("batch must not exceed " + MAX_BATCH_SIZE + " entities; got " + entities.size());
        }
        return Optional.empty();
    }

    /**
     * Validates a batch of entity IDs for the bulk delete endpoint.
     *
     * @param ids list of entity IDs; must not be null, empty, or &gt; 500
     * @return empty Optional when valid; error message when invalid
     */
    public static Optional<String> validateDeleteBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Optional.of("'ids' array must not be null or empty");
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            return Optional.of("batch must not exceed " + MAX_BATCH_SIZE + " ids; got " + ids.size());
        }
        for (int i = 0; i < ids.size(); i++) {
            Optional<String> err = validateId(ids.get(i));
            if (err.isPresent()) {
                return Optional.of("ids[" + i + "]: " + err.get());
            }
        }
        return Optional.empty();
    }

    /**
     * Performs a shallow structural validation of an entity payload map.
     *
     * <p>Checks:
     * <ul>
     *   <li>Map is not null or empty.</li>
     *   <li>Map does not exceed {@link #MAX_MAP_KEYS} top-level keys.</li>
     *   <li>String values do not contain null bytes or raw control characters.</li>
     *   <li>Nesting depth does not exceed {@link #MAX_NESTING_DEPTH}.</li>
     * </ul>
     *
     * @param data entity payload
     * @return empty Optional when valid; error message when invalid
     */
    public static Optional<String> validateEntityPayload(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Optional.of("entity payload must not be null or empty");
        }
        if (data.size() > MAX_MAP_KEYS) {
            return Optional.of("entity payload must not exceed " + MAX_MAP_KEYS + " top-level keys");
        }
        return checkMapDepth(data, 1);
    }

    /**
     * Accumulates all violations for entity+collection saving.
     * Returns the full violation message joined with "; " or empty if clean.
     *
     * @param tenantId resolved tenant identifier
     * @param collection collection name from path
     * @param data entity payload
     * @return joined error string, or empty Optional when all checks pass
     */
    public static Optional<String> validateAll(String tenantId, String collection, Map<String, Object> data) {
        List<String> errors = new ArrayList<>();
        validateTenantId(tenantId).ifPresent(errors::add);
        validateCollection(collection).ifPresent(errors::add);
        validateEntityPayload(data).ifPresent(errors::add);
        return errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors));
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Optional<String> checkMapDepth(Map<String, Object> map, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            return Optional.of("entity payload exceeds maximum nesting depth of " + MAX_NESTING_DEPTH);
        }
        if (map.size() > MAX_MAP_KEYS) {
            return Optional.of("entity payload has more than " + MAX_MAP_KEYS + " keys at depth " + depth);
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                return Optional.of("entity payload contains a null or blank key at depth " + depth);
            }
            if (key.length() > 256) {
                return Optional.of("entity payload key '" + sanitizeForMessage(key.substring(0, 64)) + "...' exceeds 256 characters");
            }
            Object value = entry.getValue();
            if (value instanceof String s) {
                if (s.getBytes(StandardCharsets.UTF_8).length > 1_048_576) { // 1 MB per field
                    return Optional.of("field '" + sanitizeForMessage(key) + "' value exceeds 1 MB");
                }
                if (CONTROL_CHARS.matcher(s).find()) {
                    return Optional.of("field '" + sanitizeForMessage(key) + "' contains illegal control characters");
                }
            } else if (value instanceof Map<?, ?> nested) {
                Optional<String> err = checkMapDepth((Map<String, Object>) nested, depth + 1);
                if (err.isPresent()) return err;
            } else if (value instanceof List<?> list) {
                if (list.size() > 10_000) {
                    return Optional.of("field '" + sanitizeForMessage(key) + "' array exceeds 10 000 elements");
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Strips control characters <em>and newlines</em>, then truncates a raw value before
     * embedding in an error message, preventing log injection.
     */
    static String sanitizeForMessage(String raw) {
        if (raw == null) return "<null>";
        // Strip control chars + CR/LF to prevent log injection
        String cleaned = raw.replaceAll("[\\x00-\\x1F\\x7F]", "?");
        return cleaned.length() > 128 ? cleaned.substring(0, 128) + "…" : cleaned;
    }

    // -----------------------------------------------------------------------
    // Result type for limit validation
    // -----------------------------------------------------------------------

    /**
     * Result holder for {@link #validateLimit} — either a parsed int or an error.
     *
     * @doc.type record
     * @doc.purpose Typed result for limit validation avoiding exception-based flow control
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public static final class LimitResult {
        private final int value;
        private final String error;

        private LimitResult(int value, String error) {
            this.value = value;
            this.error = error;
        }

        static LimitResult ok(int value) {
            return new LimitResult(value, null);
        }

        static LimitResult error(String error) {
            return new LimitResult(-1, error);
        }

        /** @return true when the limit parsed successfully */
        public boolean isValid() {
            return error == null;
        }

        /** @return parsed limit value; only meaningful when {@link #isValid()} is true */
        public int getValue() {
            return value;
        }

        /** @return error message; non-null only when {@link #isValid()} is false */
        public Optional<String> getError() {
            return Optional.ofNullable(error);
        }
    }
}
