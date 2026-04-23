package com.ghatana.platform.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ErrorCodeMappers} — verifies translation from external error name
 * strings to canonical {@link ErrorCode} values.
 *
 * @doc.type class
 * @doc.purpose Unit tests for ErrorCodeMappers translation utilities
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("ErrorCodeMappers — external-to-canonical translation")
class ErrorCodeMappersTest {

    // ─── fromIngress ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fromIngress(null) returns UNKNOWN_ERROR")
    void fromIngress_null_returnsUnknown() { // GH-90000
        assertThat(ErrorCodeMappers.fromIngress(null)).isEqualTo(ErrorCode.UNKNOWN_ERROR); // GH-90000
    }

    @Test
    @DisplayName("fromIngress with unrecognized name returns UNKNOWN_ERROR")
    void fromIngress_unknownName_returnsUnknown() { // GH-90000
        assertThat(ErrorCodeMappers.fromIngress("TOTALLY_UNKNOWN")).isEqualTo(ErrorCode.UNKNOWN_ERROR);
    }

    @Test
    @DisplayName("fromIngress maps VALIDATION_ERROR correctly")
    void fromIngress_validationError_mapsCorrectly() { // GH-90000
        assertThat(ErrorCodeMappers.fromIngress("VALIDATION_ERROR")).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("fromIngress maps INVALID_REQUEST to VALIDATION_ERROR")
    void fromIngress_invalidRequest_mapsToValidation() { // GH-90000
        assertThat(ErrorCodeMappers.fromIngress("INVALID_REQUEST")).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("fromIngress maps UNAUTHORIZED to AUTHENTICATION_ERROR")
    void fromIngress_unauthorized_mapsToAuthentication() { // GH-90000
        assertThat(ErrorCodeMappers.fromIngress("UNAUTHORIZED")).isEqualTo(ErrorCode.AUTHENTICATION_ERROR);
    }

    @Test
    @DisplayName("fromIngress maps FORBIDDEN to AUTHORIZATION_ERROR")
    void fromIngress_forbidden_mapsToAuthorization() { // GH-90000
        assertThat(ErrorCodeMappers.fromIngress("FORBIDDEN")).isEqualTo(ErrorCode.AUTHORIZATION_ERROR);
    }

    @Test
    @DisplayName("fromIngress maps NOT_FOUND to RESOURCE_NOT_FOUND")
    void fromIngress_notFound_mapsToResourceNotFound() { // GH-90000
        assertThat(ErrorCodeMappers.fromIngress("NOT_FOUND")).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    // ─── fromDcmaar ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fromDcmaar(null) returns UNKNOWN_ERROR")
    void fromDcmaar_null_returnsUnknown() { // GH-90000
        assertThat(ErrorCodeMappers.fromDcmaar(null)).isEqualTo(ErrorCode.UNKNOWN_ERROR); // GH-90000
    }

    @Test
    @DisplayName("fromDcmaar with unrecognized name returns UNKNOWN_ERROR")
    void fromDcmaar_unknownName_returnsUnknown() { // GH-90000
        assertThat(ErrorCodeMappers.fromDcmaar("XYZZY")).isEqualTo(ErrorCode.UNKNOWN_ERROR);
    }

    @Test
    @DisplayName("fromDcmaar maps RESOURCE_NOT_FOUND correctly")
    void fromDcmaar_resourceNotFound_mapsCorrectly() { // GH-90000
        assertThat(ErrorCodeMappers.fromDcmaar("RESOURCE_NOT_FOUND")).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("fromDcmaar maps RESOURCE_ALREADY_EXISTS correctly")
    void fromDcmaar_resourceAlreadyExists_mapsCorrectly() { // GH-90000
        assertThat(ErrorCodeMappers.fromDcmaar("RESOURCE_ALREADY_EXISTS"))
                .isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS); // GH-90000
    }

    @Test
    @DisplayName("fromDcmaar maps TIMEOUT to TIMEOUT_ERROR")
    void fromDcmaar_timeout_mapsToTimeoutError() { // GH-90000
        assertThat(ErrorCodeMappers.fromDcmaar("TIMEOUT")).isEqualTo(ErrorCode.TIMEOUT_ERROR);
    }

    @Test
    @DisplayName("fromDcmaar maps DATABASE_ERROR correctly")
    void fromDcmaar_databaseError_mapsCorrectly() { // GH-90000
        assertThat(ErrorCodeMappers.fromDcmaar("DATABASE_ERROR")).isEqualTo(ErrorCode.DATABASE_ERROR);
    }

    // ─── fromKg ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fromKg(null) returns UNKNOWN_ERROR")
    void fromKg_null_returnsUnknown() { // GH-90000
        assertThat(ErrorCodeMappers.fromKg(null)).isEqualTo(ErrorCode.UNKNOWN_ERROR); // GH-90000
    }

    @Test
    @DisplayName("fromKg with unrecognized name returns UNKNOWN_ERROR")
    void fromKg_unknownName_returnsUnknown() { // GH-90000
        assertThat(ErrorCodeMappers.fromKg("SOMETHING_WEIRD")).isEqualTo(ErrorCode.UNKNOWN_ERROR);
    }

    @Test
    @DisplayName("fromKg maps VALIDATION_FAILED to VALIDATION_ERROR")
    void fromKg_validationFailed_mapsToValidationError() { // GH-90000
        assertThat(ErrorCodeMappers.fromKg("VALIDATION_FAILED")).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("fromKg maps DUPLICATE_RESOURCE to RESOURCE_ALREADY_EXISTS")
    void fromKg_duplicateResource_mapsToResourceAlreadyExists() { // GH-90000
        assertThat(ErrorCodeMappers.fromKg("DUPLICATE_RESOURCE")).isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("fromKg maps INTERNAL_ERROR to SERVICE_ERROR")
    void fromKg_internalError_mapsToServiceError() { // GH-90000
        assertThat(ErrorCodeMappers.fromKg("INTERNAL_ERROR")).isEqualTo(ErrorCode.SERVICE_ERROR);
    }

    @Test
    @DisplayName("fromKg maps TIMEOUT_ERROR to TIMEOUT_ERROR")
    void fromKg_timeoutError_mapsCorrectly() { // GH-90000
        assertThat(ErrorCodeMappers.fromKg("TIMEOUT_ERROR")).isEqualTo(ErrorCode.TIMEOUT_ERROR);
    }
}
