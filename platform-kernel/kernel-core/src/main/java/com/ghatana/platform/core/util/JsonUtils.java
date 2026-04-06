package com.ghatana.platform.core.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TimeZone;

/**
 * Thread-safe JSON serialization utilities with pre-configured Jackson ObjectMapper instances.
 * 
 * Provides production-grade JSON operations with optimal Jackson configuration including
 * Java 8 Time API support (ISO-8601), null exclusion, and BigDecimal precision.
 * 
 * All methods are static. ObjectMapper instances are thread-safe singletons.
 *
 * @doc.type class
 * @doc.purpose Thread-safe JSON serialization utilities with pre-configured Jackson ObjectMapper instances
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class JsonUtils {
    
    private static final ObjectMapper DEFAULT_MAPPER = createDefaultMapper();
    private static final ObjectMapper PRETTY_MAPPER = createPrettyMapper();

    private JsonUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Get the default ObjectMapper instance (compact output).
     */
    @NotNull
    public static ObjectMapper getDefaultMapper() {
        return DEFAULT_MAPPER;
    }

    /**
     * Get the pretty-printing ObjectMapper instance (indented output).
     */
    @NotNull
    public static ObjectMapper getPrettyMapper() {
        return PRETTY_MAPPER;
    }

    /**
     * Serialize an object to JSON string.
     */
    @NotNull
    public static String toJson(@Nullable Object value) throws JsonProcessingException {
        return DEFAULT_MAPPER.writeValueAsString(value);
    }

    /**
     * Serialize an object to a JSON byte array.
     *
     * @param value the object to serialize
     * @return JSON representation as byte array
     * @throws JsonProcessingException if serialization fails
     */
    @NotNull
    public static byte[] toJsonBytes(@Nullable Object value) throws JsonProcessingException {
        return DEFAULT_MAPPER.writeValueAsBytes(value);
    }

    /**
     * Serialize an object to pretty-printed JSON string.
     */
    @NotNull
    public static String toPrettyJson(@Nullable Object value) throws JsonProcessingException {
        return PRETTY_MAPPER.writeValueAsString(value);
    }

    /**
     * Deserialize JSON string to an object.
     */
    @Nullable
    public static <T> T fromJson(@NotNull String json, @NotNull Class<T> valueType) throws JsonProcessingException {
        return DEFAULT_MAPPER.readValue(json, valueType);
    }

    /**
     * Deserialize JSON string to a generic type.
     */
    @Nullable
    public static <T> T fromJson(@NotNull String json, @NotNull TypeReference<T> typeReference) throws JsonProcessingException {
        return DEFAULT_MAPPER.readValue(json, typeReference);
    }

    /**
     * Deserialize JSON from an InputStream.
     */
    @Nullable
    public static <T> T fromJson(@NotNull InputStream input, @NotNull Class<T> valueType) throws IOException {
        return DEFAULT_MAPPER.readValue(input, valueType);
    }

    /**
     * Deserialize JSON from an InputStream to a generic type.
     */
    @Nullable
    public static <T> T fromJson(@NotNull InputStream input, @NotNull TypeReference<T> typeReference) throws IOException {
        return DEFAULT_MAPPER.readValue(input, typeReference);
    }

    /**
     * Write JSON to an OutputStream.
     */
    public static void writeJson(@NotNull OutputStream output, @Nullable Object value) throws IOException {
        DEFAULT_MAPPER.writeValue(output, value);
    }

    /**
     * Convert an object to a Map.
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(@Nullable Object value) {
        return DEFAULT_MAPPER.convertValue(value, Map.class);
    }

    /**
     * Create a deep copy of an object via JSON serialization.
     */
    @NotNull
    public static <T> T deepCopy(@NotNull T value, @NotNull Class<T> valueType) {
        try {
            String json = toJson(value);
            return fromJson(json, valueType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create deep copy", e);
        }
    }

    /**
     * Safely serialize to JSON, returning null on error.
     */
    @Nullable
    public static String toJsonSafe(@Nullable Object value) {
        try {
            return toJson(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Safely deserialize from JSON, returning null on error.
     */
    @Nullable
    public static <T> T fromJsonSafe(@Nullable String json, @NotNull Class<T> valueType) {
        if (json == null) {
            return null;
        }
        try {
            return fromJson(json, valueType);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static ObjectMapper createDefaultMapper() {
        return createMapper(false);
    }

    private static ObjectMapper createPrettyMapper() {
        return createMapper(true);
    }

    private static ObjectMapper createMapper(boolean prettyPrint) {
        JsonMapper.Builder builder = JsonMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.INDENT_OUTPUT, prettyPrint)
            .defaultDateFormat(new StdDateFormat()
                .withColonInTimeZone(true)
                .withTimeZone(TimeZone.getTimeZone("UTC")))
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);

        builder.addModule(new JavaTimeModule()
            .addSerializer(LocalDateTime.class, 
                new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(DateTimeFormatter.ISO_DATE_TIME))
            .addSerializer(LocalDate.class, 
                new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(DateTimeFormatter.ISO_DATE))
            .addSerializer(LocalTime.class, 
                new com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer(DateTimeFormatter.ISO_TIME))
            .addDeserializer(LocalDateTime.class, 
                com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer.INSTANCE)
            .addDeserializer(LocalDate.class, 
                com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer.INSTANCE)
            .addDeserializer(LocalTime.class, 
                com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer.INSTANCE)
        );

        return builder.build();
    }
}
