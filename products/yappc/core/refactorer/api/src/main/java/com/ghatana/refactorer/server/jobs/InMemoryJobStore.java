package com.ghatana.refactorer.server.jobs;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

/**
 * In-memory implementation of {@link JobStore} used for tests and local runs.
 *
 * @doc.type class
 * @doc.purpose Provide CRUD access to job metadata while abstracting the backing storage.
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class InMemoryJobStore implements JobStore {
    private final ConcurrentMap<String, JobRecord> jobs = new ConcurrentHashMap<>();

    @Override
    public void create(JobRecord job) {
        JobRecord existing = jobs.putIfAbsent(job.jobId(), job);
        if (existing != null) {
            throw new IllegalStateException("Job already exists: " + job.jobId());
        }
    }

    @Override
    public Optional<JobRecord> get(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public Optional<JobRecord> update(String jobId, UnaryOperator<JobRecord> mutator) {
        return Optional.ofNullable(
                jobs.computeIfPresent(
                        jobId,
                        (id, current) -> {
                            JobRecord updated = mutator.apply(current);
                            return updated == null ? current : updated;
                        }));
    }

    @Override
    public void delete(String jobId) {
        jobs.remove(jobId);
    }

    public int size() {
        return jobs.size();
    }
}
