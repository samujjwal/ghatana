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
@DisplayName("ErrorResponseBuilder Tests [GH-90000]")
class ErrorResponseBuilderTest {

    @Test
    @DisplayName("from(PlatformException) includes all required fields [GH-90000]")
    void fromPlatformException_includesRequiredFields() { // GH-90000
        PlatformException ex = new PlatformException(ErrorCode.AUTHENTICATION_ERROR, "Bad token"); // GH-90000

        Map<String, Object> response = ErrorResponseBuilder.from(ex).build(); // GH-90000

        assertThat(response).containsKeys("code", "message", "status", "timestamp"); // GH-90000
        assertThat(response.get("code [GH-90000]")).isEqualTo("AUTH-001 [GH-90000]");
        assertThat(response.get("message [GH-90000]")).isEqualTo("Bad token [GH-90000]");
        assertThat(response.get("status [GH-90000]")).isEqualTo(401);
        assertThat(response.get("timestamp [GH-90000]")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("from(PlatformException) with metadata includes metadata field [GH-90000]")
    void fromPlatformException_withMetadata_includesMetadata() { // GH-90000
        PlatformException ex = new PlatformException(ErrorCode.VALIDATION_ERROR, "Bad input"); // GH-90000
        // Add metadata by creating a mutable exception; metadata is added through the exception
        // For simplicity test via withMeta on the builder directly
        Map<String, Object> response = ErrorResponseBuilder
            .from(ex) // GH-90000
            .withMeta("field", "email") // GH-90000
            .withMeta("constraint", "must not be blank") // GH-90000
            .build(); // GH-90000

        assertThat(response).containsKey("metadata [GH-90000]");
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> meta = (Map<String, Object>) response.get("metadata [GH-90000]");
        assertThat(meta).containsEntry("field", "email"); // GH-90000
        assertThat(meta).containsEntry("constraint", "must not be blank"); // GH-90000
    }

    @Test
    @DisplayName("from(ErrorCode) uses code default message and HTTP status [GH-90000]")
    void fromErrorCode_usesDefaults() { // GH-90000
        Map<String, Object> response = ErrorResponseBuilder.from(ErrorCode.NOT_FOUND).build(); // GH-90000

        assertThat(response.get("code [GH-90000]")).isEqualTo("RES-404 [GH-90000]");
        assertThat(response.get("status [GH-90000]")).isEqualTo(404);
    }

    @Test
    @DisplayName("from(ErrorCode, message) overrides default message [GH-90000]")
    void fromErrorCodeWithMessage_overridesMessage() { // GH-90000
        Map<String, Object> response = ErrorResponseBuilder
            .from(ErrorCode.NOT_FOUND, "User not found") // GH-90000
            .build(); // GH-90000

        assertThat(response.get("code [GH-90000]")).isEqualTo("RES-404 [GH-90000]");
        assertThat(response.get("message [GH-90000]")).isEqualTo("User not found [GH-90000]");
    }

    @Test
    @DisplayName("metadata is omitted when empty [GH-90000]")
    void metadataOmittedWhenEmpty() { // GH-90000
        Map<String, Object> response = ErrorResponseBuilder.from(ErrorCode.SERVICE_ERROR).build(); // GH-90000

        assertThat(response).doesNotContainKey("metadata [GH-90000]");
    }

    @Test
    @DisplayName("build() is immutable — modifications to returned map throw [GH-90000]")
    void build_returnsImmutableMap() { // GH-90000
        Map<String, Object> response = ErrorResponseBuilder.from(ErrorCode.UNKNOWN_ERROR).build(); // GH-90000

        assertThatThrownBy(() -> response.put("extra", "value")) // GH-90000
            .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    @Test
    @DisplayName("toJson() returns valid non-blank JSON string [GH-90000]")
    void toJson_returnsNonBlankJson() { // GH-90000
        String json = ErrorResponseBuilder.from(ErrorCode.UNAUTHORIZED).toJson(); // GH-90000

        assertThat(json) // GH-90000
            .isNotBlank() // GH-90000
            .contains("\"code\"") // GH-90000
            .contains("AUTH-401 [GH-90000]");
    }
}
