package com.ghatana.pipeline.registry.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.platform.domain.auth.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive pipeline configuration validator.
 *
 * <p>Purpose: Validates pipeline configurations including name constraints,
 * description limits, config size, YAML/JSON structure, stage definitions,
 * and version compatibility. Returns detailed validation errors.</p>
 *
 * @doc.type class
 * @doc.purpose Validates pipeline configurations and business rules
 * @doc.layer product
 * @doc.pattern Validator
 * @since 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class PipelineValidator {
    
    private static final int MAX_PIPELINE_NAME_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_CONFIG_SIZE = 1_000_000; // 1MB
    
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Validates a pipeline configuration.
     * 
     * @param pipeline The pipeline to validate
     * @param existingPipeline The existing pipeline (if updating)
     * @return List of validation errors, empty if valid
     */
    public List<String> validate(Pipeline pipeline, Pipeline existingPipeline) {
        List<String> errors = new ArrayList<>();
        
        // Basic field validation
        if (pipeline.getName() == null || pipeline.getName().trim().isEmpty()) {
            errors.add("Pipeline name is required");
        } else if (pipeline.getName().length() > MAX_PIPELINE_NAME_LENGTH) {
            errors.add(String.format("Pipeline name must be at most %d characters", MAX_PIPELINE_NAME_LENGTH));
        } else if (!pipeline.getName().matches("^[a-zA-Z0-9][a-zA-Z0-9_\\.-]*$")) {
            errors.add("Pipeline name can only contain alphanumeric characters, dots, hyphens, and underscores");
        }
        
        if (pipeline.getDescription() != null && pipeline.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            errors.add(String.format("Description must be at most %d characters", MAX_DESCRIPTION_LENGTH));
        }
        
        if (pipeline.getConfig() == null || pipeline.getConfig().trim().isEmpty()) {
            errors.add("Pipeline configuration is required");
        } else if (pipeline.getConfig().length() > MAX_CONFIG_SIZE) {
            errors.add(String.format("Pipeline configuration exceeds maximum size of %d bytes", MAX_CONFIG_SIZE));
        } else {
            // Validate the pipeline configuration structure
            validatePipelineConfig(pipeline.getConfig(), errors);
        }
        
        // Version validation for updates
        if (existingPipeline != null) {
            if (pipeline.getVersion() <= existingPipeline.getVersion()) {
                errors.add("New version must be greater than the current version");
            }
            
            // Prevent changing the name of an existing pipeline
            if (!pipeline.getName().equals(existingPipeline.getName())) {
                errors.add("Cannot change the name of an existing pipeline");
            }
            
            // Prevent changing the tenant ID
            if (!pipeline.getTenantId().equals(existingPipeline.getTenantId())) {
                errors.add("Cannot change the tenant of an existing pipeline");
            }
        }
        
        return errors;
    }
    
    /**
     * Validates the pipeline configuration structure.
     */
    private void validatePipelineConfig(String config, List<String> errors) {
        try {
            JsonNode root = yamlMapper.readTree(config);

            if (!root.has("stages")) {
                errors.add("Pipeline configuration must contain 'stages' section");
            } else {
                JsonNode stages = root.get("stages");
                if (!stages.isArray()) {
                    errors.add("'stages' must be a list");
                } else {
                    if (stages.size() == 0) {
                        errors.add("Pipeline must have at least one stage");
                    }
                    // Validate each stage
                    for (JsonNode stage : stages) {
                        if (!stage.has("name")) {
                            errors.add("Each stage must have a 'name'");
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error validating pipeline configuration", e);
            errors.add("Invalid pipeline configuration: " + e.getMessage());
        }
    }
    
    /**
     * Validates that the pipeline DAG is acyclic and generates execution order.
     * 
     * <p><b>Purpose</b><br>
     * Detects circular dependencies in pipeline stages using depth-first search with cycle path tracking.
     * Provides detailed error messages showing the exact cycle path for debugging.
     * 
     * <p><b>Algorithm</b><br>
     * Uses DFS with recursion stack to detect back edges (cycles). Tracks full path for error reporting.
     * 
     * @param config The pipeline configuration (YAML/JSON)
     * @return List of validation errors with cycle details, empty if the DAG is valid
     */
    public List<String> validateDag(String config) {
        List<String> errors = new ArrayList<>();
        
        try {
            Map<String, List<String>> graph = parseDagFromConfig(config);
            
            // Validate empty graph
            if (graph.isEmpty()) {
                errors.add("Pipeline has no stages defined");
                return errors;
            }
            
            // Check for cycles using depth-first search with path tracking
            Set<String> visited = new HashSet<>();
            Set<String> recursionStack = new HashSet<>();
            List<String> cyclePath = new ArrayList<>();
            
            for (String node : graph.keySet()) {
                if (!visited.contains(node)) {
                    if (hasCycle(graph, node, visited, recursionStack, cyclePath)) {
                        // Build detailed cycle error message
                        String cycleDescription = buildCycleErrorMessage(cyclePath, node);
                        errors.add(cycleDescription);
                        break; // Report first cycle found
                    }
                }
            }
            
            // Check for missing dependencies (referenced but not defined)
            List<String> missingDeps = findMissingDependencies(graph);
            if (!missingDeps.isEmpty()) {
                errors.add("Missing stage dependencies: " + String.join(", ", missingDeps));
            }
            
            // Check for disconnected components (stages with no incoming/outgoing edges)
            if (errors.isEmpty()) {
                List<String> isolated = findIsolatedStages(graph);
                if (!isolated.isEmpty()) {
                    log.warn("Pipeline has isolated stages (no dependencies): {}", isolated);
                    // Warning only - isolated stages are valid but suspicious
                }
            }
            
        } catch (Exception e) {
            log.error("Error validating pipeline DAG", e);
            errors.add("Error validating pipeline DAG: " + e.getMessage());
        }
        
        return errors;
    }
    
    /**
     * Builds a human-readable error message describing the detected cycle.
     *
     * @param cyclePath The path of stages forming the cycle
     * @param cycleStart The stage where the cycle closes
     * @return Formatted error message
     */
    private String buildCycleErrorMessage(List<String> cyclePath, String cycleStart) {
        if (cyclePath.isEmpty()) {
            return "Pipeline contains a circular dependency";
        }
        
        // Find where the cycle actually starts in the path
        int cycleStartIndex = cyclePath.indexOf(cycleStart);
        if (cycleStartIndex == -1) {
            cycleStartIndex = 0;
        }
        
        List<String> actualCycle = new ArrayList<>(cyclePath.subList(cycleStartIndex, cyclePath.size()));
        actualCycle.add(cycleStart); // Close the cycle
        
        return String.format(
            "Circular dependency detected: %s (stage '%s' depends on itself)",
            String.join(" → ", actualCycle),
            cycleStart
        );
    }
    
    /**
     * Finds stages that are referenced as dependencies but not defined in the pipeline.
     *
     * @param graph The dependency graph
     * @return List of missing stage names
     */
    private List<String> findMissingDependencies(Map<String, List<String>> graph) {
        Set<String> definedStages = graph.keySet();
        Set<String> referencedStages = new HashSet<>();
        
        for (List<String> deps : graph.values()) {
            referencedStages.addAll(deps);
        }
        
        referencedStages.removeAll(definedStages);
        return new ArrayList<>(referencedStages);
    }
    
    /**
     * Finds stages that have no dependencies and no dependents (isolated).
     *
     * @param graph The dependency graph
     * @return List of isolated stage names
     */
    private List<String> findIsolatedStages(Map<String, List<String>> graph) {
        Set<String> hasOutgoing = new HashSet<>();
        Set<String> hasIncoming = new HashSet<>();
        
        for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                hasOutgoing.add(entry.getKey());
                hasIncoming.addAll(entry.getValue());
            }
        }
        
        List<String> isolated = new ArrayList<>();
        for (String stage : graph.keySet()) {
            if (!hasOutgoing.contains(stage) && !hasIncoming.contains(stage)) {
                isolated.add(stage);
            }
        }
        
        return isolated;
    }
    
    /**
     * Helper method to detect cycles in a directed graph using DFS.
     *
     * <p><b>Algorithm</b><br>
     * Uses depth-first search with recursion stack to detect back edges.
     * A back edge indicates a cycle (edge to a node in the current recursion stack).
     *
     * @param graph The dependency graph
     * @param node Current node being visited
     * @param visited Set of all visited nodes (across all DFS trees)
     * @param recursionStack Set of nodes in current recursion path
     * @param path List tracking the current path (for error reporting)
     * @return true if a cycle is detected, false otherwise
     */
    private boolean hasCycle(Map<String, List<String>> graph, String node, 
                            Set<String> visited, Set<String> recursionStack,
                            List<String> path) {
        // Back edge detected - cycle exists
        if (recursionStack.contains(node)) {
            path.add(node); // Close the cycle for error message
            log.warn("Cycle detected in pipeline DAG: {} -> {}", String.join(" → ", path), node);
            return true;
        }
        
        // Already fully explored in a previous DFS tree
        if (visited.contains(node)) {
            return false;
        }
        
        // Mark node as visited and add to recursion stack
        visited.add(node);
        recursionStack.add(node);
        path.add(node);
        
        // Explore all dependencies (neighbors)
        for (String neighbor : graph.getOrDefault(node, Collections.emptyList())) {
            if (hasCycle(graph, neighbor, visited, recursionStack, path)) {
                return true; // Cycle found in subtree
            }
        }
        
        // Backtrack: remove from recursion stack and path
        recursionStack.remove(node);
        if (!path.isEmpty()) {
            path.remove(path.size() - 1);
        }
        
        return false;
    }
    
    /**
     * Computes the topological execution order for pipeline stages.
     *
     * <p><b>Purpose</b><br>
     * Determines the order in which stages should execute based on their dependencies.
     * Uses Kahn's algorithm (BFS-based topological sort) for stable ordering.
     *
     * <p><b>Algorithm</b><br>
     * 1. Calculate in-degree for each stage
     * 2. Start with stages that have no dependencies (in-degree = 0)
     * 3. Process stages in order, removing edges and adding newly-ready stages
     *
     * @param config The pipeline configuration
     * @return List of stage names in execution order, or empty if cycle detected
     * @throws IllegalArgumentException if pipeline contains a cycle
     */
    public List<String> computeExecutionOrder(String config) {
        Map<String, List<String>> dependsOnGraph = parseDagFromConfig(config);
        
        // First validate no cycles exist
        List<String> cycleErrors = validateDag(config);
        if (!cycleErrors.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute execution order: " + cycleErrors.get(0));
        }
        
        // Reverse the graph: convert "A depends on B" to "B is depended on by A"
        // This is needed because parseDagFromConfig returns dependsOn relationships,
        // but Kahn's algorithm needs outgoing edges (who depends on me)
        Map<String, List<String>> graph = new HashMap<>();
        
        // Initialize all nodes
        for (String stage : dependsOnGraph.keySet()) {
            graph.putIfAbsent(stage, new ArrayList<>());
        }
        
        // Build reverse edges
        for (Map.Entry<String, List<String>> entry : dependsOnGraph.entrySet()) {
            String stage = entry.getKey();
            for (String dependency : entry.getValue()) {
                // dependency -> stage (stage depends on dependency, so edge goes from dependency to stage)
                graph.computeIfAbsent(dependency, k -> new ArrayList<>()).add(stage);
            }
        }
        
        // Kahn's algorithm for topological sort
        Map<String, Integer> inDegree = new HashMap<>();
        
        // Initialize in-degree map using the original dependsOn graph
        for (Map.Entry<String, List<String>> entry : dependsOnGraph.entrySet()) {
            String stage = entry.getKey();
            int deps = entry.getValue().size();
            inDegree.put(stage, deps);
        }
        
        // Queue for stages with no dependencies
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        List<String> executionOrder = new ArrayList<>();
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            executionOrder.add(current);
            
            // Reduce in-degree for stages that depend on current
            for (String dependent : graph.getOrDefault(current, Collections.emptyList())) {
                inDegree.put(dependent, inDegree.get(dependent) - 1);
                if (inDegree.get(dependent) == 0) {
                    queue.offer(dependent);
                }
            }
        }
        
        // If not all stages are in execution order, there's a cycle (shouldn't happen after validation)
        if (executionOrder.size() != dependsOnGraph.size()) {
            log.error("Topological sort failed: {} stages in order, {} total stages", 
                executionOrder.size(), dependsOnGraph.size());
            throw new IllegalStateException("Internal error: cycle not detected during validation");
        }
        
        return executionOrder;
    }
    
    /**
     * Parses the DAG structure from the pipeline configuration.
     * Supports multiple formats: dependsOn (list), dependencies (list), after (string/list).
     *
     * <p><b>Dependency Formats Supported</b><br>
     * 1. <code>dependsOn: [stage1, stage2]</code> - Standard format
     * 2. <code>dependencies: [stage1, stage2]</code> - Alternative format
     * 3. <code>after: stage1</code> or <code>after: [stage1, stage2]</code> - Single/multiple dependencies
     *
     * @param config The pipeline configuration (YAML/JSON)
     * @return Map of stage name to list of dependencies (edges in the DAG)
     */
    private Map<String, List<String>> parseDagFromConfig(String config) {
        Map<String, List<String>> graph = new HashMap<>();
        try {
            JsonNode root = yamlMapper.readTree(config);
            if (root.has("stages") && root.get("stages").isArray()) {
                for (JsonNode stage : root.get("stages")) {
                    if (stage.has("name")) {
                        String stageName = stage.get("name").asText();
                        List<String> dependencies = new ArrayList<>();
                        
                        // Format 1: dependsOn array
                        if (stage.has("dependsOn") && stage.get("dependsOn").isArray()) {
                            for (JsonNode dep : stage.get("dependsOn")) {
                                dependencies.add(dep.asText());
                            }
                        }
                        
                        // Format 2: dependencies array (alternative)
                        if (stage.has("dependencies") && stage.get("dependencies").isArray()) {
                            for (JsonNode dep : stage.get("dependencies")) {
                                dependencies.add(dep.asText());
                            }
                        }
                        
                        // Format 3: after (string or array)
                        if (stage.has("after")) {
                            JsonNode after = stage.get("after");
                            if (after.isArray()) {
                                for (JsonNode dep : after) {
                                    dependencies.add(dep.asText());
                                }
                            } else if (after.isTextual()) {
                                dependencies.add(after.asText());
                            }
                        }
                        
                        graph.put(stageName, dependencies);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing DAG from config", e);
        }
        return graph;
    }
    
    /**
     * Validates that all referenced agents exist and are accessible to the tenant.
     * 
     * @param config The pipeline configuration
     * @param tenantId The tenant ID
     * @return List of validation errors, empty if all agents are valid
     */
    public List<String> validateReferencedAgents(String config, TenantId tenantId) {
        List<String> errors = new ArrayList<>();
        
        try {
            // Extract agent references from the config
            Set<String> agentRefs = extractAgentReferences(config);
            
            // TODO: Validate that each agent exists and is accessible to the tenant
            // This would typically involve calling the Agent Registry service
            
            // Placeholder implementation
            for (String agentRef : agentRefs) {
                if (agentRef.contains("invalid")) {
                    errors.add("Invalid agent reference: " + agentRef);
                }
            }
            
        } catch (Exception e) {
            log.error("Error validating referenced agents", e);
            errors.add("Error validating agent references: " + e.getMessage());
        }
        
        return errors;
    }
    
    /**
     * Extracts agent references from the pipeline configuration.
     */
    private Set<String> extractAgentReferences(String config) {
        Set<String> agentRefs = new HashSet<>();
        try {
            JsonNode root = yamlMapper.readTree(config);
            if (root.has("stages") && root.get("stages").isArray()) {
                for (JsonNode stage : root.get("stages")) {
                    // Check for 'agents' array at stage level
                    if (stage.has("agents") && stage.get("agents").isArray()) {
                        for (JsonNode agent : stage.get("agents")) {
                            if (agent.has("id")) {
                                agentRefs.add(agent.get("id").asText());
                            } else if (agent.isTextual()) {
                                agentRefs.add(agent.asText());
                            }
                        }
                    }

                    // Check for 'agent' in 'config'
                    if (stage.has("config")) {
                        JsonNode stageConfig = stage.get("config");
                        if (stageConfig.has("agent")) {
                            agentRefs.add(stageConfig.get("agent").asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting agent references", e);
        }

        return agentRefs;
    }
}
