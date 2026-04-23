/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.memory.model.procedure;

import com.ghatana.agent.memory.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EnhancedProcedure} — procedural memory item with versioned steps.
 *
 * @doc.type class
 * @doc.purpose Unit tests for EnhancedProcedure value object and MemoryItem contract
 * @doc.layer agent-memory
 * @doc.pattern Test
 */
@DisplayName("EnhancedProcedure - procedural memory item with versioned steps")
class EnhancedProcedureTest {

    private static final String TEST_ID = "proc-001";
    private static final String SITUATION = "When data ingestion pipeline fails intermittently";
    private static final String ACTION    = "Restart ingestion worker with exponential back-off";

    // ─────────────────────────────────────────────────────────────
    // MemoryItem interface contract
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MemoryItem interface contract")
    class MemoryItemContractTests {

        @Test
        @DisplayName("EnhancedProcedure implements MemoryItem")
        void implementsMemoryItem() { // GH-90000
            EnhancedProcedure p = minimal(); // GH-90000
            assertThat(p).isInstanceOf(MemoryItem.class); // GH-90000
        }

        @Test
        @DisplayName("getId() returns the supplied id")
        void getId_returnsSuppliedId() { // GH-90000
            assertThat(minimal().getId()).isEqualTo(TEST_ID); // GH-90000
        }

        @Test
        @DisplayName("getType() is PROCEDURE by default")
        void getType_isProcedureByDefault() { // GH-90000
            assertThat(minimal().getType()).isEqualTo(MemoryItemType.PROCEDURE); // GH-90000
        }

        @Test
        @DisplayName("getTenantId() defaults to 'default'")
        void getTenantId_defaultsToDefault() { // GH-90000
            assertThat(minimal().getTenantId()).isEqualTo("default");
        }

        @Test
        @DisplayName("getClassification() defaults to 'INTERNAL'")
        void getClassification_defaultsToInternal() { // GH-90000
            assertThat(minimal().getClassification()).isEqualTo("INTERNAL");
        }

        @Test
        @DisplayName("getCreatedAt() and getUpdatedAt() are non-null and close to now")
        void createdAndUpdated_nonNullAndCloseToNow() { // GH-90000
            Instant before = Instant.now(); // GH-90000
            EnhancedProcedure p = minimal(); // GH-90000
            Instant after = Instant.now(); // GH-90000
            assertThat(p.getCreatedAt()).isBetween(before, after); // GH-90000
            assertThat(p.getUpdatedAt()).isBetween(before, after); // GH-90000
        }

        @Test
        @DisplayName("getLinks() defaults to empty list")
        void getLinks_defaultsToEmpty() { // GH-90000
            assertThat(minimal().getLinks()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("getLabels() defaults to empty map")
        void getLabels_defaultsToEmpty() { // GH-90000
            assertThat(minimal().getLabels()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("getEmbedding() defaults to null")
        void getEmbedding_defaultsToNull() { // GH-90000
            assertThat(minimal().getEmbedding()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("getSphereId() defaults to null")
        void getSphereId_defaultsToNull() { // GH-90000
            assertThat(minimal().getSphereId()).isNull(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Procedure-specific fields
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Procedure-specific fields")
    class ProcedureSpecificTests {

        @Test
        @DisplayName("Situation and action are stored correctly")
        void situationAndAction_storedCorrectly() { // GH-90000
            EnhancedProcedure p = minimal(); // GH-90000
            assertThat(p.getSituation()).isEqualTo(SITUATION); // GH-90000
            assertThat(p.getAction()).isEqualTo(ACTION); // GH-90000
        }

        @Test
        @DisplayName("Default useCount is 0")
        void defaultUseCount_isZero() { // GH-90000
            assertThat(minimal().getUseCount()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("Default version is 1")
        void defaultVersion_isOne() { // GH-90000
            assertThat(minimal().getVersion()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("Default successRate is 0.0")
        void defaultSuccessRate_isZero() { // GH-90000
            assertThat(minimal().getSuccessRate()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("Default steps list is empty")
        void defaultSteps_isEmpty() { // GH-90000
            assertThat(minimal().getSteps()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Default versionHistory is empty")
        void defaultVersionHistory_isEmpty() { // GH-90000
            assertThat(minimal().getVersionHistory()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Default prerequisites map is empty")
        void defaultPrerequisites_isEmpty() { // GH-90000
            assertThat(minimal().getPrerequisites()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Default environmentConstraints map is empty")
        void defaultEnvironmentConstraints_isEmpty() { // GH-90000
            assertThat(minimal().getEnvironmentConstraints()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Default learnedFromEpisodes is empty")
        void defaultLearnedFromEpisodes_isEmpty() { // GH-90000
            assertThat(minimal().getLearnedFromEpisodes()).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Enhanced fields — with steps and version history
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Enhanced fields — steps and version history")
    class EnhancedFieldTests {

        @Test
        @DisplayName("Steps are stored and accessible")
        void steps_storedAndAccessible() { // GH-90000
            ProcedureStep step = ProcedureStep.builder() // GH-90000
                    .ordinal(1) // GH-90000
                    .description("Check connectivity")
                    .build(); // GH-90000
            EnhancedProcedure p = base().steps(List.of(step)).build(); // GH-90000
            assertThat(p.getSteps()).hasSize(1); // GH-90000
            assertThat(p.getSteps().get(0).getOrdinal()).isEqualTo(1); // GH-90000
            assertThat(p.getSteps().get(0).getDescription()).isEqualTo("Check connectivity");
        }

        @Test
        @DisplayName("VersionHistory is stored and accessible")
        void versionHistory_storedAndAccessible() { // GH-90000
            ProcedureVersion v = ProcedureVersion.builder() // GH-90000
                    .version(1) // GH-90000
                    .steps(List.of()) // GH-90000
                    .successRate(0.95) // GH-90000
                    .usageCount(42) // GH-90000
                    .build(); // GH-90000
            EnhancedProcedure p = base().versionHistory(List.of(v)).build(); // GH-90000
            assertThat(p.getVersionHistory()).hasSize(1); // GH-90000
            assertThat(p.getVersionHistory().get(0).getSuccessRate()).isEqualTo(0.95); // GH-90000
        }

        @Test
        @DisplayName("Prerequisites map stores domain-specific constraints")
        void prerequisites_storedCorrectly() { // GH-90000
            Map<String, Object> prereqs = Map.of("retries", 3, "env", "production"); // GH-90000
            EnhancedProcedure p = base().prerequisites(prereqs).build(); // GH-90000
            assertThat(p.getPrerequisites()).containsKey("retries");
            assertThat(p.getPrerequisites()).containsKey("env");
        }

        @Test
        @DisplayName("Custom tenantId is stored correctly")
        void customTenantId_storedCorrectly() { // GH-90000
            EnhancedProcedure p = base().tenantId("acme-corp").build();
            assertThat(p.getTenantId()).isEqualTo("acme-corp");
        }

        @Test
        @DisplayName("Embedding can be set for semantic search")
        void embedding_storedWhenSet() { // GH-90000
            float[] emb = new float[]{0.1f, 0.2f, 0.3f};
            EnhancedProcedure p = base().embedding(emb).build(); // GH-90000
            assertThat(p.getEmbedding()).isEqualTo(emb); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ProcedureStep tests
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ProcedureStep - step definition")
    class ProcedureStepTests {

        @Test
        @DisplayName("Ordinal and description are stored correctly")
        void ordinalAndDescription_storedCorrectly() { // GH-90000
            ProcedureStep step = ProcedureStep.builder() // GH-90000
                    .ordinal(2) // GH-90000
                    .description("Restart service")
                    .build(); // GH-90000
            assertThat(step.getOrdinal()).isEqualTo(2); // GH-90000
            assertThat(step.getDescription()).isEqualTo("Restart service");
        }

        @Test
        @DisplayName("Default fallbackStep is 0 (no fallback)")
        void defaultFallbackStep_isZero() { // GH-90000
            ProcedureStep step = ProcedureStep.builder().ordinal(1).description("step").build();
            assertThat(step.getFallbackStep()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("ToolName and expectedOutcome are null by default")
        void optionalFields_nullByDefault() { // GH-90000
            ProcedureStep step = ProcedureStep.builder().ordinal(1).description("step").build();
            assertThat(step.getToolName()).isNull(); // GH-90000
            assertThat(step.getExpectedOutcome()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("ToolName is stored when provided")
        void toolName_storedWhenProvided() { // GH-90000
            ProcedureStep step = ProcedureStep.builder() // GH-90000
                    .ordinal(1).description("Run diagnostic")
                    .toolName("diagnostic-runner")
                    .expectedOutcome("Status: OK")
                    .build(); // GH-90000
            assertThat(step.getToolName()).isEqualTo("diagnostic-runner");
            assertThat(step.getExpectedOutcome()).isEqualTo("Status: OK");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private static EnhancedProcedure minimal() { // GH-90000
        return base().build(); // GH-90000
    }

    private static EnhancedProcedure.EnhancedProcedureBuilder base() { // GH-90000
        return EnhancedProcedure.builder() // GH-90000
                .id(TEST_ID) // GH-90000
                .situation(SITUATION) // GH-90000
                .action(ACTION); // GH-90000
    }
}
