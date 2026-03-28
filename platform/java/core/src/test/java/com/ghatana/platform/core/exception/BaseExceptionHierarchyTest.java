package com.ghatana.platform.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the canonical core exception hierarchy.
 *
 * @doc.type class
 * @doc.purpose Verifies shared core exceptions stay aligned to the ErrorCode-based hierarchy
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("BaseException Hierarchy Tests")
class BaseExceptionHierarchyTest {

    @Test
    @DisplayName("validation failure uses canonical validation error code")
    void validationFailureUsesCanonicalValidationErrorCode() {
        Object validationResult = new Object();

        ValidationFailureException exception = new ValidationFailureException(validationResult);

        assertThat(exception).isInstanceOf(BaseException.class);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(exception.getMessage()).isEqualTo("Validation failed");
        assertThat(exception.getValidationResult()).isSameAs(validationResult);
        assertThat(exception.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("governance policy violation uses dedicated governance error code")
    void governancePolicyViolationUsesDedicatedErrorCode() {
        IllegalStateException cause = new IllegalStateException("policy denied");

        GovernancePolicyException exception = new GovernancePolicyException("tenant boundary blocked", cause);

        assertThat(exception).isInstanceOf(BaseException.class);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(exception.getMessage()).isEqualTo("tenant boundary blocked");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("governance policy violation defaults to forbidden category")
    void governancePolicyViolationDefaultsToForbiddenCategory() {
        assertThat(ErrorCode.GOVERNANCE_POLICY_VIOLATION.getCode()).isEqualTo("AUTH-006");
        assertThat(ErrorCode.GOVERNANCE_POLICY_VIOLATION.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.GOVERNANCE_POLICY_VIOLATION.getCategory()).isEqualTo("AUTH");
    }
}