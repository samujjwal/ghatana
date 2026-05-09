package com.ghatana.digitalmarketing.application.command;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * P0-006: Dead-letter queue for permanently failed commands.
 *
 * <p>Commands that exceed MAX_ATTEMPTS are moved to the DLQ for manual inspection
 * and potential replay. This ensures no failed command is silently lost.</p>
 *
 * <p>DLQ entries include:</p>
 * <ul>
 *   <li>The original command payload</li>
 *   <li>Failure reason and last error</li>
 *   <li>Timestamp when moved to DLQ</li>
 *   <li>Manual replay capability</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Dead-letter queue for permanently failed commands (P0-006)
 * @doc.layer product
 * @doc.pattern Dead-Letter Queue
 */
public interface DmDeadLetterQueue {

    /**
     * Moves a command to the dead-letter queue.
     *
     * @param ctx the operation context
     * @param command the command to move
     * @param finalFailureReason the final failure reason
     * @return promise resolving when the command is moved to DLQ
     */
    Promise<Void> moveToDlq(DmOperationContext ctx, DmCommand command, String finalFailureReason);

    /**
     * Retrieves a DLQ entry by command ID.
     *
     * @param ctx the operation context
     * @param commandId the command ID
     * @return the DLQ entry if found
     */
    Promise<Optional<DlqEntry>> findById(DmOperationContext ctx, String commandId);

    /**
     * Lists all DLQ entries for the tenant.
     *
     * @param ctx the operation context
     * @param limit maximum results
     * @return DLQ entries
     */
    Promise<List<DlqEntry>> list(DmOperationContext ctx, int limit);

    /**
     * Replays a command from the DLQ.
     *
     * <p>This creates a new command with the same payload but a new ID,
     * resetting the attempt count to zero.</p>
     *
     * @param ctx the operation context
     * @param dlqEntryId the DLQ entry ID
     * @param replayedBy the principal requesting the replay
     * @return promise resolving to the new command ID
     */
    Promise<String> replay(DmOperationContext ctx, String dlqEntryId, String replayedBy);

    /**
     * Permanently deletes a DLQ entry (after manual resolution).
     *
     * @param ctx the operation context
     * @param dlqEntryId the DLQ entry ID
     * @return promise resolving when the entry is deleted
     */
    Promise<Void> delete(DmOperationContext ctx, String dlqEntryId);

    /**
     * DLQ entry representing a permanently failed command.
     */
    record DlqEntry(
        String id,
        String originalCommandId,
        String commandType,
        String tenantId,
        String workspaceId,
        String serializedPayload,
        String failureReason,
        int attemptCount,
        Instant movedToDlqAt,
        Instant originalCreatedAt,
        String correlationId
    ) {}
}
