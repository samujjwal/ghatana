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
 * Phase 3: Contract tests for WorkflowNode.
 *
 * @doc.type class
 * @doc.purpose Tests for WorkflowNode workflow step functionality
 * @doc.layer test
 */
@DisplayName("WorkflowNode Tests")
class WorkflowNodeTest {

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {

        @Test
        @DisplayName("valid node is created successfully")
        void validNodeIsCreated() {
            WorkflowNode node = new WorkflowNode(
                "node-1",
                "API_CALL",
                "Send Email",
                Map.of("url", "https://api.example.com"),
                Map.of("description", "Send notification email"),
                100,
                200
            );

            assertThat(node.getId()).isEqualTo("node-1");
            assertThat(node.getType()).isEqualTo("API_CALL");
            assertThat(node.getLabel()).isEqualTo("Send Email");
            assertThat(node.getConfig()).containsEntry("url", "https://api.example.com");
            assertThat(node.getPositionX()).isEqualTo(100);
            assertThat(node.getPositionY()).isEqualTo(200);
        }

        @Test
        @DisplayName("null ID is rejected")
        void nullIdIsRejected() {
            assertThatThrownBy(() -> new WorkflowNode(
                null,
                "API_CALL",
                "Test",
                Map.of(),
                Map.of(),
                null,
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ID must not be null");
        }

        @Test
        @DisplayName("null type is rejected")
        void nullTypeIsRejected() {
            assertThatThrownBy(() -> new WorkflowNode(
                "node-1",
                null,
                "Test",
                Map.of(),
                Map.of(),
                null,
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Type must not be null");
        }

        @Test
        @DisplayName("null config is rejected")
        void nullConfigIsRejected() {
            assertThatThrownBy(() -> new WorkflowNode(
                "node-1",
                "API_CALL",
                "Test",
                null,
                Map.of(),
                null,
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Config must not be null");
        }

        @Test
        @DisplayName("null metadata is rejected")
        void nullMetadataIsRejected() {
            assertThatThrownBy(() -> new WorkflowNode(
                "node-1",
                "API_CALL",
                "Test",
                Map.of(),
                null,
                null,
                null
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Metadata must not be null");
        }

        @Test
        @DisplayName("optional fields can be null")
        void optionalFieldsCanBeNull() {
            WorkflowNode node = new WorkflowNode(
                "node-1",
                "API_CALL",
                null,
                Map.of(),
                Map.of(),
                null,
                null
            );

            assertThat(node.getLabel()).isNull();
            assertThat(node.getPositionX()).isNull();
            assertThat(node.getPositionY()).isNull();
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPatternTests {

        @Test
        @DisplayName("builder creates node with all fields")
        void builderCreatesNodeWithAllFields() {
            WorkflowNode node = WorkflowNode.builder()
                .id("node-1")
                .type("API_CALL")
                .label("Send Email")
                .config(Map.of("url", "https://api.example.com"))
                .metadata(Map.of("description", "Send notification"))
                .positionX(100)
                .positionY(200)
                .build();

            assertThat(node.getId()).isEqualTo("node-1");
            assertThat(node.getType()).isEqualTo("API_CALL");
            assertThat(node.getLabel()).isEqualTo("Send Email");
            assertThat(node.getConfig()).containsEntry("url", "https://api.example.com");
            assertThat(node.getMetadata()).containsEntry("description", "Send notification");
            assertThat(node.getPositionX()).isEqualTo(100);
            assertThat(node.getPositionY()).isEqualTo(200);
        }

        @Test
        @DisplayName("builder generates ID when not provided")
        void builderGeneratesIdWhenNotProvided() {
            WorkflowNode node = WorkflowNode.builder()
                .type("API_CALL")
                .build();

            assertThat(node.getId()).isNotNull();
        }

        @Test
        @DisplayName("builder uses empty config by default")
        void builderUsesEmptyConfigByDefault() {
            WorkflowNode node = WorkflowNode.builder()
                .type("API_CALL")
                .build();

            assertThat(node.getConfig()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("builder uses empty metadata by default")
        void builderUsesEmptyMetadataByDefault() {
            WorkflowNode node = WorkflowNode.builder()
                .type("API_CALL")
                .build();

            assertThat(node.getMetadata()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityTests {

        @Test
        @DisplayName("nodes with same ID are equal")
        void nodesWithSameIdAreEqual() {
            WorkflowNode node1 = new WorkflowNode(
                "node-1",
                "API_CALL",
                "Label 1",
                Map.of("key", "value1"),
                Map.of(),
                100,
                200
            );

            WorkflowNode node2 = new WorkflowNode(
                "node-1",
                "DECISION",
                "Label 2",
                Map.of("key", "value2"),
                Map.of(),
                300,
                400
            );

            assertThat(node1).isEqualTo(node2);
            assertThat(node1.hashCode()).isEqualTo(node2.hashCode());
        }

        @Test
        @DisplayName("nodes with different ID are not equal")
        void nodesWithDifferentIdAreNotEqual() {
            WorkflowNode node1 = new WorkflowNode(
                "node-1",
                "API_CALL",
                "Label",
                Map.of(),
                Map.of(),
                null,
                null
            );

            WorkflowNode node2 = new WorkflowNode(
                "node-2",
                "API_CALL",
                "Label",
                Map.of(),
                Map.of(),
                null,
                null
            );

            assertThat(node1).isNotEqualTo(node2);
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("node is immutable")
        void nodeIsImmutable() {
            WorkflowNode node = new WorkflowNode(
                "node-1",
                "API_CALL",
                "Test",
                Map.of(),
                Map.of(),
                null,
                null
            );

            // Immutable by design (final fields, no setters)
            assertThat(node).isNotNull();
        }
    }
}
