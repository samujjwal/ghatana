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
@DisplayName("ErrorCode — enum invariants")
class ErrorCodeTest {

    @Test
    @DisplayName("all enum values have non-null, non-blank code")
    void allValues_nonNullCode() { 
        for (ErrorCode code : ErrorCode.values()) { 
            assertThat(code.getCode()) 
                    .as("ErrorCode.%s must have a non-blank code", code.name()) 
                    .isNotNull() 
                    .isNotBlank(); 
        }
    }

    @Test
    @DisplayName("all enum values have non-null, non-blank default message")
    void allValues_nonNullDefaultMessage() { 
        for (ErrorCode code : ErrorCode.values()) { 
            assertThat(code.getDefaultMessage()) 
                    .as("ErrorCode.%s must have a non-blank defaultMessage", code.name()) 
                    .isNotNull() 
                    .isNotBlank(); 
        }
    }

    @Test
    @DisplayName("all enum values have a non-null HTTP status code")
    void allValues_nonNullHttpStatus() { 
        for (ErrorCode code : ErrorCode.values()) { 
            assertThat(code.getHttpStatus()) 
                    .as("ErrorCode.%s must have a non-null httpStatus", code.name()) 
                    .isNotNull(); 
        }
    }

    @Test
    @DisplayName("codes are unique across all enum values")
    void allValues_uniqueCodes() { 
        long distinctCount = java.util.Arrays.stream(ErrorCode.values()) 
                .map(ErrorCode::getCode) 
                .distinct() 
                .count(); 
        assertThat(distinctCount).isEqualTo(ErrorCode.values().length); 
    }

    @Test
    @DisplayName("AUTHENTICATION_ERROR has HTTP 401")
    void authenticationError_is401() { 
        assertThat(ErrorCode.AUTHENTICATION_ERROR.getHttpStatus()).isEqualTo(401); 
    }

    @Test
    @DisplayName("AUTHORIZATION_ERROR has HTTP 403")
    void authorizationError_is403() { 
        assertThat(ErrorCode.AUTHORIZATION_ERROR.getHttpStatus()).isEqualTo(403); 
    }

    @Test
    @DisplayName("RESOURCE_NOT_FOUND has HTTP 404")
    void resourceNotFound_is404() { 
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).isEqualTo(404); 
    }

    @Test
    @DisplayName("RATE_LIMITED has HTTP 429")
    void rateLimited_is429() { 
        assertThat(ErrorCode.RATE_LIMITED.getHttpStatus()).isEqualTo(429); 
    }

    @Test
    @DisplayName("TIMEOUT has HTTP 504")
    void timeout_is504() { 
        assertThat(ErrorCode.TIMEOUT.getHttpStatus()).isEqualTo(504); 
    }

    @Test
    @DisplayName("VALIDATION_ERROR has HTTP 422")
    void validationError_is422() { 
        assertThat(ErrorCode.VALIDATION_ERROR.getHttpStatus()).isEqualTo(422); 
    }

    @Test
    @DisplayName("UNKNOWN_ERROR has HTTP 500")
    void unknownError_is500() { 
        assertThat(ErrorCode.UNKNOWN_ERROR.getHttpStatus()).isEqualTo(500); 
    }

    @Test
    @DisplayName("error code strings match prefix format")
    void codes_followPrefixFormat() { 
        for (ErrorCode code : ErrorCode.values()) { 
            assertThat(code.getCode()) 
                    .as("ErrorCode.%s code must match PREFIX-NNN format", code.name()) 
                    .matches("[A-Z]+-[A-Z0-9]+");
        }
    }
}
