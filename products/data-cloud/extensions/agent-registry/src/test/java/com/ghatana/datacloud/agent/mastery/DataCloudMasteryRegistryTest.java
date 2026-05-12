/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.*;
import com.ghatana.agent.runtime.mode.ExecutionMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DataCloudMasteryRegistry.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudMasteryRegistry
 * @doc.layer data-cloud
 * @doc.pattern Test
 */
@DisplayName("DataCloudMasteryRegistry Tests")
class DataCloudMasteryRegistryTest {

    @Test
    @DisplayName("Should save and retrieve mastery item")
    void shouldSaveAndRetrieveMasteryItem() {
        DataCloudMasteryRegistry registry = new DataCloudMasteryRegistry();

        EnvironmentScope applicability = new EnvironmentScope("tenant-123", "production");
        VersionScope versionScope = VersionScope.empty();
        ConfidenceVector score = new ConfidenceVector(0.9, 0.8, 0.7, 0.95, 0.6, 0.8, 0.9);

        MasteryItem item = new MasteryItem(
                "mastery-123",
                "skill-123",
                "domain-1",
                "agent-123",
                "release-1.0.0",
                MasteryState.COMPETENT,
                versionScope,
                applicability,
                score,
                List.of("proc-1"),
                List.of("fact-1"),
                List.of(),
                Map.of("ev-1", "ref-1"),
                List.of("eval-1"),
                List.of(),
                Instant.now(),
                Instant.now().plus(java.time.Duration.ofDays(30)),
                Map.of()
        );

        registry.save(item).await();

        EnvironmentFingerprint env = new EnvironmentFingerprint("tenant-123", Map.of(), Map.of());
        var result = registry.findBySkill("skill-123", env).await();

        assertThat(result).isPresent();
        assertThat(result.get().masteryId()).isEqualTo("mastery-123");
    }

    @Test
    @DisplayName("Should query mastery items by skill")
    void shouldQueryMasteryItemsBySkill() {
        DataCloudMasteryRegistry registry = new DataCloudMasteryRegistry();

        EnvironmentScope applicability = new EnvironmentScope("tenant-123", "production");
        VersionScope versionScope = VersionScope.empty();
        ConfidenceVector score = new ConfidenceVector(0.9, 0.8, 0.7, 0.95, 0.6, 0.8, 0.9);

        MasteryItem item1 = new MasteryItem(
                "mastery-1", "skill-123", "domain-1", "agent-123", "release-1.0.0",
                MasteryState.COMPETENT, versionScope, applicability, score,
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(),
                Instant.now(), Instant.now().plus(java.time.Duration.ofDays(30)), Map.of()
        );

        MasteryItem item2 = new MasteryItem(
                "mastery-2", "skill-456", "domain-1", "agent-123", "release-1.0.0",
                MasteryState.COMPETENT, versionScope, applicability, score,
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(),
                Instant.now(), Instant.now().plus(java.time.Duration.ofDays(30)), Map.of()
        );

        registry.save(item1).await();
        registry.save(item2).await();

        MasteryQuery query = MasteryQuery.bySkill("skill-123");
        List<MasteryItem> results = registry.query(query).await();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).skillId()).isEqualTo("skill-123");
    }

    @Test
    @DisplayName("Should transition mastery state")
    void shouldTransitionMasteryState() {
        DataCloudMasteryRegistry registry = new DataCloudMasteryRegistry();

        EnvironmentScope applicability = new EnvironmentScope("tenant-123", "production");
        VersionScope versionScope = VersionScope.empty();
        ConfidenceVector score = new ConfidenceVector(0.9, 0.8, 0.7, 0.95, 0.6, 0.8, 0.9);

        MasteryItem item = new MasteryItem(
                "mastery-123", "skill-123", "domain-1", "agent-123", "release-1.0.0",
                MasteryState.PRACTICED, versionScope, applicability, score,
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(),
                Instant.now(), Instant.now().plus(java.time.Duration.ofDays(30)), Map.of()
        );

        registry.save(item).await();

        MasteryTransition transition = MasteryTransition.manual(
                "mastery-123",
                MasteryState.PRACTICED,
                MasteryState.COMPETENT,
                "Evaluation passed",
                "user-123"
        );

        MasteryTransitionResult result = registry.transition(transition).await();

        assertThat(result.success()).isTrue();
        assertThat(result.toState()).isEqualTo(MasteryState.COMPETENT);
    }

    @Test
    @DisplayName("Should find stale mastery items")
    void shouldFindStaleMasteryItems() {
        DataCloudMasteryRegistry registry = new DataCloudMasteryRegistry();

        EnvironmentScope applicability = new EnvironmentScope("tenant-123", "production");
        VersionScope versionScope = VersionScope.empty();
        ConfidenceVector score = new ConfidenceVector(0.9, 0.8, 0.7, 0.95, 0.6, 0.8, 0.9);

        Instant past = Instant.now().minus(java.time.Duration.ofDays(60));

        MasteryItem staleItem = new MasteryItem(
                "mastery-1", "skill-123", "domain-1", "agent-123", "release-1.0.0",
                MasteryState.COMPETENT, versionScope, applicability, score,
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(),
                past, past, Map.of()
        );

        MasteryItem freshItem = new MasteryItem(
                "mastery-2", "skill-456", "domain-1", "agent-123", "release-1.0.0",
                MasteryState.COMPETENT, versionScope, applicability, score,
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(),
                Instant.now(), Instant.now().plus(java.time.Duration.ofDays(30)), Map.of()
        );

        registry.save(staleItem).await();
        registry.save(freshItem).await();

        List<MasteryItem> staleItems = registry.findStale(Instant.now()).await();

        assertThat(staleItems).hasSize(1);
        assertThat(staleItems.get(0).masteryId()).isEqualTo("mastery-1");
    }

    @Test
    @DisplayName("Should make mastery decision - allow for mastered skill")
    void shouldMakeDecisionAllowForMasteredSkill() {
        DataCloudMasteryRegistry registry = new DataCloudMasteryRegistry();

        EnvironmentScope applicability = new EnvironmentScope("tenant-123", "production");
        VersionScope versionScope = VersionScope.empty();
        ConfidenceVector score = new ConfidenceVector(0.95, 0.85, 0.8, 0.98, 0.7, 0.85, 0.95);

        MasteryItem item = new MasteryItem(
                "mastery-123", "skill-123", "domain-1", "agent-123", "release-1.0.0",
                MasteryState.MASTERED, versionScope, applicability, score,
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(),
                Instant.now(), Instant.now().plus(java.time.Duration.ofDays(30)), Map.of()
        );

        registry.save(item).await();

        MasteryQuery query = MasteryQuery.bySkill("skill-123");
        MasteryDecision decision = registry.decide(query).await();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.executionMode()).isEqualTo(ExecutionMode.DETERMINISTIC_EXECUTION);
    }

    @Test
    @DisplayName("Should make mastery decision - require verification for competent skill")
    void shouldMakeDecisionRequireVerificationForCompetentSkill() {
        DataCloudMasteryRegistry registry = new DataCloudMasteryRegistry();

        EnvironmentScope applicability = new EnvironmentScope("tenant-123", "production");
        VersionScope versionScope = VersionScope.empty();
        ConfidenceVector score = new ConfidenceVector(0.9, 0.8, 0.7, 0.95, 0.6, 0.8, 0.9);

        MasteryItem item = new MasteryItem(
                "mastery-123", "skill-123", "domain-1", "agent-123", "release-1.0.0",
                MasteryState.COMPETENT, versionScope, applicability, score,
                List.of(), List.of(), List.of(), Map.of("ev-1", "ref-1"), List.of(), List.of(),
                Instant.now(), Instant.now().plus(java.time.Duration.ofDays(30)), Map.of()
        );

        registry.save(item).await();

        MasteryQuery query = MasteryQuery.bySkill("skill-123");
        MasteryDecision decision = registry.decide(query).await();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.executionMode()).isEqualTo(ExecutionMode.BOUNDED_PROBABILISTIC_REASONING);
        assertThat(decision.requiresVerification()).isTrue();
    }

    @Test
    @DisplayName("Should make mastery decision - block when no matching skill")
    void shouldMakeDecisionBlockWhenNoMatchingSkill() {
        DataCloudMasteryRegistry registry = new DataCloudMasteryRegistry();

        MasteryQuery query = MasteryQuery.bySkill("unknown-skill");
        MasteryDecision decision = registry.decide(query).await();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.executionMode()).isEqualTo(ExecutionMode.BLOCKED);
    }
}
