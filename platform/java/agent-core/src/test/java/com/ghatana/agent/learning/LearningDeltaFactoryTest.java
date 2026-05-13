/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
                "tenant-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertNotNull(delta.deltaId());
        assertFalse(delta.deltaId().isEmpty());
        assertEquals(LearningDeltaType.PROCEDURAL_SKILL, delta.type());
        assertEquals(LearningTarget.PROCEDURAL_SKILL, delta.target());
        assertEquals(LearningDeltaState.PROPOSED, delta.state());
        assertEquals("agent-123", delta.agentId());
        assertEquals("release-1.0.0", delta.agentReleaseId());
        assertEquals("skill-123", delta.skillId());
        assertEquals(content, delta.proposedContent());
        assertEquals(evidenceRefs, delta.evidenceRefs());
        assertEquals("learning-engine", delta.proposedBy());
        assertNotNull(delta.proposedAt());
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
                "tenant-123",
                "procedure-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertEquals("procedure-456", delta.procedureId());
        assertNull(delta.semanticFactId());
        assertNull(delta.negativeKnowledgeId());
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
                "tenant-123",
                "semantic-fact-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertNull(delta.procedureId());
        assertEquals("semantic-fact-456", delta.semanticFactId());
        assertNull(delta.negativeKnowledgeId());
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
                "tenant-123",
                "negative-knowledge-456",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertNull(delta.procedureId());
        assertNull(delta.semanticFactId());
        assertEquals("negative-knowledge-456", delta.negativeKnowledgeId());
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
                "tenant-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertNotNull(delta.contentDigest());
        assertFalse(delta.contentDigest().isEmpty());
    }

    @Test
    @DisplayName("Content digest should be deterministic for same content")
    void contentDigestShouldBeDeterministicForSameContent() {
        Map<String, Object> content = Map.of("action", "test-action", "param", "value");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta1 = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                "tenant-123",
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
                "tenant-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertEquals(delta1.contentDigest(), delta2.contentDigest());
    }

    @Test
    @DisplayName("Content digest should differ for different content")
    void contentDigestShouldDifferForDifferentContent() {
        Map<String, Object> content1 = Map.of("action", "test-action");
        Map<String, Object> content2 = Map.of("action", "different-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta1 = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                "tenant-123",
                content1,
                evidenceRefs,
                "learning-engine"
        );

        LearningDelta delta2 = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                "tenant-123",
                content2,
                evidenceRefs,
                "learning-engine"
        );

        assertNotEquals(delta1.contentDigest(), delta2.contentDigest());
    }

    @Test
    @DisplayName("Content digest should use SHA-256 format (64 hex characters)")
    void contentDigestShouldUseSha256Format() {
        Map<String, Object> content = Map.of("action", "test-action");
        List<String> evidenceRefs = List.of("evidence-1");

        LearningDelta delta = LearningDeltaFactory.propose(
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                "tenant-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertEquals(64, delta.contentDigest().length());
        assertTrue(delta.contentDigest().matches("[a-f0-9]{64}"));
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
                "tenant-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertNotNull(delta.proposedAt());
        assertNull(delta.evaluatedAt());
        assertNull(delta.promotedAt());
        assertNull(delta.rejectedAt());
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
                "tenant-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertTrue(delta.labels().isEmpty());
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
                "tenant-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertNull(delta.rejectionReason());
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
                "tenant-123",
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
                "tenant-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertNotEquals(delta1.deltaId(), delta2.deltaId());
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
                "tenant-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        LearningDelta semanticDelta = LearningDeltaFactory.propose(
                LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                "agent-123",
                "release-1.0.0",
                "skill-123",
                "tenant-123",
                content,
                evidenceRefs,
                "learning-engine"
        );

        assertEquals(LearningDeltaType.PROCEDURAL_SKILL, proceduralDelta.type());
        assertEquals(LearningDeltaType.SEMANTIC_FACT, semanticDelta.type());
    }
}
