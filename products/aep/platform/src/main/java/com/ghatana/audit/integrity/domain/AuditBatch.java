package com.ghatana.audit.integrity.domain;

import com.ghatana.platform.domain.domain.audit.AuditEntry;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Represents a batch of audit entries with their computed hash chain
 * Day 42: Audit Integrity - Hash Chain Verification
 */
@Value
@Builder(toBuilder = true)
public class AuditBatch {
    
    /**
     * Unique identifier for this audit batch
     */
    String batchId;
    
    /**
     * Timestamp when this batch was created
     */
    Instant createdAt;
    
    /**
     * Starting sequence number for this batch
     */
    long startSequence;
    
    /**
     * Ending sequence number for this batch
     */
    long endSequence;
    
    /**
     * Hash of the previous batch in the chain
     */
    String previousBatchHash;
    
    /**
     * Computed hash of this batch (based on entries + previous hash)
     */
    String batchHash;
    
    /**
     * Audit entries included in this batch
     */
    List<AuditEntry> entries;
    
    /**
     * Hash algorithm used for computation
     */
    @Builder.Default
    String hashAlgorithm = "SHA-256";
    
    /**
     * Verification status of this batch
     */
    @Builder.Default
    VerificationStatus verificationStatus = VerificationStatus.PENDING;
    
    /**
     * Total number of entries in this batch
     */
    public int getEntryCount() {
        return entries != null ? entries.size() : 0;
    }
    
    /**
     * Check if this batch is the genesis batch (first in chain)
     */
    public boolean isGenesisBatch() {
        return previousBatchHash == null || previousBatchHash.isEmpty();
    }
    
    /**
     * Verification status for audit batches
     */
    public enum VerificationStatus {
        PENDING,
        VERIFIED,
        TAMPERED,
        MISSING_PREVIOUS,
        HASH_MISMATCH
    }
}