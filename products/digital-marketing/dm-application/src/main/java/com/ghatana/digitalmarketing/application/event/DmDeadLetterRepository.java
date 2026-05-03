package com.ghatana.digitalmarketing.application.event;

import com.ghatana.digitalmarketing.domain.event.DmDeadLetterEntry;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the DMOS dead-letter queue.
 *
 * <p>DLQ entries are created when an outbox entry exhausts all dispatch attempts.
 * They are kept for operator inspection and selective replay.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS DLQ persistence port (DMOS-F2-002)
 * @doc.layer product
 * @doc.pattern Repository, Port
 */
public interface DmDeadLetterRepository {

    /**
     * Persists a new DLQ entry.
     *
     * @param entry entry to save
     * @return the saved entry
     */
    Promise<DmDeadLetterEntry> save(DmDeadLetterEntry entry);

    /**
     * Returns all DLQ entries for a tenant that have not yet been replayed.
     *
     * @param tenantId tenant scope
     * @param limit    maximum entries to return
     * @return pending DLQ entries
     */
    Promise<List<DmDeadLetterEntry>> findUnreplayed(String tenantId, int limit);

    /**
     * Finds a DLQ entry by ID.
     *
     * @param id entry ID
     * @return entry if found
     */
    Promise<Optional<DmDeadLetterEntry>> findById(String id);

    /**
     * Updates a DLQ entry (e.g. to mark it replayed).
     *
     * @param entry updated entry
     * @return the updated entry
     */
    Promise<DmDeadLetterEntry> update(DmDeadLetterEntry entry);
}
