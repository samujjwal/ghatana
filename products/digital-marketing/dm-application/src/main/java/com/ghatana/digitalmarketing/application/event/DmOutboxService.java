package com.ghatana.digitalmarketing.application.event;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.event.DmDeadLetterEntry;
import com.ghatana.digitalmarketing.domain.event.DmEvent;
import com.ghatana.digitalmarketing.domain.event.DmOutboxEntry;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Transactional outbox and dead-letter queue service for DMOS domain events.
 *
 * <p>Provides the following operations:</p>
 * <ol>
 *   <li><strong>Append</strong> — write a {@link DmEvent} to the outbox within the producing transaction.</li>
 *   <li><strong>Dispatch cycle</strong> — fetch PENDING entries and deliver them to the event bus.</li>
 *   <li><strong>Retry cycle</strong> — re-attempt FAILED entries whose attempt count is below the limit.</li>
 *   <li><strong>DLQ management</strong> — list and replay dead-letter entries.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose DMOS outbox/inbox/DLQ service interface (DMOS-F2-002)
 * @doc.layer product
 * @doc.pattern ApplicationService, Outbox
 */
public interface DmOutboxService {

    /**
     * Appends a domain event to the transactional outbox.
     *
     * <p>This must be called within the same unit of work (transaction) as the
     * state change that produced the event.</p>
     *
     * @param ctx   operation context carrying tenant/workspace/actor
     * @param event event to append
     * @param <T>   payload type
     * @return the created outbox entry
     */
    <T> Promise<DmOutboxEntry> append(DmOperationContext ctx, DmEvent<T> event);

    /**
     * Dispatches up to {@code batchSize} PENDING outbox entries for the given tenant.
     *
     * <p>Each entry is dispatched and then either marked DISPATCHED (on success)
     * or FAILED / DEAD (on failure). DEAD entries are automatically moved to the DLQ.</p>
     *
     * @param tenantId  tenant scope, must not be blank
     * @param batchSize maximum entries to process, must be positive
     * @return count of successfully dispatched entries
     */
    Promise<Integer> dispatchPending(String tenantId, int batchSize);

    /**
     * Retries all FAILED but still-retryable outbox entries for the given tenant.
     *
     * @param tenantId  tenant scope
     * @param batchSize maximum entries to retry
     * @return count of entries that were successfully retried
     */
    Promise<Integer> retryFailed(String tenantId, int batchSize);

    /**
     * Returns all dead-letter entries for the tenant that have not been replayed.
     *
     * @param ctx   operation context
     * @param limit maximum entries to return
     * @return list of unreplayed DLQ entries
     */
    Promise<List<DmDeadLetterEntry>> listDeadLetters(DmOperationContext ctx, int limit);

    /**
     * Marks a DLQ entry as replayed and re-queues it for dispatch.
     *
     * @param ctx     operation context
     * @param dlqId   DLQ entry ID to replay
     * @return the replayed DLQ entry
     */
    Promise<DmDeadLetterEntry> replayDeadLetter(DmOperationContext ctx, String dlqId);
}
