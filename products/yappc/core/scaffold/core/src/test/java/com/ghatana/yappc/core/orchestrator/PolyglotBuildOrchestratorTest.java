/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
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
    void setUp() { // GH-90000
        orchestrator = new PolyglotBuildOrchestrator(); // GH-90000
    }

    @Test
    void testOrchestrateBuildsWithNoDependencies() throws Exception { // GH-90000
        List<PolyglotBuildOrchestrator.BuildTarget> targets = List.of( // GH-90000
                new PolyglotBuildOrchestrator.BuildTarget( // GH-90000
                        "service-a",
                        "python",
                        Paths.get("/project/service-a [GH-90000]"),
                        List.of(), // GH-90000
                        Map.of()), // GH-90000
                new PolyglotBuildOrchestrator.BuildTarget( // GH-90000
                        "service-b",
                        "go",
                        Paths.get("/project/service-b [GH-90000]"),
                        List.of(), // GH-90000
                        Map.of())); // GH-90000

        PolyglotBuildOrchestrator.BuildOrchestrationResult result = runPromise(() -> orchestrator.orchestrateBuilds(targets)); // GH-90000

        assertTrue(result.success()); // GH-90000
        assertEquals(2, result.results().size()); // GH-90000
        assertEquals(2, result.buildOrder().size()); // GH-90000
    }

    @Test
    void testOrchestrateBuildsWithDependencies() throws Exception { // GH-90000
        List<PolyglotBuildOrchestrator.BuildTarget> targets = List.of( // GH-90000
                new PolyglotBuildOrchestrator.BuildTarget( // GH-90000
                        "library",
                        "rust",
                        Paths.get("/project/library [GH-90000]"),
                        List.of(), // GH-90000
                        Map.of()), // GH-90000
                new PolyglotBuildOrchestrator.BuildTarget( // GH-90000
                        "service",
                        "dotnet",
                        Paths.get("/project/service [GH-90000]"),
                        List.of("library [GH-90000]"),
                        Map.of())); // GH-90000

        PolyglotBuildOrchestrator.BuildOrchestrationResult result = runPromise(() -> orchestrator.orchestrateBuilds(targets)); // GH-90000

        assertTrue(result.success()); // GH-90000
        assertEquals(2, result.results().size()); // GH-90000
        assertEquals("library", result.buildOrder().get(0)); // GH-90000
        assertEquals("service", result.buildOrder().get(1)); // GH-90000
    }

    @Test
    void testBuildDependencyResolver() { // GH-90000
        PolyglotBuildOrchestrator.BuildDependencyResolver resolver = new PolyglotBuildOrchestrator.BuildDependencyResolver(); // GH-90000

        List<PolyglotBuildOrchestrator.BuildTarget> targets = List.of( // GH-90000
                new PolyglotBuildOrchestrator.BuildTarget( // GH-90000
                        "c", "go", Paths.get("/c [GH-90000]"), List.of("b [GH-90000]"), Map.of()),
                new PolyglotBuildOrchestrator.BuildTarget( // GH-90000
                        "b", "python", Paths.get("/b [GH-90000]"), List.of("a [GH-90000]"), Map.of()),
                new PolyglotBuildOrchestrator.BuildTarget( // GH-90000
                        "a", "rust", Paths.get("/a [GH-90000]"), List.of(), Map.of()));

        List<PolyglotBuildOrchestrator.BuildTarget> ordered = resolver.resolveBuildOrder(targets); // GH-90000

        assertEquals(3, ordered.size()); // GH-90000
        assertEquals("a", ordered.get(0).name()); // GH-90000
        assertEquals("b", ordered.get(1).name()); // GH-90000
        assertEquals("c", ordered.get(2).name()); // GH-90000
    }

    @Test
    void testCircularDependencyDetection() { // GH-90000
        PolyglotBuildOrchestrator.BuildDependencyResolver resolver = new PolyglotBuildOrchestrator.BuildDependencyResolver(); // GH-90000

        List<PolyglotBuildOrchestrator.BuildTarget> targets = List.of( // GH-90000
                new PolyglotBuildOrchestrator.BuildTarget( // GH-90000
                        "a", "go", Paths.get("/a [GH-90000]"), List.of("b [GH-90000]"), Map.of()),
                new PolyglotBuildOrchestrator.BuildTarget( // GH-90000
                        "b", "python", Paths.get("/b [GH-90000]"), List.of("a [GH-90000]"), Map.of()));

        assertThrows(IllegalStateException.class, () -> { // GH-90000
            resolver.resolveBuildOrder(targets); // GH-90000
        });
    }
}
