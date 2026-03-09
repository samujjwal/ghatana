/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.cli;

import com.ghatana.refactorer.diagnostics.DiagnosticsRunner;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandLine.Command(name = "diagnose", description = "Run diagnostics only")
/**
 * @doc.type class
 * @doc.purpose Handles diagnose command operations
 * @doc.layer core
 * @doc.pattern Command
 */
public final class DiagnoseCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DiagnoseCommand.class);
    @CommandLine.ParentCommand PolyfixCommand parent;

    public void run() {
        PolyfixProjectContext ctx = PolyfixCommand.buildContext(parent.root);
        var diags = DiagnosticsRunner.runAll(ctx);
        log.info("Diagnostics: {}", diags.size());
    }
}
