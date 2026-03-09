package com.ghatana.yappc.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ghatana.kg.core.KnowledgeGraphNode;
import com.ghatana.yappc.kg.service.domain.GraphNode;
import com.ghatana.yappc.kg.service.domain.KnowledgeGraphServiceImpl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
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
            KnowledgeGraphServiceImpl service = new KnowledgeGraphServiceImpl();
            AtomicReference<List<GraphNode>> nodesRef = new AtomicReference<>();
            Eventloop eventloop = Eventloop.getCurrentEventloop();
            service.listNodes()
                .whenResult(nodesRef::set)
                .whenException(e -> { throw new RuntimeException(e); });
            eventloop.run();
            List<GraphNode> nodes = nodesRef.get();

            if (nodeType != null) {
                nodes = nodes.stream()
                    .filter(n -> n.types().contains(nodeType))
                    .toList();
            }

            if (tag != null) {
                nodes = nodes.stream()
                    .filter(n -> n.tags().contains(tag))
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
                    for (GraphNode node : nodes) {
                        log.info("  ID: {}", node.id());
                        log.info("  Label: {}", node.label());
                        log.info("  Types: {}", node.types());
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
            KnowledgeGraphServiceImpl service = new KnowledgeGraphServiceImpl();
            AtomicReference<Optional<GraphNode>> nodeRef = new AtomicReference<>();
            Eventloop eventloop = Eventloop.getCurrentEventloop();
            service.findNode(nodeId)
                .whenResult(nodeRef::set)
                .whenException(e -> { throw new RuntimeException(e); });
            eventloop.run();
            Optional<GraphNode> node = nodeRef.get();

            if (node.isEmpty()) {
                log.error("Node not found: {}", nodeId);
                return 1;
            }

            GraphNode n = node.get();
            if (json) {
                log.info("{}", mapper.writeValueAsString(n));
            } else {
                log.info("Node Details:");
                log.info("-".repeat(40));
                log.info("  ID: {}", n.id());
                log.info("  Label: {}", n.label());
                log.info("  Types: {}", n.types());
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
            KnowledgeGraphServiceImpl service = new KnowledgeGraphServiceImpl();

            KnowledgeGraphNode.Builder builder = KnowledgeGraphNode.builder()
                .label(label)
                .nodeType(nodeType);

            if (description != null) {
                builder.description(description);
            }

            if (sourceUri != null) {
                builder.sourceUri(sourceUri);
            }

            if (tags != null) {
                builder.tags(Set.copyOf(tags));
            }

            if (properties != null) {
                for (String prop : properties) {
                    String[] parts = prop.split("=", 2);
                    if (parts.length == 2) {
                        builder.property(parts[0], parts[1]);
                    }
                }
            }

            KnowledgeGraphNode node = builder.build();
            AtomicReference<GraphNode> createdRef = new AtomicReference<>();
            Eventloop eventloop = Eventloop.getCurrentEventloop();
            service.createNode("default", node)
                .whenResult(createdRef::set)
                .whenException(e -> { throw new RuntimeException(e); });
            eventloop.run();
            GraphNode created = createdRef.get();

            if (json) {
                log.info("{}", mapper.writeValueAsString(created));
            } else {
                log.info("Node created successfully:");
                log.info("  ID: {}", created.id());
                log.info("  Label: {}", created.label());
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

            KnowledgeGraphServiceImpl service = new KnowledgeGraphServiceImpl();
            AtomicReference<Boolean> deletedRef = new AtomicReference<>();
            Eventloop eventloop = Eventloop.getCurrentEventloop();
            service.deleteNode("default", nodeId)
                .whenResult(deletedRef::set)
                .whenException(e -> { throw new RuntimeException(e); });
            eventloop.run();
            boolean deleted = deletedRef.get();

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
