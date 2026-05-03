package com.ghatana.digitalmarketing.application.command;

import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the DMOS command store.
 *
 * <p>Implementations must enforce tenant isolation on all query operations.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS command store persistence port (DMOS-F2-003)
 * @doc.layer product
 * @doc.pattern Repository, Port
 */
public interface DmCommandRepository {

    /**
     * Persists a new command.
     *
     * @param command command to save
     * @return saved command
     */
    Promise<DmCommand> save(DmCommand command);

    /**
     * Finds a command by its ID.
     *
     * @param id command ID
     * @return the command if found
     */
    Promise<Optional<DmCommand>> findById(String id);

    /**
     * Returns PENDING commands ready for execution, ordered by {@code scheduledAt}.
     *
     * @param tenantId tenant scope
     * @param limit    maximum commands to return
     * @return pending commands
     */
    Promise<List<DmCommand>> findPending(String tenantId, int limit);

    /**
     * Returns commands by type and status for a tenant.
     *
     * @param tenantId    tenant scope
     * @param commandType command type filter
     * @param status      status filter
     * @param limit       max results
     * @return matching commands
     */
    Promise<List<DmCommand>> findByTypeAndStatus(
            String tenantId, DmCommandType commandType, DmCommandStatus status, int limit);

    /**
     * Updates an existing command (status, attempt count, timestamps).
     *
     * @param command updated command
     * @return updated command
     */
    Promise<DmCommand> update(DmCommand command);

    /**
     * Counts commands by status for the given tenant.
     *
     * @param tenantId tenant scope
     * @param status   status filter
     * @return count
     */
    Promise<Long> countByStatus(String tenantId, DmCommandStatus status);
}
