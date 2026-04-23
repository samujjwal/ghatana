/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.workflow.optimization;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for pipeline execution optimization strategies.
 *
 * <p>Validates parallel execution scheduling, stage pruning, resource
 * allocation, and throughput improvements achieved by the optimizer
 * relative to a baseline sequential execution plan.
 *
 * @doc.type    class
 * @doc.purpose Pipeline optimization: parallel scheduling, stage pruning, throughput, resource allocation
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("PipelineOptimizationTest")
@Tag("optimization")
class PipelineOptimizationTest {

    private PipelineOptimizer optimizer;

    @BeforeEach
    void setUp() { // GH-90000
        optimizer = new PipelineOptimizer(); // GH-90000
    }

    // ── Parallel scheduling ───────────────────────────────────────────────────

    @Test
    @DisplayName("independent stages are scheduled in parallel")
    void independentStagesScheduledInParallel() { // GH-90000
        Pipeline pipeline = Pipeline.of( // GH-90000
                Stage.of("A", List.of()),          // no dependencies // GH-90000
                Stage.of("B", List.of()),          // no dependencies // GH-90000
                Stage.of("C", List.of("A", "B"))   // depends on A and B // GH-90000
        );
        ExecutionPlan plan = optimizer.optimize(pipeline); // GH-90000
        // Stages A and B should be in the same parallel group
        assertThat(plan.parallelGroups()).hasSizeGreaterThanOrEqualTo(2); // GH-90000
        List<String> firstGroup = plan.parallelGroups().get(0); // GH-90000
        assertThat(firstGroup).containsExactlyInAnyOrder("A", "B"); // GH-90000
    }

    @Test
    @DisplayName("stage with dependency is executed after its dependency")
    void dependentStageExecutedAfterDependency() { // GH-90000
        Pipeline pipeline = Pipeline.of( // GH-90000
                Stage.of("X", List.of()), // GH-90000
                Stage.of("Y", List.of("X"))
        );
        ExecutionPlan plan = optimizer.optimize(pipeline); // GH-90000
        List<List<String>> groups = plan.parallelGroups(); // GH-90000
        // X must appear in an earlier group than Y
        int xGroup = -1, yGroup = -1;
        for (int i = 0; i < groups.size(); i++) { // GH-90000
            if (groups.get(i).contains("X")) xGroup = i;
            if (groups.get(i).contains("Y")) yGroup = i;
        }
        assertThat(xGroup).isLessThan(yGroup); // GH-90000
    }

    @Test
    @DisplayName("single-stage pipeline produces a single execution group")
    void singleStagePipelineHasOneGroup() { // GH-90000
        Pipeline pipeline = Pipeline.of(Stage.of("solo", List.of())); // GH-90000
        ExecutionPlan plan = optimizer.optimize(pipeline); // GH-90000
        assertThat(plan.parallelGroups()).hasSize(1); // GH-90000
    }

    // ── Stage pruning ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("disabled stages are pruned from the execution plan")
    void disabledStagesArePruned() { // GH-90000
        Pipeline pipeline = Pipeline.of( // GH-90000
                Stage.of("enabled-1", List.of()), // GH-90000
                Stage.of("disabled-2", List.of()).disable(), // GH-90000
                Stage.of("enabled-3", List.of()) // GH-90000
        );
        ExecutionPlan plan = optimizer.optimize(pipeline); // GH-90000
        List<String> allStages = plan.parallelGroups().stream() // GH-90000
                .flatMap(Collection::stream).toList(); // GH-90000
        assertThat(allStages).doesNotContain("disabled-2");
        assertThat(allStages).containsExactlyInAnyOrder("enabled-1", "enabled-3"); // GH-90000
    }

    @Test
    @DisplayName("empty pipeline produces empty execution plan")
    void emptyPipelineProducesEmptyPlan() { // GH-90000
        ExecutionPlan plan = optimizer.optimize(Pipeline.of()); // GH-90000
        assertThat(plan.parallelGroups()).isEmpty(); // GH-90000
    }

    // ── Throughput ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parallel plan has shorter critical path than sequential baseline")
    void parallelPlanShorterCriticalPath() { // GH-90000
        // Chain: A → C, B → C (A and B can run together) // GH-90000
        Pipeline pipeline = Pipeline.of( // GH-90000
                Stage.of("A", List.of()).withDuration(Duration.ofSeconds(5)), // GH-90000
                Stage.of("B", List.of()).withDuration(Duration.ofSeconds(5)), // GH-90000
                Stage.of("C", List.of("A", "B")).withDuration(Duration.ofSeconds(2)) // GH-90000
        );
        ExecutionPlan plan = optimizer.optimize(pipeline); // GH-90000
        // Sequential: 5 + 5 + 2 = 12s; parallel: max(5, 5) + 2 = 7s // GH-90000
        assertThat(plan.estimatedDuration()).isLessThan(Duration.ofSeconds(12)); // GH-90000
        assertThat(plan.estimatedDuration()).isEqualTo(Duration.ofSeconds(7)); // GH-90000
    }

    @ParameterizedTest
    @CsvSource({ // GH-90000
        "2, 10",   // 2 parallel stages, critical path = 10s
        "4, 10",   // 4 parallel stages, critical path = 10s (all parallel) // GH-90000
    })
    @DisplayName("critical path calculation for N independent stages")
    void criticalPathForNParallelStages(int stageCount, int durationEachSeconds) { // GH-90000
        List<Stage> stages = IntStream.rangeClosed(1, stageCount) // GH-90000
                .mapToObj(i -> Stage.of("s" + i, List.of()).withDuration(Duration.ofSeconds(durationEachSeconds))) // GH-90000
                .toList(); // GH-90000
        Pipeline pipeline = Pipeline.of(stages.toArray(new Stage[0])); // GH-90000
        ExecutionPlan plan = optimizer.optimize(pipeline); // GH-90000
        // All independent — critical path = max single-stage duration
        assertThat(plan.estimatedDuration()).isEqualTo(Duration.ofSeconds(durationEachSeconds)); // GH-90000
    }

    // ── Resource allocation ───────────────────────────────────────────────────

    @Test
    @DisplayName("optimizer respects max parallelism limit")
    void maxParallelismLimitRespected() { // GH-90000
        optimizer.setMaxParallelism(2); // GH-90000
        Pipeline pipeline = Pipeline.of( // GH-90000
                Stage.of("p1", List.of()), // GH-90000
                Stage.of("p2", List.of()), // GH-90000
                Stage.of("p3", List.of()), // GH-90000
                Stage.of("p4", List.of()) // GH-90000
        );
        ExecutionPlan plan = optimizer.optimize(pipeline); // GH-90000
        plan.parallelGroups().forEach(group -> // GH-90000
                assertThat(group.size()).isLessThanOrEqualTo(2)); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    record Stage(String name, List<String> dependencies, boolean enabled, Duration duration) { // GH-90000
        static Stage of(String name, List<String> deps) { // GH-90000
            return new Stage(name, deps, true, Duration.ZERO); // GH-90000
        }

        Stage disable() { // GH-90000
            return new Stage(name, dependencies, false, duration); // GH-90000
        }

        Stage withDuration(Duration d) { // GH-90000
            return new Stage(name, dependencies, enabled, d); // GH-90000
        }
    }

    record Pipeline(List<Stage> stages) { // GH-90000
        static Pipeline of(Stage... stages) { // GH-90000
            return new Pipeline(Arrays.asList(stages)); // GH-90000
        }

        static Pipeline of(List<Stage> stages) { // GH-90000
            return new Pipeline(stages); // GH-90000
        }
    }

    record ExecutionPlan(List<List<String>> parallelGroups, Duration estimatedDuration) {} // GH-90000

    static class PipelineOptimizer {
        private int maxParallelism = Integer.MAX_VALUE;

        void setMaxParallelism(int max) { // GH-90000
            this.maxParallelism = max;
        }

        ExecutionPlan optimize(Pipeline pipeline) { // GH-90000
            List<Stage> enabledStages = pipeline.stages().stream() // GH-90000
                    .filter(Stage::enabled).toList(); // GH-90000
            if (enabledStages.isEmpty()) return new ExecutionPlan(List.of(), Duration.ZERO); // GH-90000

            // Topological sort into levels
            Map<String, Integer> level = new LinkedHashMap<>(); // GH-90000
            for (Stage s : enabledStages) level.put(s.name(), 0); // GH-90000

            boolean changed = true;
            while (changed) { // GH-90000
                changed = false;
                for (Stage s : enabledStages) { // GH-90000
                    int maxDepLevel = s.dependencies().stream() // GH-90000
                            .filter(level::containsKey) // GH-90000
                            .mapToInt(d -> level.getOrDefault(d, 0) + 1) // GH-90000
                            .max().orElse(0); // GH-90000
                    if (maxDepLevel > level.get(s.name())) { // GH-90000
                        level.put(s.name(), maxDepLevel); // GH-90000
                        changed = true;
                    }
                }
            }

            // Group by level, respecting maxParallelism
            Map<Integer, List<String>> grouped = new TreeMap<>(); // GH-90000
            level.forEach((name, lvl) -> // GH-90000
                    grouped.computeIfAbsent(lvl, k -> new ArrayList<>()).add(name)); // GH-90000

            List<List<String>> parallelGroups = new ArrayList<>(); // GH-90000
            for (List<String> group : grouped.values()) { // GH-90000
                // Split into sub-groups respecting maxParallelism
                for (int i = 0; i < group.size(); i += maxParallelism) { // GH-90000
                    parallelGroups.add(group.subList(i, Math.min(i + maxParallelism, group.size()))); // GH-90000
                }
            }

            // Compute critical path
            Map<String, Duration> stageMap = new HashMap<>(); // GH-90000
            enabledStages.forEach(s -> stageMap.put(s.name(), s.duration())); // GH-90000
            Duration criticalPath = Duration.ZERO;
            Map<String, Duration> finishing = new LinkedHashMap<>(); // GH-90000
            for (Stage s : enabledStages) { // GH-90000
                Duration maxDep = s.dependencies().stream() // GH-90000
                        .map(d -> finishing.getOrDefault(d, Duration.ZERO)) // GH-90000
                        .max(Comparator.naturalOrder()).orElse(Duration.ZERO); // GH-90000
                finishing.put(s.name(), maxDep.plus(stageMap.get(s.name()))); // GH-90000
                if (finishing.get(s.name()).compareTo(criticalPath) > 0) // GH-90000
                    criticalPath = finishing.get(s.name()); // GH-90000
            }

            return new ExecutionPlan(parallelGroups, criticalPath); // GH-90000
        }
    }
}
