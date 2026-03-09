package com.ghatana.yappc.core.graph;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Project graph for adapter discovery and task visualization. Week 1, Day 4 deliverable: graph
 * command outputs adapter task graph JSON.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
/**
 * ProjectGraph component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose ProjectGraph component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class ProjectGraph {

    private List<TaskNode> tasks = new ArrayList<>();
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    /**
 * Discover available adapters using Java ServiceLoader. */
    public void discoverAdapters() {
        // For now, add some mock adapters - Week 3 will implement real discovery
        tasks =
                Arrays.asList(
                        new TaskNode("gradle", "init", List.of()),
                        new TaskNode("nx", "workspace", List.of("gradle")),
                        new TaskNode("pnpm", "install", List.of()),
                        new TaskNode("java-pack", "generate", List.of("gradle")),
                        new TaskNode("react-pack", "generate", List.of("nx", "pnpm")));
    }

    /**
 * Export graph as JSON string. */
    public String toJson() {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (Exception e) {
            return "{\"error\": \"Failed to serialize graph: " + e.getMessage() + "\"}";
        }
    }

    /**
 * Export graph in DOT format for Graphviz. */
    public String toDot() {
        StringBuilder dot = new StringBuilder();
        dot.append("digraph ProjectGraph {\\n");
        dot.append("  rankdir=TB;\\n");
        dot.append("  node [shape=box];\\n\\n");

        for (TaskNode task : tasks) {
            String nodeId = task.adapterId() + "_" + task.taskId();
            dot.append("  \"")
                    .append(nodeId)
                    .append("\" [label=\"")
                    .append(task.adapterId())
                    .append(":")
                    .append(task.taskId())
                    .append("\"];\\n");
            for (String dep : task.dependsOn()) {
                dot.append("  \"").append(dep).append("\" -> \"").append(nodeId).append("\";\\n");
            }
        }

        dot.append("}");
        return dot.toString();
    }

    /**
 * Export graph in Mermaid format for documentation. */
    public String toMermaid() {
        StringBuilder mermaid = new StringBuilder();
        mermaid.append("graph TD\\n");

        for (TaskNode task : tasks) {
            String nodeId =
                    task.adapterId().replace("-", "_") + "_" + task.taskId().replace("-", "_");
            mermaid.append("  ")
                    .append(nodeId)
                    .append("[\"")
                    .append(task.adapterId())
                    .append(":")
                    .append(task.taskId())
                    .append("\"]\\n");
            for (String dep : task.dependsOn()) {
                String safeDep = dep.replace("-", "_");
                mermaid.append("  ").append(safeDep).append(" --> ").append(nodeId).append("\\n");
            }
        }

        return mermaid.toString();
    }

    public List<TaskNode> getTasks() {
        return tasks;
    }
}
