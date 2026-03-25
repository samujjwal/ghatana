package com.ghatana.platform.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ErrorResponseBuilder}.
 *
 * @doc.type class
 * @doc.purpose Validates ErrorResponseBuilder produces consistent HTTP error response payloads
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ErrorResponseBuilder Tests")
class ErrorResponseBuilderTest {

    @Test
    @DisplayName("from(PlatformException) includes all required fields")
    void fromPlatformException_includesRequiredFields() {
        PlatformException ex = new PlatformException(ErrorCode.AUTHENTICATION_ERROR, "Bad token");

        Map<String, Object> response = ErrorResponseBuilder.from(ex).build();

        assertThat(response).containsKeys("code", "message", "status", "timestamp");
        assertThat(response.get("code")).isEqualTo("AUTH-001");
        assertThat(response.get("message")).isEqualTo("Bad token");
        assertThat(response.get("status")).isEqualTo(401);
        assertThat(response.get("timestamp")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("from(PlatformException) with metadata includes metadata field")
    void fromPlatformException_withMetadata_includesMetadata() {
        PlatformException ex = new PlatformException(ErrorCode.VALIDATION_ERROR, "Bad input");
        // Add metadata by creating a mutable exception; metadata is added through the exception
        // For simplicity test via withMeta on the builder directly
        Map<String, Object> response = ErrorResponseBuilder
            .from(ex)
            .withMeta("field", "email")
            .withMeta("constraint", "must not be blank")
            .build();

        assertThat(response).containsKey("metadata");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.get("metadata");
        assertThat(meta).containsEntry("field", "email");
        assertThat(meta).containsEntry("constraint", "must not be blank");
    }

    @Test
    @DisplayName("from(ErrorCode) uses code default message and HTTP status")
    void fromErrorCode_usesDefaults() {
        Map<String, Object> response = ErrorResponseBuilder.from(ErrorCode.NOT_FOUND).build();

        assertThat(response.get("code")).isEqualTo("RES-404");
        assertThat(response.get("status")).isEqualTo(404);
    }

    @Test
    @DisplayName("from(ErrorCode, message) overrides default message")
    void fromErrorCodeWithMessage_overridesMessage() {
        Map<String, Object> response = ErrorResponseBuilder
            .from(ErrorCode.NOT_FOUND, "User not found")
            .build();

        assertThat(response.get("code")).isEqualTo("RES-404");
        assertThat(response.get("message")).isEqualTo("User not found");
    }

    @Test
    @DisplayName("metadata is omitted when empty")
    void metadataOmittedWhenEmpty() {
        Map<String, Object> response = ErrorResponseBuilder.from(ErrorCode.SERVICE_ERROR).build();

        assertThat(response).doesNotContainKey("metadata");
    }

    @Test
    @DisplayName("build() is immutable — modifications to returned map throw")
    void build_returnsImmutableMap() {
        Map<String, Object> response = ErrorResponseBuilder.from(ErrorCode.UNKNOWN_ERROR).build();

        assertThatThrownBy(() -> response.put("extra", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("toJson() returns valid non-blank JSON string")
    void toJson_returnsNonBlankJson() {
        String json = ErrorResponseBuilder.from(ErrorCode.UNAUTHORIZED).toJson();

        assertThat(json)
            .isNotBlank()
            .contains("\"code\"")
            .contains("AUTH-401");
    }
}
