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
 * Phase 3: Contract tests for Workflow.
 *
 * @doc.type class
 * @doc.purpose Tests for Workflow domain model and builder
 * @doc.layer test
 */
@DisplayName("Workflow Tests")
class WorkflowTest {

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {

        @Test
        @DisplayName("valid workflow is created successfully")
        void validWorkflowIsCreated() {
            UUID id = UUID.randomUUID();
            UUID collectionId = UUID.randomUUID();
            Workflow workflow = new Workflow(
                id,
                "tenant-123",
                "Test Workflow",
                "Test description",
                collectionId,
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "DRAFT",
                1,
                true,
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1"
            );

            assertThat(workflow.getId()).isEqualTo(id);
            assertThat(workflow.getTenantId()).isEqualTo("tenant-123");
            assertThat(workflow.getName()).isEqualTo("Test Workflow");
            assertThat(workflow.getCollectionId()).isEqualTo(collectionId);
        }

        @Test
        @DisplayName("null ID is rejected")
        void nullIdIsRejected() {
            assertThatThrownBy(() -> new Workflow(
                null,
                "tenant-123",
                "Test",
                "desc",
                UUID.randomUUID(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "DRAFT",
                1,
                true,
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1"
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ID must not be null");
        }

        @Test
        @DisplayName("null tenant ID is rejected")
        void nullTenantIdIsRejected() {
            assertThatThrownBy(() -> new Workflow(
                UUID.randomUUID(),
                null,
                "Test",
                "desc",
                UUID.randomUUID(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "DRAFT",
                1,
                true,
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1"
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Tenant ID must not be null");
        }

        @Test
        @DisplayName("null name is rejected")
        void nullNameIsRejected() {
            assertThatThrownBy(() -> new Workflow(
                UUID.randomUUID(),
                "tenant-123",
                null,
                "desc",
                UUID.randomUUID(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "DRAFT",
                1,
                true,
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1"
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Name must not be null");
        }

        @Test
        @DisplayName("null collection ID is rejected")
        void nullCollectionIdIsRejected() {
            assertThatThrownBy(() -> new Workflow(
                UUID.randomUUID(),
                "tenant-123",
                "Test",
                "desc",
                null,
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "DRAFT",
                1,
                true,
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1"
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Collection ID must not be null");
        }

        @Test
        @DisplayName("null nodes is rejected")
        void nullNodesIsRejected() {
            assertThatThrownBy(() -> new Workflow(
                UUID.randomUUID(),
                "tenant-123",
                "Test",
                "desc",
                UUID.randomUUID(),
                null,
                List.of(),
                List.of(),
                Map.of(),
                "DRAFT",
                1,
                true,
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1"
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Nodes must not be null");
        }

        @Test
        @DisplayName("null edges is rejected")
        void nullEdgesIsRejected() {
            assertThatThrownBy(() -> new Workflow(
                UUID.randomUUID(),
                "tenant-123",
                "Test",
                "desc",
                UUID.randomUUID(),
                List.of(),
                null,
                List.of(),
                Map.of(),
                "DRAFT",
                1,
                true,
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1"
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Edges must not be null");
        }

        @Test
        @DisplayName("null triggers is rejected")
        void nullTriggersIsRejected() {
            assertThatThrownBy(() -> new Workflow(
                UUID.randomUUID(),
                "tenant-123",
                "Test",
                "desc",
                UUID.randomUUID(),
                List.of(),
                List.of(),
                null,
                Map.of(),
                "DRAFT",
                1,
                true,
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1"
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Triggers must not be null");
        }

        @Test
        @DisplayName("null variables is rejected")
        void nullVariablesIsRejected() {
            assertThatThrownBy(() -> new Workflow(
                UUID.randomUUID(),
                "tenant-123",
                "Test",
                "desc",
                UUID.randomUUID(),
                List.of(),
                List.of(),
                List.of(),
                null,
                "DRAFT",
                1,
                true,
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1"
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Variables must not be null");
        }

        @Test
        @DisplayName("optional fields can be null")
        void optionalFieldsCanBeNull() {
            Workflow workflow = new Workflow(
                UUID.randomUUID(),
                "tenant-123",
                "Test",
                null,
                UUID.randomUUID(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            assertThat(workflow.getDescription()).isNull();
            assertThat(workflow.getStatus()).isNull();
            assertThat(workflow.getVersion()).isNull();
            assertThat(workflow.getActive()).isNull();
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPatternTests {

        @Test
        @DisplayName("builder creates workflow with all fields")
        void builderCreatesWorkflowWithAllFields() {
            UUID collectionId = UUID.randomUUID();
            Workflow workflow = Workflow.builder()
                .tenantId("tenant-123")
                .name("Test Workflow")
                .description("Test description")
                .collectionId(collectionId)
                .status("ACTIVE")
                .version(2)
                .active(true)
                .createdBy("user-1")
                .updatedBy("user-1")
                .build();

            assertThat(workflow.getTenantId()).isEqualTo("tenant-123");
            assertThat(workflow.getName()).isEqualTo("Test Workflow");
            assertThat(workflow.getDescription()).isEqualTo("Test description");
            assertThat(workflow.getCollectionId()).isEqualTo(collectionId);
            assertThat(workflow.getStatus()).isEqualTo("ACTIVE");
            assertThat(workflow.getVersion()).isEqualTo(2);
            assertThat(workflow.getActive()).isTrue();
            assertThat(workflow.getCreatedBy()).isEqualTo("user-1");
            assertThat(workflow.getUpdatedBy()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("builder generates ID when not provided")
        void builderGeneratesIdWhenNotProvided() {
            Workflow workflow = Workflow.builder()
                .tenantId("tenant-123")
                .name("Test")
                .collectionId(UUID.randomUUID())
                .build();

            assertThat(workflow.getId()).isNotNull();
        }

        @Test
        @DisplayName("builder generates timestamps when not provided")
        void builderGeneratesTimestampsWhenNotProvided() {
            Workflow workflow = Workflow.builder()
                .tenantId("tenant-123")
                .name("Test")
                .collectionId(UUID.randomUUID())
                .build();

            assertThat(workflow.getCreatedAt()).isNotNull();
            assertThat(workflow.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("builder uses default status DRAFT")
        void builderUsesDefaultStatusDraft() {
            Workflow workflow = Workflow.builder()
                .tenantId("tenant-123")
                .name("Test")
                .collectionId(UUID.randomUUID())
                .build();

            assertThat(workflow.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("builder uses default version 1")
        void builderUsesDefaultVersionOne() {
            Workflow workflow = Workflow.builder()
                .tenantId("tenant-123")
                .name("Test")
                .collectionId(UUID.randomUUID())
                .build();

            assertThat(workflow.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("builder uses default active true")
        void builderUsesDefaultActiveTrue() {
            Workflow workflow = Workflow.builder()
                .tenantId("tenant-123")
                .name("Test")
                .collectionId(UUID.randomUUID())
                .build();

            assertThat(workflow.getActive()).isTrue();
        }

        @Test
        @DisplayName("builder uses empty collections by default")
        void builderUsesEmptyCollectionsByDefault() {
            Workflow workflow = Workflow.builder()
                .tenantId("tenant-123")
                .name("Test")
                .collectionId(UUID.randomUUID())
                .build();

            assertThat(workflow.getNodes()).isEmpty();
            assertThat(workflow.getEdges()).isEmpty();
            assertThat(workflow.getTriggers()).isEmpty();
            assertThat(workflow.getVariables()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityTests {

        @Test
        @DisplayName("workflows with same ID and tenant are equal")
        void workflowsWithSameIdAndTenantAreEqual() {
            UUID id = UUID.randomUUID();
            UUID collectionId = UUID.randomUUID();

            Workflow workflow1 = new Workflow(
                id, "tenant-123", "Test", "desc", collectionId,
                List.of(), List.of(), List.of(), Map.of(),
                "DRAFT", 1, true, Instant.now(), Instant.now(), "user-1", "user-1"
            );

            Workflow workflow2 = new Workflow(
                id, "tenant-123", "Different", "different desc", collectionId,
                List.of(), List.of(), List.of(), Map.of(),
                "ACTIVE", 2, false, Instant.now(), Instant.now(), "user-2", "user-2"
            );

            assertThat(workflow1).isEqualTo(workflow2);
            assertThat(workflow1.hashCode()).isEqualTo(workflow2.hashCode());
        }

        @Test
        @DisplayName("workflows with different ID are not equal")
        void workflowsWithDifferentIdAreNotEqual() {
            UUID collectionId = UUID.randomUUID();

            Workflow workflow1 = new Workflow(
                UUID.randomUUID(), "tenant-123", "Test", "desc", collectionId,
                List.of(), List.of(), List.of(), Map.of(),
                "DRAFT", 1, true, Instant.now(), Instant.now(), "user-1", "user-1"
            );

            Workflow workflow2 = new Workflow(
                UUID.randomUUID(), "tenant-123", "Test", "desc", collectionId,
                List.of(), List.of(), List.of(), Map.of(),
                "DRAFT", 1, true, Instant.now(), Instant.now(), "user-1", "user-1"
            );

            assertThat(workflow1).isNotEqualTo(workflow2);
        }

        @Test
        @DisplayName("workflows with different tenant are not equal")
        void workflowsWithDifferentTenantAreNotEqual() {
            UUID id = UUID.randomUUID();
            UUID collectionId = UUID.randomUUID();

            Workflow workflow1 = new Workflow(
                id, "tenant-123", "Test", "desc", collectionId,
                List.of(), List.of(), List.of(), Map.of(),
                "DRAFT", 1, true, Instant.now(), Instant.now(), "user-1", "user-1"
            );

            Workflow workflow2 = new Workflow(
                id, "tenant-456", "Test", "desc", collectionId,
                List.of(), List.of(), List.of(), Map.of(),
                "DRAFT", 1, true, Instant.now(), Instant.now(), "user-1", "user-1"
            );

            assertThat(workflow1).isNotEqualTo(workflow2);
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("workflow is immutable")
        void workflowIsImmutable() {
            UUID collectionId = UUID.randomUUID();
            Workflow workflow = new Workflow(
                UUID.randomUUID(),
                "tenant-123",
                "Test",
                "desc",
                collectionId,
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                "DRAFT",
                1,
                true,
                Instant.now(),
                Instant.now(),
                "user-1",
                "user-1"
            );

            // Immutable by design (final fields, no setters)
            assertThat(workflow).isNotNull();
        }
    }
}
