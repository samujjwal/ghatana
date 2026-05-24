/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3: Contract tests for GraphRecord.
 *
 * @doc.type class
 * @doc.purpose Tests for GraphRecord node/edge functionality
 * @doc.layer test
 */
@DisplayName("GraphRecord Tests")
class GraphRecordTest {

    @Nested
    @DisplayName("Builder and Construction")
    class BuilderTests {

        @Test
        @DisplayName("builder creates node with all fields")
        void builderCreatesNodeWithAllFields() {
            GraphRecord node = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("social-graph")
                .elementType(GraphRecord.GraphElementType.NODE)
                .label("Person")
                .data(Map.of("name", "Alice", "age", 30))
                .build();

            assertThat(node.getTenantId()).isEqualTo("tenant-123");
            assertThat(node.getCollectionName()).isEqualTo("social-graph");
            assertThat(node.getElementType()).isEqualTo(GraphRecord.GraphElementType.NODE);
            assertThat(node.getLabel()).isEqualTo("Person");
            assertThat(node.getRecordType()).isEqualTo(RecordType.GRAPH);
        }

        @Test
        @DisplayName("builder creates edge with all fields")
        void builderCreatesEdgeWithAllFields() {
            GraphRecord edge = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("social-graph")
                .elementType(GraphRecord.GraphElementType.EDGE)
                .label("KNOWS")
                .sourceNodeId("node-1")
                .targetNodeId("node-2")
                .direction(GraphRecord.EdgeDirection.DIRECTED)
                .weight(0.85)
                .data(Map.of("since", "2023-01-15"))
                .build();

            assertThat(edge.getElementType()).isEqualTo(GraphRecord.GraphElementType.EDGE);
            assertThat(edge.getLabel()).isEqualTo("KNOWS");
            assertThat(edge.getSourceNodeId()).isEqualTo("node-1");
            assertThat(edge.getTargetNodeId()).isEqualTo("node-2");
            assertThat(edge.getDirection()).isEqualTo(GraphRecord.EdgeDirection.DIRECTED);
            assertThat(edge.getWeight()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("builder uses default elementType NODE")
        void builderUsesDefaultElementTypeNode() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .label("Test")
                .build();

            assertThat(record.getElementType()).isEqualTo(GraphRecord.GraphElementType.NODE);
        }

        @Test
        @DisplayName("builder uses default direction DIRECTED")
        void builderUsesDefaultDirectionDirected() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .label("Test")
                .build();

            assertThat(record.getDirection()).isEqualTo(GraphRecord.EdgeDirection.DIRECTED);
        }

        @Test
        @DisplayName("builder uses default weight 1.0")
        void builderUsesDefaultWeightOne() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .label("Test")
                .build();

            assertThat(record.getWeight()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("builder uses default version 1")
        void builderUsesDefaultVersionOne() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .label("Test")
                .build();

            assertThat(record.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("builder uses default active true")
        void builderUsesDefaultActiveTrue() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .label("Test")
                .build();

            assertThat(record.getActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Type Helpers")
    class TypeHelperTests {

        @Test
        @DisplayName("isNode returns true for NODE type")
        void isNodeReturnsTrueForNodeType() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .elementType(GraphRecord.GraphElementType.NODE)
                .label("Test")
                .build();

            assertThat(record.isNode()).isTrue();
            assertThat(record.isEdge()).isFalse();
        }

        @Test
        @DisplayName("isEdge returns true for EDGE type")
        void isEdgeReturnsTrueForEdgeType() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .elementType(GraphRecord.GraphElementType.EDGE)
                .label("Test")
                .build();

            assertThat(record.isEdge()).isTrue();
            assertThat(record.isNode()).isFalse();
        }
    }

    @Nested
    @DisplayName("Edge Connection Helpers")
    class EdgeConnectionTests {

        @Test
        @DisplayName("connectsNode returns true for source node")
        void connectsNodeReturnsTrueForSourceNode() {
            GraphRecord edge = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .elementType(GraphRecord.GraphElementType.EDGE)
                .label("CONNECTS")
                .sourceNodeId("node-1")
                .targetNodeId("node-2")
                .build();

            assertThat(edge.connectsNode("node-1")).isTrue();
        }

        @Test
        @DisplayName("connectsNode returns true for target node")
        void connectsNodeReturnsTrueForTargetNode() {
            GraphRecord edge = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .elementType(GraphRecord.GraphElementType.EDGE)
                .label("CONNECTS")
                .sourceNodeId("node-1")
                .targetNodeId("node-2")
                .build();

            assertThat(edge.connectsNode("node-2")).isTrue();
        }

        @Test
        @DisplayName("connectsNode returns false for unrelated node")
        void connectsNodeReturnsFalseForUnrelatedNode() {
            GraphRecord edge = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .elementType(GraphRecord.GraphElementType.EDGE)
                .label("CONNECTS")
                .sourceNodeId("node-1")
                .targetNodeId("node-2")
                .build();

            assertThat(edge.connectsNode("node-3")).isFalse();
        }

        @Test
        @DisplayName("connectsNode returns false for node record")
        void connectsNodeReturnsFalseForNodeRecord() {
            GraphRecord node = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .elementType(GraphRecord.GraphElementType.NODE)
                .label("Test")
                .build();

            assertThat(node.connectsNode("node-1")).isFalse();
        }

        @Test
        @DisplayName("connectsNode returns false for null nodeId")
        void connectsNodeReturnsFalseForNullNodeId() {
            GraphRecord edge = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .elementType(GraphRecord.GraphElementType.EDGE)
                .label("CONNECTS")
                .sourceNodeId("node-1")
                .targetNodeId("node-2")
                .build();

            assertThat(edge.connectsNode(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Soft Delete and Restore")
    class SoftDeleteRestoreTests {

        @Test
        @DisplayName("softDelete sets active to false")
        void softDeleteSetsActiveToFalse() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .label("Test")
                .active(true)
                .build();

            record.softDelete("user-1");

            assertThat(record.getActive()).isFalse();
            assertThat(record.getUpdatedBy()).isEqualTo("user-1");
            assertThat(record.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("restore sets active to true")
        void restoreSetsActiveToTrue() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .label("Test")
                .active(false)
                .build();

            record.restore("user-1");

            assertThat(record.getActive()).isTrue();
            assertThat(record.getUpdatedBy()).isEqualTo("user-1");
            assertThat(record.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("isDeleted returns true when active is false")
        void isDeletedReturnsTrueWhenActiveIsFalse() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .label("Test")
                .active(false)
                .build();

            assertThat(record.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("isDeleted returns false when active is true")
        void isDeletedReturnsFalseWhenActiveIsTrue() {
            GraphRecord record = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .label("Test")
                .active(true)
                .build();

            assertThat(record.isDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("toBuilder")
    class ToBuilderTests {

        @Test
        @DisplayName("toBuilder creates builder with existing values")
        void toBuilderCreatesBuilderWithExistingValues() {
            GraphRecord original = GraphRecord.builder()
                .tenantId("tenant-123")
                .collectionName("graph")
                .elementType(GraphRecord.GraphElementType.NODE)
                .label("Person")
                .data(Map.of("name", "Alice"))
                .build();

            GraphRecord updated = original.toBuilder()
                .label("UpdatedPerson")
                .data(Map.of("name", "Bob"))
                .build();

            assertThat(updated.getTenantId()).isEqualTo("tenant-123");
            assertThat(updated.getCollectionName()).isEqualTo("graph");
            assertThat(updated.getLabel()).isEqualTo("UpdatedPerson");
            assertThat(updated.getData()).containsEntry("name", "Bob");
        }
    }

    @Nested
    @DisplayName("Enum Values")
    class EnumTests {

        @Test
        @DisplayName("GraphElementType has NODE and EDGE values")
        void graphElementTypeHasNodeAndEdgeValues() {
            GraphRecord.GraphElementType[] types = GraphRecord.GraphElementType.values();

            assertThat(types).containsExactlyInAnyOrder(
                GraphRecord.GraphElementType.NODE,
                GraphRecord.GraphElementType.EDGE
            );
        }

        @Test
        @DisplayName("EdgeDirection has all direction values")
        void edgeDirectionHasAllDirectionValues() {
            GraphRecord.EdgeDirection[] directions = GraphRecord.EdgeDirection.values();

            assertThat(directions).containsExactlyInAnyOrder(
                GraphRecord.EdgeDirection.DIRECTED,
                GraphRecord.EdgeDirection.UNDIRECTED,
                GraphRecord.EdgeDirection.BIDIRECTIONAL
            );
        }
    }
}
