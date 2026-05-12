/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LearningDeltaFactory.
 *
 * @doc.type class
 * @doc.purpose Tests for LearningDeltaFactory
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("LearningDeltaFactory Tests")
class LearningDeltaFactoryTest {

    @Test
    @DisplayName("Should propose learning delta with generated ID")
    void shouldProposeWithGeneratedId() {
        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertThat(delta.deltaId()).isNotNull();
        assertThat(delta.deltaId()).isNotEmpty();
        assertThat(delta.type()).isEqualTo(LearningDeltaType.PROCEDURAL_SKILL);
        assertThat(delta.target()).isEqualTo(LearningTarget.PROCEDURAL_SKILL);
        assertThat(delta.state()).isEqualTo(LearningDeltaState.PROPOSED);
        assertThat(delta.agentId()).isEqualTo("agent-123");
        assertThat(delta.agentReleaseId()).isEqualTo("release-1.0.0");
        assertThat(delta.skillId()).isEqualTo("skill-123");
        assertThat(delta.proposedContent()).isEqualTo(content);
        assertThat(delta.evidenceRefs()).isEqualTo(evidenceRefs);
        assertThat(delta.proposedBy()).isEqualTo("learning-engine");
        assertThat(delta.proposedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should propose learning delta with procedure ID")
    void shouldProposeWithProcedureId() {
        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.proposeWithProcedure(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                "procedure-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertThat(delta.procedureId()).isEqualTo("procedure-456");
        assertThat(delta.semanticFactId()).isNull();
        assertThat(delta.negativeKnowledgeId()).isNull();
    }

    @Test
    @DisplayName("Should propose learning delta with semantic fact ID")
    void shouldProposeWithSemanticFactId() {
        Map<String, Object> content = Map.of("fact", "test-fact");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.proposeWithSemanticFact(
                LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                "semantic-fact-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertThat(delta.procedureId()).isNull();
        assertThat(delta.semanticFactId()).isEqualTo("semantic-fact-456");
        assertThat(delta.negativeKnowledgeId()).isNull();
    }

    @Test
    @DisplayName("Should propose learning delta with negative knowledge ID")
    void shouldProposeWithNegativeKnowledgeId() {
        Map<String, Object> content = Map.of("negative", "test-negative");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.proposeWithNegativeKnowledge(
                LearningDeltaType.NEGATIVE_KNOWLEDGE,
                LearningTarget.NEGATIVE_KNOWLEDGE,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                "negative-knowledge-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertThat(delta.procedureId()).isNull();
        assertThat(delta.semanticFactId()).isNull();
        assertThat(delta.negativeKnowledgeId()).isEqualTo("negative-knowledge-456");
    }

    @Test
    @DisplayName("Should compute content digest")
    void shouldComputeContentDigest() {
        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertThat(delta.contentDigest()).isNotNull();
        assertThat(delta.contentDigest()).isNotEmpty();
    }

    @Test
    @DisplayName("Should set proposedAt to current time")
    void shouldSetProposedAtToCurrentTime() {
        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertThat(delta.proposedAt()).isNotNull();
        assertThat(delta.evaluatedAt()).isNull();
        assertThat(delta.promotedAt()).isNull();
        assertThat(delta.rejectedAt()).isNull();
    }

    @Test
    @DisplayName("Should initialize with empty labels")
    void shouldInitializeWithEmptyLabels() {
        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertThat(delta.labels()).isEmpty();
    }

    @Test
    @DisplayName("Should initialize with null rejection reason")
    void shouldInitializeWithNullRejectionReason() {
        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertThat(delta.rejectionReason()).isNull();
    }

    @Test
    @DisplayName("Should create different delta IDs for multiple proposals")
    void shouldCreateDifferentDeltaIdsForMultipleProposals() {
        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta1 = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        LearningDelta delta2 = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertThat(delta1.deltaId()).isNotEqualTo(delta2.deltaId());
    }

    @Test
    @DisplayName("Should support different learning delta types")
    void shouldSupportDifferentLearningDeltaTypes() {
        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta proceduralDelta = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        LearningDelta semanticDelta = LearningDeltaFactory.propose(
                LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                "agent-123",
                "release-1.0.0",
                "skill-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertThat(proceduralDelta.type()).isEqualTo(LearningDeltaType.PROCEDURAL_SKILL);
        assertThat(semanticDelta.type()).isEqualTo(LearningDeltaType.SEMANTIC_FACT);
    }
}
