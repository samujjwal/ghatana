package com.ghatana.core.event.history;

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
