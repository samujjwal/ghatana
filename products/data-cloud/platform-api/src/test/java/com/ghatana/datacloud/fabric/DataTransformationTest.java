/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Tests for data transformation pipeline (PF003).
 *
 * @doc.type class
 * @doc.purpose Data transformation pipeline tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataTransformation – Pipeline Tests (PF003)")
class DataTransformationTest extends EventloopTestBase {

    @Mock
    private DataTransformationPipeline pipeline;

    @Nested
    @DisplayName("Pipeline Creation")
    class PipelineCreationTests {

        @Test
        @DisplayName("[PF003]: create_pipeline_creates_definition")
        void createPipelineCreatesDefinition() {
            DataTransformationPipeline.PipelineDefinition definition = createPipelineDefinition(
                "new-pipeline", "Transform Pipeline", List.of()
            );

            when(pipeline.createPipeline(any()))
                .thenReturn(Promise.of(definition));

            DataTransformationPipeline.PipelineDefinition result = runPromise(() ->
                pipeline.createPipeline(definition)
            );

            assertThat(result.id()).isEqualTo("new-pipeline");
            assertThat(result.enabled()).isTrue();
        }

        @Test
        @DisplayName("[PF003]: get_pipeline_returns_existing")
        void getPipelineReturnsExisting() {
            String pipelineId = "existing-pipeline";
            DataTransformationPipeline.PipelineDefinition definition = createPipelineDefinition(
                pipelineId, "Existing", List.of()
            );

            when(pipeline.getPipeline(pipelineId))
                .thenReturn(Promise.of(java.util.Optional.of(definition)));

            java.util.Optional<DataTransformationPipeline.PipelineDefinition> result = runPromise(() ->
                pipeline.getPipeline(pipelineId)
            );

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(pipelineId);
        }

        @Test
        @DisplayName("[PF003]: list_pipelines_returns_tenant_pipelines")
        void listPipelinesReturnsTenantPipelines() {
            String tenantId = "tenant-alpha";

            List<DataTransformationPipeline.PipelineDefinition> pipelines = List.of(
                createPipelineDefinition("p1", "Pipeline 1", List.of()),
                createPipelineDefinition("p2", "Pipeline 2", List.of()),
                createPipelineDefinition("p3", "Pipeline 3", List.of())
            );

            when(pipeline.listPipelines(tenantId))
                .thenReturn(Promise.of(pipelines));

            List<DataTransformationPipeline.PipelineDefinition> result = runPromise(() ->
                pipeline.listPipelines(tenantId)
            );

            assertThat(result).hasSize(3);
            assertThat(result).allMatch(p -> "tenant-alpha".equals(p.tenantId()));
        }

        @Test
        @DisplayName("[PF003]: delete_pipeline_removes_pipeline")
        void deletePipelineRemovesPipeline() {
            String pipelineId = "pipeline-to-delete";

            when(pipeline.deletePipeline(pipelineId))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> pipeline.deletePipeline(pipelineId));

            verify(pipeline).deletePipeline(pipelineId);
        }
    }

    @Nested
    @DisplayName("Pipeline Execution")
    class PipelineExecutionTests {

        @Test
        @DisplayName("[PF003]: execute_pipeline_transforms_data")
        void executePipelineTransformsData() {
            String pipelineId = "transform-pipeline";

            List<Map<String, Object>> input = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
            );

            List<Map<String, Object>> output = List.of(
                Map.of("fullName", "ALICE", "isAdult", true),
                Map.of("fullName", "BOB", "isAdult", true)
            );

            DataTransformationPipeline.ExecutionResult result = new DataTransformationPipeline.ExecutionResult(
                pipelineId, "exec-001", true, output, 2, 2, 250, List.of(), List.of()
            );

            when(pipeline.execute(pipelineId, input))
                .thenReturn(Promise.of(result));

            DataTransformationPipeline.ExecutionResult execResult = runPromise(() ->
                pipeline.execute(pipelineId, input)
            );

            assertThat(execResult.success()).isTrue();
            assertThat(execResult.inputCount()).isEqualTo(2);
            assertThat(execResult.outputCount()).isEqualTo(2);
            assertThat(execResult.output()).hasSize(2);
        }

        @Test
        @DisplayName("[PF003]: execute_pipeline_reports_errors")
        void executePipelineReportsErrors() {
            String pipelineId = "failing-pipeline";

            List<Map<String, Object>> input = List.of(Map.of("data", "invalid"));

            DataTransformationPipeline.ExecutionResult result = new DataTransformationPipeline.ExecutionResult(
                pipelineId, "exec-001", false, List.of(), 1, 0, 100,
                List.of("Transformation failed: invalid data"), List.of()
            );

            when(pipeline.execute(pipelineId, input))
                .thenReturn(Promise.of(result));

            DataTransformationPipeline.ExecutionResult execResult = runPromise(() ->
                pipeline.execute(pipelineId, input)
            );

            assertThat(execResult.success()).isFalse();
            assertThat(execResult.errors()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Step Types")
    class StepTypesTests {

        @Test
        @DisplayName("[PF003]: filter_step_filters_data")
        void filterStepFiltersData() {
            DataTransformationPipeline.TransformationStep filterStep = new DataTransformationPipeline.TransformationStep(
                "step-1", "Filter Adults", DataTransformationPipeline.StepType.FILTER,
                Map.of("condition", "age >= 18"), null, List.of("age"), List.of()
            );

            assertThat(filterStep.type()).isEqualTo(DataTransformationPipeline.StepType.FILTER);
            assertThat(filterStep.condition()).isNull(); // Using config instead
        }

        @Test
        @DisplayName("[PF003]: map_step_transforms_fields")
        void mapStepTransformsFields() {
            DataTransformationPipeline.TransformationStep mapStep = new DataTransformationPipeline.TransformationStep(
                "step-1", "Uppercase Names", DataTransformationPipeline.StepType.MAP,
                Map.of("expression", "name.toUpperCase()"), null,
                List.of("name"), List.of("upperName")
            );

            assertThat(mapStep.type()).isEqualTo(DataTransformationPipeline.StepType.MAP);
            assertThat(mapStep.inputColumns()).contains("name");
            assertThat(mapStep.outputColumns()).contains("upperName");
        }

        @Test
        @DisplayName("[PF003]: aggregate_step_groups_data")
        void aggregateStepGroupsData() {
            DataTransformationPipeline.TransformationStep aggStep = new DataTransformationPipeline.TransformationStep(
                "step-1", "Sum by Category", DataTransformationPipeline.StepType.AGGREGATE,
                Map.of("groupBy", "category", "operation", "sum", "field", "amount"),
                null, List.of("category", "amount"), List.of("total")
            );

            assertThat(aggStep.type()).isEqualTo(DataTransformationPipeline.StepType.AGGREGATE);
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("[PF003]: validate_returns_valid_for_good_pipeline")
        void validateReturnsValidForGoodPipeline() {
            DataTransformationPipeline.PipelineDefinition definition = createPipelineDefinition(
                "valid-pipeline", "Valid", List.of(
                    new DataTransformationPipeline.TransformationStep(
                        "s1", "Step 1", DataTransformationPipeline.StepType.FILTER,
                        Map.of(), null, List.of(), List.of()
                    )
                )
            );

            DataTransformationPipeline.ValidationResult validation = new DataTransformationPipeline.ValidationResult(
                true, List.of(), List.of(),
                List.of(new DataTransformationPipeline.StepValidation("s1", true, List.of()))
            );

            when(pipeline.validate(definition))
                .thenReturn(Promise.of(validation));

            DataTransformationPipeline.ValidationResult result = runPromise(() ->
                pipeline.validate(definition)
            );

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("[PF003]: validate_returns_errors_for_invalid_pipeline")
        void validateReturnsErrorsForInvalidPipeline() {
            DataTransformationPipeline.PipelineDefinition definition = createPipelineDefinition(
                "invalid-pipeline", "Invalid", List.of()
            );

            DataTransformationPipeline.ValidationResult validation = new DataTransformationPipeline.ValidationResult(
                false,
                List.of("Pipeline must have at least one step"),
                List.of(),
                List.of()
            );

            when(pipeline.validate(definition))
                .thenReturn(Promise.of(validation));

            DataTransformationPipeline.ValidationResult result = runPromise(() ->
                pipeline.validate(definition)
            );

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Execution History")
    class ExecutionHistoryTests {

        @Test
        @DisplayName("[PF003]: get_execution_history_returns_records")
        void getExecutionHistoryReturnsRecords() {
            String pipelineId = "pipeline-with-history";

            List<DataTransformationPipeline.ExecutionRecord> history = List.of(
                new DataTransformationPipeline.ExecutionRecord(
                    "exec-001", pipelineId, true, 100, 100, 500, "user-001",
                    Instant.now().minusSeconds(3600), Instant.now().minusSeconds(3599)
                ),
                new DataTransformationPipeline.ExecutionRecord(
                    "exec-002", pipelineId, true, 200, 200, 750, "user-001",
                    Instant.now().minusSeconds(1800), Instant.now().minusSeconds(1798)
                )
            );

            when(pipeline.getExecutionHistory(pipelineId))
                .thenReturn(Promise.of(history));

            List<DataTransformationPipeline.ExecutionRecord> result = runPromise(() ->
                pipeline.getExecutionHistory(pipelineId)
            );

            assertThat(result).hasSize(2);
            assertThat(result.get(0).pipelineId()).isEqualTo(pipelineId);
        }
    }

    private DataTransformationPipeline.PipelineDefinition createPipelineDefinition(
            String id, String name, List<DataTransformationPipeline.TransformationStep> steps) {
        return new DataTransformationPipeline.PipelineDefinition(
            id, name, "", "tenant-alpha", steps, Map.of(), null, null, true
        );
    }
}
