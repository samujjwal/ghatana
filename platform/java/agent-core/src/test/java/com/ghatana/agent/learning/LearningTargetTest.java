/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LearningTarget}.
 *
 * @doc.type class
 * @doc.purpose Tests for LearningTarget enum properties
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("LearningTarget Tests")
class LearningTargetTest {

    @Test
    @DisplayName("MODEL_ADAPTER is offline-only")
    void modelAdapterIsOfflineOnly() {
        assertThat(LearningTarget.MODEL_ADAPTER.isOfflineOnlyTarget()).isTrue();
    }

    @Test
    @DisplayName("MASTERY_STATE is offline-only")
    void masteryStateIsOfflineOnly() {
        assertThat(LearningTarget.MASTERY_STATE.isOfflineOnlyTarget()).isTrue();
    }

    @Test
    @DisplayName("PROCEDURAL_SKILL is not offline-only")
    void proceduralSkillIsNotOfflineOnly() {
        assertThat(LearningTarget.PROCEDURAL_SKILL.isOfflineOnlyTarget()).isFalse();
    }

    @Test
    @DisplayName("SEMANTIC_FACT is not offline-only")
    void semanticFactIsNotOfflineOnly() {
        assertThat(LearningTarget.SEMANTIC_FACT.isOfflineOnlyTarget()).isFalse();
    }

    @Test
    @DisplayName("EPISODIC_MEMORY is not offline-only")
    void episodicMemoryIsNotOfflineOnly() {
        assertThat(LearningTarget.EPISODIC_MEMORY.isOfflineOnlyTarget()).isFalse();
    }

    @Test
    @DisplayName("NEGATIVE_KNOWLEDGE is not offline-only")
    void negativeKnowledgeIsNotOfflineOnly() {
        assertThat(LearningTarget.NEGATIVE_KNOWLEDGE.isOfflineOnlyTarget()).isFalse();
    }

    @Test
    @DisplayName("RETRIEVAL_POLICY is not offline-only")
    void retrievalPolicyIsNotOfflineOnly() {
        assertThat(LearningTarget.RETRIEVAL_POLICY.isOfflineOnlyTarget()).isFalse();
    }

    @Test
    @DisplayName("CONFIDENCE_THRESHOLD is not offline-only")
    void confidenceThresholdIsNotOfflineOnly() {
        assertThat(LearningTarget.CONFIDENCE_THRESHOLD.isOfflineOnlyTarget()).isFalse();
    }

    @Test
    @DisplayName("ROUTING_POLICY is not offline-only")
    void routingPolicyIsNotOfflineOnly() {
        assertThat(LearningTarget.ROUTING_POLICY.isOfflineOnlyTarget()).isFalse();
    }

    @Test
    @DisplayName("PROMPT_TEMPLATE is not offline-only")
    void promptTemplateIsNotOfflineOnly() {
        assertThat(LearningTarget.PROMPT_TEMPLATE.isOfflineOnlyTarget()).isFalse();
    }

    @Test
    @DisplayName("PLANNER_POLICY is not offline-only")
    void plannerPolicyIsNotOfflineOnly() {
        assertThat(LearningTarget.PLANNER_POLICY.isOfflineOnlyTarget()).isFalse();
    }
}
