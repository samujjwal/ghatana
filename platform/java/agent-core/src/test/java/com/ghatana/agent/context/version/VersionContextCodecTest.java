/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link VersionContextCodec}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for VersionContextCodec round-trip, edge cases, and digest
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("VersionContextCodec Tests")
class VersionContextCodecTest {

    private final VersionContextCodec codec = VersionContextCodec.INSTANCE;

    private VersionContext buildContext() {
        return new VersionContext(
                Map.of("spring-boot", "3.2.0", "reactor", "3.6.0"),
                Map.of("jvm", "21.0.2"),
                Map.of("gradle", "8.5"),
                Map.of("data-cloud-api", "v2"),
                "sha256:abc123",
                Instant.parse("2026-05-13T10:00:00Z")
        );
    }

    @Test
    @DisplayName("encode produces valid JSON containing key fields")
    void encodeProducesJson() {
        VersionContext ctx = buildContext();
        String json = codec.encode(ctx);

        assertThat(json).isNotBlank();
        assertThat(json).contains("\"spring-boot\"");
        assertThat(json).contains("\"3.2.0\"");
        assertThat(json).contains("\"jvm\"");
        assertThat(json).contains("\"sha256:abc123\"");
    }

    @Test
    @DisplayName("decode round-trips back to equal VersionContext")
    void decodeRoundTrip() {
        VersionContext original = buildContext();
        String json = codec.encode(original);
        VersionContext decoded = codec.decode(json);

        assertThat(decoded.dependencies()).isEqualTo(original.dependencies());
        assertThat(decoded.runtimes()).isEqualTo(original.runtimes());
        assertThat(decoded.tools()).isEqualTo(original.tools());
        assertThat(decoded.apiContracts()).isEqualTo(original.apiContracts());
        assertThat(decoded.sourceRef()).isEqualTo(original.sourceRef());
    }

    @Test
    @DisplayName("decodeOrEmpty returns empty context for malformed JSON")
    void decodeOrEmptyReturnsFallbackOnMalformedJson() {
        VersionContext result = codec.decodeOrEmpty("not-valid-json{{");

        assertThat(result).isNotNull();
        assertThat(result.dependencies()).isEmpty();
        assertThat(result.runtimes()).isEmpty();
        assertThat(result.sourceRef()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("decodeOrEmpty returns empty context for empty string")
    void decodeOrEmptyReturnsFallbackForBlankString() {
        VersionContext result = codec.decodeOrEmpty("");

        assertThat(result).isNotNull();
        assertThat(result.dependencies()).isEmpty();
    }

    @Test
    @DisplayName("encode rejects null context with NullPointerException")
    void encodeRejectsNull() {
        assertThatThrownBy(() -> codec.encode(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("encodeWithDigest produces non-null encoded context with digest")
    void encodeWithDigestProducesDigest() {
        VersionContext ctx = buildContext();
        VersionContextCodec.EncodedContext encoded = codec.encodeWithDigest(ctx);

        assertThat(encoded).isNotNull();
        assertThat(encoded.json()).isNotBlank();
        assertThat(encoded.digest()).isNotBlank();
        // Digest is first 16 hex chars of SHA-256 in current codec contract.
        assertThat(encoded.digest()).matches("[0-9a-f]{16}");
    }

    @Test
    @DisplayName("encodeWithDigest is deterministic for equal contexts")
    void encodeWithDigestIsDeterministic() {
        VersionContext ctx1 = buildContext();
        VersionContext ctx2 = buildContext();

        String digest1 = codec.encodeWithDigest(ctx1).digest();
        String digest2 = codec.encodeWithDigest(ctx2).digest();

        assertThat(digest1).isEqualTo(digest2);
    }

    @Test
    @DisplayName("empty VersionContext encodes and decodes correctly")
    void emptyContextRoundTrip() {
        VersionContext empty = VersionContext.empty();
        String json = codec.encode(empty);
        VersionContext decoded = codec.decode(json);

        assertThat(decoded.dependencies()).isEmpty();
        assertThat(decoded.runtimes()).isEmpty();
        assertThat(decoded.tools()).isEmpty();
        assertThat(decoded.apiContracts()).isEmpty();
        assertThat(decoded.sourceRef()).isEqualTo("unknown");
    }
}
