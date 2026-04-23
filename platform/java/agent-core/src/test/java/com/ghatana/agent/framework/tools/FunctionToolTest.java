/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
        public String greet(String name) { // GH-90000
            return "Hello, " + name + "!";
        }

        public int add(int a, int b) { // GH-90000
            return a + b;
        }

        public double calculateScore(String category, double weight, boolean normalized) { // GH-90000
            return normalized ? weight * 100 : weight;
        }

        public void failMethod(String input) { // GH-90000
            throw new IllegalStateException("Intentional failure: " + input); // GH-90000
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
        void shouldCreateFromClassAndMethod() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "greet"); // GH-90000

            assertThat(tool.getTargetClass()).isEqualTo(SampleService.class); // GH-90000
            assertThat(tool.getMethodName()).isEqualTo("greet");
            assertThat(tool.getResolvedMethod()).isNotNull(); // GH-90000
            assertThat(tool.getDescription()).contains("SampleService.greet");
        }

        @Test
        @DisplayName("should allow custom description via withDescription")
        void shouldAllowCustomDescription() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "greet") // GH-90000
                    .withDescription("Greet a user by name");

            assertThat(tool.getDescription()).isEqualTo("Greet a user by name");
        }

        @Test
        @DisplayName("should introspect parameter schema")
        void shouldIntrospectParameterSchema() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "calculateScore"); // GH-90000

            List<FunctionTool.ParameterInfo> params = tool.getParameterSchema(); // GH-90000
            assertThat(params).hasSize(3); // GH-90000
            assertThat(params.get(0).jsonType()).isEqualTo("string");
            assertThat(params.get(1).jsonType()).isEqualTo("number");
            assertThat(params.get(2).jsonType()).isEqualTo("boolean");
            // Primitives are always required
            assertThat(params.get(1).required()).isTrue(); // GH-90000
            assertThat(params.get(2).required()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should generate JSON Schema compatible output")
        void shouldGenerateJsonSchema() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "add"); // GH-90000

            Map<String, Object> schema = tool.toJsonSchema(); // GH-90000
            assertThat(schema).containsKey("name");
            assertThat(schema).containsKey("description");
            assertThat(schema).containsKey("parameters");

            assertThat(schema.get("parameters")).isInstanceOf(Map.class);
            Map<?, ?> params = (Map<?, ?>) schema.get("parameters");
            assertThat(params.get("type")).isEqualTo("object");
            assertThat(params.keySet().containsAll(List.of("properties", "required"))).isTrue();
        }

        @Test
        @DisplayName("should handle non-existent method gracefully")
        void shouldHandleNonExistentMethod() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "nonExistent"); // GH-90000

            assertThat(tool.getResolvedMethod()).isNull(); // GH-90000
            assertThat(tool.getParameterSchema()).isEmpty(); // GH-90000
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
        void shouldValidateCorrectInput() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "greet"); // GH-90000

            List<String> errors = tool.validateInput(Map.of("name", "Alice")); // GH-90000
            assertThat(errors).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should detect missing required parameters")
        void shouldDetectMissingRequiredParams() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "add"); // GH-90000

            // 'a' and 'b' are int → required
            List<String> errors = tool.validateInput(Map.of()); // GH-90000
            assertThat(errors).hasSizeGreaterThanOrEqualTo(2); // GH-90000
            assertThat(errors).anyMatch(e -> e.contains("Required parameter"));
        }

        @Test
        @DisplayName("should detect unknown parameters")
        void shouldDetectUnknownParams() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "greet"); // GH-90000

            List<String> errors = tool.validateInput(Map.of("name", "Alice", "unknown", "value")); // GH-90000
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
        void shouldInvokeWithCorrectArgs() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "greet"); // GH-90000
            SampleService target = new SampleService(); // GH-90000

            // Method uses -parameters flag so name is available
            String paramName = tool.getParameterSchema().get(0).name(); // GH-90000
            Object result = tool.invoke(target, Map.of(paramName, "World")); // GH-90000
            assertThat(result).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("should coerce number types during invocation")
        void shouldCoerceNumberTypes() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "add"); // GH-90000
            SampleService target = new SampleService(); // GH-90000

            List<FunctionTool.ParameterInfo> params = tool.getParameterSchema(); // GH-90000
            // Long values should be coerced to int
            Object result = tool.invoke(target, Map.of( // GH-90000
                    params.get(0).name(), 10L, // GH-90000
                    params.get(1).name(), 20L // GH-90000
            ));
            assertThat(result).isEqualTo(30); // GH-90000
        }

        @Test
        @DisplayName("should throw ToolInvocationException for unresolvable method")
        void shouldThrowForUnresolvableMethod() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "nonExistent"); // GH-90000
            SampleService target = new SampleService(); // GH-90000

            assertThatThrownBy(() -> tool.invoke(target, Map.of())) // GH-90000
                    .isInstanceOf(FunctionTool.ToolInvocationException.class) // GH-90000
                    .hasMessageContaining("could not be resolved");
        }

        @Test
        @DisplayName("should wrap method exceptions in ToolInvocationException")
        void shouldWrapMethodExceptions() { // GH-90000
            FunctionTool tool = FunctionTool.create(SampleService.class, "failMethod"); // GH-90000
            SampleService target = new SampleService(); // GH-90000

            String paramName = tool.getParameterSchema().get(0).name(); // GH-90000
            assertThatThrownBy(() -> tool.invoke(target, Map.of(paramName, "test"))) // GH-90000
                    .isInstanceOf(FunctionTool.ToolInvocationException.class) // GH-90000
                    .hasMessageContaining("threw exception")
                    .hasCauseInstanceOf(IllegalStateException.class); // GH-90000
        }
    }
}
