package com.ghatana.refactorer.server.jobs;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simple concurrent in-memory job queue.
 *
 * @doc.type class
 * @doc.purpose Buffer submissions and coordinate asynchronous execution ordering.
 * @doc.layer product
 * @doc.pattern Queue
 */
public final class InMemoryJobQueue implements JobQueue {
    private final ConcurrentLinkedQueue<JobRecord> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void enqueue(JobRecord job) {
        queue.add(job);
    }

    @Override
    public Optional<JobRecord> dispatchNext() {
        return Optional.ofNullable(queue.poll());
    }

    @Override
    public boolean remove(String jobId) {
        return queue.removeIf(job -> job.jobId().equals(jobId));
    }

    @Override
    public int size() {
        return queue.size();
    }
}
