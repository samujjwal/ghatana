/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for DefaultObsolescenceDetector.
 *
 * @doc.type class
 * @doc.purpose Tests for DefaultObsolescenceDetector
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultObsolescenceDetector Tests")
class DefaultObsolescenceDetectorTest extends EventloopTestBase {

    @Test
    @DisplayName("Should detect version obsolescence for stale items")
    void shouldDetectVersionObsolescenceForStaleItems() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        DefaultObsolescenceDetector detector = new DefaultObsolescenceDetector(registry);

        MasteryItem staleItem = createMasteryItem(
                "mastery-123",
                Instant.now().minusSeconds(3600) // Stale 1 hour ago
        );

        EnvironmentFingerprint env = createEnvironmentFingerprint();

        List<ObsolescenceEvent> events = runPromise(() -> detector.detect(staleItem, env));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).reason()).isEqualTo(ObsolescenceReason.VERSION_MISMATCH);
        assertThat(events.get(0).description()).contains("stale");
    }

    @Test
    @DisplayName("Should detect API deprecation")
    void shouldDetectApiDeprecation() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        DefaultObsolescenceDetector detector = new DefaultObsolescenceDetector(registry);

        MasteryItem deprecatedItem = createMasteryItem(
                "mastery-123",
                Instant.now().plusSeconds(86400),
                Map.of("deprecated", "true")
        );

        EnvironmentFingerprint env = createEnvironmentFingerprint();

        List<ObsolescenceEvent> events = runPromise(() -> detector.detect(deprecatedItem, env));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).reason()).isEqualTo(ObsolescenceReason.API_CHANGE);
        assertThat(events.get(0).description()).contains("deprecated");
    }

    @Test
    @DisplayName("Should detect runtime incompatibility")
    void shouldDetectRuntimeIncompatibility() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        DefaultObsolescenceDetector detector = new DefaultObsolescenceDetector(registry);

        MasteryItem incompatibleItem = createMasteryItem(
                "mastery-123",
                Instant.now().plusSeconds(86400),
                Map.of("requiredRuntime", "node-18")
        );

        EnvironmentFingerprint env = createEnvironmentFingerprint(
                Map.of("node-20", "20.0.0") // Has node-20, not node-18
        );

        List<ObsolescenceEvent> events = runPromise(() -> detector.detect(incompatibleItem, env));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).reason()).isEqualTo(ObsolescenceReason.RUNTIME_INCOMPATIBILITY);
        assertThat(events.get(0).description()).contains("Runtime not available");
    }

    @Test
    @DisplayName("Should detect repeated failures")
    void shouldDetectRepeatedFailures() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        DefaultObsolescenceDetector detector = new DefaultObsolescenceDetector(registry);

        MasteryItem failingItem = createMasteryItem(
                "mastery-123",
                Instant.now().plusSeconds(86400),
                Map.of("failureCount", "5") // At threshold
        );

        EnvironmentFingerprint env = createEnvironmentFingerprint();

        List<ObsolescenceEvent> events = runPromise(() -> detector.detect(failingItem, env));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).reason()).isEqualTo(ObsolescenceReason.REPEATED_FAILURES);
        assertThat(events.get(0).description()).contains("Repeated failures");
    }

    @Test
    @DisplayName("Should detect security vulnerabilities")
    void shouldDetectSecurityVulnerabilities() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        DefaultObsolescenceDetector detector = new DefaultObsolescenceDetector(registry);

        MasteryItem vulnerableItem = createMasteryItem(
                "mastery-123",
                Instant.now().plusSeconds(86400),
                Map.of("securityVulnerability", "true")
        );

        EnvironmentFingerprint env = createEnvironmentFingerprint();

        List<ObsolescenceEvent> events = runPromise(() -> detector.detect(vulnerableItem, env));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).reason()).isEqualTo(ObsolescenceReason.SECURITY_VULNERABILITY);
        assertThat(events.get(0).description()).contains("Security vulnerability");
    }

    @Test
    @DisplayName("Should detect documentation contradictions")
    void shouldDetectDocumentationContradictions() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        DefaultObsolescenceDetector detector = new DefaultObsolescenceDetector(registry);

        MasteryItem contradictoryItem = createMasteryItem(
                "mastery-123",
                Instant.now().plusSeconds(86400),
                Map.of("documentationContradiction", "true")
        );

        EnvironmentFingerprint env = createEnvironmentFingerprint();

        List<ObsolescenceEvent> events = runPromise(() -> detector.detect(contradictoryItem, env));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).reason()).isEqualTo(ObsolescenceReason.DOCUMENTATION_CONTRADICTION);
        assertThat(events.get(0).description()).contains("Documentation contradiction");
    }

    @Test
    @DisplayName("Should return no events for healthy items")
    void shouldReturnNoEventsForHealthyItems() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        DefaultObsolescenceDetector detector = new DefaultObsolescenceDetector(registry);

        MasteryItem healthyItem = createMasteryItem(
                "mastery-123",
                Instant.now().plusSeconds(3600) // Not stale yet
        );

        EnvironmentFingerprint env = createEnvironmentFingerprint();

        List<ObsolescenceEvent> events = runPromise(() -> detector.detect(healthyItem, env));

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should scan all mastery items")
    void shouldScanAllMasteryItems() {
        MasteryRegistry registry = mock(MasteryRegistry.class);
        DefaultObsolescenceDetector detector = new DefaultObsolescenceDetector(registry);

        MasteryItem staleItem = createMasteryItem(
                "mastery-1",
                Instant.now().minusSeconds(3600)
        );

        when(registry.query(com.ghatana.agent.mastery.MasteryQuery.activeOnly()))
                .thenReturn(Promise.of(List.of(staleItem)));

        EnvironmentFingerprint env = createEnvironmentFingerprint();

        List<ObsolescenceEvent> events = runPromise(() -> detector.scanAll(env));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).masteryId()).isEqualTo("mastery-1");
    }

    // Helper methods

    private MasteryItem createMasteryItem(String masteryId, Instant staleAfter) {
        return createMasteryItem(masteryId, staleAfter, Map.of());
    }

    private MasteryItem createMasteryItem(String masteryId, Instant staleAfter, Map<String, String> labels) {
        return new MasteryItem(
                masteryId,
                "tenant-123",
                "skill-123",
                "test-domain",
                "agent-123",
                "release-123",
                MasteryState.COMPETENT,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant-123", "production"),
                new MasteryScore(0.8, 0.7, 0.9, 0.85, 0.75, 0.8, 0.9),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.now(),
                staleAfter,
                labels,
                0.8
        );
    }

    private EnvironmentFingerprint createEnvironmentFingerprint() {
        return createEnvironmentFingerprint(Map.of());
    }

    private EnvironmentFingerprint createEnvironmentFingerprint(Map<String, String> runtimes) {
        return new EnvironmentFingerprint(
                "tenant-123",
                "repo-123",
                "java",
                Map.of(),
                Map.of(),
                runtimes,
                Map.of(),
                Map.of(),
                Map.of(),
                Instant.now(),
                List.of()
        );
    }
}
