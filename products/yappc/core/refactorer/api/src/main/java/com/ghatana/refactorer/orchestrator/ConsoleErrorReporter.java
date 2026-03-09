package com.ghatana.refactorer.orchestrator;

import com.ghatana.refactorer.shared.error.ErrorReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console-based error reporter for testing. 
 * @doc.type class
 * @doc.purpose Handles console error reporter operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class ConsoleErrorReporter implements ErrorReporter {

    private static final Logger log = LoggerFactory.getLogger(ConsoleErrorReporter.class);
    private boolean hasFatalErrors = false;

    @Override
    public void reportWarning(String source, String message, Throwable cause) {
        log.error("[WARN] {} - {}", source, message);
        if (cause != null) {
            cause.printStackTrace();
        }
    }

    @Override
    public void reportError(String source, String message, Throwable cause) {
        log.error("[ERROR] {} - {}", source, message);
        if (cause != null) {
            cause.printStackTrace();
        }
    }

    @Override
    public void reportFatal(String source, String message, Throwable cause) {
        this.hasFatalErrors = true;
        log.error("[FATAL] {} - {}", source, message);
        if (cause != null) {
            cause.printStackTrace();
        }
    }

    @Override
    public boolean hasFatalErrors() {
        return this.hasFatalErrors;
    }
}
