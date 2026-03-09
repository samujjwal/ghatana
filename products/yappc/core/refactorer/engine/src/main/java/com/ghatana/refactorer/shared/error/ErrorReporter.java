package com.ghatana.refactorer.shared.error;

/**
 * Interface for standardized error reporting during polyfix execution. 
 * @doc.type interface
 * @doc.purpose Defines the contract for error reporter
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface ErrorReporter {
    /**
 * Reports a warning condition. */
    void reportWarning(String source, String message, Throwable cause);

    /**
 * Reports an error condition that may allow continued execution. */
    void reportError(String source, String message, Throwable cause);

    /**
 * Reports a fatal error that should halt execution. */
    void reportFatal(String source, String message, Throwable cause);

    /**
 * Checks if fatal errors have been reported. */
    boolean hasFatalErrors();
}
