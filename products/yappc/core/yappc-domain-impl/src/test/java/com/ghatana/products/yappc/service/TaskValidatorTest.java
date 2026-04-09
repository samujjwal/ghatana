package com.ghatana.products.yappc.service;

import com.ghatana.platform.core.validation.ValidationResult;
import com.ghatana.products.yappc.domain.task.ParameterSpec;
import com.ghatana.products.yappc.domain.task.SDLCPhase;
import com.ghatana.products.yappc.domain.task.TaskComplexity;
import com.ghatana.products.yappc.domain.task.TaskDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskValidator")
class TaskValidatorTest {

    private final TaskValidator validator = new TaskValidator();

    private TaskDefinition validDefinition() {
        return TaskDefinition.builder()
                .id("task-1")
                .name("Task")
                .description("desc")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION)
                .requiredCapabilities(List.of("cap"))
                .parameters(Map.of())
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();
    }

    @Test
    @DisplayName("validateTaskDefinition: blank id and name → reports both violations")
    void validateTaskDefinitionShouldFailWhenRequiredFieldsAreMissing() {
        TaskDefinition invalidDefinition = TaskDefinition.builder()
                .id(" ")
                .name("")
                .description("desc")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION)
                .requiredCapabilities(List.of("cap"))
                .parameters(Map.of())
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        ValidationResult result = validator.validateTaskDefinition(invalidDefinition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations()).extracting(ValidationResult.Violation::message)
                .contains("Task ID cannot be null or blank", "Task name cannot be null or blank");
    }

    @Test
    @DisplayName("validateTaskDefinition: empty requiredCapabilities → capability violation")
    void validateTaskDefinitionShouldFailWhenNoCapabilitiesProvided() {
        TaskDefinition definition = TaskDefinition.builder()
                .id("task-1")
                .name("My Task")
                .description("desc")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION)
                .requiredCapabilities(List.of())
                .parameters(Map.of())
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        ValidationResult result = validator.validateTaskDefinition(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations()).extracting(ValidationResult.Violation::message)
                .contains("Task must require at least one capability");
    }

    @Test
    @DisplayName("validateTaskDefinition: valid definition → no violations")
    void validateTaskDefinitionShouldSucceedForValidDefinition() {
        ValidationResult result = validator.validateTaskDefinition(validDefinition());

        assertThat(result.isValid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    @DisplayName("validateInput: missing required param and wrong type → two violations")
    void validateInputShouldFailForMissingRequiredAndInvalidType() {
        TaskDefinition definition = TaskDefinition.builder()
                .id("task-1")
                .name("Task")
                .description("desc")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION)
                .requiredCapabilities(List.of("cap"))
                .parameters(Map.of(
                        "title", ParameterSpec.required("string", "required title"),
                        "count", ParameterSpec.optional("number", "optional number", null)
                ))
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        ValidationResult result = validator.validateInput(definition, Map.of("count", "not-a-number"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations()).extracting(ValidationResult.Violation::message)
                .contains("Required parameter missing: title", "Parameter count has invalid type. Expected: number");
    }

    @Test
    @DisplayName("validateInput: valid map input → passes")
    void validateInputShouldSucceedForValidInput() {
        TaskDefinition definition = TaskDefinition.builder()
                .id("task-1")
                .name("Task")
                .description("desc")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION)
                .requiredCapabilities(List.of("cap"))
                .parameters(Map.of("title", ParameterSpec.required("string", "required title")))
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        ValidationResult result = validator.validateInput(definition, Map.of("title", "hello"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    @DisplayName("validateInput: non-Map input → skips map checks, returns valid")
    void validateInputShouldSucceedForNonMapInput() {
        ValidationResult result = validator.validateInput(validDefinition(), "plain string input");

        assertThat(result.isValid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    @DisplayName("validateInput: boolean value for boolean param → passes type check")
    void validateInputShouldAcceptBooleanForBooleanType() {
        TaskDefinition definition = TaskDefinition.builder()
                .id("task-bool")
                .name("Bool Task")
                .description("desc")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION)
                .requiredCapabilities(List.of("cap"))
                .parameters(Map.of("flag", ParameterSpec.required("boolean", "a boolean flag")))
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        ValidationResult result = validator.validateInput(definition, Map.of("flag", true));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("validateInput: string value for boolean param → fails type check")
    void validateInputShouldRejectWrongTypeForBooleanParam() {
        TaskDefinition definition = TaskDefinition.builder()
                .id("task-bool")
                .name("Bool Task")
                .description("desc")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION)
                .requiredCapabilities(List.of("cap"))
                .parameters(Map.of("flag", ParameterSpec.required("boolean", "a boolean flag")))
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        ValidationResult result = validator.validateInput(definition, Map.of("flag", "true"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations()).extracting(ValidationResult.Violation::message)
                .contains("Parameter flag has invalid type. Expected: boolean");
    }

    @Test
    @DisplayName("validateInput: Map value for object param → passes type check")
    void validateInputShouldAcceptMapForObjectType() {
        TaskDefinition definition = TaskDefinition.builder()
                .id("task-obj")
                .name("Object Task")
                .description("desc")
                .domain("testing")
                .phase(SDLCPhase.IMPLEMENTATION)
                .requiredCapabilities(List.of("cap"))
                .parameters(Map.of("config", ParameterSpec.optional("object", "config object", null)))
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        ValidationResult result = validator.validateInput(definition, Map.of("config", Map.of("key", "value")));

        assertThat(result.isValid()).isTrue();
    }
}
