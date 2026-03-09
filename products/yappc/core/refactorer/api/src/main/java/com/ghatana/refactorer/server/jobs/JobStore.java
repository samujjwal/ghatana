package com.ghatana.refactorer.server.jobs;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Persists job lifecycle metadata. Implementations must be thread-safe.
 *
 * @doc.type interface
 * @doc.purpose Define persistence contract for refactorer job lifecycle state
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface JobStore {

    /**
 * Creates a new job record. Throws if the job already exists. */
    void create(JobRecord job);

    /**
 * Retrieves the current job record if present. */
    Optional<JobRecord> get(String jobId);

    /**
 * Updates the job record using the provided mutator. */
    Optional<JobRecord> update(String jobId, UnaryOperator<JobRecord> mutator);

    /**
 * Deletes the job record. */
    void delete(String jobId);
}
