/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.learning.delta;

import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaFactory;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.learning.LearningTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DataCloudLearningDeltaRepository.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudLearningDeltaRepository
 * @doc.layer data-cloud
 * @doc.pattern Test
 */
@DisplayName("DataCloudLearningDeltaRepository Tests")
class DataCloudLearningDeltaRepositoryTest {

    @Test
    @DisplayName("Should save and retrieve learning delta")
    void shouldSaveAndRetrieveLearningDelta() {
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository();

        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        repository.save(delta).await();
        Optional<LearningDelta> result = repository.findById(delta.deltaId()).await();

        assertThat(result).isPresent();
        assertThat(result.get().deltaId()).isEqualTo(delta.deltaId());
        assertThat(result.get().state()).isEqualTo(LearningDeltaState.PROPOSED);
    }

    @Test
    @DisplayName("Should find learning deltas by agent ID")
    void shouldFindByAgentId() {
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository();

        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta1 = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        LearningDelta delta2 = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                "agent-456",
                "release-1.0.0",
                "skill-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        repository.save(delta1).await();
        repository.save(delta2).await();

        List<LearningDelta> results = repository.findByAgentId("agent-123").await();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).agentId()).isEqualTo("agent-123");
    }

    @Test
    @DisplayName("Should find learning deltas by skill ID")
    void shouldFindBySkillId() {
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository();

        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta1 = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        LearningDelta delta2 = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                "agent-123",
                "release-1.0.0",
                "skill-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        repository.save(delta1).await();
        repository.save(delta2).await();

        List<LearningDelta> results = repository.findBySkillId("skill-123").await();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).skillId()).isEqualTo("skill-123");
    }

    @Test
    @DisplayName("Should find learning deltas by state")
    void shouldFindByState() {
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository();

        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta1 = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        LearningDelta delta2 = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                "agent-123",
                "release-1.0.0",
                "skill-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        repository.save(delta1).await();
        repository.save(delta2).await();

        List<LearningDelta> results = repository.findByState(LearningDeltaState.PROPOSED).await();

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Should find pending evaluation deltas")
    void shouldFindPendingEvaluation() {
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository();

        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta1 = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        LearningDelta delta2 = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                "agent-123",
                "release-1.0.0",
                "skill-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        repository.save(delta1).await();
        repository.save(delta2).await();

        List<LearningDelta> results = repository.findPendingEvaluation().await();

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Should find promotable deltas")
    void shouldFindPromotable() {
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository();

        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        repository.save(delta).await();

        // Update to EVALUATED state to make it promotable
        repository.updateState(delta.deltaId(), LearningDeltaState.EVALUATED).await();

        List<LearningDelta> results = repository.findPromotable().await();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).state()).isEqualTo(LearningDeltaState.EVALUATED);
    }

    @Test
    @DisplayName("Should update delta state")
    void shouldUpdateDeltaState() {
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository();

        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        repository.save(delta).await();

        LearningDelta updated = repository.updateState(delta.deltaId(), LearningDeltaState.EVALUATED).await();

        assertThat(updated).isNotNull();
        assertThat(updated.state()).isEqualTo(LearningDeltaState.EVALUATED);
        assertThat(updated.evaluatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update delta state with rejection reason")
    void shouldUpdateDeltaStateWithRejectionReason() {
        DataCloudLearningDeltaRepository repository = new DataCloudLearningDeltaRepository();

        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.propose(
                com.ghatana.agent.learning.LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        repository.save(delta).await();

        LearningDelta updated = repository.updateState(
                delta.deltaId(),
                LearningDeltaState.REJECTED,
                "Insufficient evidence"
        ).await();

        assertThat(updated).isNotNull();
        assertThat(updated.state()).isEqualTo(LearningDeltaState.REJECTED);
        assertThat(updated.rejectionReason()).isEqualTo("Insufficient evidence");
        assertThat(updated.rejectedAt()).isNotNull();
    }
}
