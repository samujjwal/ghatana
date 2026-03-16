package com.ghatana.contracts.mappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.pipeline.registry.model.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapper for Pipeline serialization/deserialization.
 *
 * <p>Uses JSON as the wire format (proto definitions are not yet generated).
 * Once {@code pipeline.registry.v1.Pipeline} proto is available, replace the
 * JSON round-trip with proper proto conversion.
 *
 * @doc.type class
 * @doc.purpose Pipeline JSON serialization mapping for transport
 * @doc.layer service
 * @doc.pattern Mapper
 */
public class PipelineMapper {

    private static final Logger log = LoggerFactory.getLogger(PipelineMapper.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PipelineMapper() {
        // Static utility class
    }

    /**
     * Serializes a Pipeline domain model to a JSON {@code byte[]}.
     *
     * @param pipeline the domain model to serialize; must not be {@code null}
     * @return UTF-8 encoded JSON representation
     * @throws IllegalArgumentException if serialization fails
     */
    public static byte[] toProto(Pipeline pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline must not be null");
        }
        try {
            byte[] json = OBJECT_MAPPER.writeValueAsBytes(pipeline);
            log.debug("Serialized pipeline '{}' to {} bytes", pipeline.getId(), json.length);
            return json;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize pipeline: " + pipeline.getId(), e);
        }
    }

    /**
     * Deserializes a JSON {@code byte[]} to a Pipeline domain model.
     *
     * @param wire the serialized bytes; must not be {@code null}
     * @return the reconstructed domain model
     * @throws IllegalArgumentException if deserialization fails
     */
    public static Pipeline fromProto(byte[] wire) {
        if (wire == null || wire.length == 0) {
            throw new IllegalArgumentException("wire bytes must not be null or empty");
        }
        try {
            Pipeline pipeline = OBJECT_MAPPER.readValue(wire, Pipeline.class);
            log.debug("Deserialized pipeline '{}'", pipeline.getId());
            return pipeline;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize pipeline from bytes", e);
        }
    }

    /**
     * Serializes a Pipeline domain model to a JSON string.
     *
     * @param pipeline the domain model to serialize
     * @return JSON string representation
     */
    public static String toJson(Pipeline pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline must not be null");
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(pipeline);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize pipeline to JSON: " + pipeline.getId(), e);
        }
    }

    /**
     * Deserializes a JSON string to a Pipeline domain model.
     *
     * @param json JSON string
     * @return the reconstructed domain model
     */
    public static Pipeline fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("json must not be null or blank");
        }
        try {
            return OBJECT_MAPPER.readValue(json, Pipeline.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize pipeline from JSON", e);
        }
    }
}

