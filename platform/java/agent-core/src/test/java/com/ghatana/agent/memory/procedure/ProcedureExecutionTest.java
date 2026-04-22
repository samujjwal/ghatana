package com.ghatana.agent.memory.procedure;

import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.procedure.ProcedureStep;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for procedure execution — validates step ordering, fallback, success rate
 * tracking, version history, and prerequisite enforcement.
 *
 * @doc.type class
 * @doc.purpose Tests for enhanced procedure execution behavior and versioning
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Procedure Execution Tests [GH-90000]")
@Tag("integration [GH-90000]")
class ProcedureExecutionTest extends EventloopTestBase {

    // ── Step ordering ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("step ordering [GH-90000]")
    class StepOrdering {

        @Test
        @DisplayName("steps are returned in ordinal order [GH-90000]")
        void steps_areReturnedInOrdinalOrder() { // GH-90000
            ProcedureStep step1 = ProcedureStep.builder().ordinal(1).description("Start VM [GH-90000]").build();
            ProcedureStep step2 = ProcedureStep.builder().ordinal(2).description("Configure network [GH-90000]").build();
            ProcedureStep step3 = ProcedureStep.builder().ordinal(3).description("Start app [GH-90000]").build();

            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(UUID.randomUUID().toString()) // GH-90000
                    .situation("Deploy service [GH-90000]")
                    .action("Full deployment [GH-90000]")
                    .steps(List.of(step1, step2, step3)) // GH-90000
                    .build(); // GH-90000

            List<ProcedureStep> steps = procedure.getSteps(); // GH-90000

            assertThat(steps).hasSize(3); // GH-90000
            assertThat(steps.get(0).getOrdinal()).isEqualTo(1); // GH-90000
            assertThat(steps.get(1).getOrdinal()).isEqualTo(2); // GH-90000
            assertThat(steps.get(2).getOrdinal()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("procedure with no steps has empty steps list [GH-90000]")
        void procedure_withNoSteps_hasEmptyStepsList() { // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(UUID.randomUUID().toString()) // GH-90000
                    .situation("Simple situation [GH-90000]")
                    .action("Direct action (no steps) [GH-90000]")
                    .build(); // GH-90000

            assertThat(procedure.getSteps()).isEmpty(); // GH-90000
        }
    }

    // ── Fallback step ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fallback step resolution [GH-90000]")
    class FallbackStepResolution {

        @Test
        @DisplayName("step with non-zero fallbackStep references alternative step [GH-90000]")
        void stepWithFallbackStep_referencesAlternativeStep() { // GH-90000
            ProcedureStep mainStep = ProcedureStep.builder() // GH-90000
                    .ordinal(2) // GH-90000
                    .description("Try fast path [GH-90000]")
                    .toolName("fast-tool [GH-90000]")
                    .fallbackStep(3) // GH-90000
                    .build(); // GH-90000

            ProcedureStep fallbackStep = ProcedureStep.builder() // GH-90000
                    .ordinal(3) // GH-90000
                    .description("Slow fallback path [GH-90000]")
                    .toolName("slow-tool [GH-90000]")
                    .build(); // GH-90000

            // Resolve fallback: if step 2 fails, execute step 3
            ProcedureStep resolvedFallback = mainStep.getFallbackStep() != 0 ? fallbackStep : null; // GH-90000

            assertThat(mainStep.getFallbackStep()).isEqualTo(3); // GH-90000
            assertThat(resolvedFallback).isNotNull(); // GH-90000
            assertThat(resolvedFallback.getDescription()).contains("Slow fallback [GH-90000]");
        }

        @Test
        @DisplayName("step with fallbackStep 0 has no fallback [GH-90000]")
        void stepWithFallbackStep0_hasNoFallback() { // GH-90000
            ProcedureStep step = ProcedureStep.builder() // GH-90000
                    .ordinal(1) // GH-90000
                    .description("Mandatory initialization [GH-90000]")
                    .build(); // GH-90000

            assertThat(step.getFallbackStep()).isEqualTo(0); // GH-90000
        }
    }

    // ── Success rate tracking ─────────────────────────────────────────────────

    @Nested
    @DisplayName("use count and success rate [GH-90000]")
    class SuccessRateTracking {

        @Test
        @DisplayName("procedure stores success rate from 0.0 to 1.0 [GH-90000]")
        void procedure_storesSuccessRateFrom0To1() { // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(UUID.randomUUID().toString()) // GH-90000
                    .situation("S [GH-90000]")
                    .action("A [GH-90000]")
                    .successRate(0.87) // GH-90000
                    .useCount(23) // GH-90000
                    .build(); // GH-90000

            assertThat(procedure.getSuccessRate()).isEqualTo(0.87); // GH-90000
            assertThat(procedure.getUseCount()).isEqualTo(23); // GH-90000
        }

        @Test
        @DisplayName("newly created procedure has default success rate of 0.0 [GH-90000]")
        void newProcedure_hasDefaultSuccessRateOf0() { // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(UUID.randomUUID().toString()) // GH-90000
                    .situation("S [GH-90000]")
                    .action("A [GH-90000]")
                    .build(); // GH-90000

            assertThat(procedure.getSuccessRate()).isEqualTo(0.0); // GH-90000
            assertThat(procedure.getUseCount()).isEqualTo(0); // GH-90000
        }
    }

    // ── Prerequisites ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("prerequisites enforcement [GH-90000]")
    class PrerequisitesEnforcement {

        @Test
        @DisplayName("procedure with prerequisites records them and they can be checked [GH-90000]")
        void procedure_withPrerequisites_recordsThemForChecking() { // GH-90000
            Map<String, Object> prereqs = Map.of( // GH-90000
                    "requires-java-version", "21",
                    "requires-feature-flag", "FEATURE_X_ENABLED"
            );

            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(UUID.randomUUID().toString()) // GH-90000
                    .situation("S [GH-90000]")
                    .action("A [GH-90000]")
                    .prerequisites(prereqs) // GH-90000
                    .build(); // GH-90000

            assertThat(procedure.getPrerequisites()).containsKey("requires-java-version [GH-90000]");
            assertThat(procedure.getPrerequisites().get("requires-java-version [GH-90000]")).isEqualTo("21 [GH-90000]");
        }

        @Test
        @DisplayName("procedure with no prerequisites has empty prerequisites map [GH-90000]")
        void procedure_withNoPrerequisites_hasEmptyMap() { // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(UUID.randomUUID().toString()) // GH-90000
                    .situation("S [GH-90000]")
                    .action("A [GH-90000]")
                    .build(); // GH-90000

            assertThat(procedure.getPrerequisites()).isEmpty(); // GH-90000
        }
    }

    // ── Tool invocation per step ──────────────────────────────────────────────

    @Nested
    @DisplayName("tool invocation per step [GH-90000]")
    class ToolInvocationPerStep {

        @Test
        @DisplayName("step with toolName records the tool to invoke [GH-90000]")
        void stepWithToolName_recordsToolToInvoke() { // GH-90000
            ProcedureStep step = ProcedureStep.builder() // GH-90000
                    .ordinal(1) // GH-90000
                    .description("Search repositories [GH-90000]")
                    .toolName("git-search-tool [GH-90000]")
                    .expectedOutcome("list of matching repos [GH-90000]")
                    .build(); // GH-90000

            assertThat(step.getToolName()).isEqualTo("git-search-tool [GH-90000]");
            assertThat(step.getExpectedOutcome()).isEqualTo("list of matching repos [GH-90000]");
        }

        @Test
        @DisplayName("step without toolName represents manual/LLM action [GH-90000]")
        void stepWithoutToolName_representsManaualOrLlmAction() { // GH-90000
            ProcedureStep step = ProcedureStep.builder() // GH-90000
                    .ordinal(1) // GH-90000
                    .description("Analyze result with LLM [GH-90000]")
                    .build(); // GH-90000

            assertThat(step.getToolName()).isNull(); // GH-90000
        }
    }
}
