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

@DisplayName("TaskValidator [GH-90000]")
class TaskValidatorTest {

    private final TaskValidator validator = new TaskValidator(); // GH-90000

    private TaskDefinition validDefinition() { // GH-90000
        return TaskDefinition.builder() // GH-90000
                .id("task-1 [GH-90000]")
                .name("Task [GH-90000]")
                .description("desc [GH-90000]")
                .domain("testing [GH-90000]")
                .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
                .requiredCapabilities(List.of("cap [GH-90000]"))
                .parameters(Map.of()) // GH-90000
                .complexity(TaskComplexity.SIMPLE) // GH-90000
                .dependencies(List.of()) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("validateTaskDefinition: blank id and name → reports both violations [GH-90000]")
    void validateTaskDefinitionShouldFailWhenRequiredFieldsAreMissing() { // GH-90000
        TaskDefinition invalidDefinition = TaskDefinition.builder() // GH-90000
                .id("  [GH-90000]")
                .name(" [GH-90000]")
                .description("desc [GH-90000]")
                .domain("testing [GH-90000]")
                .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
                .requiredCapabilities(List.of("cap [GH-90000]"))
                .parameters(Map.of()) // GH-90000
                .complexity(TaskComplexity.SIMPLE) // GH-90000
                .dependencies(List.of()) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        ValidationResult result = validator.validateTaskDefinition(invalidDefinition); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.violations()).extracting(ValidationResult.Violation::message) // GH-90000
                .contains("Task ID cannot be null or blank", "Task name cannot be null or blank"); // GH-90000
    }

    @Test
    @DisplayName("validateTaskDefinition: empty requiredCapabilities → capability violation [GH-90000]")
    void validateTaskDefinitionShouldFailWhenNoCapabilitiesProvided() { // GH-90000
        TaskDefinition definition = TaskDefinition.builder() // GH-90000
                .id("task-1 [GH-90000]")
                .name("My Task [GH-90000]")
                .description("desc [GH-90000]")
                .domain("testing [GH-90000]")
                .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
                .requiredCapabilities(List.of()) // GH-90000
                .parameters(Map.of()) // GH-90000
                .complexity(TaskComplexity.SIMPLE) // GH-90000
                .dependencies(List.of()) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        ValidationResult result = validator.validateTaskDefinition(definition); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.violations()).extracting(ValidationResult.Violation::message) // GH-90000
                .contains("Task must require at least one capability [GH-90000]");
    }

    @Test
    @DisplayName("validateTaskDefinition: valid definition → no violations [GH-90000]")
    void validateTaskDefinitionShouldSucceedForValidDefinition() { // GH-90000
        ValidationResult result = validator.validateTaskDefinition(validDefinition()); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.violations()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("validateInput: missing required param and wrong type → two violations [GH-90000]")
    void validateInputShouldFailForMissingRequiredAndInvalidType() { // GH-90000
        TaskDefinition definition = TaskDefinition.builder() // GH-90000
                .id("task-1 [GH-90000]")
                .name("Task [GH-90000]")
                .description("desc [GH-90000]")
                .domain("testing [GH-90000]")
                .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
                .requiredCapabilities(List.of("cap [GH-90000]"))
                .parameters(Map.of( // GH-90000
                        "title", ParameterSpec.required("string", "required title"), // GH-90000
                        "count", ParameterSpec.optional("number", "optional number", null) // GH-90000
                ))
                .complexity(TaskComplexity.SIMPLE) // GH-90000
                .dependencies(List.of()) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        ValidationResult result = validator.validateInput(definition, Map.of("count", "not-a-number")); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.violations()).extracting(ValidationResult.Violation::message) // GH-90000
                .contains("Required parameter missing: title", "Parameter count has invalid type. Expected: number"); // GH-90000
    }

    @Test
    @DisplayName("validateInput: valid map input → passes [GH-90000]")
    void validateInputShouldSucceedForValidInput() { // GH-90000
        TaskDefinition definition = TaskDefinition.builder() // GH-90000
                .id("task-1 [GH-90000]")
                .name("Task [GH-90000]")
                .description("desc [GH-90000]")
                .domain("testing [GH-90000]")
                .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
                .requiredCapabilities(List.of("cap [GH-90000]"))
                .parameters(Map.of("title", ParameterSpec.required("string", "required title"))) // GH-90000
                .complexity(TaskComplexity.SIMPLE) // GH-90000
                .dependencies(List.of()) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        ValidationResult result = validator.validateInput(definition, Map.of("title", "hello")); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.violations()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("validateInput: non-Map input → skips map checks, returns valid [GH-90000]")
    void validateInputShouldSucceedForNonMapInput() { // GH-90000
        ValidationResult result = validator.validateInput(validDefinition(), "plain string input"); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.violations()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("validateInput: boolean value for boolean param → passes type check [GH-90000]")
    void validateInputShouldAcceptBooleanForBooleanType() { // GH-90000
        TaskDefinition definition = TaskDefinition.builder() // GH-90000
                .id("task-bool [GH-90000]")
                .name("Bool Task [GH-90000]")
                .description("desc [GH-90000]")
                .domain("testing [GH-90000]")
                .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
                .requiredCapabilities(List.of("cap [GH-90000]"))
                .parameters(Map.of("flag", ParameterSpec.required("boolean", "a boolean flag"))) // GH-90000
                .complexity(TaskComplexity.SIMPLE) // GH-90000
                .dependencies(List.of()) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        ValidationResult result = validator.validateInput(definition, Map.of("flag", true)); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("validateInput: string value for boolean param → fails type check [GH-90000]")
    void validateInputShouldRejectWrongTypeForBooleanParam() { // GH-90000
        TaskDefinition definition = TaskDefinition.builder() // GH-90000
                .id("task-bool [GH-90000]")
                .name("Bool Task [GH-90000]")
                .description("desc [GH-90000]")
                .domain("testing [GH-90000]")
                .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
                .requiredCapabilities(List.of("cap [GH-90000]"))
                .parameters(Map.of("flag", ParameterSpec.required("boolean", "a boolean flag"))) // GH-90000
                .complexity(TaskComplexity.SIMPLE) // GH-90000
                .dependencies(List.of()) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        ValidationResult result = validator.validateInput(definition, Map.of("flag", "true")); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.violations()).extracting(ValidationResult.Violation::message) // GH-90000
                .contains("Parameter flag has invalid type. Expected: boolean [GH-90000]");
    }

    @Test
    @DisplayName("validateInput: Map value for object param → passes type check [GH-90000]")
    void validateInputShouldAcceptMapForObjectType() { // GH-90000
        TaskDefinition definition = TaskDefinition.builder() // GH-90000
                .id("task-obj [GH-90000]")
                .name("Object Task [GH-90000]")
                .description("desc [GH-90000]")
                .domain("testing [GH-90000]")
                .phase(SDLCPhase.IMPLEMENTATION) // GH-90000
                .requiredCapabilities(List.of("cap [GH-90000]"))
                .parameters(Map.of("config", ParameterSpec.optional("object", "config object", null))) // GH-90000
                .complexity(TaskComplexity.SIMPLE) // GH-90000
                .dependencies(List.of()) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000

        ValidationResult result = validator.validateInput(definition, Map.of("config", Map.of("key", "value"))); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
    }
}
