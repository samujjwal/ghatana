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
@DisplayName("BaseException Hierarchy Tests [GH-90000]")
class BaseExceptionHierarchyTest {

    @Test
    @DisplayName("validation failure uses canonical validation error code [GH-90000]")
    void validationFailureUsesCanonicalValidationErrorCode() { // GH-90000
        Object validationResult = new Object(); // GH-90000

        ValidationFailureException exception = new ValidationFailureException(validationResult); // GH-90000

        assertThat(exception).isInstanceOf(BaseException.class); // GH-90000
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR); // GH-90000
        assertThat(exception.getMessage()).isEqualTo("Validation failed [GH-90000]");
        assertThat(exception.getValidationResult()).isSameAs(validationResult); // GH-90000
        assertThat(exception.isRetryable()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("governance policy violation uses dedicated governance error code [GH-90000]")
    void governancePolicyViolationUsesDedicatedErrorCode() { // GH-90000
        IllegalStateException cause = new IllegalStateException("policy denied [GH-90000]");

        GovernancePolicyException exception = new GovernancePolicyException("tenant boundary blocked", cause); // GH-90000

        assertThat(exception).isInstanceOf(BaseException.class); // GH-90000
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GOVERNANCE_POLICY_VIOLATION); // GH-90000
        assertThat(exception.getMessage()).isEqualTo("tenant boundary blocked [GH-90000]");
        assertThat(exception.getCause()).isSameAs(cause); // GH-90000
        assertThat(exception.isRetryable()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("governance policy violation defaults to forbidden category [GH-90000]")
    void governancePolicyViolationDefaultsToForbiddenCategory() { // GH-90000
        assertThat(ErrorCode.GOVERNANCE_POLICY_VIOLATION.getCode()).isEqualTo("AUTH-006 [GH-90000]");
        assertThat(ErrorCode.GOVERNANCE_POLICY_VIOLATION.getHttpStatus()).isEqualTo(403); // GH-90000
        assertThat(ErrorCode.GOVERNANCE_POLICY_VIOLATION.getCategory()).isEqualTo("AUTH [GH-90000]");
    }
}
