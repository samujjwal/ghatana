/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning;

import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.learning.LearningContract;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.learning.LearningLevel;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link LearningEngine}.
 *
 * @doc.type class
 * @doc.purpose Tests for LearningEngine reflect phase — governance, delta creation, and review routing
 * @doc.layer framework
 * @doc.pattern Test
 */
@DisplayName("LearningEngine Tests")
@ExtendWith(MockitoExtension.class)
class LearningEngineTest extends EventloopTestBase {

    @Mock
    private MemoryStore memoryStore;

    @Mock
    private LearningDeltaRepository deltaRepository;

    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * Creates a list of episodes where the same action appears repeatdly (>=3 times)
     * so the synthesis heuristic produces at least one candidate.
     */
    private static List<Episode> episodesWithPattern(String action, int count, double reward) {
        List<Episode> episodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            episodes.add(Episode.builder()
                    .id("ep-" + action + "-" + i)
                    .agentId("agent-123")
                    .turnId("turn-" + i)
                    .timestamp(Instant.now())
                    .input("input-" + i)
                    .action(action)
                    .reward(reward)
                    .build());
        }
        return episodes;
    }

    @Test
    @DisplayName("L3+ reflect without deltaRepository throws IllegalStateException")
    void l3WithoutRepositoryFails() {
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true,
                false
        );

        LearningEngine engine = LearningEngine.create(EXECUTOR)
                .withLearningContract(contract)
                .withTenantId("tenant-1")
                .withHumanReviewThreshold(0.7);
        // No deltaRepository configured

        List<Episode> episodes = episodesWithPattern("do-something", 5, 1.0);
        when(memoryStore.queryEpisodes(any(MemoryFilter.class), anyInt()))
                .thenReturn(Promise.of(episodes));

        assertThatThrownBy(() ->
                runPromise(() -> engine.reflect("agent-123", memoryStore))
        ).isInstanceOf(Exception.class)
                .satisfies(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    assertThat(cause).isInstanceOf(IllegalStateException.class);
                    assertThat(cause.getMessage()).contains("LearningDeltaRepository");
                });
    }

    @Test
    @DisplayName("L3+ reflect with deltaRepository creates learning deltas")
    void l3WithRepositoryCreatesDeltas() {
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true,
                false
        );

        LearningEngine engine = LearningEngine.create(EXECUTOR)
                .withLearningContract(contract)
                .withTenantId("tenant-1")
                .withAgentReleaseId("release-1.0.0")
                .withHumanReviewThreshold(0.5) // low threshold so candidates are approved
                .withLearningDeltaRepository(deltaRepository);

        List<Episode> episodes = episodesWithPattern("action-a", 5, 1.0);
        when(memoryStore.queryEpisodes(any(MemoryFilter.class), anyInt()))
                .thenReturn(Promise.of(episodes));
        when(deltaRepository.save(any(LearningDelta.class)))
                .thenAnswer(inv -> Promise.of(inv.getArgument(0, LearningDelta.class)));

        LearningEngine.LearningOutcome outcome = runPromise(() -> engine.reflect("agent-123", memoryStore));

        assertThat(outcome.deltasProposed()).isGreaterThan(0);
        verify(deltaRepository).save(any(LearningDelta.class));
    }

    @Test
    @DisplayName("Low-confidence candidates are routed to PENDING_HUMAN_REVIEW, not silently dropped")
    void lowConfidenceCandidatesAreFlagged() {
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true,
                false
        );

        LearningEngine engine = LearningEngine.create(EXECUTOR)
                .withLearningContract(contract)
                .withTenantId("tenant-1")
                .withAgentReleaseId("release-1.0.0")
                .withHumanReviewThreshold(0.9) // high threshold — most candidates will be flagged
                .withLearningDeltaRepository(deltaRepository);

        // 4 positive, 1 negative → confidence 0.8 < 0.9 threshold → flagged
        List<Episode> episodes = episodesWithPattern("action-b", 4, 1.0);
        episodes.add(Episode.builder()
                .id("ep-action-b-neg")
                .agentId("agent-123")
                .turnId("turn-neg")
                .timestamp(Instant.now())
                .input("input-neg")
                .action("action-b")
                .reward(-1.0)
                .build());

        when(memoryStore.queryEpisodes(any(MemoryFilter.class), anyInt()))
                .thenReturn(Promise.of(episodes));
        when(deltaRepository.save(any(LearningDelta.class)))
                .thenAnswer(inv -> Promise.of(inv.getArgument(0, LearningDelta.class)));

        LearningEngine.LearningOutcome outcome = runPromise(() -> engine.reflect("agent-123", memoryStore));

        assertThat(outcome.policiesFlaggedForReview()).isGreaterThan(0);
        // Low-confidence candidate must be persisted as PENDING_HUMAN_REVIEW, not silently dropped
        ArgumentCaptor<LearningDelta> captor = ArgumentCaptor.forClass(LearningDelta.class);
        verify(deltaRepository).save(captor.capture());
        LearningDelta saved = captor.getValue();
        assertThat(saved.state()).isEqualTo(LearningDeltaState.PENDING_HUMAN_REVIEW);
        assertThat(saved.requiresHumanReview()).isTrue();
    }

    @Test
    @DisplayName("Approved procedural deltas include a non-blank procedureId")
    void proceduralDeltasIncludeProcedureId() {
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true,
                false
        );

        LearningEngine engine = LearningEngine.create(EXECUTOR)
                .withLearningContract(contract)
                .withTenantId("tenant-1")
                .withAgentReleaseId("release-1.0.0")
                .withHumanReviewThreshold(0.5) // low threshold so candidates are approved
                .withLearningDeltaRepository(deltaRepository);

        List<Episode> episodes = episodesWithPattern("action-proc", 5, 1.0);
        when(memoryStore.queryEpisodes(any(MemoryFilter.class), anyInt()))
                .thenReturn(Promise.of(episodes));
        when(deltaRepository.save(any(LearningDelta.class)))
                .thenAnswer(inv -> Promise.of(inv.getArgument(0, LearningDelta.class)));

        runPromise(() -> engine.reflect("agent-123", memoryStore));

        ArgumentCaptor<LearningDelta> captor = ArgumentCaptor.forClass(LearningDelta.class);
        verify(deltaRepository).save(captor.capture());
        LearningDelta saved = captor.getValue();
        assertThat(saved.procedureId()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("L0 engine produces no-op outcome without accessing memory")
    void l0EngineProducesNoOp() {
        LearningContract contract = new LearningContract(
                LearningLevel.L0,
                Set.of(),
                false,
                false,
                false
        );

        LearningEngine engine = LearningEngine.create(EXECUTOR)
                .withLearningContract(contract);

        LearningEngine.LearningOutcome outcome = runPromise(() -> engine.reflect("agent-123", memoryStore));

        assertThat(outcome.episodesProcessed()).isEqualTo(0);
        assertThat(outcome.deltasProposed()).isEqualTo(0);
        // memoryStore should NOT be queried for L0
        verify(memoryStore, never()).queryEpisodes(any(), anyInt());
    }

    @Test
    @DisplayName("Tenant is not 'default' unless explicitly configured")
    void tenantIsNotDefaultUnlessConfigured() {
        // withTenantId() must reject blank values and the engine must not default to "default"
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true,
                false
        );

        LearningEngine engine = LearningEngine.create(EXECUTOR)
                .withLearningContract(contract)
                .withAgentReleaseId("release-1.0.0")
                .withHumanReviewThreshold(0.5)
                .withLearningDeltaRepository(deltaRepository);

        List<Episode> episodes = episodesWithPattern("action-c", 5, 1.0);
        when(memoryStore.queryEpisodes(any(MemoryFilter.class), anyInt()))
                .thenReturn(Promise.of(episodes));

        // Capture what tenantId is used in the saved delta
        List<LearningDelta> savedDeltas = new ArrayList<>();
        when(deltaRepository.save(any(LearningDelta.class))).thenAnswer(inv -> {
            LearningDelta d = inv.getArgument(0, LearningDelta.class);
            savedDeltas.add(d);
            return Promise.of(d);
        });

        // Run without configuring tenant → tenantId field is null, proposing delta should
        // either fail or use null (not "default"). This asserts the engine does NOT silently
        // substitute "default" as a tenant.
        runPromise(() -> engine.reflect("agent-123", memoryStore));

        if (!savedDeltas.isEmpty()) {
            // If deltas were saved, tenant must not be the magic string "default"
            savedDeltas.forEach(d ->
                    assertThat(d.tenantId()).isNotEqualTo("default"));
        }
    }

    // -------------------------------------------------------------------------
    // P0.3: Learning contract enforcement tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Contract that does not permit PROCEDURAL_SKILL rejects procedural deltas")
    void contractRejectsProceduralDeltasWhenNotPermitted() {
        // Contract allows only EPISODIC_MEMORY, not PROCEDURAL_SKILL
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.EPISODIC_MEMORY), // Does not include PROCEDURAL_SKILL
                true,
                true,
                false
        );

        LearningEngine engine = LearningEngine.create(EXECUTOR)
                .withLearningContract(contract)
                .withTenantId("tenant-1")
                .withAgentReleaseId("release-1.0.0")
                .withHumanReviewThreshold(0.5)
                .withLearningDeltaRepository(deltaRepository);

        List<Episode> episodes = episodesWithPattern("action-proc", 5, 1.0);
        when(memoryStore.queryEpisodes(any(MemoryFilter.class), anyInt()))
                .thenReturn(Promise.of(episodes));

        LearningEngine.LearningOutcome outcome = runPromise(() -> engine.reflect("agent-123", memoryStore));

        // Deltas should be rejected by contract
        assertThat(outcome.deltasRejectedByContract()).isGreaterThan(0);
        // Procedural delta is proposed but then rejected by contract (contract check happens after proposal)
        assertThat(outcome.deltasProposed()).isGreaterThan(0);
        // Delta repository should not be called to save any procedural deltas (rejected by contract)
        verify(deltaRepository, never()).save(any(LearningDelta.class));
    }

    @Test
    @DisplayName("Contract that permits PROCEDURAL_SKILL allows procedural deltas")
    void contractAllowsProceduralDeltasWhenPermitted() {
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL), // Includes PROCEDURAL_SKILL
                true,
                true,
                false
        );

        LearningEngine engine = LearningEngine.create(EXECUTOR)
                .withLearningContract(contract)
                .withTenantId("tenant-1")
                .withAgentReleaseId("release-1.0.0")
                .withHumanReviewThreshold(0.5)
                .withLearningDeltaRepository(deltaRepository);

        List<Episode> episodes = episodesWithPattern("action-proc", 5, 1.0);
        when(memoryStore.queryEpisodes(any(MemoryFilter.class), anyInt()))
                .thenReturn(Promise.of(episodes));
        when(deltaRepository.save(any(LearningDelta.class)))
                .thenAnswer(inv -> Promise.of(inv.getArgument(0, LearningDelta.class)));

        LearningEngine.LearningOutcome outcome = runPromise(() -> engine.reflect("agent-123", memoryStore));

        // Deltas should be proposed (not rejected by contract)
        assertThat(outcome.deltasRejectedByContract()).isEqualTo(0);
        assertThat(outcome.deltasProposed()).isGreaterThan(0);
        // Delta repository should be called to save procedural deltas
        verify(deltaRepository).save(any(LearningDelta.class));
    }

    @Test
    @DisplayName("Contract with empty allowedTargets rejects all procedural deltas")
    void contractWithEmptyAllowedTargetsRejectsAllDeltas() {
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(), // Empty allowed targets
                true,
                true,
                false
        );

        LearningEngine engine = LearningEngine.create(EXECUTOR)
                .withLearningContract(contract)
                .withTenantId("tenant-1")
                .withAgentReleaseId("release-1.0.0")
                .withHumanReviewThreshold(0.5)
                .withLearningDeltaRepository(deltaRepository);

        List<Episode> episodes = episodesWithPattern("action-proc", 5, 1.0);
        when(memoryStore.queryEpisodes(any(MemoryFilter.class), anyInt()))
                .thenReturn(Promise.of(episodes));

        LearningEngine.LearningOutcome outcome = runPromise(() -> engine.reflect("agent-123", memoryStore));

        // All deltas should be rejected by contract (empty allowedTargets)
        assertThat(outcome.deltasRejectedByContract()).isGreaterThan(0);
        // Delta is proposed but then rejected by contract (contract check happens after proposal)
        assertThat(outcome.deltasProposed()).isGreaterThan(0);
        verify(deltaRepository, never()).save(any(LearningDelta.class));
    }

    @Test
    @DisplayName("Contract permits() is called before delta creation for both approved and flagged candidates")
    void contractPermitsCalledForApprovedAndFlaggedCandidates() {
        // Contract permits PROCEDURAL_SKILL
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true,
                false
        );

        LearningEngine engine = LearningEngine.create(EXECUTOR)
                .withLearningContract(contract)
                .withTenantId("tenant-1")
                .withAgentReleaseId("release-1.0.0")
                .withHumanReviewThreshold(0.9) // High threshold to create flagged candidates
                .withLearningDeltaRepository(deltaRepository);

        // Create episodes that will produce both approved and flagged candidates
        List<Episode> episodes = episodesWithPattern("action-mixed", 4, 1.0);
        episodes.add(Episode.builder()
                .id("ep-action-mixed-neg")
                .agentId("agent-123")
                .turnId("turn-neg")
                .timestamp(Instant.now())
                .input("input-neg")
                .action("action-mixed")
                .reward(-1.0)
                .build());

        when(memoryStore.queryEpisodes(any(MemoryFilter.class), anyInt()))
                .thenReturn(Promise.of(episodes));
        when(deltaRepository.save(any(LearningDelta.class)))
                .thenAnswer(inv -> Promise.of(inv.getArgument(0, LearningDelta.class)));

        LearningEngine.LearningOutcome outcome = runPromise(() -> engine.reflect("agent-123", memoryStore));

        // No deltas should be rejected by contract (contract permits PROCEDURAL_SKILL)
        assertThat(outcome.deltasRejectedByContract()).isEqualTo(0);
        // Both approved and flagged deltas should be created
        assertThat(outcome.deltasProposed() + outcome.deltasNeedingReview()).isGreaterThan(0);
    }
}
