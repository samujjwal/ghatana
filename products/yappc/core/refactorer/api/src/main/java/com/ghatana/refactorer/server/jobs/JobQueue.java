package com.ghatana.refactorer.server.jobs;

import java.util.Optional;

/**
 * Interface for a job queue that manages job execution.
 
 * @doc.type interface
 * @doc.purpose Defines the contract for job queue
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface JobQueue {

    /**
     * Enqueues a job record for execution.
     *
     * @param job The job record to enqueue
     */
    void enqueue(JobRecord job);

    /**
     * Retrieves and removes the next job record from the queue, if available.
     *
     * @return An Optional containing the next job record, or empty if the queue
     * is empty
     */
    Optional<JobRecord> dispatchNext();

    /**
     * Removes the job with the specified ID from the queue.
     *
     * @param jobId The ID of the job to remove
     * @return true if the job was found and removed, false otherwise
     */
    boolean remove(String jobId);

    /**
     * Gets the number of jobs currently in the queue.
     *
     * @return The number of jobs in the queue
     */
    int size();
}
