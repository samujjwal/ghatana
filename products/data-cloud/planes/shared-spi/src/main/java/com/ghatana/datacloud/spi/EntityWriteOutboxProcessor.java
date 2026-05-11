package com.ghatana.datacloud.spi;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * P0-06: Processor for entity write outbox entries.
 * 
 * <p>This processor asynchronously handles outbox entries created during entity write transactions.
 * It processes events, audit records, semantic indexing, and websocket broadcasts outside the
 * write transaction to ensure atomicity while maintaining eventual consistency.
 *
 * @doc.type interface
 * @doc.purpose Processor for entity write outbox entries
 * @doc.layer product
 * @doc.pattern Processor, Outbox
 */
public interface EntityWriteOutboxProcessor {
    
    /**
     * Processes a pending outbox entry.
     * 
     * <p>This method should:
     * <ul>
     *   <li>Mark the entry as PROCESSING</li>
     *   <li>Append the event to the event log</li>
     *   <li>Emit audit record</li>
     *   <li>Trigger semantic indexing</li>
     *   <li>Broadcast websocket notification</li>
     *   <li>Mark the entry as COMPLETED or FAILED</li>
     * </ul>
     *
     * @param outbox the outbox entry to process
     * @return promise that completes when processing is done
     */
    Promise<Void> process(EntityWriteOutbox outbox);
    
    /**
     * Polls for pending outbox entries and processes them.
     * 
     * <p>This method should be called by a background scheduler to process
     * pending outbox entries that haven't been processed yet.
     *
     * @param tenantId the tenant ID to poll for (optional, if null polls all tenants)
     * @param limit maximum number of entries to process
     * @return promise that completes when polling is done
     */
    Promise<Integer> pollAndProcess(String tenantId, int limit);
    
    /**
     * Gets pending outbox entries for a tenant.
     *
     * @param tenantId the tenant ID
     * @param limit maximum number of entries to return
     * @return promise that completes with the list of pending entries
     */
    Promise<List<EntityWriteOutbox>> getPendingEntries(String tenantId, int limit);
    
    /**
     * Marks an outbox entry as completed.
     *
     * @param outboxId the outbox entry ID
     * @return promise that completes when the update is done
     */
    Promise<Void> markCompleted(String outboxId);
    
    /**
     * Marks an outbox entry as failed.
     *
     * @param outboxId the outbox entry ID
     * @param errorMessage the error message
     * @return promise that completes when the update is done
     */
    Promise<Void> markFailed(String outboxId, String errorMessage);
}
