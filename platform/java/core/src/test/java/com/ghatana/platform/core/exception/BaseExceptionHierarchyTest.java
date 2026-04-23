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
    void validationFailureUsesCanonicalValidationErrorCode() { // GH-90000
        Object validationResult = new Object(); // GH-90000

        ValidationFailureException exception = new ValidationFailureException(validationResult); // GH-90000

        assertThat(exception).isInstanceOf(BaseException.class); // GH-90000
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR); // GH-90000
        assertThat(exception.getMessage()).isEqualTo("Validation failed");
        assertThat(exception.getValidationResult()).isSameAs(validationResult); // GH-90000
        assertThat(exception.isRetryable()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("governance policy violation uses dedicated governance error code")
    void governancePolicyViolationUsesDedicatedErrorCode() { // GH-90000
        IllegalStateException cause = new IllegalStateException("policy denied");

        GovernancePolicyException exception = new GovernancePolicyException("tenant boundary blocked", cause); // GH-90000

        assertThat(exception).isInstanceOf(BaseException.class); // GH-90000
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GOVERNANCE_POLICY_VIOLATION); // GH-90000
        assertThat(exception.getMessage()).isEqualTo("tenant boundary blocked");
        assertThat(exception.getCause()).isSameAs(cause); // GH-90000
        assertThat(exception.isRetryable()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("governance policy violation defaults to forbidden category")
    void governancePolicyViolationDefaultsToForbiddenCategory() { // GH-90000
        assertThat(ErrorCode.GOVERNANCE_POLICY_VIOLATION.getCode()).isEqualTo("AUTH-006");
        assertThat(ErrorCode.GOVERNANCE_POLICY_VIOLATION.getHttpStatus()).isEqualTo(403); // GH-90000
        assertThat(ErrorCode.GOVERNANCE_POLICY_VIOLATION.getCategory()).isEqualTo("AUTH");
    }
}
