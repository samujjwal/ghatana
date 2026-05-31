package com.ghatana.kernel.interop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KernelFhirHl7Plugin")
@Tag("purity-validation")
class KernelFhirHl7PluginTest {

    private final KernelFhirHl7Plugin plugin = new KernelFhirHl7Plugin();

    @Test
    @DisplayName("validates supported FHIR R4 resources")
    void validatesSupportedFhirR4Resources() {
        KernelFhirValidationResult result = plugin.validateResource(
            "Observation",
            """
                {"resourceType":"Observation","status":"final","code":{"text":"Glucose"},"subject":{"reference":"Patient/p-1"}}
                """
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.safeReasonCode()).isEqualTo("FHIR_RESOURCE_VALID");
    }

    @Test
    @DisplayName("rejects mismatched and incomplete resources with safe reason codes")
    void rejectsInvalidResourcesWithSafeReasonCodes() {
        KernelFhirValidationResult mismatch = plugin.validateResource(
            "Patient",
            "{\"resourceType\":\"Observation\",\"status\":\"final\"}"
        );
        KernelFhirValidationResult incomplete = plugin.validateResource(
            "Observation",
            "{\"resourceType\":\"Observation\",\"status\":\"final\"}"
        );

        assertThat(mismatch.valid()).isFalse();
        assertThat(mismatch.safeReasonCode()).isEqualTo("FHIR_RESOURCE_TYPE_MISMATCH");
        assertThat(incomplete.valid()).isFalse();
        assertThat(incomplete.safeReasonCode()).isEqualTo("FHIR_REQUIRED_FIELD_MISSING");
    }
}
