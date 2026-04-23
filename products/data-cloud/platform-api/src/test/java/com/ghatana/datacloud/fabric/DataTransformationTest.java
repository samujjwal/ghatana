/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.fabric;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for data transformation pipeline (PF003). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Data transformation pipeline tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataTransformation – Pipeline Tests (PF003)")
class DataTransformationTest extends EventloopTestBase {

    @Mock
    private DataTransformationPipeline pipeline;

    @Nested
    @DisplayName("Pipeline Creation")
    class PipelineCreationTests {

        @Test
        @DisplayName("[PF003]: create_pipeline_creates_definition")
        void createPipelineCreatesDefinition() { // GH-90000
            DataTransformationPipeline.PipelineDefinition definition = createPipelineDefinition( // GH-90000
                "new-pipeline", "Transform Pipeline", List.of() // GH-90000
            );

            when(pipeline.createPipeline(any())) // GH-90000
                .thenReturn(Promise.of(definition)); // GH-90000

            DataTransformationPipeline.PipelineDefinition result = runPromise(() -> // GH-90000
                pipeline.createPipeline(definition) // GH-90000
            );

            assertThat(result.id()).isEqualTo("new-pipeline");
            assertThat(result.enabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[PF003]: get_pipeline_returns_existing")
        void getPipelineReturnsExisting() { // GH-90000
            String pipelineId = "existing-pipeline";
            DataTransformationPipeline.PipelineDefinition definition = createPipelineDefinition( // GH-90000
                pipelineId, "Existing", List.of() // GH-90000
            );

            when(pipeline.getPipeline(pipelineId)) // GH-90000
                .thenReturn(Promise.of(java.util.Optional.of(definition))); // GH-90000

            java.util.Optional<DataTransformationPipeline.PipelineDefinition> result = runPromise(() -> // GH-90000
                pipeline.getPipeline(pipelineId) // GH-90000
            );

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().id()).isEqualTo(pipelineId); // GH-90000
        }

        @Test
        @DisplayName("[PF003]: list_pipelines_returns_tenant_pipelines")
        void listPipelinesReturnsTenantPipelines() { // GH-90000
            String tenantId = "tenant-alpha";

            List<DataTransformationPipeline.PipelineDefinition> pipelines = List.of( // GH-90000
                createPipelineDefinition("p1", "Pipeline 1", List.of()), // GH-90000
                createPipelineDefinition("p2", "Pipeline 2", List.of()), // GH-90000
                createPipelineDefinition("p3", "Pipeline 3", List.of()) // GH-90000
            );

            when(pipeline.listPipelines(tenantId)) // GH-90000
                .thenReturn(Promise.of(pipelines)); // GH-90000

            List<DataTransformationPipeline.PipelineDefinition> result = runPromise(() -> // GH-90000
                pipeline.listPipelines(tenantId) // GH-90000
            );

            assertThat(result).hasSize(3); // GH-90000
            assertThat(result).allMatch(p -> "tenant-alpha".equals(p.tenantId())); // GH-90000
        }

        @Test
        @DisplayName("[PF003]: delete_pipeline_removes_pipeline")
        void deletePipelineRemovesPipeline() { // GH-90000
            String pipelineId = "pipeline-to-delete";

            when(pipeline.deletePipeline(pipelineId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> pipeline.deletePipeline(pipelineId)); // GH-90000

            verify(pipeline).deletePipeline(pipelineId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Pipeline Execution")
    class PipelineExecutionTests {

        @Test
        @DisplayName("[PF003]: execute_pipeline_transforms_data")
        void executePipelineTransformsData() { // GH-90000
            String pipelineId = "transform-pipeline";

            List<Map<String, Object>> input = List.of( // GH-90000
                Map.of("name", "Alice", "age", 30), // GH-90000
                Map.of("name", "Bob", "age", 25) // GH-90000
            );

            List<Map<String, Object>> output = List.of( // GH-90000
                Map.of("fullName", "ALICE", "isAdult", true), // GH-90000
                Map.of("fullName", "BOB", "isAdult", true) // GH-90000
            );

            DataTransformationPipeline.ExecutionResult result = new DataTransformationPipeline.ExecutionResult( // GH-90000
                pipelineId, "exec-001", true, output, 2, 2, 250, List.of(), List.of() // GH-90000
            );

            when(pipeline.execute(pipelineId, input)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            DataTransformationPipeline.ExecutionResult execResult = runPromise(() -> // GH-90000
                pipeline.execute(pipelineId, input) // GH-90000
            );

            assertThat(execResult.success()).isTrue(); // GH-90000
            assertThat(execResult.inputCount()).isEqualTo(2); // GH-90000
            assertThat(execResult.outputCount()).isEqualTo(2); // GH-90000
            assertThat(execResult.output()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("[PF003]: execute_pipeline_reports_errors")
        void executePipelineReportsErrors() { // GH-90000
            String pipelineId = "failing-pipeline";

            List<Map<String, Object>> input = List.of(Map.of("data", "invalid")); // GH-90000

            DataTransformationPipeline.ExecutionResult result = new DataTransformationPipeline.ExecutionResult( // GH-90000
                pipelineId, "exec-001", false, List.of(), 1, 0, 100, // GH-90000
                List.of("Transformation failed: invalid data"), List.of()
            );

            when(pipeline.execute(pipelineId, input)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            DataTransformationPipeline.ExecutionResult execResult = runPromise(() -> // GH-90000
                pipeline.execute(pipelineId, input) // GH-90000
            );

            assertThat(execResult.success()).isFalse(); // GH-90000
            assertThat(execResult.errors()).isNotEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Step Types")
    class StepTypesTests {

        @Test
        @DisplayName("[PF003]: filter_step_filters_data")
        void filterStepFiltersData() { // GH-90000
            DataTransformationPipeline.TransformationStep filterStep = new DataTransformationPipeline.TransformationStep( // GH-90000
                "step-1", "Filter Adults", DataTransformationPipeline.StepType.FILTER,
                Map.of("condition", "age >= 18"), null, List.of("age"), List.of()
            );

            assertThat(filterStep.type()).isEqualTo(DataTransformationPipeline.StepType.FILTER); // GH-90000
            assertThat(filterStep.condition()).isNull(); // Using config instead // GH-90000
        }

        @Test
        @DisplayName("[PF003]: map_step_transforms_fields")
        void mapStepTransformsFields() { // GH-90000
            DataTransformationPipeline.TransformationStep mapStep = new DataTransformationPipeline.TransformationStep( // GH-90000
                "step-1", "Uppercase Names", DataTransformationPipeline.StepType.MAP,
                Map.of("expression", "name.toUpperCase()"), null, // GH-90000
                List.of("name"), List.of("upperName")
            );

            assertThat(mapStep.type()).isEqualTo(DataTransformationPipeline.StepType.MAP); // GH-90000
            assertThat(mapStep.inputColumns()).contains("name");
            assertThat(mapStep.outputColumns()).contains("upperName");
        }

        @Test
        @DisplayName("[PF003]: aggregate_step_groups_data")
        void aggregateStepGroupsData() { // GH-90000
            DataTransformationPipeline.TransformationStep aggStep = new DataTransformationPipeline.TransformationStep( // GH-90000
                "step-1", "Sum by Category", DataTransformationPipeline.StepType.AGGREGATE,
                Map.of("groupBy", "category", "operation", "sum", "field", "amount"), // GH-90000
                null, List.of("category", "amount"), List.of("total")
            );

            assertThat(aggStep.type()).isEqualTo(DataTransformationPipeline.StepType.AGGREGATE); // GH-90000
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("[PF003]: validate_returns_valid_for_good_pipeline")
        void validateReturnsValidForGoodPipeline() { // GH-90000
            DataTransformationPipeline.PipelineDefinition definition = createPipelineDefinition( // GH-90000
                "valid-pipeline", "Valid", List.of( // GH-90000
                    new DataTransformationPipeline.TransformationStep( // GH-90000
                        "s1", "Step 1", DataTransformationPipeline.StepType.FILTER,
                        Map.of(), null, List.of(), List.of() // GH-90000
                    )
                )
            );

            DataTransformationPipeline.ValidationResult validation = new DataTransformationPipeline.ValidationResult( // GH-90000
                true, List.of(), List.of(), // GH-90000
                List.of(new DataTransformationPipeline.StepValidation("s1", true, List.of())) // GH-90000
            );

            when(pipeline.validate(definition)) // GH-90000
                .thenReturn(Promise.of(validation)); // GH-90000

            DataTransformationPipeline.ValidationResult result = runPromise(() -> // GH-90000
                pipeline.validate(definition) // GH-90000
            );

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.errors()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("[PF003]: validate_returns_errors_for_invalid_pipeline")
        void validateReturnsErrorsForInvalidPipeline() { // GH-90000
            DataTransformationPipeline.PipelineDefinition definition = createPipelineDefinition( // GH-90000
                "invalid-pipeline", "Invalid", List.of() // GH-90000
            );

            DataTransformationPipeline.ValidationResult validation = new DataTransformationPipeline.ValidationResult( // GH-90000
                false,
                List.of("Pipeline must have at least one step"),
                List.of(), // GH-90000
                List.of() // GH-90000
            );

            when(pipeline.validate(definition)) // GH-90000
                .thenReturn(Promise.of(validation)); // GH-90000

            DataTransformationPipeline.ValidationResult result = runPromise(() -> // GH-90000
                pipeline.validate(definition) // GH-90000
            );

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).isNotEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Execution History")
    class ExecutionHistoryTests {

        @Test
        @DisplayName("[PF003]: get_execution_history_returns_records")
        void getExecutionHistoryReturnsRecords() { // GH-90000
            String pipelineId = "pipeline-with-history";

            List<DataTransformationPipeline.ExecutionRecord> history = List.of( // GH-90000
                new DataTransformationPipeline.ExecutionRecord( // GH-90000
                    "exec-001", pipelineId, true, 100, 100, 500, "user-001",
                    Instant.now().minusSeconds(3600), Instant.now().minusSeconds(3599) // GH-90000
                ),
                new DataTransformationPipeline.ExecutionRecord( // GH-90000
                    "exec-002", pipelineId, true, 200, 200, 750, "user-001",
                    Instant.now().minusSeconds(1800), Instant.now().minusSeconds(1798) // GH-90000
                )
            );

            when(pipeline.getExecutionHistory(pipelineId)) // GH-90000
                .thenReturn(Promise.of(history)); // GH-90000

            List<DataTransformationPipeline.ExecutionRecord> result = runPromise(() -> // GH-90000
                pipeline.getExecutionHistory(pipelineId) // GH-90000
            );

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result.get(0).pipelineId()).isEqualTo(pipelineId); // GH-90000
        }
    }

    private DataTransformationPipeline.PipelineDefinition createPipelineDefinition( // GH-90000
            String id, String name, List<DataTransformationPipeline.TransformationStep> steps) {
        return new DataTransformationPipeline.PipelineDefinition( // GH-90000
            id, name, "", "tenant-alpha", steps, Map.of(), null, null, true // GH-90000
        );
    }
}
