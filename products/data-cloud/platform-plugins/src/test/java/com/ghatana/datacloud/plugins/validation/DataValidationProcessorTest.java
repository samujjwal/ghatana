package com.ghatana.datacloud.plugins.validation;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataValidationProcessorTest extends EventloopTestBase {

    private static final TenantContext TENANT = TenantContext.of("tenant-a", "workspace-a");

    @Test
    @DisplayName("default processor stays Data-Cloud-local and never auto-enables AEP validation")
    @SuppressWarnings("deprecation")
    void defaultProcessorDoesNotEnableAepValidation() {
        DataValidationProcessor processor = new DataValidationProcessor();

        assertThat(processor.isAepAvailable()).isFalse();
    }

    @Test
    @DisplayName("default processor rejects empty entity payloads")
    void defaultProcessorRejectsEmptyEntityPayloads() {
        DataValidationProcessor processor = new DataValidationProcessor();
        EntityStore.Entity entity = EntityStore.Entity.builder()
                .collection("documents")
                .data(Map.of())
                .build();

        DataValidationProcessor.ValidationResult result =
                runPromise(() -> processor.validate(TENANT, entity));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).singleElement()
                .extracting(DataValidationProcessor.ValidationError::code)
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("rule-based validation still works without external runtime discovery")
    void validateWithRulesUsesBuiltInStrategy() {
        DataValidationProcessor processor = new DataValidationProcessor();

        DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules(
                TENANT,
                "documents",
                Map.of("title", "x"),
                List.of(DataValidationProcessor.ValidationRule.minLength("title", 3))));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).singleElement()
                .extracting(DataValidationProcessor.ValidationError::code)
                .isEqualTo("MIN_LENGTH");
    }
}
