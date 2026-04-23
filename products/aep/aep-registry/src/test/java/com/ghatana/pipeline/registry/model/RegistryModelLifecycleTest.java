/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.pipeline.registry.model;

import com.ghatana.platform.domain.auth.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PipelineRegistration} and {@link Pattern} domain model lifecycle.
 *
 * @doc.type class
 * @doc.purpose Tests for pipeline and pattern model construction and state transitions
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Pipeline Registry – Model Lifecycle Tests")
class RegistryModelLifecycleTest {

    private static final TenantId TENANT = TenantId.of("test-tenant");

    // =========================================================================
    // PipelineRegistration
    // =========================================================================

    @Nested
    @DisplayName("PipelineRegistration")
    class PipelineRegistrationTests {

        @Test
        @DisplayName("create() produces a pipeline with correct name and tenant")
        void createProducesCorrectFields() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.create("My Pipeline", TENANT); // GH-90000

            assertThat(pipeline.getName()).isEqualTo("My Pipeline");
            assertThat(pipeline.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(pipeline.isActive()).isTrue(); // GH-90000
            assertThat(pipeline.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(pipeline.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("withId() sets only the id")
        void withIdSetsId() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.withId("p-123");

            assertThat(pipeline.getId()).isEqualTo("p-123");
            assertThat(pipeline.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("builder sets all fields correctly")
        void builderSetsAllFields() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.builder() // GH-90000
                    .id("p-456")
                    .name("Builder Pipeline")
                    .tenantId(TENANT) // GH-90000
                    .description("A description")
                    .version(2) // GH-90000
                    .active(false) // GH-90000
                    .config("{\"key\":\"value\"}") // GH-90000
                    .updatedBy("alice")
                    .build(); // GH-90000

            assertThat(pipeline.getId()).isEqualTo("p-456");
            assertThat(pipeline.getName()).isEqualTo("Builder Pipeline");
            assertThat(pipeline.getDescription()).isEqualTo("A description");
            assertThat(pipeline.getVersion()).isEqualTo(2); // GH-90000
            assertThat(pipeline.isActive()).isFalse(); // GH-90000
            assertThat(pipeline.getConfig()).isEqualTo("{\"key\":\"value\"}"); // GH-90000
            assertThat(pipeline.getUpdatedBy()).isEqualTo("alice");
        }

        @Test
        @DisplayName("builder defaults: stages and metadata are empty lists/maps")
        void builderDefaults() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.builder() // GH-90000
                    .name("P")
                    .tenantId(TENANT) // GH-90000
                    .updatedBy("user")
                    .build(); // GH-90000

            assertThat(pipeline.getStages()).isEmpty(); // GH-90000
            assertThat(pipeline.getMetadata()).isEmpty(); // GH-90000
            assertThat(pipeline.getTags()).isEmpty(); // GH-90000
            assertThat(pipeline.getVersionStatus()).isEqualTo(PipelineVersionStatus.DRAFT); // GH-90000
        }

        @Test
        @DisplayName("newVersion() increments version and resets to DRAFT")
        void newVersionIncrements() { // GH-90000
            PipelineRegistration original = PipelineRegistration.builder() // GH-90000
                    .id("p-1")
                    .name("Pipeline")
                    .tenantId(TENANT) // GH-90000
                    .version(3) // GH-90000
                    .updatedBy("user")
                    .versionStatus(PipelineVersionStatus.PUBLISHED) // GH-90000
                    .versionLabel("v3.0.0")
                    .build(); // GH-90000

            PipelineRegistration next = original.newVersion(); // GH-90000

            assertThat(next.getVersion()).isEqualTo(4); // GH-90000
            assertThat(next.getVersionStatus()).isEqualTo(PipelineVersionStatus.DRAFT); // GH-90000
            assertThat(next.getVersionLabel()).isNull(); // GH-90000
            assertThat(next.isActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("newVersion() copies tenant, name, and description")
        void newVersionCopiesFields() { // GH-90000
            PipelineRegistration original = PipelineRegistration.builder() // GH-90000
                    .name("My Pipeline")
                    .tenantId(TENANT) // GH-90000
                    .description("Desc")
                    .version(1) // GH-90000
                    .updatedBy("user")
                    .build(); // GH-90000

            PipelineRegistration next = original.newVersion(); // GH-90000

            assertThat(next.getName()).isEqualTo("My Pipeline");
            assertThat(next.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(next.getDescription()).isEqualTo("Desc");
        }

        @Test
        @DisplayName("hasStructuredConfig() is false when structuredConfig is null")
        void hasStructuredConfigFalseWhenNull() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.builder() // GH-90000
                    .name("P")
                    .tenantId(TENANT) // GH-90000
                    .updatedBy("u")
                    .build(); // GH-90000

            assertThat(pipeline.hasStructuredConfig()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasStages() is false when stages are empty")
        void hasStagesFalseWhenEmpty() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.builder() // GH-90000
                    .name("P")
                    .tenantId(TENANT) // GH-90000
                    .updatedBy("u")
                    .build(); // GH-90000

            assertThat(pipeline.hasStages()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("getEffectiveConfig() returns legacy config when structuredConfig absent")
        void effectiveConfigIsLegacyWhenStructuredAbsent() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.builder() // GH-90000
                    .name("P")
                    .tenantId(TENANT) // GH-90000
                    .updatedBy("u")
                    .config("{\"mode\":\"batch\"}") // GH-90000
                    .build(); // GH-90000

            assertThat(pipeline.getEffectiveConfig()).isEqualTo("{\"mode\":\"batch\"}"); // GH-90000
        }

        @Test
        @DisplayName("addStage() appends a stage")
        void addStageAppends() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.create("P", TENANT); // GH-90000
            PipelineStage stage = PipelineStage.builder().name("stage-1").build();

            pipeline.addStage(stage); // GH-90000

            assertThat(pipeline.hasStages()).isTrue(); // GH-90000
            assertThat(pipeline.getStages()).hasSize(1); // GH-90000
            assertThat(pipeline.getStages().get(0).getName()).isEqualTo("stage-1");
        }

        @Test
        @DisplayName("addMetadata() adds key-value entry")
        void addMetadataEntry() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.create("P", TENANT); // GH-90000

            pipeline.addMetadata("owner", "team-a"); // GH-90000

            assertThat(pipeline.getMetadata()).containsEntry("owner", "team-a"); // GH-90000
        }

        @Test
        @DisplayName("updateFrom() copies description and tags from update")
        void updateFromCopiesFields() { // GH-90000
            PipelineRegistration base = PipelineRegistration.builder() // GH-90000
                    .name("Base")
                    .tenantId(TENANT) // GH-90000
                    .description("Old description")
                    .updatedBy("original")
                    .build(); // GH-90000

            PipelineRegistration update = PipelineRegistration.builder() // GH-90000
                    .name("Base")
                    .tenantId(TENANT) // GH-90000
                    .description("New description")
                    .updatedBy("editor")
                    .active(false) // GH-90000
                    .build(); // GH-90000

            base.updateFrom(update); // GH-90000

            assertThat(base.getDescription()).isEqualTo("New description");
            assertThat(base.getUpdatedBy()).isEqualTo("editor");
            assertThat(base.isActive()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // Pattern
    // =========================================================================

    @Nested
    @DisplayName("Pattern")
    class PatternTests {

        @Test
        @DisplayName("create() sets name, tenant, specification, and DRAFT status")
        void createSetsFields() { // GH-90000
            Pattern pattern = Pattern.create("fraud_pattern", TENANT, "SEQ(login_failed, transaction)"); // GH-90000

            assertThat(pattern.getName()).isEqualTo("fraud_pattern");
            assertThat(pattern.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(pattern.getSpecification()).isEqualTo("SEQ(login_failed, transaction)");
            assertThat(pattern.getVersion()).isEqualTo(1); // GH-90000
            assertThat(pattern.getStatus()).isEqualTo("DRAFT");
            assertThat(pattern.getCreatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("withId() sets only the id field")
        void withIdSetsId() { // GH-90000
            Pattern pattern = Pattern.withId("pat-000");

            assertThat(pattern.getId()).isEqualTo("pat-000");
            assertThat(pattern.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("builder defaults: status is DRAFT and confidence is 0")
        void builderDefaults() { // GH-90000
            Pattern pattern = Pattern.builder() // GH-90000
                    .name("p")
                    .tenantId(TENANT) // GH-90000
                    .specification("SEQ(A)")
                    .build(); // GH-90000

            assertThat(pattern.getStatus()).isEqualTo("DRAFT");
            assertThat(pattern.getConfidence()).isEqualTo(0); // GH-90000
            assertThat(pattern.getTags()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("withCompiledPlan() sets status to COMPILED and clamps confidence 0-100")
        void withCompiledPlanClampsConfidence() { // GH-90000
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); // GH-90000

            pattern.withCompiledPlan("binary-plan-data", 150); // > 100 → clamped to 100 // GH-90000

            assertThat(pattern.getStatus()).isEqualTo("COMPILED");
            assertThat(pattern.getDetectionPlan()).isEqualTo("binary-plan-data");
            assertThat(pattern.getConfidence()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("withCompiledPlan() clamps negative confidence to 0")
        void withCompiledPlanClampsNegative() { // GH-90000
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); // GH-90000

            pattern.withCompiledPlan("plan", -50); // GH-90000

            assertThat(pattern.getConfidence()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("activate() sets status to ACTIVE")
        void activateSetsActive() { // GH-90000
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); // GH-90000
            pattern.withCompiledPlan("plan", 90); // GH-90000

            pattern.activate(); // GH-90000

            assertThat(pattern.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("deactivate() sets status to INACTIVE")
        void deactivateSetsInactive() { // GH-90000
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); // GH-90000
            pattern.activate(); // GH-90000

            pattern.deactivate(); // GH-90000

            assertThat(pattern.getStatus()).isEqualTo("INACTIVE");
        }

        @Test
        @DisplayName("newVersion() increments version and resets to DRAFT")
        void newVersionIncrementsVersion() { // GH-90000
            Pattern original = Pattern.create("p", TENANT, "SEQ(A)"); // GH-90000
            original.setVersion(3); // GH-90000
            original.withCompiledPlan("plan", 80); // GH-90000
            original.activate(); // GH-90000

            Pattern next = original.newVersion(); // GH-90000

            assertThat(next.getVersion()).isEqualTo(4); // GH-90000
            assertThat(next.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("newVersion() copies name, tenant, specification from original")
        void newVersionCopiesFields() { // GH-90000
            Pattern original = Pattern.builder() // GH-90000
                    .name("complex_pattern")
                    .tenantId(TENANT) // GH-90000
                    .specification("SEQ(A,B,C)")
                    .description("Detects ABC sequence")
                    .version(2) // GH-90000
                    .status("ACTIVE")
                    .build(); // GH-90000

            Pattern next = original.newVersion(); // GH-90000

            assertThat(next.getName()).isEqualTo("complex_pattern");
            assertThat(next.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(next.getSpecification()).isEqualTo("SEQ(A,B,C)");
            assertThat(next.getDescription()).isEqualTo("Detects ABC sequence");
        }
    }
}
