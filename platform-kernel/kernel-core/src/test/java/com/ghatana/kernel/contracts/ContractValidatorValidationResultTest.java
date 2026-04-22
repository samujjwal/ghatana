package com.ghatana.kernel.contracts;

import com.ghatana.platform.core.validation.ValidationResult;
import com.ghatana.platform.core.validation.ValidationResult.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContractValidator ValidationResult Tests [GH-90000]")
@SuppressWarnings("deprecation [GH-90000]")
class ContractValidatorValidationResultTest {

    @Test
    @DisplayName("failed string errors should preserve compatibility view and create typed core errors [GH-90000]")
    void failedStringErrorsShouldPreserveCompatibilityView() { // GH-90000
        ContractValidator.ValidationResult result = ContractValidator.ValidationResult.failed( // GH-90000
            List.of("missing schema", "invalid version")); // GH-90000

        assertThat(result.valid()).isFalse(); // GH-90000
        assertThat(result.errors()).containsExactly("missing schema", "invalid version"); // GH-90000
        assertThat(result.getErrors()) // GH-90000
            .extracting(Violation::field, Violation::message) // GH-90000
            .containsExactly( // GH-90000
                org.assertj.core.groups.Tuple.tuple("CONTRACT_VALIDATION_FAILED", "missing schema"), // GH-90000
                org.assertj.core.groups.Tuple.tuple("CONTRACT_VALIDATION_FAILED", "invalid version")); // GH-90000
        assertThat(result.toCoreValidationResult().isValid()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("typed core results should round-trip through kernel validation results [GH-90000]")
    void typedCoreResultsShouldRoundTrip() { // GH-90000
        ValidationResult coreResult = ValidationResult.invalid("subjects", "Schema must not be empty"); // GH-90000

        ContractValidator.ValidationResult result =
            ContractValidator.ValidationResult.fromCoreValidationResult(coreResult); // GH-90000

        assertThat(result.valid()).isFalse(); // GH-90000
        assertThat(result.errors()).containsExactly("Schema must not be empty [GH-90000]");
        assertThat(result.getErrors()).singleElement().satisfies(error -> { // GH-90000
            assertThat(error.field()).isEqualTo("subjects [GH-90000]");
            assertThat(error.message()).isEqualTo("Schema must not be empty [GH-90000]");
        });
        assertThat(result.toCoreValidationResult()).isSameAs(coreResult); // GH-90000
    }
}
