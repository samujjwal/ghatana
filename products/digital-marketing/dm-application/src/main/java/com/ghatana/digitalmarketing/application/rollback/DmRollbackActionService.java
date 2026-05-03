package com.ghatana.digitalmarketing.application.rollback;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.rollback.DmRollbackAction;
import com.ghatana.digitalmarketing.domain.rollback.DmRollbackStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for rollback and compensating action management.
 *
 * @doc.type interface
 * @doc.purpose Schedules and tracks compensating rollback actions (DMOS-F2-014)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmRollbackActionService {

    Promise<DmRollbackAction> schedule(DmOperationContext ctx, ScheduleRollbackCommand command);

    Promise<DmRollbackAction> markCompleted(DmOperationContext ctx, String actionId);

    Promise<DmRollbackAction> markFailed(DmOperationContext ctx, String actionId, String reason);

    Promise<Optional<DmRollbackAction>> findById(DmOperationContext ctx, String actionId);

    Promise<List<DmRollbackAction>> listByCommand(DmOperationContext ctx, String commandId);

    Promise<List<DmRollbackAction>> listByStatus(DmOperationContext ctx, DmRollbackStatus status, int limit);

    /**
     * Command to schedule a rollback compensating action.
     */
    record ScheduleRollbackCommand(
        String commandId,
        String actionType,
        String targetEntityId,
        String targetEntityType
    ) {
        public ScheduleRollbackCommand {
            Objects.requireNonNull(commandId, "commandId must not be null");
            Objects.requireNonNull(actionType, "actionType must not be null");
            Objects.requireNonNull(targetEntityId, "targetEntityId must not be null");
            Objects.requireNonNull(targetEntityType, "targetEntityType must not be null");
            if (commandId.isBlank()) throw new IllegalArgumentException("commandId must not be blank");
            if (actionType.isBlank()) throw new IllegalArgumentException("actionType must not be blank");
        }
    }
}
