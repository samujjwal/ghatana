package com.ghatana.datacloud.launcher.connectors;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * P0-08: In-memory implementation of ConnectorSyncJobService for local development.
 * 
 * <p>This implementation stores connector jobs in memory and processes them synchronously.
 * For production, a durable database-backed implementation should be used.
 *
 * @doc.type class
 * @doc.purpose In-memory connector sync job service for local development
 * @doc.layer product
 * @doc.pattern Service, InMemory
 */
public class InMemoryConnectorSyncJobService implements ConnectorSyncJobService {
    
    private static final Logger log = LoggerFactory.getLogger(InMemoryConnectorSyncJobService.class);
    
    private final Map<String, ConnectorSyncJob> jobStore = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ConnectorSyncJob> pendingQueue = new ConcurrentLinkedQueue<>();
    
    @Override
    public Promise<ConnectorSyncJob> createJob(String tenantId, String connectionId, String jobType,
                                               Map<String, Object> jobConfig, String correlationId) {
        log.info("[P0-08] Creating connector job: tenantId={}, connectionId={}, jobType={}",
            tenantId, connectionId, jobType);
        
        ConnectorSyncJob job = ConnectorSyncJob.builder()
            .tenantId(tenantId)
            .connectionId(connectionId)
            .jobType(jobType)
            .jobConfig(jobConfig)
            .correlationId(correlationId)
            .state(ConnectorJobState.PENDING)
            .build();
        
        jobStore.put(job.id(), job);
        pendingQueue.add(job);
        
        return Promise.of(job);
    }
    
    @Override
    public Promise<ConnectorSyncJob> getJob(String jobId) {
        return Promise.of(jobStore.get(jobId));
    }
    
    @Override
    public Promise<List<ConnectorSyncJob>> listJobs(String tenantId, String connectionId, int limit) {
        List<ConnectorSyncJob> jobs = new ArrayList<>();
        
        for (ConnectorSyncJob job : jobStore.values()) {
            if (jobs.size() >= limit) {
                break;
            }
            
            if (tenantId.equals(job.tenantId()) && connectionId.equals(job.connectionId())) {
                jobs.add(job);
            }
        }
        
        return Promise.of(jobs);
    }
    
    @Override
    public Promise<Void> updateJobState(String jobId, ConnectorJobState newState, Map<String, Object> evidence) {
        ConnectorSyncJob job = jobStore.get(jobId);
        if (job != null) {
            ConnectorSyncJob updated = ConnectorSyncJob.builder()
                .id(job.id())
                .tenantId(job.tenantId())
                .connectionId(job.connectionId())
                .jobType(job.jobType())
                .jobConfig(job.jobConfig())
                .evidence(evidence)
                .correlationId(job.correlationId())
                .createdAt(job.createdAt())
                .startedAt(job.startedAt())
                .completedAt(newState == ConnectorJobState.COMPLETED || newState == ConnectorJobState.FAILED 
                    ? Instant.now() : job.completedAt())
                .state(newState)
                .retryCount(job.retryCount())
                .errorMessage(job.errorMessage())
                .build();
            jobStore.put(jobId, updated);
        }
        return Promise.of((Void) null);
    }
    
    @Override
    public Promise<Void> markJobFailed(String jobId, String errorMessage, Map<String, Object> evidence) {
        ConnectorSyncJob job = jobStore.get(jobId);
        if (job != null) {
            ConnectorSyncJob failed = ConnectorSyncJob.builder()
                .id(job.id())
                .tenantId(job.tenantId())
                .connectionId(job.connectionId())
                .jobType(job.jobType())
                .jobConfig(job.jobConfig())
                .evidence(evidence)
                .correlationId(job.correlationId())
                .createdAt(job.createdAt())
                .startedAt(job.startedAt())
                .completedAt(Instant.now())
                .state(ConnectorJobState.FAILED)
                .retryCount(job.retryCount())
                .errorMessage(errorMessage)
                .build();
            jobStore.put(jobId, failed);
        }
        return Promise.of((Void) null);
    }
    
    @Override
    public Promise<Void> cancelJob(String jobId) {
        return updateJobState(jobId, ConnectorJobState.CANCELLED, null);
    }
    
    @Override
    public Promise<ConnectorSyncJob> retryJob(String jobId) {
        ConnectorSyncJob job = jobStore.get(jobId);
        if (job != null && job.state() == ConnectorJobState.FAILED) {
            ConnectorSyncJob retried = ConnectorSyncJob.builder()
                .id(job.id())
                .tenantId(job.tenantId())
                .connectionId(job.connectionId())
                .jobType(job.jobType())
                .jobConfig(job.jobConfig())
                .evidence(job.evidence())
                .correlationId(job.correlationId())
                .createdAt(job.createdAt())
                .startedAt(Instant.now())
                .completedAt(null)
                .state(ConnectorJobState.RETRYING)
                .retryCount(job.retryCount() + 1)
                .errorMessage(null)
                .build();
            jobStore.put(jobId, retried);
            pendingQueue.add(retried);
            return Promise.of(retried);
        }
        return Promise.of(job);
    }
    
    @Override
    public Promise<Integer> pollAndProcess(String tenantId, int limit) {
        int processed = 0;
        int count = 0;
        
        for (ConnectorSyncJob job : pendingQueue) {
            if (count >= limit) {
                break;
            }
            
            if (tenantId == null || tenantId.equals(job.tenantId())) {
                if (job.state() == ConnectorJobState.PENDING || job.state() == ConnectorJobState.RETRYING) {
                    // Update to RUNNING state
                    ConnectorSyncJob running = ConnectorSyncJob.builder()
                        .id(job.id())
                        .tenantId(job.tenantId())
                        .connectionId(job.connectionId())
                        .jobType(job.jobType())
                        .jobConfig(job.jobConfig())
                        .evidence(job.evidence())
                        .correlationId(job.correlationId())
                        .createdAt(job.createdAt())
                        .startedAt(Instant.now())
                        .completedAt(job.completedAt())
                        .state(ConnectorJobState.RUNNING)
                        .retryCount(job.retryCount())
                        .errorMessage(job.errorMessage())
                        .build();
                    jobStore.put(job.id(), running);
                    
                    // Remove from pending queue
                    pendingQueue.remove(job);
                    
                    log.info("[P0-08] Processing job: id={}, jobType={}, connectionId={}",
                        job.id(), job.jobType(), job.connectionId());
                    
                    // In a real implementation, this would trigger the actual connector operation
                    // For now, we just mark it as completed
                    updateJobState(job.id(), ConnectorJobState.COMPLETED, 
                        Map.of("processedAt", Instant.now().toString()));
                    
                    processed++;
                    count++;
                }
            }
        }
        
        return Promise.of(processed);
    }
}
