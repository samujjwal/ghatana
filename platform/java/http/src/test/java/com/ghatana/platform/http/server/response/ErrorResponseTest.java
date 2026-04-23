package com.ghatana.platform.http.server.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for ErrorResponse factory methods and field construction
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ErrorResponse — factory methods, field structure, and serialization")
class ErrorResponseTest {

    @Test
    @DisplayName("of(status, message) creates response with correct fields")
    void ofStatusMessage() { // GH-90000
        ErrorResponse error = ErrorResponse.of(400, "Invalid input"); // GH-90000

        assertThat(error.getStatus()).isEqualTo(400); // GH-90000
        assertThat(error.getMessage()).isEqualTo("Invalid input");
        assertThat(error.getCode()).isNull(); // GH-90000
        assertThat(error.getPath()).isNull(); // GH-90000
        assertThat(error.getTimestamp()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("of(status, code, message) creates response with all three fields")
    void ofStatusCodeMessage() { // GH-90000
        ErrorResponse error = ErrorResponse.of(404, "USER_NOT_FOUND", "User 123 not found"); // GH-90000

        assertThat(error.getStatus()).isEqualTo(404); // GH-90000
        assertThat(error.getCode()).isEqualTo("USER_NOT_FOUND");
        assertThat(error.getMessage()).isEqualTo("User 123 not found");
    }

    @Test
    @DisplayName("timestamp defaults to current time on construction")
    void timestampDefaults() { // GH-90000
        Instant before = Instant.now().minusSeconds(1); // GH-90000
        ErrorResponse error = ErrorResponse.of(500, "server error"); // GH-90000
        Instant after = Instant.now().plusSeconds(1); // GH-90000

        assertThat(error.getTimestamp()).isAfter(before).isBefore(after); // GH-90000
    }

    @Test
    @DisplayName("badRequest factory sets status 400, code BAD_REQUEST, and path")
    void badRequestFactory() { // GH-90000
        ErrorResponse error = ErrorResponse.badRequest("Missing field: email", "/api/users"); // GH-90000

        assertThat(error.getStatus()).isEqualTo(400); // GH-90000
        assertThat(error.getCode()).isEqualTo("BAD_REQUEST");
        assertThat(error.getMessage()).isEqualTo("Missing field: email");
        assertThat(error.getPath()).isEqualTo("/api/users");
    }

    @Test
    @DisplayName("notFound factory sets status 404, code NOT_FOUND, and path")
    void notFoundFactory() { // GH-90000
        ErrorResponse error = ErrorResponse.notFound("User 42 not found", "/api/users/42"); // GH-90000

        assertThat(error.getStatus()).isEqualTo(404); // GH-90000
        assertThat(error.getCode()).isEqualTo("NOT_FOUND");
        assertThat(error.getPath()).isEqualTo("/api/users/42");
    }

    @Test
    @DisplayName("internalServerError factory sets status 500 and code")
    void internalServerErrorFactory() { // GH-90000
        ErrorResponse error = ErrorResponse.internalServerError("Unexpected failure", "/api/data"); // GH-90000

        assertThat(error.getStatus()).isEqualTo(500); // GH-90000
        assertThat(error.getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(error.getPath()).isEqualTo("/api/data");
    }

    @Test
    @DisplayName("builder creates response with all optional fields")
    void builderWithAllFields() { // GH-90000
        ErrorResponse error = ErrorResponse.builder() // GH-90000
                .status(409) // GH-90000
                .code("CONFLICT")
                .message("Email already exists")
                .path("/api/users")
                .traceId("trace-abc-123")
                .details("Email john@example.com is taken")
                .build(); // GH-90000

        assertThat(error.getStatus()).isEqualTo(409); // GH-90000
        assertThat(error.getCode()).isEqualTo("CONFLICT");
        assertThat(error.getTraceId()).isEqualTo("trace-abc-123");
        assertThat(error.getDetails()).isEqualTo("Email john@example.com is taken");
    }

    @Test
    @DisplayName("builder creates response with validation errors list")
    void builderWithValidationErrors() { // GH-90000
        ErrorResponse.ValidationError ve = ErrorResponse.ValidationError.builder() // GH-90000
                .field("email")
                .message("Email format is invalid")
                .rejectedValue("not-an-email")
                .build(); // GH-90000

        ErrorResponse error = ErrorResponse.builder() // GH-90000
                .status(400) // GH-90000
                .code("VALIDATION_ERROR")
                .message("Validation failed")
                .validationErrors(List.of(ve)) // GH-90000
                .build(); // GH-90000

        assertThat(error.getValidationErrors()).hasSize(1); // GH-90000
        ErrorResponse.ValidationError got = error.getValidationErrors().get(0); // GH-90000
        assertThat(got.getField()).isEqualTo("email");
        assertThat(got.getMessage()).isEqualTo("Email format is invalid");
        assertThat(got.getRejectedValue()).isEqualTo("not-an-email");
    }

    @Test
    @DisplayName("builder creates response with structured detailsMap")
    void builderWithDetailsMap() { // GH-90000
        ErrorResponse error = ErrorResponse.builder() // GH-90000
                .status(429) // GH-90000
                .code("RATE_LIMITED")
                .message("Too many requests")
                .detailsMap(Map.of("retryAfter", "60", "limit", "100")) // GH-90000
                .build(); // GH-90000

        assertThat(error.getDetailsMap()).containsKey("retryAfter");
        assertThat(error.getDetailsMap()).containsKey("limit");
    }

    @Test
    @DisplayName("ValidationError builder populates all fields")
    void validationErrorBuilder() { // GH-90000
        ErrorResponse.ValidationError ve = ErrorResponse.ValidationError.builder() // GH-90000
                .field("age")
                .message("Age must be at least 18")
                .rejectedValue(15) // GH-90000
                .build(); // GH-90000

        assertThat(ve.getField()).isEqualTo("age");
        assertThat(ve.getMessage()).isEqualTo("Age must be at least 18");
        assertThat(ve.getRejectedValue()).isEqualTo(15); // GH-90000
    }
}
