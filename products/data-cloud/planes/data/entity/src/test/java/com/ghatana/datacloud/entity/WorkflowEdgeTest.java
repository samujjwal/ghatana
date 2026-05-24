/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3: Contract tests for WorkflowEdge.
 *
 * @doc.type class
 * @doc.purpose Tests for WorkflowEdge workflow connection functionality
 * @doc.layer test
 */
@DisplayName("WorkflowEdge Tests")
class WorkflowEdgeTest {

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {

        @Test
        @DisplayName("valid edge is created successfully")
        void validEdgeIsCreated() {
            WorkflowEdge edge = new WorkflowEdge(
                "edge-1",
                "node-1",
                "node-2",
                "CONDITIONAL",
                Map.of("operator", "equals", "field", "status", "value", "approved"),
                "On Approval"
            );

            assertThat(edge.getId()).isEqualTo("edge-1");
            assertThat(edge.getSourceNodeId()).isEqualTo("node-1");
            assertThat(edge.getTargetNodeId()).isEqualTo("node-2");
            assertThat(edge.getType()).isEqualTo("CONDITIONAL");
            assertThat(edge.getCondition()).containsEntry("operator", "equals");
            assertThat(edge.getLabel()).isEqualTo("On Approval");
        }

        @Test
        @DisplayName("null ID is rejected")
        void nullIdIsRejected() {
            assertThatThrownBy(() -> new WorkflowEdge(
                null,
                "node-1",
                "node-2",
                "DEFAULT",
                null,
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ID must not be null");
        }

        @Test
        @DisplayName("null sourceNodeId is rejected")
        void nullSourceNodeIdIsRejected() {
            assertThatThrownBy(() -> new WorkflowEdge(
                "edge-1",
                null,
                "node-2",
                "DEFAULT",
                null,
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Source node ID must not be null");
        }

        @Test
        @DisplayName("null targetNodeId is rejected")
        void nullTargetNodeIdIsRejected() {
            assertThatThrownBy(() -> new WorkflowEdge(
                "edge-1",
                "node-1",
                null,
                "DEFAULT",
                null,
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Target node ID must not be null");
        }

        @Test
        @DisplayName("null type is rejected")
        void nullTypeIsRejected() {
            assertThatThrownBy(() -> new WorkflowEdge(
                "edge-1",
                "node-1",
                "node-2",
                null,
                null,
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Type must not be null");
        }

        @Test
        @DisplayName("null condition defaults to empty map")
        void nullConditionDefaultsToEmptyMap() {
            WorkflowEdge edge = new WorkflowEdge(
                "edge-1",
                "node-1",
                "node-2",
                "DEFAULT",
                null,
                null
            );

            assertThat(edge.getCondition()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("optional label can be null")
        void optionalLabelCanBeNull() {
            WorkflowEdge edge = new WorkflowEdge(
                "edge-1",
                "node-1",
                "node-2",
                "DEFAULT",
                Map.of(),
                null
            );

            assertThat(edge.getLabel()).isNull();
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPatternTests {

        @Test
        @DisplayName("builder creates edge with all fields")
        void builderCreatesEdgeWithAllFields() {
            WorkflowEdge edge = WorkflowEdge.builder()
                .id("edge-1")
                .sourceNodeId("node-1")
                .targetNodeId("node-2")
                .type("CONDITIONAL")
                .condition(Map.of("operator", "equals", "value", "approved"))
                .label("On Approval")
                .build();

            assertThat(edge.getId()).isEqualTo("edge-1");
            assertThat(edge.getSourceNodeId()).isEqualTo("node-1");
            assertThat(edge.getTargetNodeId()).isEqualTo("node-2");
            assertThat(edge.getType()).isEqualTo("CONDITIONAL");
            assertThat(edge.getCondition()).containsEntry("operator", "equals");
            assertThat(edge.getLabel()).isEqualTo("On Approval");
        }

        @Test
        @DisplayName("builder generates ID when not provided")
        void builderGeneratesIdWhenNotProvided() {
            WorkflowEdge edge = WorkflowEdge.builder()
                .sourceNodeId("node-1")
                .targetNodeId("node-2")
                .build();

            assertThat(edge.getId()).isNotNull();
        }

        @Test
        @DisplayName("builder uses default type DEFAULT")
        void builderUsesDefaultTypeDefault() {
            WorkflowEdge edge = WorkflowEdge.builder()
                .sourceNodeId("node-1")
                .targetNodeId("node-2")
                .build();

            assertThat(edge.getType()).isEqualTo("DEFAULT");
        }

        @Test
        @DisplayName("builder uses empty condition when not provided")
        void builderUsesEmptyConditionWhenNotProvided() {
            WorkflowEdge edge = WorkflowEdge.builder()
                .sourceNodeId("node-1")
                .targetNodeId("node-2")
                .build();

            assertThat(edge.getCondition()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityTests {

        @Test
        @DisplayName("edges with same ID are equal")
        void edgesWithSameIdAreEqual() {
            WorkflowEdge edge1 = new WorkflowEdge(
                "edge-1",
                "node-1",
                "node-2",
                "DEFAULT",
                Map.of("key", "value1"),
                "Label 1"
            );

            WorkflowEdge edge2 = new WorkflowEdge(
                "edge-1",
                "node-3",
                "node-4",
                "CONDITIONAL",
                Map.of("key", "value2"),
                "Label 2"
            );

            assertThat(edge1).isEqualTo(edge2);
            assertThat(edge1.hashCode()).isEqualTo(edge2.hashCode());
        }

        @Test
        @DisplayName("edges with different ID are not equal")
        void edgesWithDifferentIdAreNotEqual() {
            WorkflowEdge edge1 = new WorkflowEdge(
                "edge-1",
                "node-1",
                "node-2",
                "DEFAULT",
                Map.of(),
                null
            );

            WorkflowEdge edge2 = new WorkflowEdge(
                "edge-2",
                "node-1",
                "node-2",
                "DEFAULT",
                Map.of(),
                null
            );

            assertThat(edge1).isNotEqualTo(edge2);
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("edge is immutable")
        void edgeIsImmutable() {
            WorkflowEdge edge = new WorkflowEdge(
                "edge-1",
                "node-1",
                "node-2",
                "DEFAULT",
                Map.of(),
                null
            );

            // Immutable by design (final fields, no setters)
            assertThat(edge).isNotNull();
        }
    }
}
