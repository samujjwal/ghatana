/*
 * Copyright (c) 2026 Ghatana Inc. 
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
        void createProducesCorrectFields() { 
            PipelineRegistration pipeline = PipelineRegistration.create("My Pipeline", TENANT); 

            assertThat(pipeline.getName()).isEqualTo("My Pipeline");
            assertThat(pipeline.getTenantId()).isEqualTo(TENANT); 
            assertThat(pipeline.isActive()).isTrue(); 
            assertThat(pipeline.getCreatedAt()).isNotNull(); 
            assertThat(pipeline.getUpdatedAt()).isNotNull(); 
        }

        @Test
        @DisplayName("withId() sets only the id")
        void withIdSetsId() { 
            PipelineRegistration pipeline = PipelineRegistration.withId("p-123");

            assertThat(pipeline.getId()).isEqualTo("p-123");
            assertThat(pipeline.getName()).isNull(); 
        }

        @Test
        @DisplayName("builder sets all fields correctly")
        void builderSetsAllFields() { 
            PipelineRegistration pipeline = PipelineRegistration.builder() 
                    .id("p-456")
                    .name("Builder Pipeline")
                    .tenantId(TENANT) 
                    .description("A description")
                    .version(2) 
                    .active(false) 
                    .config("{\"key\":\"value\"}") 
                    .updatedBy("alice")
                    .build(); 

            assertThat(pipeline.getId()).isEqualTo("p-456");
            assertThat(pipeline.getName()).isEqualTo("Builder Pipeline");
            assertThat(pipeline.getDescription()).isEqualTo("A description");
            assertThat(pipeline.getVersion()).isEqualTo(2); 
            assertThat(pipeline.isActive()).isFalse(); 
            assertThat(pipeline.getConfig()).isEqualTo("{\"key\":\"value\"}"); 
            assertThat(pipeline.getUpdatedBy()).isEqualTo("alice");
        }

        @Test
        @DisplayName("builder defaults: stages and metadata are empty lists/maps")
        void builderDefaults() { 
            PipelineRegistration pipeline = PipelineRegistration.builder() 
                    .name("P")
                    .tenantId(TENANT) 
                    .updatedBy("user")
                    .build(); 

            assertThat(pipeline.getStages()).isEmpty(); 
            assertThat(pipeline.getMetadata()).isEmpty(); 
            assertThat(pipeline.getTags()).isEmpty(); 
            assertThat(pipeline.getVersionStatus()).isEqualTo(PipelineVersionStatus.DRAFT); 
        }

        @Test
        @DisplayName("newVersion() increments version and resets to DRAFT")
        void newVersionIncrements() { 
            PipelineRegistration original = PipelineRegistration.builder() 
                    .id("p-1")
                    .name("Pipeline")
                    .tenantId(TENANT) 
                    .version(3) 
                    .updatedBy("user")
                    .versionStatus(PipelineVersionStatus.PUBLISHED) 
                    .versionLabel("v3.0.0")
                    .build(); 

            PipelineRegistration next = original.newVersion(); 

            assertThat(next.getVersion()).isEqualTo(4); 
            assertThat(next.getVersionStatus()).isEqualTo(PipelineVersionStatus.DRAFT); 
            assertThat(next.getVersionLabel()).isNull(); 
            assertThat(next.isActive()).isTrue(); 
        }

        @Test
        @DisplayName("newVersion() copies tenant, name, and description")
        void newVersionCopiesFields() { 
            PipelineRegistration original = PipelineRegistration.builder() 
                    .name("My Pipeline")
                    .tenantId(TENANT) 
                    .description("Desc")
                    .version(1) 
                    .updatedBy("user")
                    .build(); 

            PipelineRegistration next = original.newVersion(); 

            assertThat(next.getName()).isEqualTo("My Pipeline");
            assertThat(next.getTenantId()).isEqualTo(TENANT); 
            assertThat(next.getDescription()).isEqualTo("Desc");
        }

        @Test
        @DisplayName("hasStructuredConfig() is false when structuredConfig is null")
        void hasStructuredConfigFalseWhenNull() { 
            PipelineRegistration pipeline = PipelineRegistration.builder() 
                    .name("P")
                    .tenantId(TENANT) 
                    .updatedBy("u")
                    .build(); 

            assertThat(pipeline.hasStructuredConfig()).isFalse(); 
        }

        @Test
        @DisplayName("hasStages() is false when stages are empty")
        void hasStagesFalseWhenEmpty() { 
            PipelineRegistration pipeline = PipelineRegistration.builder() 
                    .name("P")
                    .tenantId(TENANT) 
                    .updatedBy("u")
                    .build(); 

            assertThat(pipeline.hasStages()).isFalse(); 
        }

        @Test
        @DisplayName("getEffectiveConfig() returns legacy config when structuredConfig absent")
        void effectiveConfigIsLegacyWhenStructuredAbsent() { 
            PipelineRegistration pipeline = PipelineRegistration.builder() 
                    .name("P")
                    .tenantId(TENANT) 
                    .updatedBy("u")
                    .config("{\"mode\":\"batch\"}") 
                    .build(); 

            assertThat(pipeline.getEffectiveConfig()).isEqualTo("{\"mode\":\"batch\"}"); 
        }

        @Test
        @DisplayName("addStage() appends a stage")
        void addStageAppends() { 
            PipelineRegistration pipeline = PipelineRegistration.create("P", TENANT); 
            PipelineStage stage = PipelineStage.builder().name("stage-1").build();

            pipeline.addStage(stage); 

            assertThat(pipeline.hasStages()).isTrue(); 
            assertThat(pipeline.getStages()).hasSize(1); 
            assertThat(pipeline.getStages().get(0).getName()).isEqualTo("stage-1");
        }

        @Test
        @DisplayName("addMetadata() adds key-value entry")
        void addMetadataEntry() { 
            PipelineRegistration pipeline = PipelineRegistration.create("P", TENANT); 

            pipeline.addMetadata("owner", "team-a"); 

            assertThat(pipeline.getMetadata()).containsEntry("owner", "team-a"); 
        }

        @Test
        @DisplayName("updateFrom() copies description and tags from update")
        void updateFromCopiesFields() { 
            PipelineRegistration base = PipelineRegistration.builder() 
                    .name("Base")
                    .tenantId(TENANT) 
                    .description("Old description")
                    .updatedBy("original")
                    .build(); 

            PipelineRegistration update = PipelineRegistration.builder() 
                    .name("Base")
                    .tenantId(TENANT) 
                    .description("New description")
                    .updatedBy("editor")
                    .active(false) 
                    .build(); 

            base.updateFrom(update); 

            assertThat(base.getDescription()).isEqualTo("New description");
            assertThat(base.getUpdatedBy()).isEqualTo("editor");
            assertThat(base.isActive()).isFalse(); 
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
        void createSetsFields() { 
            Pattern pattern = Pattern.create("fraud_pattern", TENANT, "SEQ(login_failed, transaction)"); 

            assertThat(pattern.getName()).isEqualTo("fraud_pattern");
            assertThat(pattern.getTenantId()).isEqualTo(TENANT); 
            assertThat(pattern.getSpecification()).isEqualTo("SEQ(login_failed, transaction)");
            assertThat(pattern.getVersion()).isEqualTo(1); 
            assertThat(pattern.getStatus()).isEqualTo("DRAFT");
            assertThat(pattern.getCreatedAt()).isNotNull(); 
        }

        @Test
        @DisplayName("withId() sets only the id field")
        void withIdSetsId() { 
            Pattern pattern = Pattern.withId("pat-000");

            assertThat(pattern.getId()).isEqualTo("pat-000");
            assertThat(pattern.getName()).isNull(); 
        }

        @Test
        @DisplayName("builder defaults: status is DRAFT and confidence is 0")
        void builderDefaults() { 
            Pattern pattern = Pattern.builder() 
                    .name("p")
                    .tenantId(TENANT) 
                    .specification("SEQ(A)")
                    .build(); 

            assertThat(pattern.getStatus()).isEqualTo("DRAFT");
            assertThat(pattern.getConfidence()).isEqualTo(0); 
            assertThat(pattern.getTags()).isEmpty(); 
        }

        @Test
        @DisplayName("withCompiledPlan() sets status to COMPILED and clamps confidence 0-100")
        void withCompiledPlanClampsConfidence() { 
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); 

            pattern.withCompiledPlan("plan-id-123", 150); // > 100 → clamped to 100 

            assertThat(pattern.getStatus()).isEqualTo("COMPILED");
            assertThat(pattern.getDetectionPlanId()).isEqualTo("plan-id-123");
            assertThat(pattern.getConfidence()).isEqualTo(100); 
        }

        @Test
        @DisplayName("withCompiledPlan() clamps negative confidence to 0")
        void withCompiledPlanClampsNegative() { 
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); 

            pattern.withCompiledPlan("plan-id-456", -50); 

            assertThat(pattern.getConfidence()).isEqualTo(0); 
        }

        @Test
        @DisplayName("activate() sets status to ACTIVE")
        void activateSetsActive() { 
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); 
            pattern.withCompiledPlan("plan-id-789", 90); 

            pattern.activate(); 

            assertThat(pattern.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("deactivate() sets status to INACTIVE")
        void deactivateSetsInactive() { 
            Pattern pattern = Pattern.create("p", TENANT, "SEQ(A)"); 
            pattern.activate(); 

            pattern.deactivate(); 

            assertThat(pattern.getStatus()).isEqualTo("INACTIVE");
        }

        @Test
        @DisplayName("newVersion() increments version and resets to DRAFT")
        void newVersionIncrementsVersion() { 
            Pattern original = Pattern.create("p", TENANT, "SEQ(A)"); 
            original.setVersion(3); 
            original.withCompiledPlan("plan", 80); 
            original.activate(); 

            Pattern next = original.newVersion(); 

            assertThat(next.getVersion()).isEqualTo(4); 
            assertThat(next.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("newVersion() copies name, tenant, specification from original")
        void newVersionCopiesFields() { 
            Pattern original = Pattern.builder() 
                    .name("complex_pattern")
                    .tenantId(TENANT) 
                    .specification("SEQ(A,B,C)")
                    .description("Detects ABC sequence")
                    .version(2) 
                    .status("ACTIVE")
                    .build(); 

            Pattern next = original.newVersion(); 

            assertThat(next.getName()).isEqualTo("complex_pattern");
            assertThat(next.getTenantId()).isEqualTo(TENANT); 
            assertThat(next.getSpecification()).isEqualTo("SEQ(A,B,C)");
            assertThat(next.getDescription()).isEqualTo("Detects ABC sequence");
        }
    }
}
