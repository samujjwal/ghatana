package com.ghatana.yappc.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ghatana.yappc.cli.adapter.CliKgFacade;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI commands for node operations.
 *
 * @doc.type class
 * @doc.purpose Node management CLI commands
 * @doc.layer product
 * @doc.pattern Command
 */
@Command(
    name = "node",
    description = "Manage Knowledge Graph nodes",
    subcommands = {
        NodeCommand.ListNodes.class,
        NodeCommand.GetNode.class,
        NodeCommand.AddNode.class,
        NodeCommand.DeleteNode.class
    }
)
public class NodeCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(NodeCommand.class);

    @ParentCommand
    KgCli parent;

    private static final ObjectMapper mapper = JsonUtils.getDefaultMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public void run() {
        log.info("Use 'kg node --help' for available commands");
    }

    @Command(name = "list", description = "List all nodes in the graph")
    static class ListNodes implements Callable<Integer> {

        @ParentCommand
        NodeCommand parent;

        @Option(names = {"--type"}, description = "Filter by node type")
        String nodeType;

        @Option(names = {"--tag"}, description = "Filter by tag")
        String tag;

        @Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        @Override
        public Integer call() throws Exception {
            CliKgFacade service = new CliKgFacade();
            List<YAPPCGraphNode> nodes = service.listNodes(null);

            if (nodeType != null) {
                final String typeFilter = nodeType.toUpperCase();
                nodes = nodes.stream()
                    .filter(n -> n.type().name().equals(typeFilter))
                    .toList();
            }

            if (tag != null) {
                nodes = nodes.stream()
                    .filter(n -> n.tags() != null && n.tags().contains(tag))
                    .toList();
            }

            if (json) {
                log.info("{}", mapper.writeValueAsString(nodes));
            } else {
                if (nodes.isEmpty()) {
                    log.info("No nodes found.");
                } else {
                    log.info("Found {} nodes:", nodes.size());
                    log.info("-".repeat(60));
                    for (YAPPCGraphNode node : nodes) {
                        log.info("  ID: {}", node.id());
                        log.info("  Name: {}", node.name());
                        log.info("  Type: {}", node.type());
                        log.info("  Tags: {}", node.tags());
                        log.info("-".repeat(60));
                    }
                }
            }

            return 0;
        }
    }

    @Command(name = "get", description = "Get a specific node by ID")
    static class GetNode implements Callable<Integer> {

        @ParentCommand
        NodeCommand parent;

        @Parameters(index = "0", description = "Node ID")
        String nodeId;

        @Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        @Override
        public Integer call() throws Exception {
            CliKgFacade service = new CliKgFacade();
            Optional<YAPPCGraphNode> node = service.findNode(nodeId, null);

            if (node.isEmpty()) {
                log.error("Node not found: {}", nodeId);
                return 1;
            }

            YAPPCGraphNode n = node.get();
            if (json) {
                log.info("{}", mapper.writeValueAsString(n));
            } else {
                log.info("Node Details:");
                log.info("-".repeat(40));
                log.info("  ID: {}", n.id());
                log.info("  Name: {}", n.name());
                log.info("  Type: {}", n.type());
                log.info("  Tags: {}", n.tags());
            }

            return 0;
        }
    }

    @Command(name = "add", description = "Add a new node to the graph")
    static class AddNode implements Callable<Integer> {

        @ParentCommand
        NodeCommand parent;

        @Option(names = {"-l", "--label"}, required = true, description = "Node label")
        String label;

        @Option(names = {"-t", "--type"}, required = true, description = "Node type (e.g., CLASS, METHOD, DOCUMENT)")
        String nodeType;

        @Option(names = {"--tag"}, description = "Tags for the node (can be repeated)")
        List<String> tags;

        @Option(names = {"-d", "--description"}, description = "Node description")
        String description;

        @Option(names = {"--source"}, description = "Source URI")
        String sourceUri;

        @Option(names = {"-p", "--property"}, description = "Properties in key=value format")
        List<String> properties;

        @Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        @Override
        public Integer call() throws Exception {
            CliKgFacade service = new CliKgFacade();

            YAPPCGraphNode.YAPPCNodeType resolvedType;
            try {
                resolvedType = YAPPCGraphNode.YAPPCNodeType.valueOf(nodeType.toUpperCase());
            } catch (IllegalArgumentException e) {
                resolvedType = YAPPCGraphNode.YAPPCNodeType.COMPONENT;
            }

            Map<String, Object> props = new HashMap<>();
            if (description != null) props.put("description", description);
            if (sourceUri != null)   props.put("sourceUri", sourceUri);
            if (properties != null) {
                for (String prop : properties) {
                    String[] parts = prop.split("=", 2);
                    if (parts.length == 2) props.put(parts[0], parts[1]);
                }
            }

            YAPPCGraphNode node = YAPPCGraphNode.builder()
                .id(UUID.randomUUID().toString())
                .type(resolvedType)
                .name(label)
                .description(description)
                .tags(tags != null ? Set.copyOf(tags) : Set.of())
                .properties(props.isEmpty() ? null : props)
                .build();

            YAPPCGraphNode created = service.createNode(node);

            if (json) {
                log.info("{}", mapper.writeValueAsString(created));
            } else {
                log.info("Node created successfully:");
                log.info("  ID: {}", created.id());
                log.info("  Name: {}", created.name());
            }

            return 0;
        }
    }

    @Command(name = "delete", description = "Delete a node from the graph")
    static class DeleteNode implements Callable<Integer> {

        @ParentCommand
        NodeCommand parent;

        @Parameters(index = "0", description = "Node ID to delete")
        String nodeId;

        @Option(names = {"-f", "--force"}, description = "Force deletion without confirmation")
        boolean force;

        @Override
        public Integer call() throws Exception {
            if (!force) {
                log.info("Are you sure you want to delete node '{}'? (use --force to skip)", nodeId);
                return 1;
            }

            CliKgFacade service = new CliKgFacade();
            boolean deleted = service.deleteNode(nodeId, null);

            if (deleted) {
                log.info("Node deleted successfully: {}", nodeId);
                return 0;
            } else {
                log.error("Failed to delete node: {}", nodeId);
                return 1;
            }
        }
    }
}
