/*
 * Copyright (c) 2025 Ghatana Technologies
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
 * @doc.purpose Unit tests for GateEvaluator lifecycle gate enforcement (3.3.5)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GateEvaluator Tests (3.3.5)")
class GateEvaluatorTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private GateEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new GateEvaluator();
    }

    // ─── YAML StageSpec factory ─────────────────────────────────────────────

    /**
     * Creates a {@link StageSpec} from YAML, preserving the production deserialization
     * path and avoiding the need for test-only constructors.
     */
    private StageSpec stage(
            String id,
            List<String> entryCriteria,
            List<String> exitCriteria,
            List<String> artifacts) {
        try {
            StringBuilder yaml = new StringBuilder();
            yaml.append("id: ").append(id).append('\n');
            yaml.append("name: Test Stage\n");
            if (!entryCriteria.isEmpty()) {
                yaml.append("entry_criteria:\n");
                entryCriteria.forEach(c -> yaml.append("  - \"").append(c).append("\"\n"));
            }
            if (!exitCriteria.isEmpty()) {
                yaml.append("exit_criteria:\n");
                exitCriteria.forEach(c -> yaml.append("  - \"").append(c).append("\"\n"));
            }
            if (!artifacts.isEmpty()) {
                yaml.append("artifacts:\n");
                artifacts.forEach(a -> yaml.append("  - ").append(a).append('\n'));
            }
            return YAML.readValue(yaml.toString(), StageSpec.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test StageSpec", e);
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
        void allCriteriaMetGateIsOpen() {
            StageSpec s = stage("intent", List.of(
                    "Business problem is clearly articulated",
                    "Initial stakeholders identified"
            ), List.of(), List.of());

            Map<String, Boolean> verdicts = Map.of(
                    "Business", true,
                    "stakeholders", true
            );

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, verdicts);

            assertThat(result.open()).isTrue();
            assertThat(result.satisfiedCount()).isEqualTo(2);
            assertThat(result.totalCount()).isEqualTo(2);
            assertThat(result.unmetCriteria()).isEmpty();
        }

        @Test
        @DisplayName("one criterion not met → gate is closed with unmet list")
        void oneCriterionNotMetGateIsClosed() {
            StageSpec s = stage("plan", List.of(
                    "Requirements document approved",
                    "Architecture review complete"
            ), List.of(), List.of());

            Map<String, Boolean> verdicts = Map.of(
                    "Requirements", true
                    // "Architecture" NOT in verdicts
            );

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, verdicts);

            assertThat(result.open()).isFalse();
            assertThat(result.satisfiedCount()).isEqualTo(1);
            assertThat(result.totalCount()).isEqualTo(2);
            assertThat(result.unmetCriteria()).hasSize(1);
            assertThat(result.unmetCriteria().get(0)).contains("Architecture");
        }

        @Test
        @DisplayName("criterion with verdict=false is not satisfied")
        void criterionWithFalseVerdictIsNotSatisfied() {
            StageSpec s = stage("design", List.of(
                    "Security review passed"
            ), List.of(), List.of());

            Map<String, Boolean> verdicts = Map.of("Security", false);

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, verdicts);

            assertThat(result.open()).isFalse();
            assertThat(result.unmetCriteria()).hasSize(1);
        }

        @Test
        @DisplayName("empty criteria list → gate trivially open")
        void emptyCriteriaListGateTriviallyOpen() {
            StageSpec s = stage("onboard", List.of(), List.of(), List.of());

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, Map.of());

            assertThat(result.open()).isTrue();
            assertThat(result.totalCount()).isZero();
            assertThat(result.satisfiedCount()).isZero();
        }

        @Test
        @DisplayName("verdict matching is case-insensitive")
        void verdictMatchingIsCaseInsensitive() {
            StageSpec s = stage("build", List.of(
                    "Unit tests passing with >= 80% coverage"
            ), List.of(), List.of());

            // Key is uppercase, criterion contains lowercase variants
            Map<String, Boolean> verdicts = Map.of("UNIT", true);

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, verdicts);

            assertThat(result.open()).isTrue();
        }

        @Test
        @DisplayName("empty verdicts map → no criteria satisfied")
        void emptyVerdictsNoCriteriaSatisfied() {
            StageSpec s = stage("review", List.of(
                    "Peer code review completed",
                    "Security scan passed"
            ), List.of(), List.of());

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, Map.of());

            assertThat(result.open()).isFalse();
            assertThat(result.satisfiedCount()).isZero();
            assertThat(result.unmetCriteria()).hasSize(2);
        }

        @Test
        @DisplayName("null verdicts throws NullPointerException")
        void nullVerdictsThrows() {
            StageSpec s = stage("any", List.of(), List.of(), List.of());
            assertThatThrownBy(() -> evaluator.evaluateEntry(s, null))
                    .isInstanceOf(NullPointerException.class);
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
        void allExitCriteriaMetGateOpen() {
            StageSpec s = stage("impl", List.of(), List.of(
                    "All acceptance tests green",
                    "Deployment runbook updated"
            ), List.of());

            Map<String, Boolean> verdicts = Map.of(
                    "acceptance", true,
                    "runbook", true
            );

            GateEvaluator.GateResult result = evaluator.evaluateExit(s, verdicts);

            assertThat(result.open()).isTrue();
            assertThat(result.satisfiedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("exit criterion not met → gate closed with reason")
        void exitCriterionNotMetGateClosed() {
            StageSpec s = stage("launch", List.of(), List.of(
                    "Rollback plan documented",
                    "Monitoring alerts configured"
            ), List.of());

            Map<String, Boolean> verdicts = Map.of("Rollback", true);

            GateEvaluator.GateResult result = evaluator.evaluateExit(s, verdicts);

            assertThat(result.open()).isFalse();
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
        void allArtifactsPresent() {
            StageSpec s = stage("release", List.of(), List.of(), List.of(
                    "release-notes.md",
                    "test-coverage-report.html"
            ));

            Set<String> available = Set.of(
                    "release-notes.md",
                    "test-coverage-report.html",
                    "extra-doc.pdf"   // extra artifact doesn't break anything
            );

            GateEvaluator.GateResult result = evaluator.evaluateArtifacts(s, available);

            assertThat(result.open()).isTrue();
            assertThat(result.satisfiedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("missing required artifact → gate closed listing missing ID")
        void missingArtifactGateClosed() {
            StageSpec s = stage("deploy", List.of(), List.of(), List.of(
                    "deployment-config.yaml",
                    "security-scan-report.json"
            ));

            Set<String> available = Set.of("deployment-config.yaml");

            GateEvaluator.GateResult result = evaluator.evaluateArtifacts(s, available);

            assertThat(result.open()).isFalse();
            assertThat(result.satisfiedCount()).isEqualTo(1);
            assertThat(result.unmetCriteria()).containsExactly("security-scan-report.json");
        }

        @Test
        @DisplayName("no required artifacts → gate trivially open")
        void noRequiredArtifactsGateTriviallyOpen() {
            StageSpec s = stage("learn", List.of(), List.of(), List.of());

            GateEvaluator.GateResult result = evaluator.evaluateArtifacts(s, Set.of());

            assertThat(result.open()).isTrue();
            assertThat(result.totalCount()).isZero();
        }

        @Test
        @DisplayName("null available artifacts throws NullPointerException")
        void nullAvailableArtifactsThrows() {
            StageSpec s = stage("any", List.of(), List.of(), List.of());
            assertThatThrownBy(() -> evaluator.evaluateArtifacts(s, null))
                    .isInstanceOf(NullPointerException.class);
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
        void isFullySatisfiedMatchesOpenFlag() {
            StageSpec s = stage("x", List.of("All done"), List.of(), List.of());
            Map<String, Boolean> met = Map.of("done", true);
            Map<String, Boolean> unmet = Map.of();

            GateEvaluator.GateResult open = evaluator.evaluateEntry(s, met);
            GateEvaluator.GateResult closed = evaluator.evaluateEntry(s, unmet);

            assertThat(open.isFullySatisfied()).isTrue();
            assertThat(closed.isFullySatisfied()).isFalse();
        }

        @Test
        @DisplayName("satisfactionRatio() is 1.0 for empty criteria")
        void satisfactionRatioIsOneForEmptyCriteria() {
            StageSpec s = stage("empty", List.of(), List.of(), List.of());
            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, Map.of());
            assertThat(result.satisfactionRatio()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("satisfactionRatio() is 0.5 for half satisfied")
        void satisfactionRatioIsHalfForHalfSatisfied() {
            StageSpec s = stage("half", List.of("First criterion", "Second criterion"), List.of(), List.of());
            Map<String, Boolean> verdicts = Map.of("First", true);

            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, verdicts);

            assertThat(result.satisfactionRatio()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("toString() includes open flag and count summary")
        void toStringIncludesKeyInfo() {
            StageSpec s = stage("log", List.of("Login required"), List.of(), List.of());
            GateEvaluator.GateResult result = evaluator.evaluateEntry(s, Map.of());
            String str = result.toString();
            assertThat(str).contains("open=false").contains("0/1");
        }
    }
}
