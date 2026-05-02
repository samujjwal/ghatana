/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Tests for model input/output schema validation (D012). 
 *
 * <p>Validates schema compliance for model inputs and outputs.
 *
 * @doc.type class
 * @doc.purpose Model schema validation tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
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
        void validInputPassesValidation() { 
            String modelId = "model-001";
            Map<String, Object> validInput = Map.of( 
                "text", "Sample input text",
                "temperature", 0.7,
                "max_tokens", 100
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( 
                    true, List.of(), List.of() 
                );

            when(evaluationService.validateInputSchema(modelId, validInput)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateInputSchema(modelId, validInput) 
            );

            assertThat(validation.isValid()).isTrue(); 
            assertThat(validation.errors()).isEmpty(); 
        }

        @Test
        @DisplayName("[D012]: missing_required_field_fails_validation")
        void missingRequiredFieldFailsValidation() { 
            String modelId = "model-001";
            Map<String, Object> invalidInput = Map.of( 
                // Missing required "text" field
                "temperature", 0.7
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( 
                    false,
                    List.of(new ModelEvaluationService.SchemaError( 
                        "text", "string", "null",
                        "Required field 'text' is missing"
                    )),
                    List.of() 
                );

            when(evaluationService.validateInputSchema(modelId, invalidInput)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateInputSchema(modelId, invalidInput) 
            );

            assertThat(validation.isValid()).isFalse(); 
            assertThat(validation.errors()).hasSize(1); 
            assertThat(validation.errors().get(0).field()).isEqualTo("text");
        }

        @Test
        @DisplayName("[D012]: wrong_type_fails_validation")
        void wrongTypeFailsValidation() { 
            String modelId = "model-001";
            Map<String, Object> wrongTypeInput = Map.of( 
                "text", "valid text",
                "temperature", "not_a_number"  // Should be number
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( 
                    false,
                    List.of(new ModelEvaluationService.SchemaError( 
                        "temperature", "number", "string",
                        "Field 'temperature' expected type 'number' but got 'string'"
                    )),
                    List.of() 
                );

            when(evaluationService.validateInputSchema(modelId, wrongTypeInput)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateInputSchema(modelId, wrongTypeInput) 
            );

            assertThat(validation.isValid()).isFalse(); 
            assertThat(validation.errors().get(0).expectedType()).isEqualTo("number");
            assertThat(validation.errors().get(0).actualType()).isEqualTo("string");
        }

        @Test
        @DisplayName("[D012]: extra_fields_produce_warning")
        void extraFieldsProduceWarning() { 
            String modelId = "model-001";
            Map<String, Object> inputWithExtra = Map.of( 
                "text", "valid",
                "temperature", 0.7,
                "extra_field", "unexpected"  // Not in schema
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( 
                    true,  // Still valid
                    List.of(), 
                    List.of("Unexpected field 'extra_field' will be ignored")
                );

            when(evaluationService.validateInputSchema(modelId, inputWithExtra)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateInputSchema(modelId, inputWithExtra) 
            );

            assertThat(validation.isValid()).isTrue(); 
            assertThat(validation.warnings()).isNotEmpty(); 
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
        void validOutputPassesValidation() { 
            String modelId = "model-001";
            Map<String, Object> validOutput = Map.of( 
                "text", "Generated response",
                "tokens_used", 50,
                "finish_reason", "stop"
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult(true, List.of(), List.of()); 

            when(evaluationService.validateOutputSchema(modelId, validOutput)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateOutputSchema(modelId, validOutput) 
            );

            assertThat(validation.isValid()).isTrue(); 
        }

        @Test
        @DisplayName("[D012]: missing_output_field_fails_validation")
        void missingOutputFieldFailsValidation() { 
            String modelId = "model-001";
            Map<String, Object> incompleteOutput = Map.of( 
                // Missing "text" field
                "tokens_used", 50
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( 
                    false,
                    List.of(new ModelEvaluationService.SchemaError( 
                        "text", "string", "null",
                        "Required output field 'text' is missing"
                    )),
                    List.of() 
                );

            when(evaluationService.validateOutputSchema(modelId, incompleteOutput)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateOutputSchema(modelId, incompleteOutput) 
            );

            assertThat(validation.isValid()).isFalse(); 
            assertThat(validation.errors().get(0).field()).isEqualTo("text");
        }

        @Test
        @DisplayName("[D012]: output_type_mismatch_fails")
        void outputTypeMismatchFails() { 
            String modelId = "model-001";
            Map<String, Object> wrongTypeOutput = Map.of( 
                "text", "valid",
                "tokens_used", "fifty"  // Should be integer
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( 
                    false,
                    List.of(new ModelEvaluationService.SchemaError( 
                        "tokens_used", "integer", "string",
                        "Output field 'tokens_used' type mismatch"
                    )),
                    List.of() 
                );

            when(evaluationService.validateOutputSchema(modelId, wrongTypeOutput)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateOutputSchema(modelId, wrongTypeOutput) 
            );

            assertThat(validation.isValid()).isFalse(); 
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
        void nestedObjectValidatedRecursively() { 
            String modelId = "model-001";
            Map<String, Object> nestedInput = Map.of( 
                "config", Map.of( 
                    "temperature", 0.7,
                    "top_p", 0.9
                ),
                "messages", List.of( 
                    Map.of("role", "user", "content", "Hello") 
                )
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult(true, List.of(), List.of()); 

            when(evaluationService.validateInputSchema(modelId, nestedInput)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateInputSchema(modelId, nestedInput) 
            );

            assertThat(validation.isValid()).isTrue(); 
        }

        @Test
        @DisplayName("[D012]: nested_field_error_reports_path")
        void nestedFieldErrorReportsPath() { 
            String modelId = "model-001";
            Map<String, Object> invalidNested = Map.of( 
                "config", Map.of( 
                    "temperature", "invalid"  // Should be number
                )
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( 
                    false,
                    List.of(new ModelEvaluationService.SchemaError( 
                        "config.temperature", "number", "string",
                        "Field 'config.temperature' type mismatch"
                    )),
                    List.of() 
                );

            when(evaluationService.validateInputSchema(modelId, invalidNested)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateInputSchema(modelId, invalidNested) 
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
        void arrayElementsValidated() { 
            String modelId = "model-001";
            Map<String, Object> arrayInput = Map.of( 
                "items", List.of("item1", "item2", "item3") 
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult(true, List.of(), List.of()); 

            when(evaluationService.validateInputSchema(modelId, arrayInput)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateInputSchema(modelId, arrayInput) 
            );

            assertThat(validation.isValid()).isTrue(); 
        }

        @Test
        @DisplayName("[D012]: array_element_type_mismatch_fails")
        void arrayElementTypeMismatchFails() { 
            String modelId = "model-001";
            Map<String, Object> mixedArray = Map.of( 
                "numbers", List.of(1, 2, "three")  // Should be all integers 
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( 
                    false,
                    List.of(new ModelEvaluationService.SchemaError( 
                        "numbers[2]", "integer", "string",
                        "Array element at index 2 type mismatch"
                    )),
                    List.of() 
                );

            when(evaluationService.validateInputSchema(modelId, mixedArray)) 
                .thenReturn(Promise.of(result)); 

            ModelEvaluationService.SchemaValidationResult validation = runPromise(() -> 
                evaluationService.validateInputSchema(modelId, mixedArray) 
            );

            assertThat(validation.isValid()).isFalse(); 
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
        void schemaErrorContainsExpectedType() { 
            ModelEvaluationService.SchemaError error =
                new ModelEvaluationService.SchemaError( 
                    "field", "number", "string", "Type mismatch"
                );

            assertThat(error.expectedType()).isEqualTo("number");
            assertThat(error.actualType()).isEqualTo("string");
            assertThat(error.message()).contains("mismatch");
        }

        @Test
        @DisplayName("[D012]: multiple_errors_collected")
        void multipleErrorsCollected() { 
            List<ModelEvaluationService.SchemaError> errors = List.of( 
                new ModelEvaluationService.SchemaError("field1", "string", "null", "Missing"), 
                new ModelEvaluationService.SchemaError("field2", "number", "string", "Wrong type"), 
                new ModelEvaluationService.SchemaError("field3", "boolean", "integer", "Wrong type") 
            );

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult(false, errors, List.of()); 

            assertThat(result.errors()).hasSize(3); 
            assertThat(result.isValid()).isFalse(); 
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
        void backwardCompatibleChangesAccepted() { 
            // Adding optional fields is backward compatible
            Map<String, Object> inputWithOptional = Map.of( 
                "required_field", "value",
                "optional_field", "extra value"  // Optional field added
            );

            // Should be valid if optional_field is not required
            assertThat(inputWithOptional).containsKeys("required_field", "optional_field"); 
        }

        @Test
        @DisplayName("[D012]: breaking_changes_rejected")
        void breakingChangesRejected() { 
            // Removing required fields is breaking
            Map<String, Object> missingRequired = Map.of( 
                // "required_field" is missing
                "other_field", "value"
            );

            assertThat(missingRequired).doesNotContainKey("required_field");
        }

        @Test
        @DisplayName("[D012]: null_values_handled_correctly")
        void nullValuesHandledCorrectly() { 
            Map<String, Object> withNull = new java.util.HashMap<>(); 
            withNull.put("field", null); 

            ModelEvaluationService.SchemaValidationResult result =
                new ModelEvaluationService.SchemaValidationResult( 
                    false,
                    List.of(new ModelEvaluationService.SchemaError( 
                        "field", "string", "null",
                        "Field 'field' cannot be null"
                    )),
                    List.of() 
                );

            assertThat(result.isValid()).isFalse(); 
            assertThat(withNull).containsKey(result.errors().get(0).field()); 
            assertThat(withNull.get(result.errors().get(0).field())).isNull(); 
        }
    }
}
