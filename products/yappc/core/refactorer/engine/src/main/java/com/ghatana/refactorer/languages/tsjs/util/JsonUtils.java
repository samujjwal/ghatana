/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages.tsjs.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Simple JSON serialization/deserialization utilities for the TypeScript/JavaScript language
 * service. Delegates to Jackson ObjectMapper configured with lenient unknown-properties handling.
 *
 * @doc.type class
 * @doc.purpose JSON serialization utilities for TS/JS language tooling
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Deserialize a JSON string into an object of the specified type.
     *
     * @param json      the JSON string
     * @param valueType the target class
     * @param <T>       the target type
     * @return the deserialized object
     * @throws JsonProcessingException if parsing fails
     */
    public static <T> T fromJson(String json, Class<T> valueType) throws JsonProcessingException {
        return MAPPER.readValue(json, valueType);
    }

    /**
     * Serialize an object to a JSON string.
     *
     * @param value the object to serialize
     * @return the JSON string representation
     * @throws JsonProcessingException if serialization fails
     */
    public static String toJson(Object value) throws JsonProcessingException {
        return MAPPER.writeValueAsString(value);
    }
}
