/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GateEvaluator} — covers plan item 3.3.5.
 *
 * <p>Verifies all criterion types:
 * <ul>
 *   <li>Entry criteria evaluation against keyword-matching verdicts</li>
 *   <li>Exit criteria evaluation</li>
 *   <li>Artifact presence evaluation</li>
 *   <li>Trivially-open gate for stages with no criteria</li>
 *   <li>{@link GateEvaluator.GateResult} helper methods</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit tests for GateEvaluator lifecycle gate enforcement (3.3.5) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GateEvaluator Tests (3.3.5)")
class GateEvaluatorTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()); // GH-90000

    private GateEvaluator evaluator;

    @BeforeEach
    void setUp() { // GH-90000
        evaluator = new GateEvaluator(); // GH-90000
    }

    // ─── YAML StageSpec factory ─────────────────────────────────────────────

    /**
     * Creates a {@link StageSpec} from YAML, preserving the production deserialization
     * path and avoiding the need for test-only constructors.
     */
    private StageSpec stage( // GH-90000
            String id,
            List<String> entryCriteria,
            List<String> exitCriteria,
            List<String> artifacts) {
        try {
            StringBuilder yaml = new StringBuilder(); // GH-90000
            yaml.append("id: ").append(id).append('\n');
            yaml.append("name: Test Stage\n");
            if (!entryCriteria.isEmpty()) { // GH-90000
                yaml.append("entry_criteria:\n");
                entryCriteria.forEach(c -> yaml.append("  - \"").append(c).append("\"\n")); // GH-90000
            }
            if (!exitCriteria.isEmpty()) { // GH-90000
                yaml.append("exit_criteria:\n");
                exitCriteria.forEach(c -> yaml.append("  - \"").append(c).append("\"\n")); // GH-90000
            }
            if (!artifacts.isEmpty()) { // GH-90000
                yaml.append("artifacts:\n");
                artifacts.forEach(a -> yaml.append("  - ").append(a).append('\n'));
            }
            return YAML.readValue(yaml.toString(), StageSpec.class); // GH-90000
        } catch (IOException e) { // GH-90000
            throw new RuntimeException("Failed to create test StageSpec", e); // GH-90000
        }
    }

    // =========================================================================
    // Entry Criteria Tests
    // =========================================================================

    @Nested
    @DisplayName("Entry criteria evaluation")
    class EntryCriteriaTests {

        @Test
        @DisplayName("all criteria met → gate is open")
        void allCriteriaMetGateIsOpen() { // GH-90000
            StageSpec s = stage("intent", List.of( // GH-90000
                    "Business problem is clearly articulated",
                    "Initial stakeholders identified"
            ), List.of(), List.of()); // GH-90000

            Map<String, Boolean> verdicts = Map.of( // GH-90000
                    "Business", true,
                    "stakeholders", true
            );

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, verdicts); // GH-90000

            assertThat(result.open()).isTrue(); // GH-90000
            assertThat(result.satisfiedCount()).isEqualTo(2); // GH-90000
            assertThat(result.totalCount()).isEqualTo(2); // GH-90000
            assertThat(result.unmetCriteria()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("one criterion not met → gate is closed with unmet list")
        void oneCriterionNotMetGateIsClosed() { // GH-90000
            StageSpec s = stage("plan", List.of( // GH-90000
                    "Requirements document approved",
                    "Architecture review complete"
            ), List.of(), List.of()); // GH-90000

            Map<String, Boolean> verdicts = Map.of( // GH-90000
                    "Requirements", true
                    // "Architecture" NOT in verdicts
            );

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, verdicts); // GH-90000

            assertThat(result.open()).isFalse(); // GH-90000
            assertThat(result.satisfiedCount()).isEqualTo(1); // GH-90000
            assertThat(result.totalCount()).isEqualTo(2); // GH-90000
            assertThat(result.unmetCriteria()).hasSize(1); // GH-90000
            assertThat(result.unmetCriteria().get(0)).contains("Architecture");
        }

        @Test
        @DisplayName("criterion with verdict=false is not satisfied")
        void criterionWithFalseVerdictIsNotSatisfied() { // GH-90000
            StageSpec s = stage("design", List.of( // GH-90000
                    "Security review passed"
            ), List.of(), List.of()); // GH-90000

            Map<String, Boolean> verdicts = Map.of("Security", false); // GH-90000

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, verdicts); // GH-90000

            assertThat(result.open()).isFalse(); // GH-90000
            assertThat(result.unmetCriteria()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("empty criteria list → gate trivially open")
        void emptyCriteriaListGateTriviallyOpen() { // GH-90000
            StageSpec s = stage("onboard", List.of(), List.of(), List.of()); // GH-90000

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, Map.of()); // GH-90000

            assertThat(result.open()).isTrue(); // GH-90000
            assertThat(result.totalCount()).isZero(); // GH-90000
            assertThat(result.satisfiedCount()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("verdict matching is case-insensitive")
        void verdictMatchingIsCaseInsensitive() { // GH-90000
            StageSpec s = stage("build", List.of( // GH-90000
                    "Unit tests passing with >= 80% coverage"
            ), List.of(), List.of()); // GH-90000

            // Key is uppercase, criterion contains lowercase variants
            Map<String, Boolean> verdicts = Map.of("UNIT", true); // GH-90000

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, verdicts); // GH-90000

            assertThat(result.open()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("empty verdicts map → no criteria satisfied")
        void emptyVerdictsNoCriteriaSatisfied() { // GH-90000
            StageSpec s = stage("review", List.of( // GH-90000
                    "Peer code review completed",
                    "Security scan passed"
            ), List.of(), List.of()); // GH-90000

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, Map.of()); // GH-90000

            assertThat(result.open()).isFalse(); // GH-90000
            assertThat(result.satisfiedCount()).isZero(); // GH-90000
            assertThat(result.unmetCriteria()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("null verdicts throws NullPointerException")
        void nullVerdictsThrows() { // GH-90000
            StageSpec s = stage("any", List.of(), List.of(), List.of()); // GH-90000
            assertThatThrownBy(() -> evaluator.evaluateEntry(s, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // Exit Criteria Tests
    // =========================================================================

    @Nested
    @DisplayName("Exit criteria evaluation")
    class ExitCriteriaTests {

        @Test
        @DisplayName("all exit criteria met → gate is open")
        void allExitCriteriaMetGateOpen() { // GH-90000
            StageSpec s = stage("impl", List.of(), List.of( // GH-90000
                    "All acceptance tests green",
                    "Deployment runbook updated"
            ), List.of()); // GH-90000

            Map<String, Boolean> verdicts = Map.of( // GH-90000
                    "acceptance", true,
                    "runbook", true
            );

            GateEvaluator.GateResult result = evaluator.evaluateExit(s, verdicts); // GH-90000

            assertThat(result.open()).isTrue(); // GH-90000
            assertThat(result.satisfiedCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("exit criterion not met → gate closed with reason")
        void exitCriterionNotMetGateClosed() { // GH-90000
            StageSpec s = stage("launch", List.of(), List.of( // GH-90000
                    "Rollback plan documented",
                    "Monitoring alerts configured"
            ), List.of()); // GH-90000

            Map<String, Boolean> verdicts = Map.of("Rollback", true); // GH-90000

            GateEvaluator.GateResult result = evaluator.evaluateExit(s, verdicts); // GH-90000

            assertThat(result.open()).isFalse(); // GH-90000
            assertThat(result.unmetCriteria()).anyMatch(c -> c.toLowerCase().contains("monitoring"));
        }
    }

    // =========================================================================
    // Artifact Gate Tests
    // =========================================================================

    @Nested
    @DisplayName("Artifact gate evaluation")
    class ArtifactGateTests {

        @Test
        @DisplayName("all required artifacts present → gate open")
        void allArtifactsPresent() { // GH-90000
            StageSpec s = stage("release", List.of(), List.of(), List.of( // GH-90000
                    "release-notes.md",
                    "test-coverage-report.html"
            ));

            Set<String> available = Set.of( // GH-90000
                    "release-notes.md",
                    "test-coverage-report.html",
                    "extra-doc.pdf"   // extra artifact doesn't break anything
            );

            GateEvaluator.GateResult result = evaluator.evaluateArtifacts(s, available); // GH-90000

            assertThat(result.open()).isTrue(); // GH-90000
            assertThat(result.satisfiedCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("missing required artifact → gate closed listing missing ID")
        void missingArtifactGateClosed() { // GH-90000
            StageSpec s = stage("deploy", List.of(), List.of(), List.of( // GH-90000
                    "deployment-config.yaml",
                    "security-scan-report.json"
            ));

            Set<String> available = Set.of("deployment-config.yaml");

            GateEvaluator.GateResult result = evaluator.evaluateArtifacts(s, available); // GH-90000

            assertThat(result.open()).isFalse(); // GH-90000
            assertThat(result.satisfiedCount()).isEqualTo(1); // GH-90000
            assertThat(result.unmetCriteria()).containsExactly("security-scan-report.json");
        }

        @Test
        @DisplayName("no required artifacts → gate trivially open")
        void noRequiredArtifactsGateTriviallyOpen() { // GH-90000
            StageSpec s = stage("learn", List.of(), List.of(), List.of()); // GH-90000

            GateEvaluator.GateResult result = evaluator.evaluateArtifacts(s, Set.of()); // GH-90000

            assertThat(result.open()).isTrue(); // GH-90000
            assertThat(result.totalCount()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("null available artifacts throws NullPointerException")
        void nullAvailableArtifactsThrows() { // GH-90000
            StageSpec s = stage("any", List.of(), List.of(), List.of()); // GH-90000
            assertThatThrownBy(() -> evaluator.evaluateArtifacts(s, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // GateResult utility method tests
    // =========================================================================

    @Nested
    @DisplayName("GateResult utilities")
    class GateResultUtilityTests {

        @Test
        @DisplayName("isFullySatisfied() delegates to open flag")
        void isFullySatisfiedMatchesOpenFlag() { // GH-90000
            StageSpec s = stage("x", List.of("All done"), List.of(), List.of());
            Map<String, Boolean> met = Map.of("done", true); // GH-90000
            Map<String, Boolean> unmet = Map.of(); // GH-90000

            GateEvaluator.GateResult open = evaluator.evaluateEntry(s, met); // GH-90000
            GateEvaluator.GateResult closed = evaluator.evaluateEntry(s, unmet); // GH-90000

            assertThat(open.isFullySatisfied()).isTrue(); // GH-90000
            assertThat(closed.isFullySatisfied()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("satisfactionRatio() is 1.0 for empty criteria")
        void satisfactionRatioIsOneForEmptyCriteria() { // GH-90000
            StageSpec s = stage("empty", List.of(), List.of(), List.of()); // GH-90000
            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, Map.of()); // GH-90000
            assertThat(result.satisfactionRatio()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("satisfactionRatio() is 0.5 for half satisfied")
        void satisfactionRatioIsHalfForHalfSatisfied() { // GH-90000
            StageSpec s = stage("half", List.of("First criterion", "Second criterion"), List.of(), List.of()); // GH-90000
            Map<String, Boolean> verdicts = Map.of("First", true); // GH-90000

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, verdicts); // GH-90000

            assertThat(result.satisfactionRatio()).isEqualTo(0.5); // GH-90000
        }

        @Test
        @DisplayName("toString() includes open flag and count summary")
        void toStringIncludesKeyInfo() { // GH-90000
            StageSpec s = stage("log", List.of("Login required"), List.of(), List.of());
            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, Map.of()); // GH-90000
            String str = result.toString(); // GH-90000
            assertThat(str).contains("open=false").contains("0/1");
        }
    }
}
