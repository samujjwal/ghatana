package com.ghatana.orchestrator.queue.impl;

import com.ghatana.orchestrator.queue.ExecutionJob;
import com.ghatana.orchestrator.queue.ExecutionQueue;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory implementation of ExecutionQueue.
 * 
 * <p>Provides a thread-safe, in-memory queue implementation with idempotency support.
 * Suitable for development, testing, and single-node deployments.</p>
 * 
 * @doc.type class
 * @doc.purpose Thread-safe in-memory execution queue with idempotency tracking
 * @doc.layer product
 * @doc.pattern Repository
 * @since 2.0.0
 */
public class InMemoryExecutionQueue implements ExecutionQueue {

    private final ConcurrentLinkedQueue<ExecutionJob> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, ExecutionJob> idempotencyKeys = new ConcurrentHashMap<>();
    private final AtomicInteger size = new AtomicInteger(0);

    @Override
    public Promise<Void> enqueue(String tenantId, String pipelineId, Object triggerData, String idempotencyKey) {
        // Check for duplicate
        if (idempotencyKeys.containsKey(idempotencyKey)) {
            return Promise.of(null); // Already enqueued, ignore
        }

        ExecutionJob job = new ExecutionJob(tenantId, pipelineId, triggerData, idempotencyKey, java.util.UUID.randomUUID().toString(), null);

        // Add to queue and idempotency tracking
        queue.offer(job);
        idempotencyKeys.put(idempotencyKey, job);
        size.incrementAndGet();
        
        return Promise.of(null);
    }

    @Override
    public Promise<List<ExecutionJob>> poll(int maxJobs, int visibilityTimeoutSeconds) {
        List<ExecutionJob> jobs = new ArrayList<>();
        for (int i = 0; i < maxJobs; i++) {
            ExecutionJob job = queue.poll();
            if (job == null) break;
            jobs.add(job);
            size.decrementAndGet();
            // Note: In-memory queue doesn't support visibility timeout or lease management in this simple version
            // Idempotency key is removed when job is polled (simplified)
            idempotencyKeys.remove(job.getIdempotencyKey());
        }
        return Promise.of(jobs);
    }

    @Override
    public Promise<Void> complete(String jobId, String status, Object result) {
        // In-memory queue doesn't track completed jobs
        return Promise.of(null);
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public Promise<Void> clear() {
        queue.clear();
        idempotencyKeys.clear();
        size.set(0);
        return Promise.of(null);
    }

    /**
     * Peek at the next job without removing it.
     */
    public ExecutionJob peek() {
        return queue.peek();
    }
}
