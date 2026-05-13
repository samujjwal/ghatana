/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.mastery.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MasteryItemMapper evidence refs round trip.
 *
 * @doc.type class
 * @doc.purpose Tests for evidence refs persistence round trip
 * @doc.layer data-cloud
 * @doc.pattern Test
 */
@DisplayName("MasteryItemMapper Tests")
class MasteryItemMapperTest {

    @Test
    @DisplayName("Evidence refs should persist round trip as List<String>")
    void evidenceRefsShouldPersistRoundTripAsList() {
        MasteryItem item = new MasteryItem(
                "mastery-123",
                "tenant-1",
                "skill-456",
                "test-domain",
                "agent-789",
                "release-1.0.0",
                MasteryState.COMPETENT,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-1", "production"),
                MasteryScore.correctnessOnly(0.8),
                List.of("proc-1", "proc-2"),
                List.of("fact-1", "fact-2"),
                List.of("neg-1"),
                List.of("evidence-1", "evidence-2", "evidence-3"),
                List.of("eval-1"),
                List.of("failure-1"),
                Instant.now(),
                Instant.now().plus(java.time.Duration.ofDays(30)),
                Map.of("label1", "value1"),
                List.of(),
                0.8
        );

        // Convert to data map
        Map<String, Object> dataMap = MasteryItemMapper.toDataMap(item);

        // Convert back from data map
        MasteryItem restoredItem = MasteryItemMapper.fromDataMap(dataMap);

        // Verify evidence refs round trip correctly
        assertThat(restoredItem.evidenceRefs()).isEqualTo(item.evidenceRefs());
        assertThat(restoredItem.evidenceRefs()).isInstanceOf(List.class);
        assertThat(restoredItem.evidenceRefs()).hasSize(3);
        assertThat(restoredItem.evidenceRefs()).containsExactly("evidence-1", "evidence-2", "evidence-3");
    }

    @Test
    @DisplayName("Empty evidence refs list should persist round trip")
    void emptyEvidenceRefsListShouldPersistRoundTrip() {
        MasteryItem item = new MasteryItem(
                "mastery-123",
                "tenant-1",
                "skill-456",
                "test-domain",
                "agent-789",
                "release-1.0.0",
                MasteryState.COMPETENT,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-1", "production"),
                MasteryScore.correctnessOnly(0.8),
                List.of(),
                List.of(),
                List.of(),
                List.of(), // empty evidence refs
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now().plus(java.time.Duration.ofDays(30)),
                Map.of(),
                List.of(),
                0.8
        );

        Map<String, Object> dataMap = MasteryItemMapper.toDataMap(item);
        MasteryItem restoredItem = MasteryItemMapper.fromDataMap(dataMap);

        assertThat(restoredItem.evidenceRefs()).isEmpty();
    }

    @Test
    @DisplayName("Single evidence ref should persist round trip")
    void singleEvidenceRefShouldPersistRoundTrip() {
        MasteryItem item = new MasteryItem(
                "mastery-123",
                "tenant-1",
                "skill-456",
                "test-domain",
                "agent-789",
                "release-1.0.0",
                MasteryState.COMPETENT,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-1", "production"),
                MasteryScore.correctnessOnly(0.8),
                List.of(),
                List.of(),
                List.of(),
                List.of("evidence-1"), // single evidence ref
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now().plus(java.time.Duration.ofDays(30)),
                Map.of(),
                List.of(),
                0.8
        );

        Map<String, Object> dataMap = MasteryItemMapper.toDataMap(item);
        MasteryItem restoredItem = MasteryItemMapper.fromDataMap(dataMap);

        assertThat(restoredItem.evidenceRefs()).hasSize(1);
        assertThat(restoredItem.evidenceRefs()).containsExactly("evidence-1");
    }

    @Test
    @DisplayName("Evaluation refs should persist round trip as List<String>")
    void evaluationRefsShouldPersistRoundTripAsList() {
        MasteryItem item = new MasteryItem(
                "mastery-123",
                "tenant-1",
                "skill-456",
                "test-domain",
                "agent-789",
                "release-1.0.0",
                MasteryState.COMPETENT,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-1", "production"),
                MasteryScore.correctnessOnly(0.8),
                List.of(),
                List.of(),
                List.of(),
                List.of("evidence-1"),
                List.of("eval-1", "eval-2", "eval-3"), // evaluation refs
                List.of(),
                Instant.now(),
                Instant.now().plus(java.time.Duration.ofDays(30)),
                Map.of(),
                List.of(),
                0.8
        );

        Map<String, Object> dataMap = MasteryItemMapper.toDataMap(item);
        MasteryItem restoredItem = MasteryItemMapper.fromDataMap(dataMap);

        assertThat(restoredItem.evaluationRefs()).isEqualTo(item.evaluationRefs());
        assertThat(restoredItem.evaluationRefs()).isInstanceOf(List.class);
        assertThat(restoredItem.evaluationRefs()).hasSize(3);
        assertThat(restoredItem.evaluationRefs()).containsExactly("eval-1", "eval-2", "eval-3");
    }

    @Test
    @DisplayName("All list fields should persist round trip correctly")
    void allListFieldsShouldPersistRoundTripCorrectly() {
        MasteryItem item = new MasteryItem(
                "mastery-123",
                "tenant-1",
                "skill-456",
                "test-domain",
                "agent-789",
                "release-1.0.0",
                MasteryState.COMPETENT,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-1", "production"),
                MasteryScore.correctnessOnly(0.8),
                List.of("proc-1", "proc-2"),
                List.of("fact-1", "fact-2"),
                List.of("neg-1"),
                List.of("evidence-1", "evidence-2"),
                List.of("eval-1", "eval-2"),
                List.of("failure-1", "failure-2"),
                Instant.now(),
                Instant.now().plus(java.time.Duration.ofDays(30)),
                Map.of("label1", "value1"),
                List.of(),
                0.8
        );

        Map<String, Object> dataMap = MasteryItemMapper.toDataMap(item);
        MasteryItem restoredItem = MasteryItemMapper.fromDataMap(dataMap);

        assertThat(restoredItem.procedureIds()).isEqualTo(item.procedureIds());
        assertThat(restoredItem.semanticFactIds()).isEqualTo(item.semanticFactIds());
        assertThat(restoredItem.negativeKnowledgeIds()).isEqualTo(item.negativeKnowledgeIds());
        assertThat(restoredItem.evidenceRefs()).isEqualTo(item.evidenceRefs());
        assertThat(restoredItem.evaluationRefs()).isEqualTo(item.evaluationRefs());
        assertThat(restoredItem.knownFailureModeIds()).isEqualTo(item.knownFailureModeIds());
    }
}
