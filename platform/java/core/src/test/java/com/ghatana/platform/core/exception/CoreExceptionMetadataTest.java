package com.ghatana.platform.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for metadata propagation on canonical core exceptions.
 *
 * @doc.type class
 * @doc.purpose Verifies constructor metadata is preserved without post-construction mutation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Core Exception Metadata Tests")
class CoreExceptionMetadataTest {

    @Test
    @DisplayName("validation exception captures validation errors in metadata")
    void validationExceptionCapturesValidationErrorsInMetadata() {
        Map<String, Object> errors = Map.of("email", "must not be blank");

        ValidationException exception = new ValidationException("validation failed", errors);

        assertThat(exception.getValidationErrors()).containsEntry("email", "must not be blank");
        assertThat(exception.getMetadata()).containsKey("validationErrors");
        Object metadataValue = exception.getMetadata("validationErrors");
        assertThat(metadataValue).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("configuration exception captures field metadata")
    void configurationExceptionCapturesFieldMetadata() {
        ConfigurationException exception = ConfigurationException.invalidValue("port", -1, "must be positive");

        assertThat(exception.getFieldName()).isEqualTo("port");
        assertThat(exception.getInvalidValue()).isEqualTo(-1);
        assertThat(exception.getMetadata()).containsEntry("field", "port");
        assertThat(exception.getMetadata()).containsEntry("invalidValue", -1);
    }

    @Test
    @DisplayName("rate limit exception captures limiter context metadata")
    void rateLimitExceptionCapturesLimiterContextMetadata() {
        RateLimitExceededException exception = new RateLimitExceededException("too fast", "tenant-a", 5.0, 8.5, 2500);

        assertThat(exception.getTenantId()).isEqualTo("tenant-a");
        assertThat(exception.getRetryAfterMillis()).isEqualTo(2500);
        assertThat(exception.getMetadata()).containsEntry("tenantId", "tenant-a");
        assertThat(exception.getMetadata()).containsEntry("requestsPerSecond", 5.0);
        assertThat(exception.getMetadata()).containsEntry("currentRate", 8.5);
        assertThat(exception.getMetadata()).containsEntry("retryAfterMillis", 2500L);
    }
}
