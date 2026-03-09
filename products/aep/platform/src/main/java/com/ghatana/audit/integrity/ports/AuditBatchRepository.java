package com.ghatana.audit.integrity.ports;

import com.ghatana.audit.integrity.domain.AuditBatch;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for audit batch persistence
 * Day 42: Audit Integrity - Repository Port
 */
public interface AuditBatchRepository {
    
    /**
     * Save an audit batch
     */
    AuditBatch save(AuditBatch batch);
    
    /**
     * Find audit batch by ID
     */
    Optional<AuditBatch> findById(String batchId);
    
    /**
     * Find audit batches within a time range
     */
    List<AuditBatch> findBatchesByTimeRange(Instant startTime, Instant endTime);
    
    /**
     * Find audit batches by sequence range
     */
    List<AuditBatch> findBatchesBySequenceRange(long startSequence, long endSequence);
    
    /**
     * Find the latest audit batch (highest sequence)
     */
    Optional<AuditBatch> findLatestBatch();
    
    /**
     * Find the genesis batch (first in chain)
     */
    Optional<AuditBatch> findGenesisBatch();
    
    /**
     * Get total count of audit batches
     */
    long getTotalBatchCount();
    
    /**
     * Delete audit batches older than the specified time
     */
    int deleteBatchesOlderThan(Instant cutoffTime);
}