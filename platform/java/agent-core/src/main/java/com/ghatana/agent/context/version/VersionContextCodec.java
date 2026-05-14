/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical codec for serialising and deserialising {@link VersionContext} to/from JSON.
 *
 * <p>All code that must persist, transmit, or compare a version context MUST use this codec.
 * Using {@code VersionContext.toString()} as a machine protocol is forbidden — it produces
 * the JVM default record representation which is not stable or parseable.
 *
 * <p>The canonical JSON format is:
 * <pre>{@code
 * {
 *   "dependencies": { "react-router": "7.2.0", "spring-boot": "3.2.0" },
 *   "runtimes":     { "jvm": "21.0.2" },
 *   "tools":        { "gradle": "8.5" },
 *   "apiContracts": { "data-cloud-api": "v2" },
 *   "sourceRef":    "sha256:abc123",
 *   "resolvedAt":   "2026-05-13T10:00:00Z"
 * }
 * }</pre>
 *
 * <p>Codec instances are thread-safe and stateless. Use {@link #INSTANCE} for the default
 * shared instance.
 *
 * @doc.type class
 * @doc.purpose Canonical JSON codec for VersionContext serialisation
 * @doc.layer agent-core
 * @doc.pattern Codec
 */
public final class VersionContextCodec {

    /** Shared default instance. Thread-safe. */
    public static final VersionContextCodec INSTANCE = new VersionContextCodec();

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .findAndRegisterModules();

    /**
     * Jackson-backed DTO used for round-trip serialisation. Kept package-private to avoid
     * leaking Jackson annotations into the domain record.
     */
    record Dto(
            @JsonProperty("dependencies") Map<String, String> dependencies,
            @JsonProperty("runtimes") Map<String, String> runtimes,
            @JsonProperty("tools") Map<String, String> tools,
            @JsonProperty("apiContracts") Map<String, String> apiContracts,
            @JsonProperty("sourceRef") String sourceRef,
            @JsonProperty("resolvedAt") String resolvedAt
    ) {}

    private VersionContextCodec() {}

    /**
     * Encodes a {@link VersionContext} to its canonical JSON string representation.
     *
     * @param context the version context to encode (must not be null)
     * @return canonical JSON string; never null, never empty
     * @throws IllegalStateException if Jackson fails to serialise the context
     */
    @NotNull
    public String encode(@NotNull VersionContext context) {
        Objects.requireNonNull(context, "context must not be null");
        Dto dto = new Dto(
                context.dependencies(),
                context.runtimes(),
                context.tools(),
                context.apiContracts(),
                context.sourceRef(),
                context.resolvedAt().toString()
        );
        try {
            return MAPPER.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to encode VersionContext to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Decodes a canonical JSON string back to a {@link VersionContext}.
     *
     * <p>Returns {@code null} when the input is {@code null} or blank, allowing callers to
     * treat an absent context gracefully without an exception.
     *
     * @param json the JSON string produced by {@link #encode}
     * @return decoded version context, or {@code null} when input is blank
     * @throws IllegalArgumentException if the JSON is non-blank but malformed
     */
    @Nullable
    public VersionContext decode(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Dto dto = MAPPER.readValue(json, Dto.class);
            return new VersionContext(
                    dto.dependencies() != null ? dto.dependencies() : Map.of(),
                    dto.runtimes() != null ? dto.runtimes() : Map.of(),
                    dto.tools() != null ? dto.tools() : Map.of(),
                    dto.apiContracts() != null ? dto.apiContracts() : Map.of(),
                    dto.sourceRef() != null ? dto.sourceRef() : "unknown",
                    dto.resolvedAt() != null ? Instant.parse(dto.resolvedAt()) : Instant.EPOCH
            );
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to decode VersionContext from JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Decodes the JSON string and returns {@link VersionContext#empty()} when the input is
     * blank or malformed instead of throwing.
     *
     * <p>Use this in defensive paths where a missing/corrupt context must not halt execution.
     *
     * @param json the JSON string, possibly blank or null
     * @return decoded version context, or an empty context on failure
     */
    @NotNull
    public VersionContext decodeOrEmpty(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return VersionContext.empty();
        }
        try {
            VersionContext decoded = decode(json);
            return decoded != null ? decoded : VersionContext.empty();
        } catch (IllegalArgumentException e) {
            return VersionContext.empty();
        }
    }

    /**
     * Returns the canonical JSON string and a short content-based digest suitable for
     * use as a trace attribute. The digest is the first 16 hex characters of a SHA-256
     * over the encoded bytes.
     *
     * @param context the version context to digest (must not be null)
     * @return pair of encoded JSON and its hex digest
     */
    @NotNull
    public EncodedContext encodeWithDigest(@NotNull VersionContext context) {
        String encoded = encode(context);
        String digest = computeDigest(encoded);
        return new EncodedContext(encoded, digest);
    }

    @NotNull
    private static String computeDigest(@NotNull String input) {
        try {
            byte[] hash = java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash, 0, 8); // first 16 hex chars
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec; this branch is unreachable in production
            return "unknown-digest";
        }
    }

    /**
     * Encoded version context with JSON and digest.
     *
     * @param json   canonical JSON representation
     * @param digest first 16 hex characters of the SHA-256 of the JSON bytes
     */
    public record EncodedContext(
            @NotNull String json,
            @NotNull String digest
    ) {}
}
