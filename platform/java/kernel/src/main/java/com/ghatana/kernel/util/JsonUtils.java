package com.ghatana.kernel.util;

/**
 * JSON utilities for serialization and deserialization.
 *
 * <p><b>Deprecated:</b> Use {@link com.ghatana.platform.core.util.JsonUtils} directly.
 * This class is a thin delegation wrapper kept for binary compatibility of existing
 * kernel consumers. It will be removed in a future release.</p>
 *
 * <p>Delegates all operations to {@code com.ghatana.platform.core.util.JsonUtils},
 * which provides the canonical thread-safe JSON utilities with full Jackson configuration.</p>
 *
 * <p>Migration: Replace {@code com.ghatana.kernel.util.JsonUtils} imports with
 * {@code com.ghatana.platform.core.util.JsonUtils}.</p>
 *
 * @doc.type class
 * @doc.purpose Deprecated delegation wrapper — delegates to platform core JsonUtils (CONS-005)
 * @doc.layer core
 * @doc.pattern Utility
 * @deprecated Use {@link com.ghatana.platform.core.util.JsonUtils} from platform/java/core
 */
@Deprecated(since = "2026-03-25", forRemoval = true)
public class JsonUtils {

    /**
     * Serializes an object to JSON string.
     *
     * @param obj the object to serialize
     * @return JSON string
     * @throws RuntimeException if serialization fails
     * @deprecated Use {@link com.ghatana.platform.core.util.JsonUtils#toJson(Object)}
     */
    @Deprecated(since = "2026-03-25", forRemoval = true)
    public static String toJson(Object obj) {
        try {
            return com.ghatana.platform.core.util.JsonUtils.toJson(obj);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    /**
     * Deserializes JSON string to object.
     *
     * @param json  the JSON string
     * @param clazz the target class
     * @param <T>   the type
     * @return deserialized object
     * @throws RuntimeException if deserialization fails
     * @deprecated Use {@link com.ghatana.platform.core.util.JsonUtils#fromJson(String, Class)}
     */
    @Deprecated(since = "2026-03-25", forRemoval = true)
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return com.ghatana.platform.core.util.JsonUtils.fromJson(json, clazz);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize from JSON", e);
        }
    }

    private JsonUtils() {
        // Utility class — use com.ghatana.platform.core.util.JsonUtils instead
    }
}

