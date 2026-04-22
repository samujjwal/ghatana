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
@DisplayName("Core Exception Metadata Tests [GH-90000]")
class CoreExceptionMetadataTest {

    @Test
    @DisplayName("validation exception captures validation errors in metadata [GH-90000]")
    void validationExceptionCapturesValidationErrorsInMetadata() { // GH-90000
        Map<String, Object> errors = Map.of("email", "must not be blank"); // GH-90000

        ValidationException exception = new ValidationException("validation failed", errors); // GH-90000

        assertThat(exception.getValidationErrors()).containsEntry("email", "must not be blank"); // GH-90000
        assertThat(exception.getMetadata()).containsKey("validationErrors [GH-90000]");
        Object metadataValue = exception.getMetadata("validationErrors [GH-90000]");
        assertThat(metadataValue).isInstanceOf(Map.class); // GH-90000
    }

    @Test
    @DisplayName("configuration exception captures field metadata [GH-90000]")
    void configurationExceptionCapturesFieldMetadata() { // GH-90000
        ConfigurationException exception = ConfigurationException.invalidValue("port", -1, "must be positive"); // GH-90000

        assertThat(exception.getFieldName()).isEqualTo("port [GH-90000]");
        assertThat(exception.getInvalidValue()).isEqualTo(-1); // GH-90000
        assertThat(exception.getMetadata()).containsEntry("field", "port"); // GH-90000
        assertThat(exception.getMetadata()).containsEntry("invalidValue", -1); // GH-90000
    }

    @Test
    @DisplayName("rate limit exception captures limiter context metadata [GH-90000]")
    void rateLimitExceptionCapturesLimiterContextMetadata() { // GH-90000
        RateLimitExceededException exception = new RateLimitExceededException("too fast", "tenant-a", 5.0, 8.5, 2500); // GH-90000

        assertThat(exception.getTenantId()).isEqualTo("tenant-a [GH-90000]");
        assertThat(exception.getRetryAfterMillis()).isEqualTo(2500); // GH-90000
        assertThat(exception.getMetadata()).containsEntry("tenantId", "tenant-a"); // GH-90000
        assertThat(exception.getMetadata()).containsEntry("requestsPerSecond", 5.0); // GH-90000
        assertThat(exception.getMetadata()).containsEntry("currentRate", 8.5); // GH-90000
        assertThat(exception.getMetadata()).containsEntry("retryAfterMillis", 2500L); // GH-90000
    }
}
