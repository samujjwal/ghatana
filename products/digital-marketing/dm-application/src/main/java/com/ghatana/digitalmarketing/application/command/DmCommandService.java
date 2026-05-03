package com.ghatana.digitalmarketing.application.command;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * DMOS command store service.
 *
 * <p>Provides lifecycle management for durable DMOS commands:
 * creation, execution tracking, failure recording, and rollback marking.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS command store service interface (DMOS-F2-003)
 * @doc.layer product
 * @doc.pattern ApplicationService, CQRS
 */
public interface DmCommandService {

    /**
     * Issues a new command to the store.
     *
     * @param ctx     operation context
     * @param request command creation request
     * @return the created command
     */
    Promise<DmCommand> issue(DmOperationContext ctx, IssueCommandRequest request);

    /**
     * Retrieves a command by ID, scoped to the tenant in {@code ctx}.
     *
     * @param ctx operation context
     * @param id  command ID
     * @return the command if found
     */
    Promise<Optional<DmCommand>> findById(DmOperationContext ctx, String id);

    /**
     * Returns all PENDING commands for the tenant up to {@code limit}.
     *
     * @param ctx   operation context
     * @param limit maximum results
     * @return pending commands
     */
    Promise<List<DmCommand>> listPending(DmOperationContext ctx, int limit);

    /**
     * Marks a command as executing (increments attempt count, sets status to EXECUTING).
     *
     * @param ctx       operation context
     * @param commandId command ID
     * @return updated command
     */
    Promise<DmCommand> markExecuting(DmOperationContext ctx, String commandId);

    /**
     * Marks a command as succeeded.
     *
     * @param ctx       operation context
     * @param commandId command ID
     * @return updated command
     */
    Promise<DmCommand> markSucceeded(DmOperationContext ctx, String commandId);

    /**
     * Marks a command as failed.
     *
     * @param ctx           operation context
     * @param commandId     command ID
     * @param failureReason human-readable failure reason
     * @return updated command
     */
    Promise<DmCommand> markFailed(DmOperationContext ctx, String commandId, String failureReason);

    /**
     * Marks a command as rolled back.
     *
     * @param ctx       operation context
     * @param commandId command ID
     * @return updated command
     */
    Promise<DmCommand> markRolledBack(DmOperationContext ctx, String commandId);

    /**
     * Counts commands by status for the tenant.
     *
     * @param ctx    operation context
     * @param status status to count
     * @return count
     */
    Promise<Long> countByStatus(DmOperationContext ctx, DmCommandStatus status);

    /**
     * Immutable command creation request.
     *
     * @param commandType       the command type
     * @param serializedPayload JSON-serialized command payload
     */
    record IssueCommandRequest(
        com.ghatana.digitalmarketing.domain.command.DmCommandType commandType,
        String serializedPayload
    ) {
        public IssueCommandRequest {
            java.util.Objects.requireNonNull(commandType, "commandType must not be null");
            if (serializedPayload == null || serializedPayload.isBlank())
                throw new IllegalArgumentException("serializedPayload must not be blank");
        }
    }
}
