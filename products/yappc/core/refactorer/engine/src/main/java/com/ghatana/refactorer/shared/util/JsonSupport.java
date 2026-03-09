/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Centralized JSON utility providing configured ObjectMapper instances and convenience methods.
 * 
 * <p>This is the canonical JSON support class for the refactorer modules. Use this instead of
 * creating local JsonUtils/JsonUtil variants.
 *
 * <p>Provides multiple ObjectMapper configurations:
 * <ul>
 *   <li>{@link #defaultMapper()} - Standard configuration with sensible defaults</li>
 *   <li>{@link #lenientMapper()} - Lenient parsing (ignores unknown properties)</li>
 *   <li>{@link #webhookMapper()} - For webhook payloads (JavaTime support, non-null only)</li>
 * </ul>
 
 * @doc.type class
 * @doc.purpose Handles json support operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class JsonSupport {
    
    private static final ObjectMapper DEFAULT_MAPPER = createDefaultMapper();
    private static final ObjectMapper LENIENT_MAPPER = createLenientMapper();
    private static final ObjectMapper WEBHOOK_MAPPER = createWebhookMapper();
    
    private JsonSupport() {
        // Utility class
    }
    
    /**
     * Default ObjectMapper with standard configuration.
     */
    public static ObjectMapper defaultMapper() {
        return DEFAULT_MAPPER;
    }
    
    /**
     * Lenient ObjectMapper that ignores unknown properties during deserialization.
     */
    public static ObjectMapper lenientMapper() {
        return LENIENT_MAPPER;
    }
    
    /**
     * Webhook-specific ObjectMapper with JavaTime module and non-null serialization.
     */
    public static ObjectMapper webhookMapper() {
        return WEBHOOK_MAPPER;
    }
    
    private static ObjectMapper createDefaultMapper() {
        ObjectMapper mapper = JsonUtils.getDefaultMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
    
    private static ObjectMapper createLenientMapper() {
        ObjectMapper mapper = JsonUtils.getDefaultMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
    
    private static ObjectMapper createWebhookMapper() {
        ObjectMapper mapper = JsonUtils.getDefaultMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
    
    // Convenience methods using default mapper
    
    /**
     * Deserialize JSON string to an object of the specified class.
     */
    public static <T> T fromJson(String json, Class<T> clazz) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return DEFAULT_MAPPER.readValue(json, clazz);
    }
    
    /**
     * Deserialize JSON string to an object using a TypeReference.
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return DEFAULT_MAPPER.readValue(json, typeRef);
    }
    
    /**
     * Deserialize JSON string to an array of objects of the specified class.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] fromJsonArray(String json, Class<T> componentType) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return (T[]) java.lang.reflect.Array.newInstance(componentType, 0);
        }
        return DEFAULT_MAPPER.readValue(
                json, DEFAULT_MAPPER.getTypeFactory().constructArrayType(componentType));
    }
    
    /**
     * Deserialize JSON string to a List of objects of the specified class.
     */
    public static <T> List<T> fromJsonToList(String json, Class<T> clazz) throws IOException {
        CollectionType listType =
                TypeFactory.defaultInstance().constructCollectionType(List.class, clazz);
        return DEFAULT_MAPPER.readValue(json, listType);
    }
    
    /**
     * Serialize an object to a JSON string.
     */
    public static String toJson(Object obj) throws JsonProcessingException {
        return DEFAULT_MAPPER.writeValueAsString(obj);
    }
    
    /**
     * Pretty-print an object as a formatted JSON string.
     */
    public static String toPrettyJson(Object obj) {
        try {
            return DEFAULT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting object to JSON", e);
        }
    }
    
    /**
     * Parses a JSON string into a JsonNode.
     */
    public static JsonNode parseJson(String json) throws JsonProcessingException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return DEFAULT_MAPPER.readTree(json);
    }
    
    /**
     * Converts an object to a Map.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object obj) {
        return DEFAULT_MAPPER.convertValue(obj, Map.class);
    }
    
    /**
     * Converts a Map to an object of the specified type.
     */
    public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
        return DEFAULT_MAPPER.convertValue(map, clazz);
    }
}
