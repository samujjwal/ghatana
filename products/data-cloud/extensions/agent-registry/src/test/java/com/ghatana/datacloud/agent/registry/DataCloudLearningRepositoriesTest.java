/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.learning.*;
import com.ghatana.agent.lifecycle.*;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Data Cloud learning repositories")
@ExtendWith(MockitoExtension.class)
class DataCloudLearningRepositoriesTest extends EventloopTestBase {

    private static final String TENANT = "tenant-learning";
    private static final Instant NOW = Instant.parse("2026-05-01T00:00:00Z");

    @Mock private DataCloudClient dataCloud;
    @Mock private EntityInterface entity;

    @BeforeEach
    void setUp() {
        lenient().when(entity.getId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void candidateRepository_serializesAndReadsCandidates() {
        DataCloudLearningCandidateRepository repo = new DataCloudLearningCandidateRepository(dataCloud, TENANT);
        LearningCandidate candidate = candidate();
        when(dataCloud.createEntity(eq(TENANT), eq(DataCloudLearningCandidateRepository.COLLECTION), any()))
                .thenReturn(Promise.of(entity));

        runPromise(() -> repo.save(candidate));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(dataCloud).createEntity(eq(TENANT), eq(DataCloudLearningCandidateRepository.COLLECTION), captor.capture());
        assertThat(captor.getValue()).containsEntry("candidateId", "candidate-1");

        when(entity.getData()).thenReturn(captor.getValue());
        when(dataCloud.queryEntities(eq(TENANT), eq(DataCloudLearningCandidateRepository.COLLECTION), any(QuerySpecInterface.class)))
                .thenReturn(Promise.of(List.of(entity)));

        Optional<LearningCandidate> found = runPromise(() -> repo.findById("candidate-1"));

        assertThat(found).isPresent();
        assertThat(found.get().target()).isEqualTo(LearningTarget.PROCEDURAL_SKILL);
    }

    @Test
    void evidenceAndArtifactRepositories_supportPromotionLookup() {
        DataCloudPromotionEvidenceRepository evidenceRepo = new DataCloudPromotionEvidenceRepository(dataCloud, TENANT);
        DataCloudLearnedArtifactRepository artifactRepo = new DataCloudLearnedArtifactRepository(dataCloud, TENANT);
        PromotionEvidence evidence = evidence();
        LearnedArtifact artifact = artifact();
        when(dataCloud.createEntity(any(), any(), any())).thenReturn(Promise.of(entity));

        runPromise(() -> evidenceRepo.save(evidence));
        runPromise(() -> artifactRepo.save(artifact));

        verify(dataCloud).createEntity(eq(TENANT), eq(DataCloudPromotionEvidenceRepository.COLLECTION), any());
        verify(dataCloud).createEntity(eq(TENANT), eq(DataCloudLearnedArtifactRepository.COLLECTION), any());

        when(entity.getData()).thenReturn(Map.of(
                "artifactId", "artifact-1",
                "agentId", "agent-1",
                "agentReleaseId", "release-1",
                "target", "PROCEDURAL_SKILL",
                "state", "ACTIVE",
                "payload", Map.of("procedure", "retry"),
                "provenanceRefs", List.of("episode-1"),
                "promotionEvidenceId", "evidence-1",
                "createdAt", NOW.toString()));
        when(dataCloud.queryEntities(eq(TENANT), eq(DataCloudLearnedArtifactRepository.COLLECTION), any()))
                .thenReturn(Promise.of(List.of(entity)));

        List<LearnedArtifact> active = runPromise(() ->
                artifactRepo.findActiveByAgentAndTarget("agent-1", LearningTarget.PROCEDURAL_SKILL));

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().promotionEvidenceId()).isEqualTo("evidence-1");
    }

    @Test
    void turnTraceRepository_persistsTurnAndPhases() {
        DataCloudAgentTurnTraceRepository repo = new DataCloudAgentTurnTraceRepository(dataCloud, TENANT);
        AgentPhaseTrace phase = new AgentPhaseTrace(
                "agent-1:turn-1:admit",
                AgentLifecyclePhase.ADMIT,
                NOW,
                NOW.plusMillis(1),
                "SUCCESS",
                null,
                Map.of("budget", 1));
        AgentTurnTrace trace = new AgentTurnTrace(
                "trace-1",
                "turn-1",
                "agent-1",
                NOW,
                NOW.plusMillis(2),
                "SUCCESS",
                List.of(phase),
                Map.of("latencyMs", 2));
        when(dataCloud.createEntity(any(), any(), any())).thenReturn(Promise.of(entity));

        runPromise(() -> repo.save(trace));

        verify(dataCloud).createEntity(eq(TENANT), eq(DataCloudAgentTurnTraceRepository.TURN_COLLECTION), any());
        verify(dataCloud).createEntity(eq(TENANT), eq(DataCloudAgentTurnTraceRepository.PHASE_COLLECTION), any());
    }

    private static LearningCandidate candidate() {
        return new LearningCandidate(
                "candidate-1",
                "agent-1",
                "release-1",
                "trace-1",
                LearningTarget.PROCEDURAL_SKILL,
                LearningCandidateState.PROPOSED,
                List.of("episode-1"),
                Map.of("procedure", "retry"),
                NOW);
    }

    private static PromotionEvidence evidence() {
        return new PromotionEvidence(
                "evidence-1",
                "candidate-1",
                "eval-pack-1",
                List.of("eval-1"),
                Map.of("score", 0.93),
                "reviewer-1",
                NOW);
    }

    private static LearnedArtifact artifact() {
        return new LearnedArtifact(
                "artifact-1",
                "agent-1",
                "release-1",
                LearningTarget.PROCEDURAL_SKILL,
                PromotionState.ACTIVE,
                Map.of("procedure", "retry"),
                List.of("episode-1"),
                "evidence-1",
                null,
                NOW,
                "candidate-1",
                null,
                "tenant-1",
                List.of("episode-1"),
                null);
    }
}
