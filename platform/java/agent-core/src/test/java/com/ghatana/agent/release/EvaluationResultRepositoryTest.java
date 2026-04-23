/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.release;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link EvaluationResult} record validation and
 * {@link InMemoryEvaluationResultRepository} contract behaviour.
 *
 * @doc.type class
 * @doc.purpose EvaluationResult + InMemoryEvaluationResultRepository tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("EvaluationResult + InMemoryEvaluationResultRepository")
class EvaluationResultRepositoryTest extends EventloopTestBase {

    private static final String RELEASE_ID = "rel-001";
    private static final String TENANT_ID  = "tenant-test";
    private static final Instant NOW       = Instant.parse("2026-04-01T10:00:00Z");

    private InMemoryEvaluationResultRepository repo;

    @BeforeEach
    void setUp() { // GH-90000
        repo = new InMemoryEvaluationResultRepository(); // GH-90000
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private EvaluationResult passingResult(String evalId) { // GH-90000
        return EvaluationResult.of(evalId, RELEASE_ID, TENANT_ID, "llm-judge", 0.95, true, NOW); // GH-90000
    }

    private EvaluationResult failingResult(String evalId) { // GH-90000
        return EvaluationResult.of(evalId, RELEASE_ID, TENANT_ID, "rubric", 0.40, false, NOW); // GH-90000
    }

    // ─────────────────── EvaluationResult record validation ──────────────────

    @Nested
    @DisplayName("EvaluationResult validation")
    class RecordValidation {

        @Test
        @DisplayName("factory method creates valid record")
        void factoryCreatesValidRecord() { // GH-90000
            EvaluationResult r = passingResult("eval-1");
            assertThat(r.evaluationId()).isEqualTo("eval-1");
            assertThat(r.agentReleaseId()).isEqualTo(RELEASE_ID); // GH-90000
            assertThat(r.tenantId()).isEqualTo(TENANT_ID); // GH-90000
            assertThat(r.evaluatorType()).isEqualTo("llm-judge");
            assertThat(r.score()).isEqualTo(0.95); // GH-90000
            assertThat(r.passed()).isTrue(); // GH-90000
            assertThat(r.data()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("score below 0.0 is rejected")
        void scoreBelow0IsRejected() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    EvaluationResult.of("e", RELEASE_ID, TENANT_ID, "judge", -0.01, false, NOW)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("score must be in [0.0, 1.0]");
        }

        @Test
        @DisplayName("score above 1.0 is rejected")
        void scoreAbove1IsRejected() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    EvaluationResult.of("e", RELEASE_ID, TENANT_ID, "judge", 1.001, true, NOW)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("blank evaluationId is rejected")
        void blankEvalIdRejected() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    EvaluationResult.of("  ", RELEASE_ID, TENANT_ID, "judge", 0.5, true, NOW)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("evaluationId");
        }

        @Test
        @DisplayName("blank evaluatorType is rejected")
        void blankEvaluatorTypeRejected() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    EvaluationResult.of("e", RELEASE_ID, TENANT_ID, "  ", 0.5, true, NOW)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("evaluatorType");
        }

        @Test
        @DisplayName("data map is made immutable on construction")
        void dataMapIsImmutable() { // GH-90000
            Map<String, Object> mutable = new java.util.HashMap<>(); // GH-90000
            mutable.put("foo", "bar"); // GH-90000
            EvaluationResult r = new EvaluationResult( // GH-90000
                    "e1", RELEASE_ID, TENANT_ID, "judge",
                    0.8, true, null, null, null, NOW, mutable);
            assertThatThrownBy(() -> r.data().put("baz", "qux")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // ─────────────────── Repository: save + findById ───────────────────────

    @Nested
    @DisplayName("save and findById")
    class SaveAndFindById {

        @Test
        @DisplayName("save returns the same instance")
        void saveReturnsSameInstance() { // GH-90000
            EvaluationResult r = passingResult("eval-1");
            EvaluationResult saved = runPromise(() -> repo.save(r)); // GH-90000
            assertThat(saved).isSameAs(r); // GH-90000
        }

        @Test
        @DisplayName("findById returns saved result")
        void findByIdReturnsSaved() { // GH-90000
            EvaluationResult r = passingResult("eval-1");
            runPromise(() -> repo.save(r)); // GH-90000
            Optional<EvaluationResult> found = runPromise(() -> repo.findById("eval-1"));
            assertThat(found).isPresent().contains(r); // GH-90000
        }

        @Test
        @DisplayName("findById returns empty for unknown ID")
        void findByIdEmptyForUnknown() { // GH-90000
            Optional<EvaluationResult> result = runPromise(() -> repo.findById("unknown"));
            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // ─────────────────── Repository: findByRelease ─────────────────────────

    @Nested
    @DisplayName("findByRelease")
    class FindByRelease {

        @Test
        @DisplayName("returns all results for a release")
        void returnsAllForRelease() { // GH-90000
            runPromise(() -> repo.save(passingResult("eval-1")));
            runPromise(() -> repo.save(failingResult("eval-2")));
            List<EvaluationResult> results = runPromise(() -> repo.findByRelease(RELEASE_ID, TENANT_ID)); // GH-90000
            assertThat(results).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("returns empty for unknown release")
        void emptyForUnknown() { // GH-90000
            List<EvaluationResult> results = runPromise(() -> repo.findByRelease("no-such", TENANT_ID)); // GH-90000
            assertThat(results).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findPassingByRelease returns only passed results")
        void findPassingReturnsOnlyPassed() { // GH-90000
            runPromise(() -> repo.save(passingResult("eval-1")));
            runPromise(() -> repo.save(failingResult("eval-2")));
            List<EvaluationResult> passing = runPromise(() -> repo.findPassingByRelease(RELEASE_ID, TENANT_ID)); // GH-90000
            assertThat(passing).hasSize(1); // GH-90000
            assertThat(passing.getFirst().passed()).isTrue(); // GH-90000
        }
    }

    // ─────────────────── Repository: countPassing ──────────────────────────

    @Nested
    @DisplayName("countPassing")
    class CountPassing {

        @Test
        @DisplayName("counts only passing results")
        void countsOnlyPassing() { // GH-90000
            runPromise(() -> repo.save(passingResult("eval-1")));
            runPromise(() -> repo.save(passingResult("eval-2")));
            runPromise(() -> repo.save(failingResult("eval-3")));
            long count = runPromise(() -> repo.countPassing(RELEASE_ID, TENANT_ID)); // GH-90000
            assertThat(count).isEqualTo(2L); // GH-90000
        }

        @Test
        @DisplayName("count is zero when no results")
        void zeroWhenNone() { // GH-90000
            long count = runPromise(() -> repo.countPassing(RELEASE_ID, TENANT_ID)); // GH-90000
            assertThat(count).isZero(); // GH-90000
        }
    }

    // ─────────────────── Repository: deleteByRelease ───────────────────────

    @Nested
    @DisplayName("deleteByRelease")
    class DeleteByRelease {

        @Test
        @DisplayName("deletes all results for a release and returns count")
        void deletesAllAndReturnsCount() { // GH-90000
            runPromise(() -> repo.save(passingResult("eval-1")));
            runPromise(() -> repo.save(failingResult("eval-2")));
            long deleted = runPromise(() -> repo.deleteByRelease(RELEASE_ID, TENANT_ID)); // GH-90000
            assertThat(deleted).isEqualTo(2L); // GH-90000
            List<EvaluationResult> after = runPromise(() -> repo.findByRelease(RELEASE_ID, TENANT_ID)); // GH-90000
            assertThat(after).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns zero when nothing to delete")
        void zeroWhenNothingToDelete() { // GH-90000
            long deleted = runPromise(() -> repo.deleteByRelease("no-such", TENANT_ID)); // GH-90000
            assertThat(deleted).isZero(); // GH-90000
        }
    }
}
