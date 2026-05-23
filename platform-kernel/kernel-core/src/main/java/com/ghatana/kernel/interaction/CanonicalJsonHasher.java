package com.ghatana.kernel.interaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * Canonical JSON hasher for deterministic payload hashing.
 *
 * <p>Ensures that JSON payloads produce consistent hashes regardless of key ordering,
 * whitespace, or formatting. This is critical for idempotency in the ProductInteractionBroker
 * where the same logical payload must produce the same hash.</p>
 *
 * <p>Canonicalization rules:</p>
 * <ul>
 *   <li>Keys are sorted alphabetically</li>
 *   <li>No whitespace between tokens</li>
 *   <li>Consistent date/time formatting</li>
 *   <li>Null values are included explicitly</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Provides deterministic JSON hashing for broker payload idempotency
 * @doc.layer kernel
 * @doc.pattern Utility
 */
public final class CanonicalJsonHasher {

    private static final ObjectMapper CANONICAL_MAPPER = createCanonicalMapper();
    private static final String HASH_ALGORITHM = "SHA-256";

    private CanonicalJsonHasher() {
        // Utility class - prevent instantiation
    }

    /**
     * Computes a deterministic SHA-256 hash of a JSON object.
     *
     * @param payload the payload to hash (can be any JSON-serializable object)
     * @return Base64-encoded SHA-256 hash
     * @throws IllegalArgumentException if payload cannot be serialized to canonical JSON
     */
    public static String hash(Object payload) {
        if (payload == null) {
            return hashNull();
        }

        try {
            String canonicalJson = canonicalize(payload);
            return computeSha256(canonicalJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload to canonical JSON", e);
        }
    }

    /**
     * Computes a deterministic SHA-256 hash of a JSON string.
     *
     * @param jsonString the JSON string to hash
     * @return Base64-encoded SHA-256 hash
     * @throws IllegalArgumentException if jsonString is not valid JSON
     */
    public static String hashString(String jsonString) {
        if (jsonString == null) {
            return hashNull();
        }

        try {
            // Parse and re-serialize to ensure canonical form
            Object parsed = CANONICAL_MAPPER.readValue(jsonString, Object.class);
            String canonicalJson = CANONICAL_MAPPER.writeValueAsString(parsed);
            return computeSha256(canonicalJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse JSON string", e);
        }
    }

    /**
     * Converts an object to canonical JSON string representation.
     *
     * @param payload the payload to canonicalize
     * @return canonical JSON string
     * @throws IllegalArgumentException if payload cannot be serialized
     */
    public static String toCanonicalJson(Object payload) {
        if (payload == null) {
            return "null";
        }

        try {
            return canonicalize(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload to canonical JSON", e);
        }
    }

    private static String canonicalize(Object payload) throws JsonProcessingException {
        try {
            return CANONICAL_MAPPER.writeValueAsString(payload);
        } catch (InvalidDefinitionException error) {
            if (isOpaquePayloadMarker(error)) {
                return CANONICAL_MAPPER.writeValueAsString(Map.of(
                        "opaqueType", payload.getClass().getName()));
            }
            throw error;
        }
    }

    private static boolean isOpaquePayloadMarker(InvalidDefinitionException error) {
        String message = error.getOriginalMessage();
        return message != null && message.contains("No serializer found for class");
    }

    private static String hashNull() {
        return computeSha256("null");
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " algorithm not available", e);
        }
    }

    private static ObjectMapper createCanonicalMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Configure for canonical JSON output
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        // Use ISO-8601 date format
        mapper.registerModule(new JavaTimeModule());
        mapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
        
        return mapper;
    }
}
