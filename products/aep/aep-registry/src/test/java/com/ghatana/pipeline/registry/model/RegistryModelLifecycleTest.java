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
@DisplayName("Pipeline Registry – Model Lifecycle Tests [GH-90000]")
class RegistryModelLifecycleTest {

    private static final TenantId TENANT = TenantId.of("test-tenant [GH-90000]");

    // =========================================================================
    // PipelineRegistration
    // =========================================================================

    @Nested
    @DisplayName("PipelineRegistration [GH-90000]")
    class PipelineRegistrationTests {

        @Test
        @DisplayName("create() produces a pipeline with correct name and tenant [GH-90000]")
        void createProducesCorrectFields() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.create("My Pipeline", TENANT); // GH-90000

            assertThat(pipeline.getName()).isEqualTo("My Pipeline [GH-90000]");
            assertThat(pipeline.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(pipeline.isActive()).isTrue(); // GH-90000
            assertThat(pipeline.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(pipeline.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("withId() sets only the id [GH-90000]")
        void withIdSetsId() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.withId("p-123 [GH-90000]");

            assertThat(pipeline.getId()).isEqualTo("p-123 [GH-90000]");
            assertThat(pipeline.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("builder sets all fields correctly [GH-90000]")
        void builderSetsAllFields() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.builder() // GH-90000
                    .id("p-456 [GH-90000]")
                    .name("Builder Pipeline [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .description("A description [GH-90000]")
                    .version(2) // GH-90000
                    .active(false) // GH-90000
                    .config("{\"key\":\"value\"}") // GH-90000
                    .updatedBy("alice [GH-90000]")
                    .build(); // GH-90000

            assertThat(pipeline.getId()).isEqualTo("p-456 [GH-90000]");
            assertThat(pipeline.getName()).isEqualTo("Builder Pipeline [GH-90000]");
            assertThat(pipeline.getDescription()).isEqualTo("A description [GH-90000]");
            assertThat(pipeline.getVersion()).isEqualTo(2); // GH-90000
            assertThat(pipeline.isActive()).isFalse(); // GH-90000
            assertThat(pipeline.getConfig()).isEqualTo("{\"key\":\"value\"}"); // GH-90000
            assertThat(pipeline.getUpdatedBy()).isEqualTo("alice [GH-90000]");
        }

        @Test
        @DisplayName("builder defaults: stages and metadata are empty lists/maps [GH-90000]")
        void builderDefaults() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.builder() // GH-90000
                    .name("P [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .updatedBy("user [GH-90000]")
                    .build(); // GH-90000

            assertThat(pipeline.getStages()).isEmpty(); // GH-90000
            assertThat(pipeline.getMetadata()).isEmpty(); // GH-90000
            assertThat(pipeline.getTags()).isEmpty(); // GH-90000
            assertThat(pipeline.getVersionStatus()).isEqualTo(PipelineVersionStatus.DRAFT); // GH-90000
        }

        @Test
        @DisplayName("newVersion() increments version and resets to DRAFT [GH-90000]")
        void newVersionIncrements() { // GH-90000
            PipelineRegistration original = PipelineRegistration.builder() // GH-90000
                    .id("p-1 [GH-90000]")
                    .name("Pipeline [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .version(3) // GH-90000
                    .updatedBy("user [GH-90000]")
                    .versionStatus(PipelineVersionStatus.PUBLISHED) // GH-90000
                    .versionLabel("v3.0.0 [GH-90000]")
                    .build(); // GH-90000

            PipelineRegistration next = original.newVersion(); // GH-90000

            assertThat(next.getVersion()).isEqualTo(4); // GH-90000
            assertThat(next.getVersionStatus()).isEqualTo(PipelineVersionStatus.DRAFT); // GH-90000
            assertThat(next.getVersionLabel()).isNull(); // GH-90000
            assertThat(next.isActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("newVersion() copies tenant, name, and description [GH-90000]")
        void newVersionCopiesFields() { // GH-90000
            PipelineRegistration original = PipelineRegistration.builder() // GH-90000
                    .name("My Pipeline [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .description("Desc [GH-90000]")
                    .version(1) // GH-90000
                    .updatedBy("user [GH-90000]")
                    .build(); // GH-90000

            PipelineRegistration next = original.newVersion(); // GH-90000

            assertThat(next.getName()).isEqualTo("My Pipeline [GH-90000]");
            assertThat(next.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(next.getDescription()).isEqualTo("Desc [GH-90000]");
        }

        @Test
        @DisplayName("hasStructuredConfig() is false when structuredConfig is null [GH-90000]")
        void hasStructuredConfigFalseWhenNull() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.builder() // GH-90000
                    .name("P [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .updatedBy("u [GH-90000]")
                    .build(); // GH-90000

            assertThat(pipeline.hasStructuredConfig()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasStages() is false when stages are empty [GH-90000]")
        void hasStagesFalseWhenEmpty() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.builder() // GH-90000
                    .name("P [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .updatedBy("u [GH-90000]")
                    .build(); // GH-90000

            assertThat(pipeline.hasStages()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("getEffectiveConfig() returns legacy config when structuredConfig absent [GH-90000]")
        void effectiveConfigIsLegacyWhenStructuredAbsent() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.builder() // GH-90000
                    .name("P [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .updatedBy("u [GH-90000]")
                    .config("{\"mode\":\"batch\"}") // GH-90000
                    .build(); // GH-90000

            assertThat(pipeline.getEffectiveConfig()).isEqualTo("{\"mode\":\"batch\"}"); // GH-90000
        }

        @Test
        @DisplayName("addStage() appends a stage [GH-90000]")
        void addStageAppends() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.create("P", TENANT); // GH-90000
            PipelineStage stage = PipelineStage.builder().name("stage-1 [GH-90000]").build();

            pipeline.addStage(stage); // GH-90000

            assertThat(pipeline.hasStages()).isTrue(); // GH-90000
            assertThat(pipeline.getStages()).hasSize(1); // GH-90000
            assertThat(pipeline.getStages().get(0).getName()).isEqualTo("stage-1 [GH-90000]");
        }

        @Test
        @DisplayName("addMetadata() adds key-value entry [GH-90000]")
        void addMetadataEntry() { // GH-90000
            PipelineRegistration pipeline = PipelineRegistration.create("P", TENANT); // GH-90000

            pipeline.addMetadata("owner", "team-a"); // GH-90000

            assertThat(pipeline.getMetadata()).containsEntry("owner", "team-a"); // GH-90000
        }

        @Test
        @DisplayName("updateFrom() copies description and tags from update [GH-90000]")
        void updateFromCopiesFields() { // GH-90000
            PipelineRegistration base = PipelineRegistration.builder() // GH-90000
                    .name("Base [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .description("Old description [GH-90000]")
                    .updatedBy("original [GH-90000]")
                    .build(); // GH-90000

            PipelineRegistration update = PipelineRegistration.builder() // GH-90000
                    .name("Base [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .description("New description [GH-90000]")
                    .updatedBy("editor [GH-90000]")
                    .active(false) // GH-90000
                    .build(); // GH-90000

            base.updateFrom(update); // GH-90000

            assertThat(base.getDescription()).isEqualTo("New description [GH-90000]");
            assertThat(base.getUpdatedBy()).isEqualTo("editor [GH-90000]");
            assertThat(base.isActive()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // Pattern
    // =========================================================================

    @Nested
    @DisplayName("Pattern [GH-90000]")
    class PatternTests {

        @Test
        @DisplayName("create() sets name, tenant, specification, and DRAFT status [GH-90000]")
        void createSetsFields() { // GH-90000
            Pattern pattern = Pattern.create("fraud_pattern", TENANT, "SEQ(login_failed, transaction)"); // GH-90000

            assertThat(pattern.getName()).isEqualTo("fraud_pattern [GH-90000]");
            assertThat(pattern.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(pattern.getSpecification()).isEqualTo("SEQ(login_failed, transaction) [GH-90000]");
            assertThat(pattern.getVersion()).isEqualTo(1); // GH-90000
            assertThat(pattern.getStatus()).isEqualTo("DRAFT [GH-90000]");
            assertThat(pattern.getCreatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("withId() sets only the id field [GH-90000]")
        void withIdSetsId() { // GH-90000
            Pattern pattern = Pattern.withId("pat-000 [GH-90000]");

            assertThat(pattern.getId()).isEqualTo("pat-000 [GH-90000]");
            assertThat(pattern.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("builder defaults: status is DRAFT and confidence is 0 [GH-90000]")
        void builderDefaults() { // GH-90000
            Pattern pattern = Pattern.builder() // GH-90000
                    .name("p [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .specification("SEQ(A) [GH-90000]")
                    .build(); // GH-90000

            assertThat(pattern.getStatus()).isEqualTo("DRAFT [GH-90000]");
            assertThat(pattern.getConfidence()).isEqualTo(0); // GH-90000
            assertThat(pattern.getTags()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("withCompiledPlan() sets status to COMPILED and clamps confidence 0-100 [GH-90000]")
        void withCompiledPlanClampsConfidence() { // GH-90000
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); // GH-90000

            pattern.withCompiledPlan("binary-plan-data", 150); // > 100 → clamped to 100 // GH-90000

            assertThat(pattern.getStatus()).isEqualTo("COMPILED [GH-90000]");
            assertThat(pattern.getDetectionPlan()).isEqualTo("binary-plan-data [GH-90000]");
            assertThat(pattern.getConfidence()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("withCompiledPlan() clamps negative confidence to 0 [GH-90000]")
        void withCompiledPlanClampsNegative() { // GH-90000
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); // GH-90000

            pattern.withCompiledPlan("plan", -50); // GH-90000

            assertThat(pattern.getConfidence()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("activate() sets status to ACTIVE [GH-90000]")
        void activateSetsActive() { // GH-90000
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); // GH-90000
            pattern.withCompiledPlan("plan", 90); // GH-90000

            pattern.activate(); // GH-90000

            assertThat(pattern.getStatus()).isEqualTo("ACTIVE [GH-90000]");
        }

        @Test
        @DisplayName("deactivate() sets status to INACTIVE [GH-90000]")
        void deactivateSetsInactive() { // GH-90000
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); // GH-90000
            pattern.activate(); // GH-90000

            pattern.deactivate(); // GH-90000

            assertThat(pattern.getStatus()).isEqualTo("INACTIVE [GH-90000]");
        }

        @Test
        @DisplayName("newVersion() increments version and resets to DRAFT [GH-90000]")
        void newVersionIncrementsVersion() { // GH-90000
            Pattern original = Pattern.create("p", TENANT, "SEQ(A)"); // GH-90000
            original.setVersion(3); // GH-90000
            original.withCompiledPlan("plan", 80); // GH-90000
            original.activate(); // GH-90000

            Pattern next = original.newVersion(); // GH-90000

            assertThat(next.getVersion()).isEqualTo(4); // GH-90000
            assertThat(next.getStatus()).isEqualTo("DRAFT [GH-90000]");
        }

        @Test
        @DisplayName("newVersion() copies name, tenant, specification from original [GH-90000]")
        void newVersionCopiesFields() { // GH-90000
            Pattern original = Pattern.builder() // GH-90000
                    .name("complex_pattern [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .specification("SEQ(A,B,C) [GH-90000]")
                    .description("Detects ABC sequence [GH-90000]")
                    .version(2) // GH-90000
                    .status("ACTIVE [GH-90000]")
                    .build(); // GH-90000

            Pattern next = original.newVersion(); // GH-90000

            assertThat(next.getName()).isEqualTo("complex_pattern [GH-90000]");
            assertThat(next.getTenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(next.getSpecification()).isEqualTo("SEQ(A,B,C) [GH-90000]");
            assertThat(next.getDescription()).isEqualTo("Detects ABC sequence [GH-90000]");
        }
    }
}
