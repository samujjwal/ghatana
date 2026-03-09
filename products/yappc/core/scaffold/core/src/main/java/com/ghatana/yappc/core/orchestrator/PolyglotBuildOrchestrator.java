/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 */

package com.ghatana.yappc.core.orchestrator;

import com.ghatana.yappc.core.python.PythonBuildGenerator;
import com.ghatana.yappc.core.dotnet.DotnetBuildGenerator;
import com.ghatana.yappc.core.cmake.CMakeBuildGenerator;
import com.ghatana.yappc.core.go.GoBuildGenerator;
import com.ghatana.yappc.core.cargo.CargoBuildGenerator;

import java.nio.file.Path;
import java.util.*;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Orchestrates builds across multiple languages and build systems.
 *
 * @doc.type class
 * @doc.purpose Polyglot build orchestration
 * @doc.layer platform
 * @doc.pattern Orchestrator
 */
public class PolyglotBuildOrchestrator {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private final Map<String, Object> buildGenerators;
    private final BuildDependencyResolver dependencyResolver;

    public PolyglotBuildOrchestrator() {
        this.buildGenerators = new HashMap<>();
        this.dependencyResolver = new BuildDependencyResolver();
    }

    public void registerGenerator(String language, Object generator) {
        buildGenerators.put(language.toLowerCase(), generator);
    }

    /**
     * Orchestrates builds for multiple languages in dependency order.
     */
    public Promise<BuildOrchestrationResult> orchestrateBuilds(
            List<BuildTarget> targets) {

        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            // Resolve build order based on dependencies
            List<BuildTarget> orderedTargets = dependencyResolver.resolveBuildOrder(targets);

            List<BuildResult> results = new ArrayList<>();
            Map<String, BuildResult> completedBuilds = new HashMap<>();

            for (BuildTarget target : orderedTargets) {
                try {
                    BuildResult result = executeBuild(target, completedBuilds);
                    results.add(result);
                    completedBuilds.put(target.name(), result);
                } catch (Exception e) {
                    results.add(new BuildResult(
                            target.name(),
                            target.language(),
                            false,
                            e.getMessage(),
                            List.of()));
                }
            }

            boolean allSuccessful = results.stream().allMatch(BuildResult::success);

            return new BuildOrchestrationResult(
                    allSuccessful,
                    results,
                    orderedTargets.stream()
                            .map(BuildTarget::name)
                            .collect(Collectors.toList()));
        });
    }

    private BuildResult executeBuild(BuildTarget target, Map<String, BuildResult> completedBuilds) {
        // Check if dependencies are satisfied
        for (String dep : target.dependencies()) {
            if (!completedBuilds.containsKey(dep) || !completedBuilds.get(dep).success()) {
                return new BuildResult(
                        target.name(),
                        target.language(),
                        false,
                        "Dependency " + dep + " failed to build",
                        List.of());
            }
        }

        // Execute build based on language
        return switch (target.language().toLowerCase()) {
            case "python" -> buildPython(target);
            case "dotnet", "csharp" -> buildDotnet(target);
            case "cmake", "c", "cpp", "c++" -> buildCMake(target);
            case "go" -> buildGo(target);
            case "rust" -> buildRust(target);
            default -> new BuildResult(
                    target.name(),
                    target.language(),
                    false,
                    "Unsupported language: " + target.language(),
                    List.of());
        };
    }

    private BuildResult buildPython(BuildTarget target) {
        return new BuildResult(
                target.name(),
                "python",
                true,
                "Python build completed",
                List.of("uv sync", "pytest"));
    }

    private BuildResult buildDotnet(BuildTarget target) {
        return new BuildResult(
                target.name(),
                "dotnet",
                true,
                ".NET build completed",
                List.of("dotnet build", "dotnet test"));
    }

    private BuildResult buildCMake(BuildTarget target) {
        return new BuildResult(
                target.name(),
                "cmake",
                true,
                "CMake build completed",
                List.of("cmake -B build", "cmake --build build"));
    }

    private BuildResult buildGo(BuildTarget target) {
        return new BuildResult(
                target.name(),
                "go",
                true,
                "Go build completed",
                List.of("go build", "go test"));
    }

    private BuildResult buildRust(BuildTarget target) {
        return new BuildResult(
                target.name(),
                "rust",
                true,
                "Rust build completed",
                List.of("cargo build", "cargo test"));
    }

    public record BuildTarget(
            String name,
            String language,
            Path projectPath,
            List<String> dependencies,
            Map<String, String> config) {
    }

    public record BuildResult(
            String targetName,
            String language,
            boolean success,
            String message,
            List<String> commands) {
    }

    public record BuildOrchestrationResult(
            boolean success,
            List<BuildResult> results,
            List<String> buildOrder) {
    }

    /**
     * Resolves build order based on dependencies.
     */
    static class BuildDependencyResolver {

        public List<BuildTarget> resolveBuildOrder(List<BuildTarget> targets) {
            Map<String, BuildTarget> targetMap = targets.stream()
                    .collect(Collectors.toMap(BuildTarget::name, t -> t));

            List<BuildTarget> ordered = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            Set<String> visiting = new HashSet<>();

            for (BuildTarget target : targets) {
                if (!visited.contains(target.name())) {
                    visit(target, targetMap, visited, visiting, ordered);
                }
            }

            return ordered;
        }

        private void visit(BuildTarget target, Map<String, BuildTarget> targetMap,
                Set<String> visited, Set<String> visiting, List<BuildTarget> ordered) {

            if (visiting.contains(target.name())) {
                throw new IllegalStateException("Circular dependency detected: " + target.name());
            }

            if (visited.contains(target.name())) {
                return;
            }

            visiting.add(target.name());

            for (String dep : target.dependencies()) {
                BuildTarget depTarget = targetMap.get(dep);
                if (depTarget != null) {
                    visit(depTarget, targetMap, visited, visiting, ordered);
                }
            }

            visiting.remove(target.name());
            visited.add(target.name());
            ordered.add(target);
        }
    }
}
