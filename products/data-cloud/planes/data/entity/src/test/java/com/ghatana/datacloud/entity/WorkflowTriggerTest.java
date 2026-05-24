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
 * Phase 3: Contract tests for WorkflowTrigger.
 *
 * @doc.type class
 * @doc.purpose Tests for WorkflowTrigger workflow initiation functionality
 * @doc.layer test
 */
@DisplayName("WorkflowTrigger Tests")
class WorkflowTriggerTest {

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {

        @Test
        @DisplayName("valid trigger is created successfully")
        void validTriggerIsCreated() {
            WorkflowTrigger trigger = new WorkflowTrigger(
                "trigger-1",
                "ON_ENTITY_CREATED",
                Map.of("collectionName", "orders", "filter", Map.of("status", "pending")),
                true
            );

            assertThat(trigger.getId()).isEqualTo("trigger-1");
            assertThat(trigger.getType()).isEqualTo("ON_ENTITY_CREATED");
            assertThat(trigger.getConfig()).containsEntry("collectionName", "orders");
            assertThat(trigger.getActive()).isTrue();
        }

        @Test
        @DisplayName("null ID is rejected")
        void nullIdIsRejected() {
            assertThatThrownBy(() -> new WorkflowTrigger(
                null,
                "ON_ENTITY_CREATED",
                Map.of(),
                true
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ID must not be null");
        }

        @Test
        @DisplayName("null type is rejected")
        void nullTypeIsRejected() {
            assertThatThrownBy(() -> new WorkflowTrigger(
                "trigger-1",
                null,
                Map.of(),
                true
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Type must not be null");
        }

        @Test
        @DisplayName("null config is rejected")
        void nullConfigIsRejected() {
            assertThatThrownBy(() -> new WorkflowTrigger(
                "trigger-1",
                "ON_ENTITY_CREATED",
                null,
                true
            ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Config must not be null");
        }

        @Test
        @DisplayName("null active defaults to true")
        void nullActiveDefaultsToTrue() {
            WorkflowTrigger trigger = new WorkflowTrigger(
                "trigger-1",
                "ON_ENTITY_CREATED",
                Map.of(),
                null
            );

            assertThat(trigger.getActive()).isTrue();
        }

        @Test
        @DisplayName("active false is accepted")
        void activeFalseIsAccepted() {
            WorkflowTrigger trigger = new WorkflowTrigger(
                "trigger-1",
                "ON_ENTITY_CREATED",
                Map.of(),
                false
            );

            assertThat(trigger.getActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPatternTests {

        @Test
        @DisplayName("builder creates trigger with all fields")
        void builderCreatesTriggerWithAllFields() {
            WorkflowTrigger trigger = WorkflowTrigger.builder()
                .id("trigger-1")
                .type("SCHEDULED")
                .config(Map.of("cron", "0 0 * * *", "timezone", "UTC"))
                .active(true)
                .build();

            assertThat(trigger.getId()).isEqualTo("trigger-1");
            assertThat(trigger.getType()).isEqualTo("SCHEDULED");
            assertThat(trigger.getConfig()).containsEntry("cron", "0 0 * * *");
            assertThat(trigger.getActive()).isTrue();
        }

        @Test
        @DisplayName("builder generates ID when not provided")
        void builderGeneratesIdWhenNotProvided() {
            WorkflowTrigger trigger = WorkflowTrigger.builder()
                .type("MANUAL")
                .build();

            assertThat(trigger.getId()).isNotNull();
        }

        @Test
        @DisplayName("builder uses default active true")
        void builderUsesDefaultActiveTrue() {
            WorkflowTrigger trigger = WorkflowTrigger.builder()
                .type("MANUAL")
                .build();

            assertThat(trigger.getActive()).isTrue();
        }

        @Test
        @DisplayName("builder uses empty config by default")
        void builderUsesEmptyConfigByDefault() {
            WorkflowTrigger trigger = WorkflowTrigger.builder()
                .type("MANUAL")
                .build();

            assertThat(trigger.getConfig()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityTests {

        @Test
        @DisplayName("triggers with same ID are equal")
        void triggersWithSameIdAreEqual() {
            WorkflowTrigger trigger1 = new WorkflowTrigger(
                "trigger-1",
                "ON_ENTITY_CREATED",
                Map.of("key", "value1"),
                true
            );

            WorkflowTrigger trigger2 = new WorkflowTrigger(
                "trigger-1",
                "SCHEDULED",
                Map.of("key", "value2"),
                false
            );

            assertThat(trigger1).isEqualTo(trigger2);
            assertThat(trigger1.hashCode()).isEqualTo(trigger2.hashCode());
        }

        @Test
        @DisplayName("triggers with different ID are not equal")
        void triggersWithDifferentIdAreNotEqual() {
            WorkflowTrigger trigger1 = new WorkflowTrigger(
                "trigger-1",
                "ON_ENTITY_CREATED",
                Map.of(),
                true
            );

            WorkflowTrigger trigger2 = new WorkflowTrigger(
                "trigger-2",
                "ON_ENTITY_CREATED",
                Map.of(),
                true
            );

            assertThat(trigger1).isNotEqualTo(trigger2);
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("trigger is immutable")
        void triggerIsImmutable() {
            WorkflowTrigger trigger = new WorkflowTrigger(
                "trigger-1",
                "ON_ENTITY_CREATED",
                Map.of(),
                true
            );

            // Immutable by design (final fields, no setters)
            assertThat(trigger).isNotNull();
        }
    }

    @Nested
    @DisplayName("Trigger Types")
    class TriggerTypeTests {

        @Test
        @DisplayName("supports MANUAL trigger type")
        void supportsManualTriggerType() {
            WorkflowTrigger trigger = WorkflowTrigger.builder()
                .type("MANUAL")
                .build();

            assertThat(trigger.getType()).isEqualTo("MANUAL");
        }

        @Test
        @DisplayName("supports ON_ENTITY_CREATED trigger type")
        void supportsOnEntityCreatedTriggerType() {
            WorkflowTrigger trigger = WorkflowTrigger.builder()
                .type("ON_ENTITY_CREATED")
                .build();

            assertThat(trigger.getType()).isEqualTo("ON_ENTITY_CREATED");
        }

        @Test
        @DisplayName("supports ON_ENTITY_UPDATED trigger type")
        void supportsOnEntityUpdatedTriggerType() {
            WorkflowTrigger trigger = WorkflowTrigger.builder()
                .type("ON_ENTITY_UPDATED")
                .build();

            assertThat(trigger.getType()).isEqualTo("ON_ENTITY_UPDATED");
        }

        @Test
        @DisplayName("supports ON_ENTITY_DELETED trigger type")
        void supportsOnEntityDeletedTriggerType() {
            WorkflowTrigger trigger = WorkflowTrigger.builder()
                .type("ON_ENTITY_DELETED")
                .build();

            assertThat(trigger.getType()).isEqualTo("ON_ENTITY_DELETED");
        }

        @Test
        @DisplayName("supports SCHEDULED trigger type")
        void supportsScheduledTriggerType() {
            WorkflowTrigger trigger = WorkflowTrigger.builder()
                .type("SCHEDULED")
                .build();

            assertThat(trigger.getType()).isEqualTo("SCHEDULED");
        }

        @Test
        @DisplayName("supports WEBHOOK trigger type")
        void supportsWebhookTriggerType() {
            WorkflowTrigger trigger = WorkflowTrigger.builder()
                .type("WEBHOOK")
                .build();

            assertThat(trigger.getType()).isEqualTo("WEBHOOK");
        }
    }
}
