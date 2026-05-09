package com.ghatana.digitalmarketing.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StandardErrorEnvelope")
class StandardErrorEnvelopeTest {

    @Test
    @DisplayName("maps canonical status codes to canonical error keys")
    void shouldMapCanonicalStatusCodes() {
        assertThat(StandardErrorEnvelope.of(400, "bad", "corr-1").error()).isEqualTo("BAD_REQUEST");
        assertThat(StandardErrorEnvelope.of(401, "unauth", "corr-1").error()).isEqualTo("UNAUTHORIZED");
        assertThat(StandardErrorEnvelope.of(403, "forbidden", "corr-1").error()).isEqualTo("FORBIDDEN");
        assertThat(StandardErrorEnvelope.of(404, "missing", "corr-1").error()).isEqualTo("NOT_FOUND");
        assertThat(StandardErrorEnvelope.of(409, "conflict", "corr-1").error()).isEqualTo("CONFLICT");
        assertThat(StandardErrorEnvelope.of(422, "invalid", "corr-1").error()).isEqualTo("UNPROCESSABLE_ENTITY");
        assertThat(StandardErrorEnvelope.of(423, "locked", "corr-1").error()).isEqualTo("LOCKED");
        assertThat(StandardErrorEnvelope.of(429, "rate", "corr-1").error()).isEqualTo("RATE_LIMITED");
        assertThat(StandardErrorEnvelope.of(500, "boom", "corr-1").error()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("retains field details when provided")
    void shouldRetainDetails() {
        StandardErrorEnvelope envelope = StandardErrorEnvelope.withDetails(
            422,
            "Validation failed",
            "corr-2",
            Map.of("field", "must not be blank")
        );

        assertThat(envelope.status()).isEqualTo(422);
        assertThat(envelope.error()).isEqualTo("UNPROCESSABLE_ENTITY");
        assertThat(envelope.details()).containsEntry("field", "must not be blank");
    }
}
