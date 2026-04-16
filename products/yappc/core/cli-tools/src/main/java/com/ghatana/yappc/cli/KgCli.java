package com.ghatana.yappc.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Main entry point for the Knowledge Graph CLI.
 *
 * <p><b>Removed:</b> Placeholder CLI commands have been removed during re-audit.
 * The stub CliKgFacade returned empty results and was misleading. Knowledge graph
 * functionality is available via YAPPCGraphService in the core/knowledge-graph module.
 *
 * @doc.type class
 * @doc.purpose Knowledge Graph CLI main entry point (placeholder removed)
 * @doc.layer product
 * @doc.pattern Command
 */
@Command(
    name = "kg",
    mixinStandardHelpOptions = true,
    version = "kg 1.0.0",
    description = "Knowledge Graph CLI - Placeholder commands removed during re-audit",
    subcommands = {
        CommandLine.HelpCommand.class
    }
)
public class KgCli implements Runnable {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new KgCli())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.err.println("Knowledge Graph CLI placeholder commands have been removed.");
        System.err.println("Use YAPPCGraphService in core/knowledge-graph module for knowledge graph operations.");
        CommandLine.usage(this, System.out);
    }

    public boolean isVerbose() {
        return verbose;
    }
}
