/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 */

package com.ghatana.yappc.core.orchestrator;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles polyglot build orchestrator test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PolyglotBuildOrchestratorTest extends EventloopTestBase {

    private PolyglotBuildOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new PolyglotBuildOrchestrator();
    }

    @Test
    void testOrchestrateBuildsWithNoDependencies() throws Exception {
        List<PolyglotBuildOrchestrator.BuildTarget> targets = List.of(
                new PolyglotBuildOrchestrator.BuildTarget(
                        "service-a",
                        "python",
                        Paths.get("/project/service-a"),
                        List.of(),
                        Map.of()),
                new PolyglotBuildOrchestrator.BuildTarget(
                        "service-b",
                        "go",
                        Paths.get("/project/service-b"),
                        List.of(),
                        Map.of()));

        PolyglotBuildOrchestrator.BuildOrchestrationResult result = runPromise(() -> orchestrator.orchestrateBuilds(targets));

        assertTrue(result.success());
        assertEquals(2, result.results().size());
        assertEquals(2, result.buildOrder().size());
    }

    @Test
    void testOrchestrateBuildsWithDependencies() throws Exception {
        List<PolyglotBuildOrchestrator.BuildTarget> targets = List.of(
                new PolyglotBuildOrchestrator.BuildTarget(
                        "library",
                        "rust",
                        Paths.get("/project/library"),
                        List.of(),
                        Map.of()),
                new PolyglotBuildOrchestrator.BuildTarget(
                        "service",
                        "dotnet",
                        Paths.get("/project/service"),
                        List.of("library"),
                        Map.of()));

        PolyglotBuildOrchestrator.BuildOrchestrationResult result = runPromise(() -> orchestrator.orchestrateBuilds(targets));

        assertTrue(result.success());
        assertEquals(2, result.results().size());
        assertEquals("library", result.buildOrder().get(0));
        assertEquals("service", result.buildOrder().get(1));
    }

    @Test
    void testBuildDependencyResolver() {
        PolyglotBuildOrchestrator.BuildDependencyResolver resolver = new PolyglotBuildOrchestrator.BuildDependencyResolver();

        List<PolyglotBuildOrchestrator.BuildTarget> targets = List.of(
                new PolyglotBuildOrchestrator.BuildTarget(
                        "c", "go", Paths.get("/c"), List.of("b"), Map.of()),
                new PolyglotBuildOrchestrator.BuildTarget(
                        "b", "python", Paths.get("/b"), List.of("a"), Map.of()),
                new PolyglotBuildOrchestrator.BuildTarget(
                        "a", "rust", Paths.get("/a"), List.of(), Map.of()));

        List<PolyglotBuildOrchestrator.BuildTarget> ordered = resolver.resolveBuildOrder(targets);

        assertEquals(3, ordered.size());
        assertEquals("a", ordered.get(0).name());
        assertEquals("b", ordered.get(1).name());
        assertEquals("c", ordered.get(2).name());
    }

    @Test
    void testCircularDependencyDetection() {
        PolyglotBuildOrchestrator.BuildDependencyResolver resolver = new PolyglotBuildOrchestrator.BuildDependencyResolver();

        List<PolyglotBuildOrchestrator.BuildTarget> targets = List.of(
                new PolyglotBuildOrchestrator.BuildTarget(
                        "a", "go", Paths.get("/a"), List.of("b"), Map.of()),
                new PolyglotBuildOrchestrator.BuildTarget(
                        "b", "python", Paths.get("/b"), List.of("a"), Map.of()));

        assertThrows(IllegalStateException.class, () -> {
            resolver.resolveBuildOrder(targets);
        });
    }
}
