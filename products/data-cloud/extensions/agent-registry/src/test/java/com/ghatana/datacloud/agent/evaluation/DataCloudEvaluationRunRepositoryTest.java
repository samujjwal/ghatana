/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.evaluation;

import com.ghatana.datacloud.agent.evaluation.DataCloudEvaluationRunRepository.EvaluationRun;
import com.ghatana.datacloud.agent.evaluation.DataCloudEvaluationRunRepository.RunState;
import com.ghatana.datacloud.agent.evaluation.DataCloudEvaluationRunRepository.TestCaseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DataCloudEvaluationRunRepository.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudEvaluationRunRepository
 * @doc.layer data-cloud
 * @doc.pattern Test
 */
@DisplayName("DataCloudEvaluationRunRepository Tests")
class DataCloudEvaluationRunRepositoryTest {

    @Test
    @DisplayName("Should save and retrieve evaluation run")
    void shouldSaveAndRetrieveEvaluationRun() {
        DataCloudEvaluationRunRepository repository = new DataCloudEvaluationRunRepository();

        Map<String, TestCaseResult> results = Map.of(
                "case-1", new TestCaseResult("case-1", true, 0.95, "Test passed", null)
        );

        EvaluationRun run = new EvaluationRun(
                "run-123",
                "pack-123",
                "skill-123",
                "agent-123",
                "release-1.0.0",
                RunState.COMPLETED,
                Instant.now(),
                Instant.now(),
                results,
                Instant.now(),
                Instant.now(),
                "system"
        );

        repository.save(run).await();
        Optional<EvaluationRun> result = repository.findById("run-123").await();

        assertThat(result).isPresent();
        assertThat(result.get().runId()).isEqualTo("run-123");
        assertThat(result.get().state()).isEqualTo(RunState.COMPLETED);
    }

    @Test
    @DisplayName("Should find evaluation runs by pack ID")
    void shouldFindByPackId() {
        DataCloudEvaluationRunRepository repository = new DataCloudEvaluationRunRepository();

        Map<String, TestCaseResult> results = Map.of(
                "case-1", new TestCaseResult("case-1", true, 0.95, "Test passed", null)
        );

        EvaluationRun run1 = new EvaluationRun(
                "run-1", "pack-123", "skill-123", "agent-123", "release-1.0.0",
                RunState.COMPLETED, Instant.now(), Instant.now(), results,
                Instant.now(), Instant.now(), "system"
        );

        EvaluationRun run2 = new EvaluationRun(
                "run-2", "pack-456", "skill-456", "agent-123", "release-1.0.0",
                RunState.COMPLETED, Instant.now(), Instant.now(), results,
                Instant.now(), Instant.now(), "system"
        );

        repository.save(run1).await();
        repository.save(run2).await();

        List<EvaluationRun> resultsList = repository.findByPackId("pack-123").await();

        assertThat(resultsList).hasSize(1);
        assertThat(resultsList.get(0).packId()).isEqualTo("pack-123");
    }

    @Test
    @DisplayName("Should find evaluation runs by skill ID")
    void shouldFindBySkillId() {
        DataCloudEvaluationRunRepository repository = new DataCloudEvaluationRunRepository();

        Map<String, TestCaseResult> results = Map.of(
                "case-1", new TestCaseResult("case-1", true, 0.95, "Test passed", null)
        );

        EvaluationRun run1 = new EvaluationRun(
                "run-1", "pack-123", "skill-123", "agent-123", "release-1.0.0",
                RunState.COMPLETED, Instant.now(), Instant.now(), results,
                Instant.now(), Instant.now(), "system"
        );

        EvaluationRun run2 = new EvaluationRun(
                "run-2", "pack-456", "skill-456", "agent-123", "release-1.0.0",
                RunState.COMPLETED, Instant.now(), Instant.now(), results,
                Instant.now(), Instant.now(), "system"
        );

        repository.save(run1).await();
        repository.save(run2).await();

        List<EvaluationRun> resultsList = repository.findBySkillId("skill-123").await();

        assertThat(resultsList).hasSize(1);
        assertThat(resultsList.get(0).skillId()).isEqualTo("skill-123");
    }

    @Test
    @DisplayName("Should find evaluation runs by state")
    void shouldFindByState() {
        DataCloudEvaluationRunRepository repository = new DataCloudEvaluationRunRepository();

        Map<String, TestCaseResult> results = Map.of(
                "case-1", new TestCaseResult("case-1", true, 0.95, "Test passed", null)
        );

        EvaluationRun run1 = new EvaluationRun(
                "run-1", "pack-123", "skill-123", "agent-123", "release-1.0.0",
                RunState.RUNNING, Instant.now(), null, results,
                Instant.now(), Instant.now(), "system"
        );

        EvaluationRun run2 = new EvaluationRun(
                "run-2", "pack-456", "skill-456", "agent-123", "release-1.0.0",
                RunState.COMPLETED, Instant.now(), Instant.now(), results,
                Instant.now(), Instant.now(), "system"
        );

        repository.save(run1).await();
        repository.save(run2).await();

        List<EvaluationRun> resultsList = repository.findByState(RunState.RUNNING).await();

        assertThat(resultsList).hasSize(1);
        assertThat(resultsList.get(0).state()).isEqualTo(RunState.RUNNING);
    }

    @Test
    @DisplayName("Should update evaluation run state")
    void shouldUpdateRunState() {
        DataCloudEvaluationRunRepository repository = new DataCloudEvaluationRunRepository();

        Map<String, TestCaseResult> results = Map.of(
                "case-1", new TestCaseResult("case-1", true, 0.95, "Test passed", null)
        );

        EvaluationRun run = new EvaluationRun(
                "run-123", "pack-123", "skill-123", "agent-123", "release-1.0.0",
                RunState.PENDING, Instant.now(), null, results,
                Instant.now(), Instant.now(), "system"
        );

        repository.save(run).await();

        EvaluationRun updated = repository.updateState("run-123", RunState.RUNNING).await();

        assertThat(updated).isNotNull();
        assertThat(updated.state()).isEqualTo(RunState.RUNNING);
        assertThat(updated.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should calculate duration for completed run")
    void shouldCalculateDurationForCompletedRun() {
        DataCloudEvaluationRunRepository repository = new DataCloudEvaluationRunRepository();

        Map<String, TestCaseResult> results = Map.of(
                "case-1", new TestCaseResult("case-1", true, 0.95, "Test passed", null)
        );

        Instant startedAt = Instant.now();
        Instant completedAt = startedAt.plusMillis(5000);

        EvaluationRun run = new EvaluationRun(
                "run-123", "pack-123", "skill-123", "agent-123", "release-1.0.0",
                RunState.COMPLETED, startedAt, completedAt, results,
                Instant.now(), Instant.now(), "system"
        );

        repository.save(run).await();

        Optional<EvaluationRun> retrieved = repository.findById("run-123").await();
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().durationMs()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("Should find evaluation runs after timestamp")
    void shouldFindAfterTimestamp() {
        DataCloudEvaluationRunRepository repository = new DataCloudEvaluationRunRepository();

        Map<String, TestCaseResult> results = Map.of(
                "case-1", new TestCaseResult("case-1", true, 0.95, "Test passed", null)
        );

        Instant now = Instant.now();
        Instant past = now.minus(java.time.Duration.ofHours(2));

        EvaluationRun recentRun = new EvaluationRun(
                "run-1", "pack-123", "skill-123", "agent-123", "release-1.0.0",
                RunState.COMPLETED, now, now, results,
                now, now, "system"
        );

        EvaluationRun oldRun = new EvaluationRun(
                "run-2", "pack-456", "skill-456", "agent-123", "release-1.0.0",
                RunState.COMPLETED, past, past, results,
                past, past, "system"
        );

        repository.save(recentRun).await();
        repository.save(oldRun).await();

        Instant threshold = now.minus(java.time.Duration.ofHours(1));
        List<EvaluationRun> resultsList = repository.findAfter(threshold).await();

        assertThat(resultsList).hasSize(1);
        assertThat(resultsList.get(0).runId()).isEqualTo("run-1");
    }
}
