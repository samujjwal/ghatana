package com.ghatana.digitalmarketing.application.event;

import com.ghatana.digitalmarketing.domain.event.DmOutboxEntry;
import com.ghatana.digitalmarketing.domain.event.DmOutboxStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the DMOS transactional outbox.
 *
 * <p>All writes must participate in the same database transaction as the
 * business state change that produces the event. Implementations are
 * responsible for tenant isolation via {@code tenantId}.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS outbox persistence port for transactional event dispatch (DMOS-F2-002)
 * @doc.layer product
 * @doc.pattern Repository, Port
 */
public interface DmOutboxRepository {

    /**
     * Persists a new outbox entry atomically with the producing transaction.
     *
     * @param entry entry to save, must not be {@code null}
     * @return the saved entry
     */
    Promise<DmOutboxEntry> save(DmOutboxEntry entry);

    /**
     * Returns all PENDING entries ready for dispatch, ordered by {@code scheduledAt} ascending.
     *
     * @param tenantId tenant scope, must not be blank
     * @param limit    maximum number of entries to return
     * @return up to {@code limit} PENDING entries
     */
    Promise<List<DmOutboxEntry>> findPending(String tenantId, int limit);

    /**
     * Returns all FAILED entries eligible for retry.
     *
     * @param tenantId tenant scope
     * @param limit    maximum entries
     * @return retryable FAILED entries
     */
    Promise<List<DmOutboxEntry>> findRetryable(String tenantId, int limit);

    /**
     * Updates the status and metadata of an existing outbox entry.
     *
     * @param entry updated entry, must not be {@code null}
     * @return the updated entry
     */
    Promise<DmOutboxEntry> update(DmOutboxEntry entry);

    /**
     * Finds an entry by its ID.
     *
     * @param id entry ID
     * @return the entry if found
     */
    Promise<Optional<DmOutboxEntry>> findById(String id);

    /**
     * Counts entries by status for the given tenant.
     *
     * @param tenantId tenant scope
     * @param status   status to count
     * @return entry count
     */
    Promise<Long> countByStatus(String tenantId, DmOutboxStatus status);
}
