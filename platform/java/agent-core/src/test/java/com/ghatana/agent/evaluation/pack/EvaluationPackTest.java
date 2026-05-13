/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation.pack;

import com.ghatana.agent.evaluation.EvaluationType;
import com.ghatana.agent.mastery.VersionScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EvaluationPack subpackage — unit tests")
class EvaluationPackTest {

    private static final String TENANT = "tenant-123";
    private static final String SKILL = "react-router:7.x";
    private static final VersionScope VERSION_SCOPE = VersionScope.empty();

    // ── EvaluationCase ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("EvaluationCase")
    class EvaluationCaseTests {

        @Test
        @DisplayName("standard() factory creates a non-required case with weight 1")
        void standardCaseDefaults() {
            EvaluationCase c = EvaluationCase.standard(
                    "c-1", "My case", "desc",
                    EvaluationType.REGRESSION, "input", "output");

            assertThat(c.caseId()).isEqualTo("c-1");
            assertThat(c.weight()).isEqualTo(1);
            assertThat(c.required()).isFalse();
            assertThat(c.rationale()).isNull();
            assertThat(c.context()).isEmpty();
        }

        @Test
        @DisplayName("required() factory creates a required case")
        void requiredCaseFlag() {
            EvaluationCase c = EvaluationCase.required(
                    "c-2", "Safety case", "desc",
                    EvaluationType.SAFETY, "input", "output", "must always pass");

            assertThat(c.required()).isTrue();
            assertThat(c.rationale()).isEqualTo("must always pass");
        }

        @Test
        @DisplayName("weight below 1 is rejected")
        void invalidWeightRejected() {
            assertThatThrownBy(() ->
                    new EvaluationCase("id", "n", "d", EvaluationType.INTEGRATION,
                            "in", "out", Map.of(), 0, false, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("weight");
        }

        @Test
        @DisplayName("null caseId is rejected")
        void nullCaseIdRejected() {
            assertThatThrownBy(() ->
                    new EvaluationCase(null, "n", "d", EvaluationType.REGRESSION,
                            "in", "out", Map.of(), 1, false, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── EvaluationPack ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("EvaluationPack")
    class EvaluationPackTests {

        private EvaluationCase regressionCase() {
            return EvaluationCase.required("r-1", "Regression", "desc",
                    EvaluationType.REGRESSION, "input", "output", "required regression");
        }

        private EvaluationCase safetyCase() {
            return EvaluationCase.standard("s-1", "Safety", "desc",
                    EvaluationType.SAFETY, "input", "output");
        }

        @Test
        @DisplayName("totalWeight sums all case weights")
        void totalWeightSumsCases() {
            EvaluationCase w2 = new EvaluationCase("c-a", "n", "d", EvaluationType.SKILL_UNIT,
                    "in", "out", Map.of(), 2, false, null);
            EvaluationCase w3 = new EvaluationCase("c-b", "n", "d", EvaluationType.INTEGRATION,
                    "in", "out", Map.of(), 3, false, null);

            EvaluationPack pack = new EvaluationPack(
                    "pack-1", TENANT, SKILL, "7.0.0", VERSION_SCOPE,
                    List.of(w2, w3), List.of(), 0.8, false, false);

            assertThat(pack.totalWeight()).isEqualTo(5);
        }

        @Test
        @DisplayName("hasCategoryOf returns true when matching case present")
        void hasCategoryOfFindsMatch() {
            EvaluationPack pack = new EvaluationPack(
                    "pack-2", TENANT, SKILL, "7.0.0", VERSION_SCOPE,
                    List.of(regressionCase(), safetyCase()), List.of(), 0.9, true, true);

            assertThat(pack.hasCategoryOf(EvaluationType.REGRESSION)).isTrue();
            assertThat(pack.hasCategoryOf(EvaluationType.SAFETY)).isTrue();
            assertThat(pack.hasCategoryOf(EvaluationType.ROLLBACK_RECOVERY)).isFalse();
        }

        @Test
        @DisplayName("minPassRate outside [0.0, 1.0] is rejected")
        void invalidMinPassRateRejected() {
            assertThatThrownBy(() ->
                    new EvaluationPack("id", TENANT, SKILL, "v", VERSION_SCOPE,
                            List.of(), List.of(), 1.5, false, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minPassRate");
        }

        @Test
        @DisplayName("cases list is defensively copied")
        void casesListIsImmutable() {
            List<EvaluationCase> mutable = new java.util.ArrayList<>();
            mutable.add(regressionCase());
            EvaluationPack pack = new EvaluationPack(
                    "pack-3", TENANT, SKILL, "7.0.0", VERSION_SCOPE,
                    mutable, List.of(), 0.8, false, false);

            mutable.clear();
            assertThat(pack.cases()).hasSize(1);
        }
    }

    // ── EvaluationRun ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("EvaluationRun")
    class EvaluationRunTests {

        @Test
        @DisplayName("started() creates an IN_PROGRESS run with null completedAt")
        void startedRunIsInProgress() {
            EvaluationRun run = EvaluationRun.started(
                    "run-1", "pack-1", TENANT, "agent-1", "release-1", "system");

            assertThat(run.status()).isEqualTo(EvaluationRunStatus.IN_PROGRESS);
            assertThat(run.completedAt()).isNull();
            assertThat(run.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("PASSED status is terminal")
        void passedIsTerminal() {
            EvaluationRun run = new EvaluationRun(
                    "run-2", "pack-1", TENANT, "agent-1", "release-1",
                    Instant.now(), Instant.now(), EvaluationRunStatus.PASSED, "system");

            assertThat(run.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("ABORTED status is terminal")
        void abortedIsTerminal() {
            EvaluationRun run = new EvaluationRun(
                    "run-3", "pack-1", TENANT, "agent-1", "release-1",
                    Instant.now(), Instant.now(), EvaluationRunStatus.ABORTED, "system");

            assertThat(run.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("IN_PROGRESS status is not terminal")
        void inProgressIsNotTerminal() {
            EvaluationRun run = EvaluationRun.started(
                    "run-4", "pack-1", TENANT, "agent-1", "release-1", "user");

            assertThat(run.isTerminal()).isFalse();
        }
    }

    // ── EvaluationRunResult ─────────────────────────────────────────────────

    @Nested
    @DisplayName("EvaluationRunResult")
    class EvaluationRunResultTests {

        @Test
        @DisplayName("passed() returns true when all constraints satisfied")
        void passedWhenAllConstraintsMet() {
            EvaluationRunResult result = new EvaluationRunResult(
                    "run-1", "pack-1", TENANT,
                    5, 5, 0, 1.0, true, true, true,
                    List.of(), Map.of("regression_passed", "true"), Instant.now());

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("passed() returns false when safetyPassed is false")
        void notPassedWhenSafetyFails() {
            EvaluationRunResult result = new EvaluationRunResult(
                    "run-2", "pack-1", TENANT,
                    5, 5, 0, 1.0, true, true, false,
                    List.of(), Map.of(), Instant.now());

            assertThat(result.passed()).isFalse();
        }

        @Test
        @DisplayName("passed() returns false when minPassRate not met")
        void notPassedWhenMinPassRateNotMet() {
            EvaluationRunResult result = new EvaluationRunResult(
                    "run-3", "pack-1", TENANT,
                    5, 3, 2, 0.6, false, true, true,
                    List.of(), Map.of(), Instant.now());

            assertThat(result.passed()).isFalse();
        }

        @Test
        @DisplayName("CaseResult.pass() creates a passing result")
        void caseResultPassFactory() {
            EvaluationRunResult.CaseResult r = EvaluationRunResult.CaseResult.pass(
                    "c-1", "My case", "output", 42L, true);

            assertThat(r.passed()).isTrue();
            assertThat(r.errorMessage()).isEmpty();
            assertThat(r.durationMs()).isEqualTo(42L);
            assertThat(r.required()).isTrue();
        }

        @Test
        @DisplayName("CaseResult.fail() creates a failing result with error message")
        void caseResultFailFactory() {
            EvaluationRunResult.CaseResult r = EvaluationRunResult.CaseResult.fail(
                    "c-2", "Bad case", "partial", "timeout", 100L, false);

            assertThat(r.passed()).isFalse();
            assertThat(r.errorMessage()).isEqualTo("timeout");
        }

        @Test
        @DisplayName("passRate outside [0.0, 1.0] is rejected")
        void invalidPassRateRejected() {
            assertThatThrownBy(() ->
                    new EvaluationRunResult("r", "p", TENANT, 1, 1, 0,
                            1.5, true, true, true, List.of(), Map.of(), Instant.now()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
