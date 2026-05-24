/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3: Contract tests for WorkflowExecution.
 *
 * @doc.type class
 * @doc.purpose Tests for WorkflowExecution domain model and builder
 * @doc.layer test
 */
@DisplayName("WorkflowExecution Tests")
class WorkflowExecutionTest {

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {

        @Test
        @DisplayName("valid execution is created successfully")
        void validExecutionIsCreated() {
            UUID id = UUID.randomUUID();
            UUID workflowId = UUID.randomUUID();
            Instant startedAt = Instant.now();
            
            WorkflowExecution execution = new WorkflowExecution(
                id,
                "tenant-123",
                workflowId,
                WorkflowExecution.Status.RUNNING,
                "user-1",
                startedAt,
                null,
                Map.of("input", "value"),
                Map.of(),
                List.of(),
                null
            );

            assertThat(execution.getId()).isEqualTo(id);
            assertThat(execution.getTenantId()).isEqualTo("tenant-123");
            assertThat(execution.getWorkflowId()).isEqualTo(workflowId);
            assertThat(execution.getStatus()).isEqualTo(WorkflowExecution.Status.RUNNING);
            assertThat(execution.getStartedBy()).isEqualTo("user-1");
            assertThat(execution.getStartedAt()).isEqualTo(startedAt);
        }

        @Test
        @DisplayName("null ID is rejected")
        void nullIdIsRejected() {
            assertThatThrownBy(() -> new WorkflowExecution(
                null,
                "tenant-123",
                UUID.randomUUID(),
                WorkflowExecution.Status.PENDING,
                "user-1",
                Instant.now(),
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ID must not be null");
        }

        @Test
        @DisplayName("null tenant ID is rejected")
        void nullTenantIdIsRejected() {
            assertThatThrownBy(() -> new WorkflowExecution(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                WorkflowExecution.Status.PENDING,
                "user-1",
                Instant.now(),
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Tenant ID must not be null");
        }

        @Test
        @DisplayName("null workflow ID is rejected")
        void nullWorkflowIdIsRejected() {
            assertThatThrownBy(() -> new WorkflowExecution(
                UUID.randomUUID(),
                "tenant-123",
                null,
                WorkflowExecution.Status.PENDING,
                "user-1",
                Instant.now(),
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Workflow ID must not be null");
        }

        @Test
        @DisplayName("null status is rejected")
        void nullStatusIsRejected() {
            assertThatThrownBy(() -> new WorkflowExecution(
                UUID.randomUUID(),
                "tenant-123",
                UUID.randomUUID(),
                null,
                "user-1",
                Instant.now(),
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Status must not be null");
        }

        @Test
        @DisplayName("null startedBy is rejected")
        void nullStartedByIsRejected() {
            assertThatThrownBy(() -> new WorkflowExecution(
                UUID.randomUUID(),
                "tenant-123",
                UUID.randomUUID(),
                WorkflowExecution.Status.PENDING,
                null,
                Instant.now(),
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Started-by must not be null");
        }

        @Test
        @DisplayName("null startedAt is rejected")
        void nullStartedAtIsRejected() {
            assertThatThrownBy(() -> new WorkflowExecution(
                UUID.randomUUID(),
                "tenant-123",
                UUID.randomUUID(),
                WorkflowExecution.Status.PENDING,
                "user-1",
                null,
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Started-at must not be null");
        }

        @Test
        @DisplayName("null input variables defaults to empty map")
        void nullInputVariablesDefaultsToEmptyMap() {
            WorkflowExecution execution = new WorkflowExecution(
                UUID.randomUUID(),
                "tenant-123",
                UUID.randomUUID(),
                WorkflowExecution.Status.PENDING,
                "user-1",
                Instant.now(),
                null,
                null,
                Map.of(),
                List.of(),
                null
            );

            assertThat(execution.getInputVariables()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("null output variables defaults to empty map")
        void nullOutputVariablesDefaultsToEmptyMap() {
            WorkflowExecution execution = new WorkflowExecution(
                UUID.randomUUID(),
                "tenant-123",
                UUID.randomUUID(),
                WorkflowExecution.Status.PENDING,
                "user-1",
                Instant.now(),
                null,
                Map.of(),
                null,
                List.of(),
                null
            );

            assertThat(execution.getOutputVariables()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("null node executions defaults to empty list")
        void nullNodeExecutionsDefaultsToEmptyList() {
            WorkflowExecution execution = new WorkflowExecution(
                UUID.randomUUID(),
                "tenant-123",
                UUID.randomUUID(),
                WorkflowExecution.Status.PENDING,
                "user-1",
                Instant.now(),
                null,
                Map.of(),
                Map.of(),
                null,
                null
            );

            assertThat(execution.getNodeExecutions()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("input variables are immutable")
        void inputVariablesAreImmutable() {
            Map<String, Object> input = Map.of("key", "value");
            WorkflowExecution execution = new WorkflowExecution(
                UUID.randomUUID(),
                "tenant-123",
                UUID.randomUUID(),
                WorkflowExecution.Status.PENDING,
                "user-1",
                Instant.now(),
                null,
                input,
                Map.of(),
                List.of(),
                null
            );

            assertThatThrownBy(() -> execution.getInputVariables().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("output variables are immutable")
        void outputVariablesAreImmutable() {
            Map<String, Object> output = Map.of("key", "value");
            WorkflowExecution execution = new WorkflowExecution(
                UUID.randomUUID(),
                "tenant-123",
                UUID.randomUUID(),
                WorkflowExecution.Status.PENDING,
                "user-1",
                Instant.now(),
                null,
                Map.of(),
                output,
                List.of(),
                null
            );

            assertThatThrownBy(() -> execution.getOutputVariables().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("node executions are immutable")
        void nodeExecutionsAreImmutable() {
            WorkflowExecution.NodeExecution nodeExec = new WorkflowExecution.NodeExecution(
                "node-1", "Node 1", WorkflowExecution.Status.COMPLETED,
                Instant.now(), Instant.now(), Map.of(), null
            );
            WorkflowExecution execution = new WorkflowExecution(
                UUID.randomUUID(),
                "tenant-123",
                UUID.randomUUID(),
                WorkflowExecution.Status.PENDING,
                "user-1",
                Instant.now(),
                null,
                Map.of(),
                Map.of(),
                List.of(nodeExec),
                null
            );

            assertThatThrownBy(() -> execution.getNodeExecutions().add(nodeExec))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPatternTests {

        @Test
        @DisplayName("builder creates execution with all fields")
        void builderCreatesExecutionWithAllFields() {
            UUID workflowId = UUID.randomUUID();
            Instant startedAt = Instant.now();
            Instant completedAt = Instant.now();

            WorkflowExecution execution = WorkflowExecution.builder()
                .tenantId("tenant-123")
                .workflowId(workflowId)
                .status(WorkflowExecution.Status.COMPLETED)
                .startedBy("user-1")
                .startedAt(startedAt)
                .completedAt(completedAt)
                .inputVariables(Map.of("input", "value"))
                .outputVariables(Map.of("output", "result"))
                .errorMessage("Error message")
                .build();

            assertThat(execution.getTenantId()).isEqualTo("tenant-123");
            assertThat(execution.getWorkflowId()).isEqualTo(workflowId);
            assertThat(execution.getStatus()).isEqualTo(WorkflowExecution.Status.COMPLETED);
            assertThat(execution.getStartedBy()).isEqualTo("user-1");
            assertThat(execution.getStartedAt()).isEqualTo(startedAt);
            assertThat(execution.getCompletedAt()).isEqualTo(completedAt);
            assertThat(execution.getInputVariables()).containsEntry("input", "value");
            assertThat(execution.getOutputVariables()).containsEntry("output", "result");
            assertThat(execution.getErrorMessage()).isEqualTo("Error message");
        }

        @Test
        @DisplayName("builder generates ID when not provided")
        void builderGeneratesIdWhenNotProvided() {
            WorkflowExecution execution = WorkflowExecution.builder()
                .tenantId("tenant-123")
                .workflowId(UUID.randomUUID())
                .startedBy("user-1")
                .build();

            assertThat(execution.getId()).isNotNull();
        }

        @Test
        @DisplayName("builder generates startedAt when not provided")
        void builderGeneratesStartedAtWhenNotProvided() {
            WorkflowExecution execution = WorkflowExecution.builder()
                .tenantId("tenant-123")
                .workflowId(UUID.randomUUID())
                .startedBy("user-1")
                .build();

            assertThat(execution.getStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("builder uses default status PENDING")
        void builderUsesDefaultStatusPending() {
            WorkflowExecution execution = WorkflowExecution.builder()
                .tenantId("tenant-123")
                .workflowId(UUID.randomUUID())
                .startedBy("user-1")
                .build();

            assertThat(execution.getStatus()).isEqualTo(WorkflowExecution.Status.PENDING);
        }

        @Test
        @DisplayName("builder uses empty collections by default")
        void builderUsesEmptyCollectionsByDefault() {
            WorkflowExecution execution = WorkflowExecution.builder()
                .tenantId("tenant-123")
                .workflowId(UUID.randomUUID())
                .startedBy("user-1")
                .build();

            assertThat(execution.getInputVariables()).isEmpty();
            assertThat(execution.getOutputVariables()).isEmpty();
            assertThat(execution.getNodeExecutions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("toBuilder")
    class ToBuilderTests {

        @Test
        @DisplayName("toBuilder creates builder with existing values")
        void toBuilderCreatesBuilderWithExistingValues() {
            UUID id = UUID.randomUUID();
            UUID workflowId = UUID.randomUUID();
            Instant startedAt = Instant.now();

            WorkflowExecution original = new WorkflowExecution(
                id,
                "tenant-123",
                workflowId,
                WorkflowExecution.Status.RUNNING,
                "user-1",
                startedAt,
                null,
                Map.of("input", "value"),
                Map.of(),
                List.of(),
                null
            );

            WorkflowExecution updated = original.toBuilder()
                .status(WorkflowExecution.Status.COMPLETED)
                .completedAt(Instant.now())
                .build();

            assertThat(updated.getId()).isEqualTo(id);
            assertThat(updated.getTenantId()).isEqualTo("tenant-123");
            assertThat(updated.getWorkflowId()).isEqualTo(workflowId);
            assertThat(updated.getStatus()).isEqualTo(WorkflowExecution.Status.COMPLETED);
            assertThat(updated.getCompletedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("NodeExecution")
    class NodeExecutionTests {

        @Test
        @DisplayName("valid node execution is created successfully")
        void validNodeExecutionIsCreated() {
            Instant startedAt = Instant.now();
            Instant completedAt = Instant.now();

            WorkflowExecution.NodeExecution nodeExec = new WorkflowExecution.NodeExecution(
                "node-1",
                "Test Node",
                WorkflowExecution.Status.COMPLETED,
                startedAt,
                completedAt,
                Map.of("result", "value"),
                null
            );

            assertThat(nodeExec.getNodeId()).isEqualTo("node-1");
            assertThat(nodeExec.getNodeName()).isEqualTo("Test Node");
            assertThat(nodeExec.getStatus()).isEqualTo(WorkflowExecution.Status.COMPLETED);
            assertThat(nodeExec.getStartedAt()).isEqualTo(startedAt);
            assertThat(nodeExec.getCompletedAt()).isEqualTo(completedAt);
        }

        @Test
        @DisplayName("null node ID is rejected")
        void nullNodeIdIsRejected() {
            assertThatThrownBy(() -> new WorkflowExecution.NodeExecution(
                null,
                "Test Node",
                WorkflowExecution.Status.PENDING,
                Instant.now(),
                null,
                Map.of(),
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Node ID must not be null");
        }

        @Test
        @DisplayName("null status is rejected")
        void nullStatusIsRejected() {
            assertThatThrownBy(() -> new WorkflowExecution.NodeExecution(
                "node-1",
                "Test Node",
                null,
                Instant.now(),
                null,
                Map.of(),
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Status must not be null");
        }

        @Test
        @DisplayName("null output defaults to empty map")
        void nullOutputDefaultsToEmptyMap() {
            WorkflowExecution.NodeExecution nodeExec = new WorkflowExecution.NodeExecution(
                "node-1",
                "Test Node",
                WorkflowExecution.Status.PENDING,
                Instant.now(),
                null,
                null,
                null
            );

            assertThat(nodeExec.getOutput()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("output is immutable")
        void outputIsImmutable() {
            Map<String, Object> output = Map.of("key", "value");
            WorkflowExecution.NodeExecution nodeExec = new WorkflowExecution.NodeExecution(
                "node-1",
                "Test Node",
                WorkflowExecution.Status.PENDING,
                Instant.now(),
                null,
                output,
                null
            );

            assertThatThrownBy(() -> nodeExec.getOutput().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityTests {

        @Test
        @DisplayName("executions with same ID and tenant are equal")
        void executionsWithSameIdAndTenantAreEqual() {
            UUID id = UUID.randomUUID();
            UUID workflowId = UUID.randomUUID();

            WorkflowExecution execution1 = new WorkflowExecution(
                id, "tenant-123", workflowId, WorkflowExecution.Status.PENDING,
                "user-1", Instant.now(), null, Map.of(), Map.of(), List.of(), null
            );

            WorkflowExecution execution2 = new WorkflowExecution(
                id, "tenant-123", workflowId, WorkflowExecution.Status.COMPLETED,
                "user-2", Instant.now(), Instant.now(), Map.of(), Map.of(), List.of(), "error"
            );

            assertThat(execution1).isEqualTo(execution2);
            assertThat(execution1.hashCode()).isEqualTo(execution2.hashCode());
        }

        @Test
        @DisplayName("executions with different ID are not equal")
        void executionsWithDifferentIdAreNotEqual() {
            UUID workflowId = UUID.randomUUID();

            WorkflowExecution execution1 = new WorkflowExecution(
                UUID.randomUUID(), "tenant-123", workflowId, WorkflowExecution.Status.PENDING,
                "user-1", Instant.now(), null, Map.of(), Map.of(), List.of(), null
            );

            WorkflowExecution execution2 = new WorkflowExecution(
                UUID.randomUUID(), "tenant-123", workflowId, WorkflowExecution.Status.PENDING,
                "user-1", Instant.now(), null, Map.of(), Map.of(), List.of(), null
            );

            assertThat(execution1).isNotEqualTo(execution2);
        }
    }

    @Nested
    @DisplayName("Status Enum")
    class StatusEnumTests {

        @Test
        @DisplayName("all status values are defined")
        void allStatusValuesAreDefined() {
            WorkflowExecution.Status[] statuses = WorkflowExecution.Status.values();
            
            assertThat(statuses).containsExactlyInAnyOrder(
                WorkflowExecution.Status.PENDING,
                WorkflowExecution.Status.RUNNING,
                WorkflowExecution.Status.COMPLETED,
                WorkflowExecution.Status.FAILED,
                WorkflowExecution.Status.CANCELLED
            );
        }
    }
}
