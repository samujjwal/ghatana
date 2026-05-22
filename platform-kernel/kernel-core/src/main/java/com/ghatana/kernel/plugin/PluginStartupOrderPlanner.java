package com.ghatana.kernel.plugin;

import java.util.*;

/**
 * Planner for determining plugin startup order based on dependency graph analysis.
 *
 * <p>This planner uses the {@link PluginDependencyGraph} to analyze plugin dependencies,
 * detect cycles, and compute a valid startup order. It provides detailed startup plans
 * with validation results and dependency information.</p>
 *
 * @doc.type class
 * @doc.purpose Startup order planner for plugins with dependency validation
 * @doc.layer kernel
 * @doc.pattern Planner
 */
public final class PluginStartupOrderPlanner {

    private final PluginDependencyGraph dependencyGraph;

    public PluginStartupOrderPlanner(PluginDependencyGraph dependencyGraph) {
        this.dependencyGraph = Objects.requireNonNull(dependencyGraph, "dependencyGraph must not be null");
    }

    /**
     * Plans the startup order for all plugins.
     *
     * @return a StartupPlan containing the order and validation results
     */
    public StartupPlan planStartup() {
        PluginDependencyGraph.CycleDetectionResult cycleResult = dependencyGraph.detectCycles();
        
        if (cycleResult.hasCycle()) {
            return new StartupPlan(
                List.of(),
                false,
                "Cycle detected in plugin dependencies: " + cycleResult.cyclePath(),
                cycleResult.cyclePath(),
                Map.of()
            );
        }

        try {
            List<String> startupOrder = dependencyGraph.computeStartupOrder();
            Map<String, StartupStage> stages = buildStartupStages(startupOrder);
            
            return new StartupPlan(
                startupOrder,
                true,
                null,
                List.of(),
                stages
            );
        } catch (Exception error) {
            return new StartupPlan(
                List.of(),
                false,
                "Failed to compute startup order: " + error.getMessage(),
                List.of(),
                Map.of()
            );
        }
    }

    /**
     * Builds startup stages for parallel execution where possible.
     *
     * <p>Plugins that have no dependencies on each other can be started in parallel.
     * This method groups plugins into stages where each stage can be executed in parallel.</p>
     *
     * @param startupOrder the topological order
     * @return map of stage number to plugins in that stage
     */
    private Map<String, StartupStage> buildStartupStages(List<String> startupOrder) {
        Map<String, StartupStage> stages = new LinkedHashMap<>();
        Map<String, Integer> pluginStageMap = new HashMap<>();
        int currentStage = 0;

        for (String pluginId : startupOrder) {
            Set<String> dependencies = dependencyGraph.getDependencies(pluginId);
            
            // Determine the stage based on the maximum stage of dependencies
            int maxDependencyStage = -1;
            for (String dependency : dependencies) {
                Integer depStage = pluginStageMap.get(dependency);
                if (depStage != null && depStage > maxDependencyStage) {
                    maxDependencyStage = depStage;
                }
            }
            
            int pluginStage = maxDependencyStage + 1;
            pluginStageMap.put(pluginId, pluginStage);
            
            String stageKey = "stage-" + pluginStage;
            StartupStage stage = stages.get(stageKey);
            if (stage == null) {
                stage = new StartupStage(stageKey, pluginStage, new ArrayList<>());
                stages.put(stageKey, stage);
            }
            stage.plugins().add(pluginId);
        }

        return stages;
    }

    /**
     * Result of startup planning.
     */
    public record StartupPlan(
        List<String> startupOrder,
        boolean isValid,
        String validationError,
        List<String> cyclePath,
        Map<String, StartupStage> stages
    ) {
        public StartupPlan {
            if (startupOrder == null) {
                startupOrder = List.of();
            }
            if (cyclePath == null) {
                cyclePath = List.of();
            }
            if (stages == null) {
                stages = Map.of();
            }
        }
    }

    /**
     * A startup stage containing plugins that can be started in parallel.
     */
    public record StartupStage(
        String stageId,
        int stageNumber,
        List<String> plugins
    ) {
        public StartupStage {
            if (plugins == null) {
                plugins = List.of();
            }
        }
    }
}
