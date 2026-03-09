package com.ghatana.refactorer.server.jobs;

import java.util.Optional;

/**
 * Abstraction for managing pending refactorer jobs ready for execution.
 *
 * @doc.type interface
 * @doc.purpose Buffer submissions and coordinate asynchronous execution ordering.
 * @doc.layer product
 * @doc.pattern Queue
 */
public interface RefactorerJobQueue {

    /**
 * Adds a new job to the pending queue. */
    void enqueue(JobRecord job);

    /**
 * Returns and removes the next pending job, if available. */
    Optional<JobRecord> dispatchNext();

    /**
 * Removes a job from the pending queue if present. */
    boolean remove(String jobId);

    /**
 * Number of currently queued jobs. */
    int size();
}
