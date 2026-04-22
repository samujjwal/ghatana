package com.ghatana.platform.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ErrorCode} enum — verifies all values have valid codes, messages,
 * and HTTP status codes.
 *
 * @doc.type class
 * @doc.purpose Unit tests for ErrorCode enum values and accessors
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("ErrorCode — enum invariants [GH-90000]")
class ErrorCodeTest {

    @Test
    @DisplayName("all enum values have non-null, non-blank code [GH-90000]")
    void allValues_nonNullCode() { // GH-90000
        for (ErrorCode code : ErrorCode.values()) { // GH-90000
            assertThat(code.getCode()) // GH-90000
                    .as("ErrorCode.%s must have a non-blank code", code.name()) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
        }
    }

    @Test
    @DisplayName("all enum values have non-null, non-blank default message [GH-90000]")
    void allValues_nonNullDefaultMessage() { // GH-90000
        for (ErrorCode code : ErrorCode.values()) { // GH-90000
            assertThat(code.getDefaultMessage()) // GH-90000
                    .as("ErrorCode.%s must have a non-blank defaultMessage", code.name()) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
        }
    }

    @Test
    @DisplayName("all enum values have a non-null HTTP status code [GH-90000]")
    void allValues_nonNullHttpStatus() { // GH-90000
        for (ErrorCode code : ErrorCode.values()) { // GH-90000
            assertThat(code.getHttpStatus()) // GH-90000
                    .as("ErrorCode.%s must have a non-null httpStatus", code.name()) // GH-90000
                    .isNotNull(); // GH-90000
        }
    }

    @Test
    @DisplayName("codes are unique across all enum values [GH-90000]")
    void allValues_uniqueCodes() { // GH-90000
        long distinctCount = java.util.Arrays.stream(ErrorCode.values()) // GH-90000
                .map(ErrorCode::getCode) // GH-90000
                .distinct() // GH-90000
                .count(); // GH-90000
        assertThat(distinctCount).isEqualTo(ErrorCode.values().length); // GH-90000
    }

    @Test
    @DisplayName("AUTHENTICATION_ERROR has HTTP 401 [GH-90000]")
    void authenticationError_is401() { // GH-90000
        assertThat(ErrorCode.AUTHENTICATION_ERROR.getHttpStatus()).isEqualTo(401); // GH-90000
    }

    @Test
    @DisplayName("AUTHORIZATION_ERROR has HTTP 403 [GH-90000]")
    void authorizationError_is403() { // GH-90000
        assertThat(ErrorCode.AUTHORIZATION_ERROR.getHttpStatus()).isEqualTo(403); // GH-90000
    }

    @Test
    @DisplayName("RESOURCE_NOT_FOUND has HTTP 404 [GH-90000]")
    void resourceNotFound_is404() { // GH-90000
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).isEqualTo(404); // GH-90000
    }

    @Test
    @DisplayName("RATE_LIMITED has HTTP 429 [GH-90000]")
    void rateLimited_is429() { // GH-90000
        assertThat(ErrorCode.RATE_LIMITED.getHttpStatus()).isEqualTo(429); // GH-90000
    }

    @Test
    @DisplayName("TIMEOUT has HTTP 504 [GH-90000]")
    void timeout_is504() { // GH-90000
        assertThat(ErrorCode.TIMEOUT.getHttpStatus()).isEqualTo(504); // GH-90000
    }

    @Test
    @DisplayName("VALIDATION_ERROR has HTTP 422 [GH-90000]")
    void validationError_is422() { // GH-90000
        assertThat(ErrorCode.VALIDATION_ERROR.getHttpStatus()).isEqualTo(422); // GH-90000
    }

    @Test
    @DisplayName("UNKNOWN_ERROR has HTTP 500 [GH-90000]")
    void unknownError_is500() { // GH-90000
        assertThat(ErrorCode.UNKNOWN_ERROR.getHttpStatus()).isEqualTo(500); // GH-90000
    }

    @Test
    @DisplayName("error code strings match prefix format [GH-90000]")
    void codes_followPrefixFormat() { // GH-90000
        for (ErrorCode code : ErrorCode.values()) { // GH-90000
            assertThat(code.getCode()) // GH-90000
                    .as("ErrorCode.%s code must match PREFIX-NNN format", code.name()) // GH-90000
                    .matches("[A-Z]+-[A-Z0-9]+ [GH-90000]");
        }
    }
}
