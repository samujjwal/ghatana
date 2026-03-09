package com.ghatana.datacloud.plugins.kafka;

import com.ghatana.datacloud.event.model.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Serializer for Event objects to/from Kafka message bytes.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides efficient serialization of Event objects for Kafka:
 * <ul>
 * <li><b>JSON Format</b>: Human-readable, schema-flexible</li>
 * <li><b>Timestamp Handling</b>: ISO-8601 format with timezone</li>
 * <li><b>Payload Preservation</b>: Original payload bytes maintained</li>
 * </ul>
 *
 * <p>
 * <b>Note on Production</b><br>
 * For high-throughput production use, consider:
 * <ul>
 * <li>Protocol Buffers for smaller message size</li>
 * <li>Avro with Schema Registry for schema evolution</li>
 * <li>Kryo for fastest serialization</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Event serialization for Kafka messages
 * @doc.layer plugin
 * @doc.pattern Serializer
 */
public class EventSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(EventSerializer.class);

    private final ObjectMapper objectMapper;

    /**
     * Creates a new event serializer with default configuration.
     */
    public EventSerializer() {
        this.objectMapper = JsonUtils.getDefaultMapper();
    }

    /**
     * Creates a new event serializer with custom object mapper.
     *
     * @param objectMapper custom Jackson ObjectMapper
     */
    public EventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes an Event to bytes.
     *
     * @param event event to serialize
     * @return serialized bytes
     * @throws SerializationException if serialization fails
     */
    public byte[] serialize(Event event) {
        try {
            return objectMapper.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize event {}: {}", event.getId(), e.getMessage());
            throw new SerializationException("Failed to serialize event", e);
        }
    }

    /**
     * Deserializes bytes to an Event.
     *
     * @param data serialized bytes
     * @return deserialized event
     * @throws SerializationException if deserialization fails
     */
    public Event deserialize(byte[] data) {
        try {
            return objectMapper.readValue(data, Event.class);
        } catch (IOException e) {
            LOG.error("Failed to deserialize event: {}", e.getMessage());
            throw new SerializationException("Failed to deserialize event", e);
        }
    }

    /**
     * Serialization exception.
     */
    public static class SerializationException extends RuntimeException {

        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
