package com.ghatana.refactorer.orchestrator;

import com.ghatana.refactorer.shared.progress.ProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple console-based progress reporter for testing. 
 * @doc.type class
 * @doc.purpose Handles console progress reporter operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class ConsoleProgressReporter implements ProgressReporter {

    private static final Logger log = LoggerFactory.getLogger(ConsoleProgressReporter.class);
    @Override
    public void reportProgress(int percent) {
        log.info("[Progress] {}% complete", percent);
    }

    @Override
    public void reportPhase(String phaseName, int phasePercent) {
        log.info("[Phase] {}: {}%", phaseName, phasePercent);
    }

    @Override
    public void reportStatus(String message) {
        log.info("[Status] {}", message);
    }

    @Override
    public void reportTaskCompletion(String taskName) {
        log.info("[Task] Completed: {}", taskName);
    }

    @Override
    public void reportError(String errorMessage) {
        log.error("[Error] {}", errorMessage);
    }

    @Override
    public void reportCompletion() {
        log.info("[Complete] Processing finished");
    }
}
