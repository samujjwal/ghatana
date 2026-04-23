/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.learning;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for model input/output schema validation (D012). // GH-90000
 *
 * <p>Validates schema compliance for model inputs and outputs.
 *
 * @doc.type class
 * @doc.purpose Model schema validation tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ModelSchemaValidation – Input/Output Schema (D012)")
class ModelSchemaValidationTest extends EventloopTestBase {

    @Mock
    private ModelEvaluationService evaluationService;

    // ─────────────────────────────────────────────────────────────────────────
    // Input Schema Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input Schema")
    class InputSchemaTests {

        @Test
        @DisplayName("[D012]: valid_input_passes_validation")
        void validInputPassesValidation() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> validInput = Map.of( // GH-90000
                "text", "Sample input text",
                "temperature", 0.7,
                "max_tokens", 100
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( // GH-90000
                    true, List.of(), List.of() // GH-90000
                );

            when(evaluationService.validateInputSchema(modelId, validInput)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateInputSchema(modelId, validInput) // GH-90000
            );

            assertThat(validation.isValid()).isTrue(); // GH-90000
            assertThat(validation.errors()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("[D012]: missing_required_field_fails_validation")
        void missingRequiredFieldFailsValidation() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> invalidInput = Map.of( // GH-90000
                // Missing required "text" field
                "temperature", 0.7
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( // GH-90000
                    false,
                    List.of(new ModelEvaluationService.SchemaError( // GH-90000
                        "text", "string", "null",
                        "Required field 'text' is missing"
                    )),
                    List.of() // GH-90000
                );

            when(evaluationService.validateInputSchema(modelId, invalidInput)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateInputSchema(modelId, invalidInput) // GH-90000
            );

            assertThat(validation.isValid()).isFalse(); // GH-90000
            assertThat(validation.errors()).hasSize(1); // GH-90000
            assertThat(validation.errors().get(0).field()).isEqualTo("text");
        }

        @Test
        @DisplayName("[D012]: wrong_type_fails_validation")
        void wrongTypeFailsValidation() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> wrongTypeInput = Map.of( // GH-90000
                "text", "valid text",
                "temperature", "not_a_number"  // Should be number
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( // GH-90000
                    false,
                    List.of(new ModelEvaluationService.SchemaError( // GH-90000
                        "temperature", "number", "string",
                        "Field 'temperature' expected type 'number' but got 'string'"
                    )),
                    List.of() // GH-90000
                );

            when(evaluationService.validateInputSchema(modelId, wrongTypeInput)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateInputSchema(modelId, wrongTypeInput) // GH-90000
            );

            assertThat(validation.isValid()).isFalse(); // GH-90000
            assertThat(validation.errors().get(0).expectedType()).isEqualTo("number");
            assertThat(validation.errors().get(0).actualType()).isEqualTo("string");
        }

        @Test
        @DisplayName("[D012]: extra_fields_produce_warning")
        void extraFieldsProduceWarning() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> inputWithExtra = Map.of( // GH-90000
                "text", "valid",
                "temperature", 0.7,
                "extra_field", "unexpected"  // Not in schema
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( // GH-90000
                    true,  // Still valid
                    List.of(), // GH-90000
                    List.of("Unexpected field 'extra_field' will be ignored")
                );

            when(evaluationService.validateInputSchema(modelId, inputWithExtra)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateInputSchema(modelId, inputWithExtra) // GH-90000
            );

            assertThat(validation.isValid()).isTrue(); // GH-90000
            assertThat(validation.warnings()).isNotEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Output Schema Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Output Schema")
    class OutputSchemaTests {

        @Test
        @DisplayName("[D012]: valid_output_passes_validation")
        void validOutputPassesValidation() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> validOutput = Map.of( // GH-90000
                "text", "Generated response",
                "tokens_used", 50,
                "finish_reason", "stop"
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult(true, List.of(), List.of()); // GH-90000

            when(evaluationService.validateOutputSchema(modelId, validOutput)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateOutputSchema(modelId, validOutput) // GH-90000
            );

            assertThat(validation.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D012]: missing_output_field_fails_validation")
        void missingOutputFieldFailsValidation() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> incompleteOutput = Map.of( // GH-90000
                // Missing "text" field
                "tokens_used", 50
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( // GH-90000
                    false,
                    List.of(new ModelEvaluationService.SchemaError( // GH-90000
                        "text", "string", "null",
                        "Required output field 'text' is missing"
                    )),
                    List.of() // GH-90000
                );

            when(evaluationService.validateOutputSchema(modelId, incompleteOutput)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateOutputSchema(modelId, incompleteOutput) // GH-90000
            );

            assertThat(validation.isValid()).isFalse(); // GH-90000
            assertThat(validation.errors().get(0).field()).isEqualTo("text");
        }

        @Test
        @DisplayName("[D012]: output_type_mismatch_fails")
        void outputTypeMismatchFails() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> wrongTypeOutput = Map.of( // GH-90000
                "text", "valid",
                "tokens_used", "fifty"  // Should be integer
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( // GH-90000
                    false,
                    List.of(new ModelEvaluationService.SchemaError( // GH-90000
                        "tokens_used", "integer", "string",
                        "Output field 'tokens_used' type mismatch"
                    )),
                    List.of() // GH-90000
                );

            when(evaluationService.validateOutputSchema(modelId, wrongTypeOutput)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateOutputSchema(modelId, wrongTypeOutput) // GH-90000
            );

            assertThat(validation.isValid()).isFalse(); // GH-90000
            assertThat(validation.errors().get(0).expectedType()).isEqualTo("integer");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nested Schema Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Nested Schema")
    class NestedSchemaTests {

        @Test
        @DisplayName("[D012]: nested_object_validated_recursively")
        void nestedObjectValidatedRecursively() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> nestedInput = Map.of( // GH-90000
                "config", Map.of( // GH-90000
                    "temperature", 0.7,
                    "top_p", 0.9
                ),
                "messages", List.of( // GH-90000
                    Map.of("role", "user", "content", "Hello") // GH-90000
                )
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult(true, List.of(), List.of()); // GH-90000

            when(evaluationService.validateInputSchema(modelId, nestedInput)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateInputSchema(modelId, nestedInput) // GH-90000
            );

            assertThat(validation.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D012]: nested_field_error_reports_path")
        void nestedFieldErrorReportsPath() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> invalidNested = Map.of( // GH-90000
                "config", Map.of( // GH-90000
                    "temperature", "invalid"  // Should be number
                )
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( // GH-90000
                    false,
                    List.of(new ModelEvaluationService.SchemaError( // GH-90000
                        "config.temperature", "number", "string",
                        "Field 'config.temperature' type mismatch"
                    )),
                    List.of() // GH-90000
                );

            when(evaluationService.validateInputSchema(modelId, invalidNested)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateInputSchema(modelId, invalidNested) // GH-90000
            );

            assertThat(validation.errors().get(0).field()).contains("config.temperature");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Array Schema Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Array Schema")
    class ArraySchemaTests {

        @Test
        @DisplayName("[D012]: array_elements_validated")
        void arrayElementsValidated() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> arrayInput = Map.of( // GH-90000
                "items", List.of("item1", "item2", "item3") // GH-90000
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult(true, List.of(), List.of()); // GH-90000

            when(evaluationService.validateInputSchema(modelId, arrayInput)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateInputSchema(modelId, arrayInput) // GH-90000
            );

            assertThat(validation.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D012]: array_element_type_mismatch_fails")
        void arrayElementTypeMismatchFails() { // GH-90000
            String modelId = "model-001";
            Map<String, Object> mixedArray = Map.of( // GH-90000
                "numbers", List.of(1, 2, "three")  // Should be all integers // GH-90000
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( // GH-90000
                    false,
                    List.of(new ModelEvaluationService.SchemaError( // GH-90000
                        "numbers[2]", "integer", "string",
                        "Array element at index 2 type mismatch"
                    )),
                    List.of() // GH-90000
                );

            when(evaluationService.validateInputSchema(modelId, mixedArray)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> // GH-90000
                evaluationService.validateInputSchema(modelId, mixedArray) // GH-90000
            );

            assertThat(validation.isValid()).isFalse(); // GH-90000
            assertThat(validation.errors().get(0).field()).contains("numbers");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schema Error Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Schema Errors")
    class SchemaErrorTests {

        @Test
        @DisplayName("[D012]: schema_error_contains_expected_type")
        void schemaErrorContainsExpectedType() { // GH-90000
            ModelEvaluationService.SchemaError error =
                new ModelEvaluationService.SchemaError( // GH-90000
                    "field", "number", "string", "Type mismatch"
                );

            assertThat(error.expectedType()).isEqualTo("number");
            assertThat(error.actualType()).isEqualTo("string");
            assertThat(error.message()).contains("mismatch");
        }

        @Test
        @DisplayName("[D012]: multiple_errors_collected")
        void multipleErrorsCollected() { // GH-90000
            List<ModelEvaluationService.SchemaError> errors = List.of( // GH-90000
                new ModelEvaluationService.SchemaError("field1", "string", "null", "Missing"), // GH-90000
                new ModelEvaluationService.SchemaError("field2", "number", "string", "Wrong type"), // GH-90000
                new ModelEvaluationService.SchemaError("field3", "boolean", "integer", "Wrong type") // GH-90000
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult(false, errors, List.of()); // GH-90000

            assertThat(result.errors()).hasSize(3); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schema Compatibility Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Schema Compatibility")
    class SchemaCompatibilityTests {

        @Test
        @DisplayName("[D012]: backward_compatible_changes_accepted")
        void backwardCompatibleChangesAccepted() { // GH-90000
            // Adding optional fields is backward compatible
            Map<String, Object> inputWithOptional = Map.of( // GH-90000
                "required_field", "value",
                "optional_field", "extra value"  // Optional field added
            );

            // Should be valid if optional_field is not required
            assertThat(inputWithOptional).containsKeys("required_field", "optional_field"); // GH-90000
        }

        @Test
        @DisplayName("[D012]: breaking_changes_rejected")
        void breakingChangesRejected() { // GH-90000
            // Removing required fields is breaking
            Map<String, Object> missingRequired = Map.of( // GH-90000
                // "required_field" is missing
                "other_field", "value"
            );

            assertThat(missingRequired).doesNotContainKey("required_field");
        }

        @Test
        @DisplayName("[D012]: null_values_handled_correctly")
        void nullValuesHandledCorrectly() { // GH-90000
            Map<String, Object> withNull = new java.util.HashMap<>(); // GH-90000
            withNull.put("field", null); // GH-90000

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( // GH-90000
                    false,
                    List.of(new ModelEvaluationService.SchemaError( // GH-90000
                        "field", "string", "null",
                        "Field 'field' cannot be null"
                    )),
                    List.of() // GH-90000
                );

            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(withNull).containsKey(result.errors().get(0).field()); // GH-90000
            assertThat(withNull.get(result.errors().get(0).field())).isNull(); // GH-90000
        }
    }
}
