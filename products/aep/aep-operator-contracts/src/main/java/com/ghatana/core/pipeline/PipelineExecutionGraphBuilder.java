package com.ghatana.core.pipeline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Builds the precomputed execution graph used by {@link PipelineExecutionEngine}.
 */
final class PipelineExecutionGraphBuilder {

    PipelineExecutionGraph build(Pipeline pipeline) {
        List<PipelineStage> stages = pipeline.getStages();
        List<PipelineEdge> edges = pipeline.getEdges();

        Map<String, PipelineStage> stageMap = new LinkedHashMap<>();
        for (PipelineStage stage : stages) {
            stageMap.put(stage.stageId(), stage);
        }

        Map<String, List<PipelineEdgeTarget>> primaryAdj = new LinkedHashMap<>();
        Map<String, List<PipelineEdgeTarget>> errorAdj = new LinkedHashMap<>();
        Map<String, List<PipelineEdgeTarget>> fallbackAdj = new LinkedHashMap<>();
        Map<String, List<PipelineEdgeTarget>> broadcastAdj = new LinkedHashMap<>();
        Set<String> hasIncomingEdge = new HashSet<>();

        for (PipelineStage stage : stages) {
            primaryAdj.put(stage.stageId(), new ArrayList<>());
            errorAdj.put(stage.stageId(), new ArrayList<>());
            fallbackAdj.put(stage.stageId(), new ArrayList<>());
            broadcastAdj.put(stage.stageId(), new ArrayList<>());
        }

        for (PipelineEdge edge : edges) {
            PipelineEdgeTarget target = new PipelineEdgeTarget(edge.to(), edge.label());
            hasIncomingEdge.add(edge.to());
            switch (edge.label()) {
                case PipelineEdge.LABEL_PRIMARY -> primaryAdj.get(edge.from()).add(target);
                case PipelineEdge.LABEL_ERROR -> errorAdj.get(edge.from()).add(target);
                case PipelineEdge.LABEL_FALLBACK -> fallbackAdj.get(edge.from()).add(target);
                case PipelineEdge.LABEL_BROADCAST -> broadcastAdj.get(edge.from()).add(target);
                default -> primaryAdj.get(edge.from()).add(target);
            }
        }

        List<String> sourceStages = new ArrayList<>();
        for (PipelineStage stage : stages) {
            if (!hasIncomingEdge.contains(stage.stageId())) {
                sourceStages.add(stage.stageId());
            }
        }

        List<String> terminalStages = new ArrayList<>();
        for (PipelineStage stage : stages) {
            String stageId = stage.stageId();
            if (primaryAdj.get(stageId).isEmpty() && broadcastAdj.get(stageId).isEmpty()) {
                terminalStages.add(stageId);
            }
        }

        return new PipelineExecutionGraph(
            stageMap,
            primaryAdj,
            errorAdj,
            fallbackAdj,
            broadcastAdj,
            sourceStages,
            terminalStages,
            topologicalSort(stages, edges)
        );
    }

    private List<String> topologicalSort(List<PipelineStage> stages, List<PipelineEdge> edges) {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> adjacency = new LinkedHashMap<>();

        for (PipelineStage stage : stages) {
            inDegree.put(stage.stageId(), 0);
            adjacency.put(stage.stageId(), new ArrayList<>());
        }

        for (PipelineEdge edge : edges) {
            String label = edge.label();
            if (PipelineEdge.LABEL_PRIMARY.equals(label) || PipelineEdge.LABEL_BROADCAST.equals(label)) {
                adjacency.get(edge.from()).add(edge.to());
                inDegree.merge(edge.to(), 1, Integer::sum);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            for (String neighbor : adjacency.getOrDefault(current, List.of())) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (result.size() != stages.size()) {
            throw new IllegalStateException("Pipeline contains a cycle — topological sort failed");
        }
        return result;
    }
}