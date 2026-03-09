package com.ghatana.yappc.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ghatana.kg.core.KnowledgeGraph;
import com.ghatana.yappc.kg.service.domain.GraphNode;
import com.ghatana.yappc.kg.service.domain.KnowledgeGraphServiceImpl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI commands for graph-level operations.
 *
 * @doc.type class
 * @doc.purpose Graph management CLI commands
 * @doc.layer product
 * @doc.pattern Command
 */
@Command(
    name = "graph",
    description = "Manage Knowledge Graphs",
    subcommands = {
        GraphCommand.CreateGraph.class,
        GraphCommand.InfoGraph.class,
        GraphCommand.DeleteGraph.class,
        GraphCommand.PathCommand.class,
        GraphCommand.RelatedCommand.class
    }
)
public class GraphCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(GraphCommand.class);

    @ParentCommand
    KgCli parent;

    private static final ObjectMapper mapper = JsonUtils.getDefaultMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public void run() {
        log.info("Use 'kg graph --help' for available commands");
    }

    @Command(name = "create", description = "Create a new knowledge graph")
    static class CreateGraph implements Callable<Integer> {

        @ParentCommand
        GraphCommand parent;

        @Option(names = {"--id"}, required = true, description = "Graph ID")
        String graphId;

        @Option(names = {"-n", "--name"}, required = true, description = "Graph name")
        String name;

        @Option(names = {"-d", "--description"}, description = "Graph description")
        String description;

        @Option(names = {"--project"}, description = "Associated project ID")
        String projectId;

        @Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        @Override
        public Integer call() throws Exception {
            KnowledgeGraphServiceImpl service = new KnowledgeGraphServiceImpl();

            try {
                AtomicReference<KnowledgeGraph> graphRef = new AtomicReference<>();
                Eventloop eventloop = Eventloop.getCurrentEventloop();
                service.createGraph(graphId, name, description, projectId)
                    .whenResult(graphRef::set)
                    .whenException(e -> { throw new RuntimeException(e); });
                eventloop.run();
                KnowledgeGraph graph = graphRef.get();

                if (json) {
                    log.info(mapper.writeValueAsString(new GraphInfo( graph.getId(), graph.getName(), graph.getDescription(), graph.getNodeCount(), graph.getEdgeCount() )));
                } else {
                    log.info("Graph created successfully:");
                    log.info("  ID: {}", graph.getId());
                    log.info("  Name: {}", graph.getName());
                    if (graph.getDescription() != null) {
                        log.info("  Description: {}", graph.getDescription());
                    }
                }

                return 0;
            } catch (IllegalArgumentException e) {
                log.error("Error creating graph: {}", e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "info", description = "Show information about a graph")
    static class InfoGraph implements Callable<Integer> {

        @ParentCommand
        GraphCommand parent;

        @Parameters(index = "0", description = "Graph ID", defaultValue = "default")
        String graphId;

        @Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        @Override
        public Integer call() throws Exception {
            KnowledgeGraphServiceImpl service = new KnowledgeGraphServiceImpl();
            AtomicReference<Optional<KnowledgeGraph>> graphRef = new AtomicReference<>();
            Eventloop eventloop = Eventloop.getCurrentEventloop();
            service.getGraph(graphId)
                .whenResult(graphRef::set)
                .whenException(e -> { throw new RuntimeException(e); });
            eventloop.run();
            Optional<KnowledgeGraph> graphOpt = graphRef.get();

            if (graphOpt.isEmpty()) {
                log.error("Graph not found: {}", graphId);
                return 1;
            }

            KnowledgeGraph graph = graphOpt.get();

            if (json) {
                log.info(mapper.writeValueAsString(new GraphInfo( graph.getId(), graph.getName(), graph.getDescription(), graph.getNodeCount(), graph.getEdgeCount() )));
            } else {
                log.info("Graph Information:");
                log.info("=".repeat(50));
                log.info("  ID: {}", graph.getId());
                log.info("  Name: {}", graph.getName());
                if (graph.getDescription() != null) {
                    log.info("  Description: {}", graph.getDescription());
                }
                log.info("  Nodes: {}", graph.getNodeCount());
                log.info("  Edges: {}", graph.getEdgeCount());
                log.info("  Created: {}", graph.getCreatedAt());
                log.info("  Updated: {}", graph.getUpdatedAt());
            }

            return 0;
        }
    }

    @Command(name = "delete", description = "Delete a knowledge graph")
    static class DeleteGraph implements Callable<Integer> {

        @ParentCommand
        GraphCommand parent;

        @Parameters(index = "0", description = "Graph ID to delete")
        String graphId;

        @Option(names = {"-f", "--force"}, description = "Force deletion without confirmation")
        boolean force;

        @Override
        public Integer call() throws Exception {
            if (!force) {
                log.info("Are you sure you want to delete graph '{}'? This will delete all nodes and edges. (use --force to skip)", graphId);
                return 1;
            }

            KnowledgeGraphServiceImpl service = new KnowledgeGraphServiceImpl();

            try {
                AtomicReference<Boolean> deletedRef = new AtomicReference<>();
                Eventloop eventloop = Eventloop.getCurrentEventloop();
                service.deleteGraph(graphId)
                    .whenResult(deletedRef::set)
                    .whenException(e -> { throw new RuntimeException(e); });
                eventloop.run();
                boolean deleted = deletedRef.get();

                if (deleted) {
                    log.info("Graph deleted successfully: {}", graphId);
                    return 0;
                } else {
                    log.error("Graph not found: {}", graphId);
                    return 1;
                }
            } catch (IllegalArgumentException e) {
                log.error("Error: {}", e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "path", description = "Check if a path exists between two nodes")
    static class PathCommand implements Callable<Integer> {

        @ParentCommand
        GraphCommand parent;

        @Option(names = {"-s", "--source"}, required = true, description = "Source node ID")
        String sourceId;

        @Option(names = {"-t", "--target"}, required = true, description = "Target node ID")
        String targetId;

        @Override
        public Integer call() throws Exception {
            KnowledgeGraphServiceImpl service = new KnowledgeGraphServiceImpl();
            AtomicReference<Optional<KnowledgeGraph>> graphRef = new AtomicReference<>();
            Eventloop eventloop = Eventloop.getCurrentEventloop();
            service.getGraph("default")
                .whenResult(graphRef::set)
                .whenException(e -> { throw new RuntimeException(e); });
            eventloop.run();
            Optional<KnowledgeGraph> graphOpt = graphRef.get();

            if (graphOpt.isEmpty()) {
                log.error("Default graph not found");
                return 1;
            }

            KnowledgeGraph graph = graphOpt.get();
            boolean hasPath = graph.hasPath(sourceId, targetId);

            if (hasPath) {
                log.info("Path exists: {} -> {}", sourceId, targetId);
                return 0;
            } else {
                log.info("No path found: {} -> {}", sourceId, targetId);
                return 1;
            }
        }
    }

    @Command(name = "related", description = "Find nodes related to a given node")
    static class RelatedCommand implements Callable<Integer> {

        @ParentCommand
        GraphCommand parent;

        @Parameters(index = "0", description = "Node ID to find related nodes for")
        String nodeId;

        @Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        @Override
        public Integer call() throws Exception {
            KnowledgeGraphServiceImpl service = new KnowledgeGraphServiceImpl();
            AtomicReference<List<GraphNode>> relatedRef = new AtomicReference<>();
            Eventloop eventloop = Eventloop.getCurrentEventloop();
            service.getRelatedNodes("default", nodeId)
                .whenResult(relatedRef::set)
                .whenException(e -> { throw new RuntimeException(e); });
            eventloop.run();
            List<GraphNode> related = relatedRef.get();

            if (json) {
                log.info("{}", mapper.writeValueAsString(related));
            } else {
                if (related.isEmpty()) {
                    log.info("No related nodes found for: {}", nodeId);
                } else {
                    log.info("Found {} related nodes:", related.size());
                    for (GraphNode node : related) {
                        log.info("  - {} ({})", node.label(), node.id());
                    }
                }
            }

            return 0;
        }
    }

    record GraphInfo(String id, String name, String description, int nodeCount, int edgeCount) {}
}
