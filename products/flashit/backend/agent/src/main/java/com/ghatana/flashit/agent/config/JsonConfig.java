package com.ghatana.flashit.agent.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared Jackson ObjectMapper configuration for the agent service.
 *
 * @doc.type class
 * @doc.purpose Provides a centralized, pre-configured ObjectMapper singleton
 * @doc.layer product
 * @doc.pattern Singleton
 */
public final class JsonConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonConfig() {
    }

    /**
     * Returns the shared ObjectMapper instance.
     *
     * @return pre-configured ObjectMapper
     */
    public static ObjectMapper objectMapper() {
        return MAPPER;
    }
}
