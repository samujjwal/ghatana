package com.ghatana.yappc.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Main entry point for the Knowledge Graph CLI.
 *
 * <p><b>Purpose</b><br>
 * Provides command-line interface for managing Knowledge Graph operations
 * including node/edge management, search, and graph traversal.
 *
 * <p><b>Usage</b><br>
 * <pre>
 * kg --help
 * kg node list
 * kg node add --label "MyNode" --type "CLASS"
 * kg edge add --source node1 --target node2 --type "DEPENDS_ON"
 * kg search "authentication"
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Knowledge Graph CLI main entry point
 * @doc.layer product
 * @doc.pattern Command
 */
@Command(
    name = "kg",
    mixinStandardHelpOptions = true,
    version = "kg 1.0.0",
    description = "Knowledge Graph CLI - Manage nodes, edges, and graph operations",
    subcommands = {
        NodeCommand.class,
        EdgeCommand.class,
        GraphCommand.class,
        SearchCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class KgCli implements Runnable {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;

    @Option(names = {"--graph-id"}, description = "Graph ID to operate on", defaultValue = "default")
    String graphId;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new KgCli())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // Default action: show help
        CommandLine.usage(this, System.out);
    }

    public boolean isVerbose() {
        return verbose;
    }

    public String getGraphId() {
        return graphId;
    }
}
