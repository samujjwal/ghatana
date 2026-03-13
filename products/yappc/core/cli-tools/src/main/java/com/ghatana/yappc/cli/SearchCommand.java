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

import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI command for searching the Knowledge Graph.
 *
 * @doc.type class
 * @doc.purpose Search CLI command
 * @doc.layer product
 * @doc.pattern Command
 */
@Command(
    name = "search",
    description = "Search nodes in the Knowledge Graph"
)
public class SearchCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(SearchCommand.class);

    @ParentCommand
    KgCli parent;

    @Parameters(index = "0", description = "Search query")
    String query;

    @Option(names = {"--limit"}, description = "Maximum number of results", defaultValue = "20")
    int limit;

    @Option(names = {"--json"}, description = "Output as JSON")
    boolean json;

    private static final ObjectMapper mapper = JsonUtils.getDefaultMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public Integer call() throws Exception {
        if (query == null || query.isBlank()) {
            log.error("Search query cannot be empty");
            return 1;
        }

        CliKgFacade service = new CliKgFacade();
        List<YAPPCGraphNode> results = service.searchNodes("default", query, null);

        if (results.size() > limit) {
            results = results.subList(0, limit);
        }

        if (json) {
            log.info("{}", mapper.writeValueAsString(results));
        } else {
            if (results.isEmpty()) {
                log.info("No nodes found matching: {}", query);
            } else {
                log.info("Found {} nodes matching '{}':", results.size(), query);
                log.info("-".repeat(60));
                for (YAPPCGraphNode node : results) {
                    log.info("  [{}] {}", node.id(), node.name());
                    if (node.type() != null) {
                        log.info("    Type: {}", node.type().name());
                    }
                    if (node.tags() != null && !node.tags().isEmpty()) {
                        log.info("    Tags: {}", node.tags());
                    }
                }
            }
        }

        return 0;
    }
}
