/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the enhanced {@link FunctionTool}.
 */
@DisplayName("FunctionTool")
class FunctionToolTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Test Target Class
    // ═══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unused")
    static class SampleService {
        public String greet(String name) {
            return "Hello, " + name + "!";
        }

        public int add(int a, int b) {
            return a + b;
        }

        public double calculateScore(String category, double weight, boolean normalized) {
            return normalized ? weight * 100 : weight;
        }

        public void failMethod(String input) {
            throw new IllegalStateException("Intentional failure: " + input);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Creation & Schema Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Creation and Schema")
    class CreationAndSchema {

        @Test
        @DisplayName("should create from class and method name")
        void shouldCreateFromClassAndMethod() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "greet");

            assertThat(tool.getTargetClass()).isEqualTo(SampleService.class);
            assertThat(tool.getMethodName()).isEqualTo("greet");
            assertThat(tool.getResolvedMethod()).isNotNull();
            assertThat(tool.getDescription()).contains("SampleService.greet");
        }

        @Test
        @DisplayName("should allow custom description via withDescription")
        void shouldAllowCustomDescription() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "greet")
                    .withDescription("Greet a user by name");

            assertThat(tool.getDescription()).isEqualTo("Greet a user by name");
        }

        @Test
        @DisplayName("should introspect parameter schema")
        void shouldIntrospectParameterSchema() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "calculateScore");

            List<FunctionTool.ParameterInfo> params = tool.getParameterSchema();
            assertThat(params).hasSize(3);
            assertThat(params.get(0).jsonType()).isEqualTo("string");
            assertThat(params.get(1).jsonType()).isEqualTo("number");
            assertThat(params.get(2).jsonType()).isEqualTo("boolean");
            // Primitives are always required
            assertThat(params.get(1).required()).isTrue();
            assertThat(params.get(2).required()).isTrue();
        }

        @Test
        @DisplayName("should generate JSON Schema compatible output")
        void shouldGenerateJsonSchema() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "add");

            Map<String, Object> schema = tool.toJsonSchema();
            assertThat(schema).containsKey("name");
            assertThat(schema).containsKey("description");
            assertThat(schema).containsKey("parameters");

            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) schema.get("parameters");
            assertThat(params.get("type")).isEqualTo("object");
            assertThat(params).containsKey("properties");
            assertThat(params).containsKey("required");
        }

        @Test
        @DisplayName("should handle non-existent method gracefully")
        void shouldHandleNonExistentMethod() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "nonExistent");

            assertThat(tool.getResolvedMethod()).isNull();
            assertThat(tool.getParameterSchema()).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("should validate correct input")
        void shouldValidateCorrectInput() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "greet");

            List<String> errors = tool.validateInput(Map.of("name", "Alice"));
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should detect missing required parameters")
        void shouldDetectMissingRequiredParams() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "add");

            // 'a' and 'b' are int → required
            List<String> errors = tool.validateInput(Map.of());
            assertThat(errors).hasSizeGreaterThanOrEqualTo(2);
            assertThat(errors).anyMatch(e -> e.contains("Required parameter"));
        }

        @Test
        @DisplayName("should detect unknown parameters")
        void shouldDetectUnknownParams() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "greet");

            List<String> errors = tool.validateInput(Map.of("name", "Alice", "unknown", "value"));
            assertThat(errors).anyMatch(e -> e.contains("Unknown parameter 'unknown'"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Execution Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Execution Binding")
    class ExecutionBinding {

        @Test
        @DisplayName("should invoke method with correct arguments")
        void shouldInvokeWithCorrectArgs() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "greet");
            SampleService target = new SampleService();

            // Method uses -parameters flag so name is available
            String paramName = tool.getParameterSchema().get(0).name();
            Object result = tool.invoke(target, Map.of(paramName, "World"));
            assertThat(result).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("should coerce number types during invocation")
        void shouldCoerceNumberTypes() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "add");
            SampleService target = new SampleService();

            List<FunctionTool.ParameterInfo> params = tool.getParameterSchema();
            // Long values should be coerced to int
            Object result = tool.invoke(target, Map.of(
                    params.get(0).name(), 10L,
                    params.get(1).name(), 20L
            ));
            assertThat(result).isEqualTo(30);
        }

        @Test
        @DisplayName("should throw ToolInvocationException for unresolvable method")
        void shouldThrowForUnresolvableMethod() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "nonExistent");
            SampleService target = new SampleService();

            assertThatThrownBy(() -> tool.invoke(target, Map.of()))
                    .isInstanceOf(FunctionTool.ToolInvocationException.class)
                    .hasMessageContaining("could not be resolved");
        }

        @Test
        @DisplayName("should wrap method exceptions in ToolInvocationException")
        void shouldWrapMethodExceptions() {
            FunctionTool tool = FunctionTool.create(SampleService.class, "failMethod");
            SampleService target = new SampleService();

            String paramName = tool.getParameterSchema().get(0).name();
            assertThatThrownBy(() -> tool.invoke(target, Map.of(paramName, "test")))
                    .isInstanceOf(FunctionTool.ToolInvocationException.class)
                    .hasMessageContaining("threw exception")
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }
}
