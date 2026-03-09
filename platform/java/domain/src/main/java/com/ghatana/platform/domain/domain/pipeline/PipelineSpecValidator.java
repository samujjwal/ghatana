package com.ghatana.platform.domain.domain.pipeline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Structural validator for {@link PipelineSpec}.
 *
 * <p>This validator performs basic DAG validation on the declarative
 * specification:
 * <ul>
 *   <li>Ensures there is at least one stage</li>
 *   <li>Ensures stage names are unique and non-null/non-blank</li>
 *   <li>Ensures {@link PipelineStageSpec.StageType} is set when stages exist</li>
 *   <li>Ensures all {@link PipelineEdgeSpec} references point to known stages</li>
 *   <li>Checks for cycles in the stage graph defined by edges</li>
 * </ul>
 *
 * <p>Connector-specific validation is intentionally left to higher layers
 * (for example, when {@code ConnectorSpec} is integrated), so this class
 * focuses on structural properties that are independent of runtime concerns.
 *
 * @doc.type utility
 * @doc.layer domain
 * @doc.purpose structural validation for declarative PipelineSpec DAGs
 * @doc.pattern Validator
 */
public final class PipelineSpecValidator {

    private static final String ERROR_PREFIX = "PipelineSpec validation error: ";

    public PipelineSpecValidator() {
        // stateless utility
    }

    /**
     * Validate the given {@link PipelineSpec} and return a
     * {@link PipelineSpecValidationResult} describing any structural issues.
     */
    public PipelineSpecValidationResult validate(PipelineSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");

        List<String> errors = new ArrayList<>();

        List<PipelineStageSpec> stages = spec.getStages();
        if (stages == null || stages.isEmpty()) {
            errors.add(ERROR_PREFIX + "must contain at least one stage");
            return PipelineSpecValidationResult.invalid(errors);
        }

        // Validate connector references from stages (connectorId -> connectors.id)
        List<ConnectorSpec> connectors = spec.getConnectors();
        Set<String> connectorIds = new HashSet<>();
        if (connectors != null) {
            for (int i = 0; i < connectors.size(); i++) {
                ConnectorSpec connector = connectors.get(i);
                if (connector == null) {
                    errors.add(ERROR_PREFIX + "connector at index " + i + " is null");
                    continue;
                }
                String id = connector.getId();
                if (id == null || id.trim().isEmpty()) {
                    errors.add(ERROR_PREFIX + "connector at index " + i + " has null/blank id");
                } else if (!connectorIds.add(id)) {
                    errors.add(ERROR_PREFIX + "duplicate connector id: '" + id + "'");
                }
            }
        }

        // Now check each stage.connectorId against defined connector ids.
        // If a stage declares a connectorId but no connectors are defined, that is also an error.
        for (int i = 0; i < stages.size(); i++) {
            PipelineStageSpec stage = stages.get(i);
            if (stage == null) {
                continue;
            }

            String connectorId = stage.getConnectorId();
            if (connectorId == null || connectorId.isBlank()) {
                continue;
            }

            if (connectorIds.isEmpty() || !connectorIds.contains(connectorId)) {
                errors.add(ERROR_PREFIX + "stage '" + stage.getName() + "' references unknown connectorId '" + connectorId + "'");
            }
        }

        // Validate stage names and types and build name -> index map
        Map<String, Integer> stageIndexByName = new HashMap<>();
        for (int i = 0; i < stages.size(); i++) {
            PipelineStageSpec stage = stages.get(i);
            if (stage == null) {
                errors.add(ERROR_PREFIX + "stage at index " + i + " is null");
                continue;
            }

            String name = stage.getName();
            if (name == null || name.trim().isEmpty()) {
                errors.add(ERROR_PREFIX + "stage at index " + i + " has null/blank name");
            } else if (stageIndexByName.containsKey(name)) {
                errors.add(ERROR_PREFIX + "duplicate stage name: '" + name + "'");
            } else {
                stageIndexByName.put(name, i);
            }

            if (stage.getType() == null) {
                errors.add(ERROR_PREFIX + "stage '" + name + "' has null type (StageType must be set)");
            }
        }

        // If we have no valid named stages, return early with accumulated errors
        if (stageIndexByName.isEmpty()) {
            return PipelineSpecValidationResult.invalid(errors);
        }

        // Build adjacency list from edges and validate references
        Map<String, List<String>> adjacency = new HashMap<>();
        for (String stageName : stageIndexByName.keySet()) {
            adjacency.put(stageName, new ArrayList<>());
        }

        List<PipelineEdgeSpec> edges = spec.getEdges();
        if (edges != null) {
            for (int i = 0; i < edges.size(); i++) {
                PipelineEdgeSpec edge = edges.get(i);
                if (edge == null) {
                    errors.add(ERROR_PREFIX + "edge at index " + i + " is null");
                    continue;
                }

                String from = edge.getFromStageId();
                String to = edge.getToStageId();

                if (from == null || from.trim().isEmpty()) {
                    errors.add(ERROR_PREFIX + "edge at index " + i + " has null/blank fromStageId");
                } else if (!stageIndexByName.containsKey(from)) {
                    errors.add(ERROR_PREFIX + "edge at index " + i + " references unknown fromStageId '" + from + "'");
                }

                if (to == null || to.trim().isEmpty()) {
                    errors.add(ERROR_PREFIX + "edge at index " + i + " has null/blank toStageId");
                } else if (!stageIndexByName.containsKey(to)) {
                    errors.add(ERROR_PREFIX + "edge at index " + i + " references unknown toStageId '" + to + "'");
                }

                if (from != null && to != null
                    && stageIndexByName.containsKey(from)
                    && stageIndexByName.containsKey(to)) {
                    adjacency.get(from).add(to);
                }
            }
        }

        // Detect cycles using DFS
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<String> path = new ArrayDeque<>();

        for (String node : adjacency.keySet()) {
            if (!visited.contains(node) && hasCycle(node, adjacency, visiting, visited, path, errors)) {
                break; // cycle errors already recorded
            }
        }

        if (errors.isEmpty()) {
            return PipelineSpecValidationResult.ok();
        }
        return PipelineSpecValidationResult.invalid(errors);
    }

    private boolean hasCycle(
        String node,
        Map<String, List<String>> adjacency,
        Set<String> visiting,
        Set<String> visited,
        Deque<String> path,
        List<String> errors
    ) {
        if (visiting.contains(node)) {
            // Found a back edge; record cycle
            path.addLast(node);
            errors.add(ERROR_PREFIX + "cycle detected in stage DAG: " + String.join(" -> ", path));
            path.removeLast();
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }

        visiting.add(node);
        visited.add(node);
        path.addLast(node);

        for (String neighbor : adjacency.getOrDefault(node, List.of())) {
            if (hasCycle(neighbor, adjacency, visiting, visited, path, errors)) {
                return true;
            }
        }

        visiting.remove(node);
        path.removeLast();
        return false;
    }
}
