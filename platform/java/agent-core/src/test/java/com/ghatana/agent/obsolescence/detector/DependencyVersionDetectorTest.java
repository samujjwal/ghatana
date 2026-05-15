/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence.detector;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.mastery.VersionConstraint;
import com.ghatana.agent.mastery.ApplicabilityScope;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.obsolescence.ObsolescenceEvent;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tests for DependencyVersionDetector.
 * Phase 7 FIX: Tests for dependency version obsolescence detection.
 *
 * @doc.type class
 * @doc.purpose Tests for DependencyVersionDetector
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DependencyVersionDetector Tests")
class DependencyVersionDetectorTest extends EventloopTestBase {

    private final DependencyVersionDetector detector = new DependencyVersionDetector();

    @Test
    @DisplayName("Should detect version mismatch")
    void shouldDetectVersionMismatch() {
        MasteryItem item = createMasteryItem();
        EnvironmentFingerprint env = createEnvironmentFingerprint("17"); // Version outside expected range

        Promise<List<ObsolescenceEvent>> result = detector.detect(item, env);
        List<ObsolescenceEvent> events = runPromise(() -> result);

        assertFalse(events.isEmpty());
    }

    @Test
    @DisplayName("Should not detect mismatch when version is in range")
    void shouldNotDetectMismatchWhenVersionInRange() {
        MasteryItem item = createMasteryItem();
        EnvironmentFingerprint env = createEnvironmentFingerprint("18"); // Version within expected range

        Promise<List<ObsolescenceEvent>> result = detector.detect(item, env);
        List<ObsolescenceEvent> events = runPromise(() -> result);

        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("Should return empty events when version scope is empty")
    void shouldReturnEmptyWhenVersionScopeEmpty() {
        MasteryItem item = new MasteryItem(
                "mastery1",
                "tenant1",
                "skill1",
                "domain1",
                "agent1",
                "release1",
                MasteryState.COMPETENT,
                VersionScope.empty(),
                ApplicabilityScope.minimal("tenant1", "production"),
                MasteryScore.correctnessOnly(0.8),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<MasteryTransition>of(),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                Map.<String, String>of(),
                0.8
        );
        EnvironmentFingerprint env = createEnvironmentFingerprint("17");

        Promise<List<ObsolescenceEvent>> result = detector.detect(item, env);
        List<ObsolescenceEvent> events = runPromise(() -> result);

        assertTrue(events.isEmpty());
    }

    private MasteryItem createMasteryItem() {
        return new MasteryItem(
                "mastery1",
                "tenant1",
                "skill1",
                "domain1",
                "agent1",
                "release1",
                MasteryState.COMPETENT,
                new VersionScope(
                        List.<VersionConstraint>of(VersionConstraint.runtimeVersion("java", "17..21", "jvm")),
                        List.<VersionConstraint>of(),
                        List.<VersionConstraint>of()
                ),
                ApplicabilityScope.minimal("tenant1", "production"),
                MasteryScore.correctnessOnly(0.8),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<String>of(),
                List.<MasteryTransition>of(),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                Map.<String, String>of(),
                0.8
        );
    }

    private EnvironmentFingerprint createEnvironmentFingerprint(String javaVersion) {
        return new EnvironmentFingerprint(
                "tenant1",
                "repo1",
                "project1",
                Map.of("java", javaVersion),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Instant.now(),
                List.of()
        );
    }
}
