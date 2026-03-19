package com.ghatana.kernel.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON utilities for serialization and deserialization.
 *
 * <p>Provides canonical JSON formatting for cryptographic signatures
 * and consistent serialization across the kernel.</p>
 *
 * @doc.type class
 * @doc.purpose JSON serialization utilities with canonical format
 * @doc.layer core
 * @doc.pattern Utility
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    /**
     * Serializes an object to JSON string.
     *
     * @param obj the object to serialize
     * @return JSON string
     * @throws RuntimeException if serialization fails
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    /**
     * Deserializes JSON string to object.
     *
     * @param json the JSON string
     * @param clazz the target class
     * @param <T> the type
     * @return deserialized object
     * @throws RuntimeException if deserialization fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize from JSON", e);
        }
    }

    private JsonUtils() {
        // Utility class
    }
}
