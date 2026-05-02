/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        repo = new InMemoryEvaluationResultRepository(); 
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private EvaluationResult passingResult(String evalId) { 
        return EvaluationResult.of(evalId, RELEASE_ID, TENANT_ID, "llm-judge", 0.95, true, NOW); 
    }

    private EvaluationResult failingResult(String evalId) { 
        return EvaluationResult.of(evalId, RELEASE_ID, TENANT_ID, "rubric", 0.40, false, NOW); 
    }

    // ─────────────────── EvaluationResult record validation ──────────────────

    @Nested
    @DisplayName("EvaluationResult validation")
    class RecordValidation {

        @Test
        @DisplayName("factory method creates valid record")
        void factoryCreatesValidRecord() { 
            EvaluationResult r = passingResult("eval-1");
            assertThat(r.evaluationId()).isEqualTo("eval-1");
            assertThat(r.agentReleaseId()).isEqualTo(RELEASE_ID); 
            assertThat(r.tenantId()).isEqualTo(TENANT_ID); 
            assertThat(r.evaluatorType()).isEqualTo("llm-judge");
            assertThat(r.score()).isEqualTo(0.95); 
            assertThat(r.passed()).isTrue(); 
            assertThat(r.data()).isEmpty(); 
        }

        @Test
        @DisplayName("score below 0.0 is rejected")
        void scoreBelow0IsRejected() { 
            assertThatThrownBy(() -> 
                    EvaluationResult.of("e", RELEASE_ID, TENANT_ID, "judge", -0.01, false, NOW)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("score must be in [0.0, 1.0]");
        }

        @Test
        @DisplayName("score above 1.0 is rejected")
        void scoreAbove1IsRejected() { 
            assertThatThrownBy(() -> 
                    EvaluationResult.of("e", RELEASE_ID, TENANT_ID, "judge", 1.001, true, NOW)) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("blank evaluationId is rejected")
        void blankEvalIdRejected() { 
            assertThatThrownBy(() -> 
                    EvaluationResult.of("  ", RELEASE_ID, TENANT_ID, "judge", 0.5, true, NOW)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("evaluationId");
        }

        @Test
        @DisplayName("blank evaluatorType is rejected")
        void blankEvaluatorTypeRejected() { 
            assertThatThrownBy(() -> 
                    EvaluationResult.of("e", RELEASE_ID, TENANT_ID, "  ", 0.5, true, NOW)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("evaluatorType");
        }

        @Test
        @DisplayName("data map is made immutable on construction")
        void dataMapIsImmutable() { 
            Map<String, Object> mutable = new java.util.HashMap<>(); 
            mutable.put("foo", "bar"); 
            EvaluationResult r = new EvaluationResult( 
                    "e1", RELEASE_ID, TENANT_ID, "judge",
                    0.8, true, null, null, null, NOW, mutable);
            assertThatThrownBy(() -> r.data().put("baz", "qux")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }
    }

    // ─────────────────── Repository: save + findById ───────────────────────

    @Nested
    @DisplayName("save and findById")
    class SaveAndFindById {

        @Test
        @DisplayName("save returns the same instance")
        void saveReturnsSameInstance() { 
            EvaluationResult r = passingResult("eval-1");
            EvaluationResult saved = runPromise(() -> repo.save(r)); 
            assertThat(saved).isSameAs(r); 
        }

        @Test
        @DisplayName("findById returns saved result")
        void findByIdReturnsSaved() { 
            EvaluationResult r = passingResult("eval-1");
            runPromise(() -> repo.save(r)); 
            Optional<EvaluationResult> found = runPromise(() -> repo.findById("eval-1"));
            assertThat(found).isPresent().contains(r); 
        }

        @Test
        @DisplayName("findById returns empty for unknown ID")
        void findByIdEmptyForUnknown() { 
            Optional<EvaluationResult> result = runPromise(() -> repo.findById("unknown"));
            assertThat(result).isEmpty(); 
        }
    }

    // ─────────────────── Repository: findByRelease ─────────────────────────

    @Nested
    @DisplayName("findByRelease")
    class FindByRelease {

        @Test
        @DisplayName("returns all results for a release")
        void returnsAllForRelease() { 
            runPromise(() -> repo.save(passingResult("eval-1")));
            runPromise(() -> repo.save(failingResult("eval-2")));
            List<EvaluationResult> results = runPromise(() -> repo.findByRelease(RELEASE_ID, TENANT_ID)); 
            assertThat(results).hasSize(2); 
        }

        @Test
        @DisplayName("returns empty for unknown release")
        void emptyForUnknown() { 
            List<EvaluationResult> results = runPromise(() -> repo.findByRelease("no-such", TENANT_ID)); 
            assertThat(results).isEmpty(); 
        }

        @Test
        @DisplayName("findPassingByRelease returns only passed results")
        void findPassingReturnsOnlyPassed() { 
            runPromise(() -> repo.save(passingResult("eval-1")));
            runPromise(() -> repo.save(failingResult("eval-2")));
            List<EvaluationResult> passing = runPromise(() -> repo.findPassingByRelease(RELEASE_ID, TENANT_ID)); 
            assertThat(passing).hasSize(1); 
            assertThat(passing.getFirst().passed()).isTrue(); 
        }
    }

    // ─────────────────── Repository: countPassing ──────────────────────────

    @Nested
    @DisplayName("countPassing")
    class CountPassing {

        @Test
        @DisplayName("counts only passing results")
        void countsOnlyPassing() { 
            runPromise(() -> repo.save(passingResult("eval-1")));
            runPromise(() -> repo.save(passingResult("eval-2")));
            runPromise(() -> repo.save(failingResult("eval-3")));
            long count = runPromise(() -> repo.countPassing(RELEASE_ID, TENANT_ID)); 
            assertThat(count).isEqualTo(2L); 
        }

        @Test
        @DisplayName("count is zero when no results")
        void zeroWhenNone() { 
            long count = runPromise(() -> repo.countPassing(RELEASE_ID, TENANT_ID)); 
            assertThat(count).isZero(); 
        }
    }

    // ─────────────────── Repository: deleteByRelease ───────────────────────

    @Nested
    @DisplayName("deleteByRelease")
    class DeleteByRelease {

        @Test
        @DisplayName("deletes all results for a release and returns count")
        void deletesAllAndReturnsCount() { 
            runPromise(() -> repo.save(passingResult("eval-1")));
            runPromise(() -> repo.save(failingResult("eval-2")));
            long deleted = runPromise(() -> repo.deleteByRelease(RELEASE_ID, TENANT_ID)); 
            assertThat(deleted).isEqualTo(2L); 
            List<EvaluationResult> after = runPromise(() -> repo.findByRelease(RELEASE_ID, TENANT_ID)); 
            assertThat(after).isEmpty(); 
        }

        @Test
        @DisplayName("returns zero when nothing to delete")
        void zeroWhenNothingToDelete() { 
            long deleted = runPromise(() -> repo.deleteByRelease("no-such", TENANT_ID)); 
            assertThat(deleted).isZero(); 
        }
    }
}
