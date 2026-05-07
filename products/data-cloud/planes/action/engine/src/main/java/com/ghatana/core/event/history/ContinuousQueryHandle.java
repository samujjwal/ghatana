package com.ghatana.core.event.history;

/**
 * @doc.type interface
 * @doc.purpose Provides continuous query handle functionality.
 * @doc.layer product
 * @doc.pattern Interface
 */
public interface ContinuousQueryHandle {
    /**
     * Cancels the continuous query and releases associated resources.
     */
    void cancel();

    /**
     * Checks if the continuous query is currently running.
     *
     * @return true if the query is running, false otherwise
     */
    boolean isRunning();
}
