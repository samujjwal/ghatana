package com.ghatana.kernel.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Typed Data Serializer — injects {@code _type} and {@code _schema_version} discriminators
 * into every JSON payload written to DataCloud (ISSUE-X03 fix).
 *
 * <h3>Problem</h3>
 * <p>DataCloud records serialized with plain {@link JsonUtils#toJson(Object)} lack
 * a stable type discriminator. When a schema evolves the deserializer cannot detect
 * version skew, leading to silent data corruption.</p>
 *
 * <h3>Solution</h3>
 * <p>This utility wraps Jackson to inject two reserved fields as the first entries of
 * every serialized object node:
 * <ul>
 *   <li>{@code _type} — fully-qualified logical type name (caller-supplied, e.g.
 *       {@code "com.ghatana.products.analytics.service.MetricsService.DataMetrics"})</li>
 *   <li>{@code _schema_version} — monotonically increasing integer starting at 1</li>
 * </ul>
 * Deserialization ignores these fields via
 * {@link com.fasterxml.jackson.databind.DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}
 * (disabled by default in the shared ObjectMapper). If a caller needs explicit version
 * awareness, use {@link #readSchemaVersion(byte[])} before deserializing.</p>
 *
 * <h3>Usage (producers)</h3>
 * <pre>{@code
 * byte[] bytes = TypedDataSerializer.toBytes(grant, "ConsentGrant", 1);
 * DataWriteRequest req = new DataWriteRequest(DATASET, id, bytes, metadata);
 * }</pre>
 *
 * <h3>Usage (consumers)</h3>
 * <pre>{@code
 * ConsentGrant grant = TypedDataSerializer.fromBytes(result.getData(), ConsentGrant.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose DataCloud serialization with _type + _schema_version discriminator injection (ISSUE-X03)
 * @doc.layer core
 * @doc.pattern Utility
 * @since 1.0.0
 */
public final class TypedDataSerializer {

    /**
     * Reserved field name for the logical type discriminator.
     * The value is a caller-supplied string — typically the simple class name or a
     * versioned logical name.
     */
    public static final String FIELD_TYPE = "_type";

    /**
     * Reserved field name for the schema version discriminator.
     * The value is an integer that consumers can inspect to trigger migration logic.
     */
    public static final String FIELD_SCHEMA_VERSION = "_schema_version";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ==================== Serialization ====================

    /**
     * Serializes {@code obj} to UTF-8 JSON bytes with {@code _type} and
     * {@code _schema_version} injected as the first two fields of the resulting object.
     *
     * @param obj           the object to serialize; must not be {@code null}
     * @param type          logical type name (e.g. {@code "ConsentGrant"}); must not be blank
     * @param schemaVersion monotonically increasing schema version starting at {@code 1}
     * @return UTF-8 encoded JSON bytes with discriminator fields
     * @throws IllegalArgumentException if {@code type} is blank or {@code schemaVersion} &lt; 1
     * @throws RuntimeException         if Jackson serialization fails
     */
    public static byte[] toBytes(Object obj, String type, int schemaVersion) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
        try {
            ObjectNode node = MAPPER.valueToTree(obj);
            // Prepend discriminator fields. Jackson ObjectNode preserves insertion order
            // when ORDER_MAP_ENTRIES_BY_KEYS is enabled — but we want them first regardless.
            // Rebuild with discriminators first, then all original fields.
            ObjectNode typed = MAPPER.createObjectNode();
            typed.put(FIELD_TYPE, type);
            typed.put(FIELD_SCHEMA_VERSION, schemaVersion);
            typed.setAll(node);
            return MAPPER.writeValueAsBytes(typed);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("TypedDataSerializer: failed to serialize " + type, e);
        }
    }

    /**
     * Serializes {@code obj} to a UTF-8 JSON String with discriminators injected.
     * Prefer {@link #toBytes(Object, String, int)} for DataCloud writes; this overload
     * is provided for logging and audit purposes.
     *
     * @param obj           the object to serialize; must not be {@code null}
     * @param type          logical type name; must not be blank
     * @param schemaVersion monotonically increasing schema version starting at {@code 1}
     * @return UTF-8 JSON string
     */
    public static String toString(Object obj, String type, int schemaVersion) {
        return new String(toBytes(obj, type, schemaVersion), StandardCharsets.UTF_8);
    }

    // ==================== Deserialization ====================

    /**
     * Deserializes UTF-8 JSON bytes into the target class, ignoring the {@code _type} and
     * {@code _schema_version} discriminator fields.
     *
     * @param data  UTF-8 JSON bytes from DataCloud; may be {@code null} (returns {@code null})
     * @param clazz target class; must not be {@code null}
     * @param <T>   target type
     * @return deserialized object, or {@code null} if {@code data} is {@code null} or empty
     * @throws RuntimeException if deserialization fails
     */
    public static <T> T fromBytes(byte[] data, Class<T> clazz) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return MAPPER.readValue(data, clazz);
        } catch (IOException e) {
            throw new RuntimeException("TypedDataSerializer: failed to deserialize " + clazz.getSimpleName(), e);
        }
    }

    /**
     * Deserializes a UTF-8 JSON string into the target class, ignoring discriminator fields.
     *
     * @param json  JSON string from DataCloud; may be {@code null} (returns {@code null})
     * @param clazz target class; must not be {@code null}
     * @param <T>   target type
     * @return deserialized object, or {@code null} if {@code json} is {@code null} or blank
     */
    public static <T> T fromString(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return fromBytes(json.getBytes(StandardCharsets.UTF_8), clazz);
    }

    // ==================== Inspection Helpers ====================

    /**
     * Reads the {@code _schema_version} field from the raw JSON bytes without full
     * deserialization. Returns {@code -1} if the field is absent (indicating the record
     * pre-dates ISSUE-X03 and should be treated as version 0).
     *
     * @param data UTF-8 JSON bytes; may be {@code null} (returns {@code -1})
     * @return schema version, or {@code -1} if absent
     */
    public static int readSchemaVersion(byte[] data) {
        if (data == null || data.length == 0) {
            return -1;
        }
        try {
            JsonNode root = MAPPER.readTree(data);
            JsonNode versionNode = root.get(FIELD_SCHEMA_VERSION);
            return (versionNode != null && versionNode.isInt()) ? versionNode.intValue() : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Reads the {@code _type} discriminator from the raw JSON bytes without full
     * deserialization. Returns {@code null} if absent.
     *
     * @param data UTF-8 JSON bytes; may be {@code null} (returns {@code null})
     * @return type discriminator string, or {@code null} if absent
     */
    public static String readType(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(data);
            JsonNode typeNode = root.get(FIELD_TYPE);
            return (typeNode != null && typeNode.isTextual()) ? typeNode.textValue() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private TypedDataSerializer() {
        // Utility class — not instantiable
    }
}
