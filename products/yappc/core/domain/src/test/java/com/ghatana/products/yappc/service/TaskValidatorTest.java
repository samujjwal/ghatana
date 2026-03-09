package com.ghatana.products.yappc.service;

import com.ghatana.products.yappc.domain.task.*;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TaskValidator.
 */
@DisplayName("TaskValidator Tests")
/**
 * @doc.type class
 * @doc.purpose Handles task validator test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class TaskValidatorTest extends EventloopTestBase {

    private TaskValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TaskValidator();
    }

    @Test
    @DisplayName("Should validate valid task definition")
    void shouldValidateValidTaskDefinition() {
        // GIVEN
        TaskDefinition task = TaskDefinition.builder()
                .id("test-task")
                .name("Test Task")
                .description("A test task")
                .domain("testing")
                .phase(SDLCPhase.TESTING)
                .requiredCapabilities(List.of("test-capability"))
                .parameters(Map.of(
                        "param1", new ParameterSpec("string", true, "Test parameter", null)
                ))
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        // WHEN
        ValidationResult result = validator.validateTaskDefinition(task);

        // THEN
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("Should reject task definition with null ID")
    void shouldRejectNullId() {
        assertThatThrownBy(() -> TaskDefinition.builder()
                .id(null)
                .name("Test Task")
                .description("A test task")
                .domain("testing")
                .phase(SDLCPhase.TESTING)
                .requiredCapabilities(List.of("test-capability"))
                .parameters(Map.of())
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should validate task input with all required parameters")
    void shouldValidateCompleteInput() {
        // GIVEN
        TaskDefinition task = TaskDefinition.builder()
                .id("test-task")
                .name("Test Task")
                .description("A test task")
                .domain("testing")
                .phase(SDLCPhase.TESTING)
                .requiredCapabilities(List.of("test-capability"))
                .parameters(Map.of(
                        "name", new ParameterSpec("string", true, "Name parameter", null),
                        "count", new ParameterSpec("number", true, "Count parameter", null)
                ))
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        Map<String, Object> input = Map.of(
                "name", "test",
                "count", 42
        );

        // WHEN
        ValidationResult result = validator.validateInput(task, input);

        // THEN
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("Should reject input missing required parameter")
    void shouldRejectMissingRequiredParameter() {
        // GIVEN
        TaskDefinition task = TaskDefinition.builder()
                .id("test-task")
                .name("Test Task")
                .description("A test task")
                .domain("testing")
                .phase(SDLCPhase.TESTING)
                .requiredCapabilities(List.of("test-capability"))
                .parameters(Map.of(
                        "required", new ParameterSpec("string", true, "Required parameter", null)
                ))
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        Map<String, Object> input = Map.of(); // Missing required parameter

        // WHEN
        ValidationResult result = validator.validateInput(task, input);

        // THEN
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Required parameter missing: required");
    }

    @Test
    @DisplayName("Should validate type correctly")
    void shouldValidateTypes() {
        // GIVEN
        TaskDefinition task = TaskDefinition.builder()
                .id("test-task")
                .name("Test Task")
                .description("A test task")
                .domain("testing")
                .phase(SDLCPhase.TESTING)
                .requiredCapabilities(List.of("test-capability"))
                .parameters(Map.of(
                        "stringParam", new ParameterSpec("string", false, "String param", null),
                        "numberParam", new ParameterSpec("number", false, "Number param", null),
                        "boolParam", new ParameterSpec("boolean", false, "Bool param", null)
                ))
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        Map<String, Object> input = Map.of(
                "stringParam", "test",
                "numberParam", 42,
                "boolParam", true
        );

        // WHEN
        ValidationResult result = validator.validateInput(task, input);

        // THEN
        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid type")
    void shouldRejectInvalidType() {
        // GIVEN
        TaskDefinition task = TaskDefinition.builder()
                .id("test-task")
                .name("Test Task")
                .description("A test task")
                .domain("testing")
                .phase(SDLCPhase.TESTING)
                .requiredCapabilities(List.of("test-capability"))
                .parameters(Map.of(
                        "numberParam", new ParameterSpec("number", false, "Number param", null)
                ))
                .complexity(TaskComplexity.SIMPLE)
                .dependencies(List.of())
                .metadata(Map.of())
                .build();

        Map<String, Object> input = Map.of(
                "numberParam", "not-a-number" // Wrong type
        );

        // WHEN
        ValidationResult result = validator.validateInput(task, input);

        // THEN
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("invalid type"));
    }
}
