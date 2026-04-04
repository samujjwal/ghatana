/*
 * Copyright (c) 2026 Ghatana Inc.
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
        void implementsMemoryItem() {
            EnhancedProcedure p = minimal();
            assertThat(p).isInstanceOf(MemoryItem.class);
        }

        @Test
        @DisplayName("getId() returns the supplied id")
        void getId_returnsSuppliedId() {
            assertThat(minimal().getId()).isEqualTo(TEST_ID);
        }

        @Test
        @DisplayName("getType() is PROCEDURE by default")
        void getType_isProcedureByDefault() {
            assertThat(minimal().getType()).isEqualTo(MemoryItemType.PROCEDURE);
        }

        @Test
        @DisplayName("getTenantId() defaults to 'default'")
        void getTenantId_defaultsToDefault() {
            assertThat(minimal().getTenantId()).isEqualTo("default");
        }

        @Test
        @DisplayName("getClassification() defaults to 'INTERNAL'")
        void getClassification_defaultsToInternal() {
            assertThat(minimal().getClassification()).isEqualTo("INTERNAL");
        }

        @Test
        @DisplayName("getCreatedAt() and getUpdatedAt() are non-null and close to now")
        void createdAndUpdated_nonNullAndCloseToNow() {
            Instant before = Instant.now();
            EnhancedProcedure p = minimal();
            Instant after = Instant.now();
            assertThat(p.getCreatedAt()).isBetween(before, after);
            assertThat(p.getUpdatedAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("getLinks() defaults to empty list")
        void getLinks_defaultsToEmpty() {
            assertThat(minimal().getLinks()).isEmpty();
        }

        @Test
        @DisplayName("getLabels() defaults to empty map")
        void getLabels_defaultsToEmpty() {
            assertThat(minimal().getLabels()).isEmpty();
        }

        @Test
        @DisplayName("getEmbedding() defaults to null")
        void getEmbedding_defaultsToNull() {
            assertThat(minimal().getEmbedding()).isNull();
        }

        @Test
        @DisplayName("getSphereId() defaults to null")
        void getSphereId_defaultsToNull() {
            assertThat(minimal().getSphereId()).isNull();
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
        void situationAndAction_storedCorrectly() {
            EnhancedProcedure p = minimal();
            assertThat(p.getSituation()).isEqualTo(SITUATION);
            assertThat(p.getAction()).isEqualTo(ACTION);
        }

        @Test
        @DisplayName("Default useCount is 0")
        void defaultUseCount_isZero() {
            assertThat(minimal().getUseCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Default version is 1")
        void defaultVersion_isOne() {
            assertThat(minimal().getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("Default successRate is 0.0")
        void defaultSuccessRate_isZero() {
            assertThat(minimal().getSuccessRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Default steps list is empty")
        void defaultSteps_isEmpty() {
            assertThat(minimal().getSteps()).isEmpty();
        }

        @Test
        @DisplayName("Default versionHistory is empty")
        void defaultVersionHistory_isEmpty() {
            assertThat(minimal().getVersionHistory()).isEmpty();
        }

        @Test
        @DisplayName("Default prerequisites map is empty")
        void defaultPrerequisites_isEmpty() {
            assertThat(minimal().getPrerequisites()).isEmpty();
        }

        @Test
        @DisplayName("Default environmentConstraints map is empty")
        void defaultEnvironmentConstraints_isEmpty() {
            assertThat(minimal().getEnvironmentConstraints()).isEmpty();
        }

        @Test
        @DisplayName("Default learnedFromEpisodes is empty")
        void defaultLearnedFromEpisodes_isEmpty() {
            assertThat(minimal().getLearnedFromEpisodes()).isEmpty();
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
        void steps_storedAndAccessible() {
            ProcedureStep step = ProcedureStep.builder()
                    .ordinal(1)
                    .description("Check connectivity")
                    .build();
            EnhancedProcedure p = base().steps(List.of(step)).build();
            assertThat(p.getSteps()).hasSize(1);
            assertThat(p.getSteps().get(0).getOrdinal()).isEqualTo(1);
            assertThat(p.getSteps().get(0).getDescription()).isEqualTo("Check connectivity");
        }

        @Test
        @DisplayName("VersionHistory is stored and accessible")
        void versionHistory_storedAndAccessible() {
            ProcedureVersion v = ProcedureVersion.builder()
                    .version(1)
                    .steps(List.of())
                    .successRate(0.95)
                    .usageCount(42)
                    .build();
            EnhancedProcedure p = base().versionHistory(List.of(v)).build();
            assertThat(p.getVersionHistory()).hasSize(1);
            assertThat(p.getVersionHistory().get(0).getSuccessRate()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("Prerequisites map stores domain-specific constraints")
        void prerequisites_storedCorrectly() {
            Map<String, Object> prereqs = Map.of("retries", 3, "env", "production");
            EnhancedProcedure p = base().prerequisites(prereqs).build();
            assertThat(p.getPrerequisites()).containsKey("retries");
            assertThat(p.getPrerequisites()).containsKey("env");
        }

        @Test
        @DisplayName("Custom tenantId is stored correctly")
        void customTenantId_storedCorrectly() {
            EnhancedProcedure p = base().tenantId("acme-corp").build();
            assertThat(p.getTenantId()).isEqualTo("acme-corp");
        }

        @Test
        @DisplayName("Embedding can be set for semantic search")
        void embedding_storedWhenSet() {
            float[] emb = new float[]{0.1f, 0.2f, 0.3f};
            EnhancedProcedure p = base().embedding(emb).build();
            assertThat(p.getEmbedding()).isEqualTo(emb);
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
        void ordinalAndDescription_storedCorrectly() {
            ProcedureStep step = ProcedureStep.builder()
                    .ordinal(2)
                    .description("Restart service")
                    .build();
            assertThat(step.getOrdinal()).isEqualTo(2);
            assertThat(step.getDescription()).isEqualTo("Restart service");
        }

        @Test
        @DisplayName("Default fallbackStep is 0 (no fallback)")
        void defaultFallbackStep_isZero() {
            ProcedureStep step = ProcedureStep.builder().ordinal(1).description("step").build();
            assertThat(step.getFallbackStep()).isEqualTo(0);
        }

        @Test
        @DisplayName("ToolName and expectedOutcome are null by default")
        void optionalFields_nullByDefault() {
            ProcedureStep step = ProcedureStep.builder().ordinal(1).description("step").build();
            assertThat(step.getToolName()).isNull();
            assertThat(step.getExpectedOutcome()).isNull();
        }

        @Test
        @DisplayName("ToolName is stored when provided")
        void toolName_storedWhenProvided() {
            ProcedureStep step = ProcedureStep.builder()
                    .ordinal(1).description("Run diagnostic")
                    .toolName("diagnostic-runner")
                    .expectedOutcome("Status: OK")
                    .build();
            assertThat(step.getToolName()).isEqualTo("diagnostic-runner");
            assertThat(step.getExpectedOutcome()).isEqualTo("Status: OK");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private static EnhancedProcedure minimal() {
        return base().build();
    }

    private static EnhancedProcedure.EnhancedProcedureBuilder base() {
        return EnhancedProcedure.builder()
                .id(TEST_ID)
                .situation(SITUATION)
                .action(ACTION);
    }
}
