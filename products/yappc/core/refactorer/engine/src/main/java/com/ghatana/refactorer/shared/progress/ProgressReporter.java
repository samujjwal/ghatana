package com.ghatana.refactorer.shared.progress;

/**
 * Interface for reporting progress during polyfix execution. 
 * @doc.type interface
 * @doc.purpose Defines the contract for progress reporter
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface ProgressReporter {
    /**
 * Reports overall progress percentage (0-100). */
    void reportProgress(int percent);

    /**
 * Reports current phase and progress within that phase. */
    void reportPhase(String phaseName, int phasePercent);

    /**
 * Reports detailed status message. */
    void reportStatus(String message);

    /**
 * Reports a completed task. */
    void reportTaskCompletion(String taskName);

    /**
 * Reports an error condition. */
    void reportError(String errorMessage);

    /**
 * Called when processing is complete. */
    void reportCompletion();
}
