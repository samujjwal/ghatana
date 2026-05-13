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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.3 acceptance tests for MasteryItemMapper round-trip fidelity.
 * Verifies that all fields survive a toDataMap → fromDataMap round trip without
 * data loss, type coercion, or accidental map-conversion.
 */
@DisplayName("MasteryItemMapper round-trip tests")
class MasteryItemMapperTest {

    private static final Instant LAST_VERIFIED = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant STALE_AFTER   = Instant.parse("2026-07-15T10:00:00Z");

    // -----------------------------------------------------------------------
    // 4.3 T1: full round-trip with all fields populated
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("4.3: All fields survive a toDataMap → fromDataMap round trip")
    void allFieldsRoundTrip() {
        MasteryItem original = buildFullItem();

        Map<String, Object> data = MasteryItemMapper.toDataMap(original);
        MasteryItem restored = MasteryItemMapper.fromDataMap(data);

        assertThat(restored.masteryId()).isEqualTo(original.masteryId());
        assertThat(restored.skillId()).isEqualTo(original.skillId());
        assertThat(restored.domain()).isEqualTo(original.domain());
        assertThat(restored.agentId()).isEqualTo(original.agentId());
        assertThat(restored.agentReleaseId()).isEqualTo(original.agentReleaseId());
        assertThat(restored.state()).isEqualTo(original.state());

        // MasteryScore
        assertThat(restored.score().correctness()).isEqualTo(original.score().correctness());
        assertThat(restored.score().freshness()).isEqualTo(original.score().freshness());
        assertThat(restored.score().evidenceStrength()).isEqualTo(original.score().evidenceStrength());
        assertThat(restored.score().regressionStability()).isEqualTo(original.score().regressionStability());

        // ApplicabilityScope
        assertThat(restored.applicability().tenantId()).isEqualTo(original.applicability().tenantId());
        assertThat(restored.applicability().environment()).isEqualTo(original.applicability().environment());
        assertThat(restored.applicability().domainConstraints())
                .isEqualTo(original.applicability().domainConstraints());

        // List<String> refs — must remain plain lists, not map-converted
        assertThat(restored.evidenceRefs()).isEqualTo(original.evidenceRefs());
        assertThat(restored.evaluationRefs()).isEqualTo(original.evaluationRefs());
        assertThat(restored.knownFailureModeIds()).isEqualTo(original.knownFailureModeIds());
        assertThat(restored.procedureIds()).isEqualTo(original.procedureIds());
        assertThat(restored.semanticFactIds()).isEqualTo(original.semanticFactIds());
        assertThat(restored.negativeKnowledgeIds()).isEqualTo(original.negativeKnowledgeIds());

        // Labels
        assertThat(restored.labels()).isEqualTo(original.labels());

        // Timestamps
        assertThat(restored.lastVerifiedAt()).isEqualTo(original.lastVerifiedAt());
        assertThat(restored.staleAfter()).isEqualTo(original.staleAfter());
    }

    // -----------------------------------------------------------------------
    // 4.3 T2: evidenceRefs remain List<String>, not accidentally map-converted
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("4.3: evidenceRefs survive as List<String> — not map-converted")
    void evidenceRefsRemainStringList() {
        List<String> expectedRefs = List.of("ev-alpha", "ev-beta", "ev-gamma");

        MasteryItem item = buildItemWithEvidenceRefs(expectedRefs);
        Map<String, Object> data = MasteryItemMapper.toDataMap(item);
        MasteryItem restored = MasteryItemMapper.fromDataMap(data);

        assertThat(restored.evidenceRefs())
                .as("evidenceRefs must round-trip as plain strings, not 'key:value' map entries")
                .containsExactlyInAnyOrderElementsOf(expectedRefs);
    }

    // -----------------------------------------------------------------------
    // 4.3 T3: VersionScope constraints round-trip with all four fields
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("4.3: VersionScope constraints round-trip with kind/name/range/ecosystem")
    void versionScopeRoundTrip() {
        VersionConstraint active = new VersionConstraint("semver", "jvm", ">=21", "maven");
        VersionConstraint maintenance = new VersionConstraint("semver", "jvm", ">=17 <21", "maven");
        VersionConstraint obsolete = new VersionConstraint("semver", "jvm", "<17", "maven");

        MasteryItem item = buildItemWithVersionScope(
                new VersionScope(List.of(active), List.of(maintenance), List.of(obsolete)));
        Map<String, Object> data = MasteryItemMapper.toDataMap(item);
        MasteryItem restored = MasteryItemMapper.fromDataMap(data);

        assertThat(restored.versionScope().active()).hasSize(1);
        VersionConstraint restoredActive = restored.versionScope().active().get(0);
        assertThat(restoredActive.kind()).isEqualTo("semver");
        assertThat(restoredActive.name()).isEqualTo("jvm");
        assertThat(restoredActive.range()).isEqualTo(">=21");
        assertThat(restoredActive.ecosystem()).isEqualTo("maven");

        assertThat(restored.versionScope().maintenance()).hasSize(1);
        assertThat(restored.versionScope().maintenance().get(0).range()).isEqualTo(">=17 <21");

        assertThat(restored.versionScope().obsolete()).hasSize(1);
        assertThat(restored.versionScope().obsolete().get(0).range()).isEqualTo("<17");
    }

    // -----------------------------------------------------------------------
    // 4.3 T4: stateHistory round-trips
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("4.3: stateHistory survives round-trip with correct field mapping")
    void stateHistoryRoundTrips() {
        Instant transitionedAt = Instant.parse("2026-02-10T08:30:00Z");
        MasteryTransition transition = new MasteryTransition(
                "txn-001",
                "mastery-rt-001",
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "passed evaluation suite",
                "evaluator-service",
                transitionedAt,
                Map.of("ev-1", "passed"),
                Map.of("source", "automated")
        );

        MasteryItem item = buildItemWithStateHistory(List.of(transition));
        Map<String, Object> data = MasteryItemMapper.toDataMap(item);
        MasteryItem restored = MasteryItemMapper.fromDataMap(data);

        assertThat(restored.stateHistory()).hasSize(1);
        MasteryTransition restoredTxn = restored.stateHistory().get(0);
        assertThat(restoredTxn.transitionId()).isEqualTo("txn-001");
        assertThat(restoredTxn.masteryId()).isEqualTo("mastery-rt-001");
        assertThat(restoredTxn.fromState()).isEqualTo(MasteryState.PRACTICED);
        assertThat(restoredTxn.toState()).isEqualTo(MasteryState.COMPETENT);
        assertThat(restoredTxn.reason()).isEqualTo("passed evaluation suite");
        assertThat(restoredTxn.initiatedBy()).isEqualTo("evaluator-service");
        assertThat(restoredTxn.transitionedAt()).isEqualTo(transitionedAt);
        assertThat(restoredTxn.evidenceRefs()).isEqualTo(Map.of("ev-1", "passed"));
        assertThat(restoredTxn.metadata()).isEqualTo(Map.of("source", "automated"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static MasteryItem buildFullItem() {
        VersionConstraint vc = new VersionConstraint("semver", "jvm", ">=21", "maven");
        VersionScope versionScope = new VersionScope(List.of(vc), List.of(), List.of());
        ApplicabilityScope applicability = new ApplicabilityScope(
                "tenant-rt", "staging", Map.of("region", "us-east-1"));
        MasteryScore score = new MasteryScore(0.9, 0.85, 0.8, 0.95, 0.7, 0.88, 0.92);

        MasteryTransition txn = new MasteryTransition(
                "txn-full-01", "mastery-full-01",
                MasteryState.OBSERVED, MasteryState.PRACTICED,
                "initial evidence batch", "system",
                Instant.parse("2026-01-10T09:00:00Z"),
                Map.of("ev-a", "ok"),
                Map.of()
        );

        return new MasteryItem(
                "mastery-full-01",
                "skill-data-modeling",
                "data-cloud",
                "agent-dc-v2",
                "release-dc-2026-01",
                MasteryState.COMPETENT,
                versionScope,
                applicability,
                score,
                List.of("proc-001", "proc-002"),
                List.of("fact-001"),
                List.of("neg-001"),
                List.of("ev-001", "ev-002", "ev-003"),
                List.of("eval-001", "eval-002"),
                List.of("failure-001"),
                List.of(txn),
                LAST_VERIFIED,
                STALE_AFTER,
                Map.of("env", "staging", "tier", "critical")
        );
    }

    private static MasteryItem buildItemWithEvidenceRefs(List<String> evidenceRefs) {
        VersionScope versionScope = new VersionScope(List.of(), List.of(), List.of());
        ApplicabilityScope applicability = ApplicabilityScope.minimal("tenant-ev", "production");
        MasteryScore score = new MasteryScore(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8);

        return new MasteryItem(
                "mastery-ev-01", "skill-ev", "test", "agent-ev", "release-ev-01",
                MasteryState.PRACTICED,
                versionScope, applicability, score,
                List.of(), List.of(), List.of(),
                evidenceRefs,
                List.of(), List.of(), List.of(),
                LAST_VERIFIED, STALE_AFTER,
                Map.of()
        );
    }

    private static MasteryItem buildItemWithVersionScope(VersionScope versionScope) {
        ApplicabilityScope applicability = ApplicabilityScope.minimal("tenant-vs", "production");
        MasteryScore score = new MasteryScore(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8);

        return new MasteryItem(
                "mastery-vs-01", "skill-vs", "test", "agent-vs", "release-vs-01",
                MasteryState.MASTERED,
                versionScope, applicability, score,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                LAST_VERIFIED, STALE_AFTER,
                Map.of()
        );
    }

    private static MasteryItem buildItemWithStateHistory(List<MasteryTransition> history) {
        VersionScope versionScope = new VersionScope(List.of(), List.of(), List.of());
        ApplicabilityScope applicability = ApplicabilityScope.minimal("tenant-sh", "production");
        MasteryScore score = new MasteryScore(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8);

        return new MasteryItem(
                "mastery-rt-001", "skill-sh", "test", "agent-sh", "release-sh-01",
                MasteryState.COMPETENT,
                versionScope, applicability, score,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                history,
                LAST_VERIFIED, STALE_AFTER,
                Map.of()
        );
    }
}
