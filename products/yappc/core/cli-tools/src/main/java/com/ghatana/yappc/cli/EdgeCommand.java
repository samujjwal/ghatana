package com.ghatana.yappc.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ghatana.yappc.cli.adapter.CliKgFacade;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI commands for edge (relationship) operations.
 *
 * @doc.type class
 * @doc.purpose Edge management CLI commands
 * @doc.layer product
 * @doc.pattern Command
 */
@Command(
    name = "edge",
    description = "Manage Knowledge Graph edges (relationships)",
    subcommands = {
        EdgeCommand.ListEdges.class,
        EdgeCommand.AddEdge.class,
        EdgeCommand.DeleteEdge.class
    }
)
public class EdgeCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(EdgeCommand.class);

    @ParentCommand
    KgCli parent;

    private static final ObjectMapper mapper = JsonUtils.getDefaultMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public void run() {
        log.info("Use 'kg edge --help' for available commands");
    }

    @Command(name = "list", description = "List edges for a node")
    static class ListEdges implements Callable<Integer> {

        @ParentCommand
        EdgeCommand parent;

        @Parameters(index = "0", description = "Node ID to list edges for")
        String nodeId;

        @Option(names = {"--type"}, description = "Filter by relationship type")
        String relationType;

        @Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        @Override
        public Integer call() throws Exception {
            CliKgFacade service = new CliKgFacade();
            List<YAPPCGraphEdge> edges = service.listEdges(nodeId, null);

            if (relationType != null) {
                final String typeFilter = relationType.toUpperCase();
                edges = edges.stream()
                    .filter(e -> e.relationshipType().name().equals(typeFilter))
                    .toList();
            }

            if (json) {
                log.info("{}", mapper.writeValueAsString(edges));
            } else {
                if (edges.isEmpty()) {
                    log.info("No edges found for node: {}", nodeId);
                } else {
                    log.info("Found {} edges for node '{}':", edges.size(), nodeId);
                    log.info("-".repeat(70));
                    for (YAPPCGraphEdge edge : edges) {
                        double confidence = edge.properties() != null
                                ? (double) edge.properties().getOrDefault("weight", 1.0)
                                : 1.0;
                        log.info("  ID: {}", edge.id());
                        log.info("  Source: {} -> Target: {}", edge.sourceNodeId(), edge.targetNodeId());
                        log.info("  Type: {}", edge.relationshipType().name());
                        log.info(String.format("  Confidence: %.2f", confidence));
                        log.info("-".repeat(70));
                    }
                }
            }

            return 0;
        }
    }

    @Command(name = "add", description = "Add a new edge between nodes")
    static class AddEdge implements Callable<Integer> {

        @ParentCommand
        EdgeCommand parent;

        @Option(names = {"-s", "--source"}, required = true, description = "Source node ID")
        String sourceNodeId;

        @Option(names = {"-t", "--target"}, required = true, description = "Target node ID")
        String targetNodeId;

        @Option(names = {"--type"}, required = true, description = "Relationship type (e.g., DEPENDS_ON, CALLS, EXTENDS)")
        String relationType;

        @Option(names = {"-w", "--weight"}, description = "Edge weight/confidence (0.0-1.0)", defaultValue = "1.0")
        double weight;

        @Option(names = {"-p", "--property"}, description = "Properties in key=value format")
        List<String> properties;

        @Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        @Override
        public Integer call() throws Exception {
            if (weight < 0.0 || weight > 1.0) {
                log.error("Weight must be between 0.0 and 1.0");
                return 1;
            }

            CliKgFacade service = new CliKgFacade();

            YAPPCGraphEdge.YAPPCRelationshipType resolvedType;
            try {
                resolvedType = YAPPCGraphEdge.YAPPCRelationshipType.valueOf(relationType.toUpperCase());
            } catch (IllegalArgumentException e) {
                resolvedType = YAPPCGraphEdge.YAPPCRelationshipType.DEPENDS_ON;
            }

            Map<String, Object> props = new HashMap<>();
            props.put("weight", weight);
            if (properties != null) {
                for (String prop : properties) {
                    String[] parts = prop.split("=", 2);
                    if (parts.length == 2) props.put(parts[0], parts[1]);
                }
            }

            try {
                YAPPCGraphEdge edge = YAPPCGraphEdge.builder()
                    .id(UUID.randomUUID().toString())
                    .sourceNodeId(sourceNodeId)
                    .targetNodeId(targetNodeId)
                    .relationshipType(resolvedType)
                    .properties(props)
                    .build();

                YAPPCGraphEdge created = service.createEdge(edge);

                if (json) {
                    log.info("{}", mapper.writeValueAsString(created));
                } else {
                    log.info("Edge created successfully:");
                    log.info("  ID: {}", created.id());
                    log.info("  {} -[{}]-> {}", created.sourceNodeId(), created.relationshipType().name(), created.targetNodeId());
                }

                return 0;
            } catch (IllegalArgumentException e) {
                log.error("Error creating edge: {}", e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete an edge from the graph")
    static class DeleteEdge implements Callable<Integer> {

        @ParentCommand
        EdgeCommand parent;

        @Parameters(index = "0", description = "Edge ID to delete")
        String edgeId;

        @Option(names = {"-f", "--force"}, description = "Force deletion without confirmation")
        boolean force;

        @Override
        public Integer call() throws Exception {
            if (!force) {
                log.info("Are you sure you want to delete edge '{}'? (use --force to skip)", edgeId);
                return 1;
            }

            CliKgFacade service = new CliKgFacade();
            boolean deleted = service.deleteEdge(edgeId);

            if (deleted) {
                log.info("Edge deleted successfully: {}", edgeId);
                return 0;
            } else {
                log.error("Failed to delete edge: {}", edgeId);
                return 1;
            }
        }
    }
}
