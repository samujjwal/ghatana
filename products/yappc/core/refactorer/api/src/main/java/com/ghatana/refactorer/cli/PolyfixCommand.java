/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.cli;

import com.ghatana.refactorer.orchestrator.LanguageServices;
import com.ghatana.refactorer.orchestrator.PolyfixOrchestrator;
import com.ghatana.refactorer.shared.*;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

@CommandLine.Command(
        name = "polyfix",
        mixinStandardHelpOptions = true,
        subcommands = {
            RunCommand.class,
            DiagnoseCommand.class,
            InitCommand.class,
            DebugCommand.class
        })
/**
 * @doc.type class
 * @doc.purpose Handles polyfix command operations
 * @doc.layer core
 * @doc.pattern Command
 */
public final class PolyfixCommand implements Runnable {
    private static final Logger LOG = LogManager.getLogger(PolyfixCommand.class);

    @CommandLine.Option(names = "--root", required = true)
    Path root;

    public void run() {
        try {
            PolyfixConfig cfg = PolyfixConfigLoader.load(root, java.util.Map.of());
            PolyfixProjectContext ctx =
                    new PolyfixProjectContext(
                            root,
                            cfg,
                            LanguageServices.load(cfg),
                            Executors.newFixedThreadPool(4),
                            LOG);
            new PolyfixOrchestrator().run(ctx);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new PolyfixCommand()).execute(args));
    }

    static PolyfixProjectContext buildContext(Path root) {
        try {
            PolyfixConfig cfg = PolyfixConfigLoader.load(root, java.util.Map.of());
            EffectiveConfigDumper.dump(root.resolve("out"), cfg);
            Logger logger = LOG;
            return new PolyfixProjectContext(
                    root, cfg, LanguageServices.load(cfg), Executors.newFixedThreadPool(4), logger);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load configuration: " + e.getMessage(), e);
        }
    }
}
