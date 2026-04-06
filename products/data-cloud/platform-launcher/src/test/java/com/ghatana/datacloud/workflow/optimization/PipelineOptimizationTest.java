/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        optimizer = new PipelineOptimizer();
    }

    // ── Parallel scheduling ───────────────────────────────────────────────────

    @Test
    @DisplayName("independent stages are scheduled in parallel")
    void independentStagesScheduledInParallel() {
        Pipeline pipeline = Pipeline.of(
                Stage.of("A", List.of()),          // no dependencies
                Stage.of("B", List.of()),          // no dependencies
                Stage.of("C", List.of("A", "B"))   // depends on A and B
        );
        ExecutionPlan plan = optimizer.optimize(pipeline);
        // Stages A and B should be in the same parallel group
        assertThat(plan.parallelGroups()).hasSizeGreaterThanOrEqualTo(2);
        List<String> firstGroup = plan.parallelGroups().get(0);
        assertThat(firstGroup).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    @DisplayName("stage with dependency is executed after its dependency")
    void dependentStageExecutedAfterDependency() {
        Pipeline pipeline = Pipeline.of(
                Stage.of("X", List.of()),
                Stage.of("Y", List.of("X"))
        );
        ExecutionPlan plan = optimizer.optimize(pipeline);
        List<List<String>> groups = plan.parallelGroups();
        // X must appear in an earlier group than Y
        int xGroup = -1, yGroup = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).contains("X")) xGroup = i;
            if (groups.get(i).contains("Y")) yGroup = i;
        }
        assertThat(xGroup).isLessThan(yGroup);
    }

    @Test
    @DisplayName("single-stage pipeline produces a single execution group")
    void singleStagePipelineHasOneGroup() {
        Pipeline pipeline = Pipeline.of(Stage.of("solo", List.of()));
        ExecutionPlan plan = optimizer.optimize(pipeline);
        assertThat(plan.parallelGroups()).hasSize(1);
    }

    // ── Stage pruning ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("disabled stages are pruned from the execution plan")
    void disabledStagesArePruned() {
        Pipeline pipeline = Pipeline.of(
                Stage.of("enabled-1", List.of()),
                Stage.of("disabled-2", List.of()).disable(),
                Stage.of("enabled-3", List.of())
        );
        ExecutionPlan plan = optimizer.optimize(pipeline);
        List<String> allStages = plan.parallelGroups().stream()
                .flatMap(Collection::stream).toList();
        assertThat(allStages).doesNotContain("disabled-2");
        assertThat(allStages).containsExactlyInAnyOrder("enabled-1", "enabled-3");
    }

    @Test
    @DisplayName("empty pipeline produces empty execution plan")
    void emptyPipelineProducesEmptyPlan() {
        ExecutionPlan plan = optimizer.optimize(Pipeline.of());
        assertThat(plan.parallelGroups()).isEmpty();
    }

    // ── Throughput ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parallel plan has shorter critical path than sequential baseline")
    void parallelPlanShorterCriticalPath() {
        // Chain: A → C, B → C (A and B can run together)
        Pipeline pipeline = Pipeline.of(
                Stage.of("A", List.of()).withDuration(Duration.ofSeconds(5)),
                Stage.of("B", List.of()).withDuration(Duration.ofSeconds(5)),
                Stage.of("C", List.of("A", "B")).withDuration(Duration.ofSeconds(2))
        );
        ExecutionPlan plan = optimizer.optimize(pipeline);
        // Sequential: 5 + 5 + 2 = 12s; parallel: max(5, 5) + 2 = 7s
        assertThat(plan.estimatedDuration()).isLessThan(Duration.ofSeconds(12));
        assertThat(plan.estimatedDuration()).isEqualTo(Duration.ofSeconds(7));
    }

    @ParameterizedTest
    @CsvSource({
        "2, 10",   // 2 parallel stages, critical path = 10s
        "4, 10",   // 4 parallel stages, critical path = 10s (all parallel)
    })
    @DisplayName("critical path calculation for N independent stages")
    void criticalPathForNParallelStages(int stageCount, int durationEachSeconds) {
        List<Stage> stages = IntStream.rangeClosed(1, stageCount)
                .mapToObj(i -> Stage.of("s" + i, List.of()).withDuration(Duration.ofSeconds(durationEachSeconds)))
                .toList();
        Pipeline pipeline = Pipeline.of(stages.toArray(new Stage[0]));
        ExecutionPlan plan = optimizer.optimize(pipeline);
        // All independent — critical path = max single-stage duration
        assertThat(plan.estimatedDuration()).isEqualTo(Duration.ofSeconds(durationEachSeconds));
    }

    // ── Resource allocation ───────────────────────────────────────────────────

    @Test
    @DisplayName("optimizer respects max parallelism limit")
    void maxParallelismLimitRespected() {
        optimizer.setMaxParallelism(2);
        Pipeline pipeline = Pipeline.of(
                Stage.of("p1", List.of()),
                Stage.of("p2", List.of()),
                Stage.of("p3", List.of()),
                Stage.of("p4", List.of())
        );
        ExecutionPlan plan = optimizer.optimize(pipeline);
        plan.parallelGroups().forEach(group ->
                assertThat(group.size()).isLessThanOrEqualTo(2));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    record Stage(String name, List<String> dependencies, boolean enabled, Duration duration) {
        static Stage of(String name, List<String> deps) {
            return new Stage(name, deps, true, Duration.ZERO);
        }

        Stage disable() {
            return new Stage(name, dependencies, false, duration);
        }

        Stage withDuration(Duration d) {
            return new Stage(name, dependencies, enabled, d);
        }
    }

    record Pipeline(List<Stage> stages) {
        static Pipeline of(Stage... stages) {
            return new Pipeline(Arrays.asList(stages));
        }

        static Pipeline of(List<Stage> stages) {
            return new Pipeline(stages);
        }
    }

    record ExecutionPlan(List<List<String>> parallelGroups, Duration estimatedDuration) {}

    static class PipelineOptimizer {
        private int maxParallelism = Integer.MAX_VALUE;

        void setMaxParallelism(int max) {
            this.maxParallelism = max;
        }

        ExecutionPlan optimize(Pipeline pipeline) {
            List<Stage> enabledStages = pipeline.stages().stream()
                    .filter(Stage::enabled).toList();
            if (enabledStages.isEmpty()) return new ExecutionPlan(List.of(), Duration.ZERO);

            // Topological sort into levels
            Map<String, Integer> level = new LinkedHashMap<>();
            for (Stage s : enabledStages) level.put(s.name(), 0);

            boolean changed = true;
            while (changed) {
                changed = false;
                for (Stage s : enabledStages) {
                    int maxDepLevel = s.dependencies().stream()
                            .filter(level::containsKey)
                            .mapToInt(d -> level.getOrDefault(d, 0) + 1)
                            .max().orElse(0);
                    if (maxDepLevel > level.get(s.name())) {
                        level.put(s.name(), maxDepLevel);
                        changed = true;
                    }
                }
            }

            // Group by level, respecting maxParallelism
            Map<Integer, List<String>> grouped = new TreeMap<>();
            level.forEach((name, lvl) ->
                    grouped.computeIfAbsent(lvl, k -> new ArrayList<>()).add(name));

            List<List<String>> parallelGroups = new ArrayList<>();
            for (List<String> group : grouped.values()) {
                // Split into sub-groups respecting maxParallelism
                for (int i = 0; i < group.size(); i += maxParallelism) {
                    parallelGroups.add(group.subList(i, Math.min(i + maxParallelism, group.size())));
                }
            }

            // Compute critical path
            Map<String, Duration> stageMap = new HashMap<>();
            enabledStages.forEach(s -> stageMap.put(s.name(), s.duration()));
            Duration criticalPath = Duration.ZERO;
            Map<String, Duration> finishing = new LinkedHashMap<>();
            for (Stage s : enabledStages) {
                Duration maxDep = s.dependencies().stream()
                        .map(d -> finishing.getOrDefault(d, Duration.ZERO))
                        .max(Comparator.naturalOrder()).orElse(Duration.ZERO);
                finishing.put(s.name(), maxDep.plus(stageMap.get(s.name())));
                if (finishing.get(s.name()).compareTo(criticalPath) > 0)
                    criticalPath = finishing.get(s.name());
            }

            return new ExecutionPlan(parallelGroups, criticalPath);
        }
    }
}
