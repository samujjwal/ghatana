package com.ghatana.yappc.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Centralized JSON serialization/deserialization
 * @doc.layer common
 * @doc.pattern Utility
 */
public final class JsonMapper {
    
    private static final Logger log = LoggerFactory.getLogger(JsonMapper.class);
    
    private static final ObjectMapper MAPPER = createMapper();
    
    private JsonMapper() {
        // Utility class
    }
    
    private static ObjectMapper createMapper() {
        ObjectMapper mapper = JsonUtils.getDefaultMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
    
    /**
     * Serializes an object to JSON string.
     * 
     * @param object Object to serialize
     * @return JSON string
     * @throws JsonProcessingException if serialization fails
     */
    public static String toJson(Object object) throws JsonProcessingException {
        return MAPPER.writeValueAsString(object);
    }
    
    /**
     * Deserializes JSON string to object.
     * 
     * @param json JSON string
     * @param clazz Target class
     * @param <T> Type parameter
     * @return Deserialized object
     * @throws JsonProcessingException if deserialization fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
        return MAPPER.readValue(json, clazz);
    }
    
    /**
     * Gets the underlying ObjectMapper for advanced usage.
     * 
     * @return ObjectMapper instance
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}
