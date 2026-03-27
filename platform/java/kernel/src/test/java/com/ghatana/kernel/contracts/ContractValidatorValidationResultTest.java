package com.ghatana.kernel.contracts;

import com.ghatana.platform.validation.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContractValidator ValidationResult Tests")
class ContractValidatorValidationResultTest {

    @Test
    @DisplayName("failed string errors should preserve compatibility view and create typed core errors")
    void failedStringErrorsShouldPreserveCompatibilityView() {
        ContractValidator.ValidationResult result = ContractValidator.ValidationResult.failed(
            List.of("missing schema", "invalid version"));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("missing schema", "invalid version");
        assertThat(result.getErrors())
            .extracting(ValidationError::getCode, ValidationError::getMessage)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("CONTRACT_VALIDATION_FAILED", "missing schema"),
                org.assertj.core.groups.Tuple.tuple("CONTRACT_VALIDATION_FAILED", "invalid version"));
        assertThat(result.toCoreValidationResult().isValid()).isFalse();
    }

    @Test
    @DisplayName("typed core results should round-trip through kernel validation results")
    void typedCoreResultsShouldRoundTrip() {
        com.ghatana.platform.validation.ValidationResult coreResult =
            com.ghatana.platform.validation.ValidationResult.failure(
                new ValidationError("SCHEMA_EMPTY", "Schema must not be empty", "subjects", null));

        ContractValidator.ValidationResult result =
            ContractValidator.ValidationResult.fromCoreValidationResult(coreResult);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("Schema must not be empty");
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getCode()).isEqualTo("SCHEMA_EMPTY");
            assertThat(error.getPath()).isEqualTo("subjects");
        });
        assertThat(result.toCoreValidationResult()).isSameAs(coreResult);
    }
}