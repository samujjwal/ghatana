/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.learning.delta;

import com.ghatana.agent.learning.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip tests for LearningDeltaMapper.
 *
 * @doc.type class
 * @doc.purpose Verifies all LearningDelta fields survive toDataMap/fromDataMap round-trip
 * @doc.layer data-cloud
 * @doc.pattern Test
 */
@DisplayName("LearningDeltaMapper Tests")
class LearningDeltaMapperTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FIXED_EVAL = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant FIXED_PROMO = Instant.parse("2026-01-03T00:00:00Z");
    private static final Instant FIXED_REJECT = Instant.parse("2026-01-04T00:00:00Z");

    @Test
    @DisplayName("Should round-trip all fields for a fully-populated delta")
    void shouldRoundTripAllFields() {
        LearningDelta original = new LearningDelta(
                "delta-abc",
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                LearningDeltaState.PROPOSED,
                "agent-1",
                "release-1.0.0",
                "skill-999",
                "tenant-xyz",
                "procedure-001",
                "fact-002",
                "neg-003",
                "sha256-abc123",
                Map.of("key1", "value1", "key2", 42),
                List.of("ev-1", "ev-2"),
                List.of("eval-ref-1"),
                List.of("ep-1", "ep-2", "ep-3"),
                "rollback-ref-007",
                0.65,
                0.85,
                true,
                "learning-engine",
                FIXED_NOW,
                FIXED_EVAL,
                FIXED_PROMO,
                FIXED_REJECT,
                Map.of("env", "staging", "version", "v2"),
                "low confidence — pending review",
                null,
                null,
                null,
                null,
                null
        );

        Map<String, Object> dataMap = LearningDeltaMapper.toDataMap(original);
        LearningDelta restored = LearningDeltaMapper.fromDataMap(dataMap);

        assertThat(restored.deltaId()).isEqualTo("delta-abc");
        assertThat(restored.type()).isEqualTo(LearningDeltaType.PROCEDURAL_SKILL);
        assertThat(restored.target()).isEqualTo(LearningTarget.PROCEDURAL_SKILL);
        assertThat(restored.state()).isEqualTo(LearningDeltaState.PROPOSED);
        assertThat(restored.agentId()).isEqualTo("agent-1");
        assertThat(restored.agentReleaseId()).isEqualTo("release-1.0.0");
        assertThat(restored.skillId()).isEqualTo("skill-999");
        assertThat(restored.tenantId()).isEqualTo("tenant-xyz");
        assertThat(restored.procedureId()).isEqualTo("procedure-001");
        assertThat(restored.semanticFactId()).isEqualTo("fact-002");
        assertThat(restored.negativeKnowledgeId()).isEqualTo("neg-003");
        assertThat(restored.contentDigest()).isEqualTo("sha256-abc123");
        assertThat(restored.proposedContent()).containsEntry("key1", "value1").containsEntry("key2", 42);
        assertThat(restored.evidenceRefs()).containsExactly("ev-1", "ev-2");
        assertThat(restored.evaluationRefs()).containsExactly("eval-ref-1");
        assertThat(restored.sourceEpisodeIds()).containsExactly("ep-1", "ep-2", "ep-3");
        assertThat(restored.rollbackRef()).isEqualTo("rollback-ref-007");
        assertThat(restored.confidenceBefore()).isEqualTo(0.65);
        assertThat(restored.confidenceAfter()).isEqualTo(0.85);
        assertThat(restored.requiresHumanReview()).isTrue();
        assertThat(restored.proposedBy()).isEqualTo("learning-engine");
        assertThat(restored.proposedAt()).isEqualTo(FIXED_NOW);
        assertThat(restored.evaluatedAt()).isEqualTo(FIXED_EVAL);
        assertThat(restored.promotedAt()).isEqualTo(FIXED_PROMO);
        assertThat(restored.rejectedAt()).isEqualTo(FIXED_REJECT);
        assertThat(restored.labels()).containsEntry("env", "staging").containsEntry("version", "v2");
        assertThat(restored.rejectionReason()).isEqualTo("low confidence — pending review");
    }

    @Test
    @DisplayName("Should round-trip delta with null optional IDs")
    void shouldRoundTripNullOptionalIds() {
        LearningDelta original = new LearningDelta(
                "delta-minimal",
                LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                LearningDeltaState.PROPOSED,
                "agent-2",
                "release-2.0.0",
                "skill-42",
                "tenant-default",
                null,   // procedureId
                null,   // semanticFactId
                null,   // negativeKnowledgeId
                "sha256-xyz",
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                null,   // rollbackRef
                0.0,
                0.0,
                false,
                "system",
                FIXED_NOW,
                null,   // evaluatedAt
                null,   // promotedAt
                null,   // rejectedAt
                Map.of(),
                null,   // rejectionReason
                null,
                null,
                null,
                null,
                null
        );

        Map<String, Object> dataMap = LearningDeltaMapper.toDataMap(original);
        LearningDelta restored = LearningDeltaMapper.fromDataMap(dataMap);

        assertThat(restored.procedureId()).isNull();
        assertThat(restored.semanticFactId()).isNull();
        assertThat(restored.negativeKnowledgeId()).isNull();
        assertThat(restored.rollbackRef()).isNull();
        assertThat(restored.evaluatedAt()).isNull();
        assertThat(restored.promotedAt()).isNull();
        assertThat(restored.rejectedAt()).isNull();
        assertThat(restored.rejectionReason()).isNull();
    }

    @Test
    @DisplayName("Should round-trip empty lists as empty lists")
    void shouldRoundTripEmptyListsAsEmptyLists() {
        LearningDelta original = new LearningDelta(
                "delta-empty-lists",
                LearningDeltaType.NEGATIVE_KNOWLEDGE,
                LearningTarget.RETRIEVAL_POLICY,
                LearningDeltaState.PROMOTED,
                "agent-3",
                "release-3.0.0",
                "skill-7",
                "tenant-abc",
                null, null, null,
                "sha256-empty",
                Map.<String, Object>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                null,
                0.5, 0.9,
                false,
                "system",
                FIXED_NOW,
                FIXED_EVAL,
                FIXED_PROMO,
                null,
                Map.<String, String>of(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        Map<String, Object> dataMap = LearningDeltaMapper.toDataMap(original);
        LearningDelta restored = LearningDeltaMapper.fromDataMap(dataMap);

        assertThat(restored.evidenceRefs()).isEmpty();
        assertThat(restored.evaluationRefs()).isEmpty();
        assertThat(restored.sourceEpisodeIds()).isEmpty();
        assertThat(restored.labels()).isEmpty();
    }

    @Test
    @DisplayName("Should round-trip maps as maps")
    void shouldRoundTripMapsAsMaps() {
        LearningDelta original = new LearningDelta(
                "delta-maps",
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                LearningDeltaState.PROPOSED,
                "agent-4",
                "release-4.0.0",
                "skill-99",
                "tenant-maps",
                "proc-001",
                null, null,
                "sha256-maps",
                Map.of("steps", List.of("a", "b"), "meta", Map.of("x", 1)),
                List.of("ev-map-1"),
                List.of(),
                List.of(),
                "rb-ref",
                0.7, 0.9,
                false,
                "engine",
                FIXED_NOW,
                null, null, null,
                Map.of("label-a", "value-a", "label-b", "value-b"),
                null,
                null,
                null,
                null,
                null,
                null
        );

        Map<String, Object> dataMap = LearningDeltaMapper.toDataMap(original);
        LearningDelta restored = LearningDeltaMapper.fromDataMap(dataMap);

        assertThat(restored.labels()).containsEntry("label-a", "value-a").containsEntry("label-b", "value-b");
        assertThat(restored.proposedContent()).containsKey("steps").containsKey("meta");
    }

    @Test
    @DisplayName("Should round-trip timestamps with millisecond precision")
    void shouldRoundTripTimestampsWithMillisecondPrecision() {
        Instant proposedAt = Instant.ofEpochMilli(1_700_000_000_123L);
        Instant evaluatedAt = Instant.ofEpochMilli(1_700_000_001_456L);

        LearningDelta original = new LearningDelta(
                "delta-ts",
                LearningDeltaType.SEMANTIC_FACT,
                LearningTarget.SEMANTIC_FACT,
                LearningDeltaState.EVALUATED,
                "agent-5",
                "release-5.0.0",
                "skill-ts",
                "tenant-ts",
                null, null, null,
                "sha256-ts",
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                0.6, 0.8,
                false,
                "system",
                proposedAt,
                evaluatedAt,
                null, null,
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        Map<String, Object> dataMap = LearningDeltaMapper.toDataMap(original);
        LearningDelta restored = LearningDeltaMapper.fromDataMap(dataMap);

        assertThat(restored.proposedAt()).isEqualTo(proposedAt);
        assertThat(restored.evaluatedAt()).isEqualTo(evaluatedAt);
    }

    @Test
    @DisplayName("Should round-trip rejection reason across update")
    void shouldRoundTripRejectionReason() {
        LearningDelta original = new LearningDelta(
                "delta-rejected",
                LearningDeltaType.PROCEDURAL_SKILL,
                LearningTarget.PROCEDURAL_SKILL,
                LearningDeltaState.REJECTED,
                "agent-6",
                "release-6.0.0",
                "skill-rej",
                "tenant-rej",
                null, null, null,
                "sha256-rej",
                Map.<String, Object>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                null,
                0.3, 0.3,
                false,
                "evaluator",
                FIXED_NOW,
                FIXED_EVAL,
                null,
                FIXED_REJECT,
                Map.<String, String>of(),
                "Insufficient evidence for procedural claim",
                null,
                null,
                null,
                null,
                null
        );

        Map<String, Object> dataMap = LearningDeltaMapper.toDataMap(original);
        LearningDelta restored = LearningDeltaMapper.fromDataMap(dataMap);

        assertThat(restored.state()).isEqualTo(LearningDeltaState.REJECTED);
        assertThat(restored.rejectionReason()).isEqualTo("Insufficient evidence for procedural claim");
        assertThat(restored.rejectedAt()).isEqualTo(FIXED_REJECT);
    }
}
