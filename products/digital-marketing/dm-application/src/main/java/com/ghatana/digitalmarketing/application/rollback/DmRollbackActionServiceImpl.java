package com.ghatana.digitalmarketing.application.rollback;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.rollback.DmRollbackAction;
import com.ghatana.digitalmarketing.domain.rollback.DmRollbackStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmRollbackActionService}.
 *
 * @doc.type class
 * @doc.purpose Schedules and executes compensating rollback actions (DMOS-F2-014)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmRollbackActionServiceImpl implements DmRollbackActionService {

    private final DmRollbackActionRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmRollbackActionServiceImpl(
            DmRollbackActionRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmRollbackAction> schedule(DmOperationContext ctx, ScheduleRollbackCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "rollback-actions", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to schedule rollback actions"));
                }
                DmRollbackAction action = DmRollbackAction.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .commandId(command.commandId())
                    .actionType(command.actionType())
                    .targetEntityId(command.targetEntityId())
                    .targetEntityType(command.targetEntityType())
                    .status(DmRollbackStatus.PENDING)
                    .scheduledAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
                return repository.save(action)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "rollback-scheduled",
                        Map.of("commandId", command.commandId(), "actionType", command.actionType())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmRollbackAction> markCompleted(DmOperationContext ctx, String actionId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(actionId, "actionId must not be null");

        return kernelAdapter.isAuthorized(ctx, "rollback-actions", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update rollback actions"));
                }
                return loadAndValidateTenant(ctx, actionId)
                    .then(existing -> {
                        DmRollbackAction updated = existing.markCompleted();
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "rollback-completed", Map.of()
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<DmRollbackAction> markFailed(DmOperationContext ctx, String actionId, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(actionId, "actionId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        return kernelAdapter.isAuthorized(ctx, "rollback-actions", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update rollback actions"));
                }
                return loadAndValidateTenant(ctx, actionId)
                    .then(existing -> {
                        DmRollbackAction updated = existing.markFailed(reason);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "rollback-failed", Map.of("reason", reason)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmRollbackAction>> findById(DmOperationContext ctx, String actionId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(actionId, "actionId must not be null");

        return repository.findById(actionId)
            .map(opt -> opt.filter(a -> a.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmRollbackAction>> listByCommand(DmOperationContext ctx, String commandId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(commandId, "commandId must not be null");

        return kernelAdapter.isAuthorized(ctx, "rollback-actions", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list rollback actions"));
                }
                return repository.listByCommand(ctx.getTenantId().getValue(), commandId);
            });
    }

    @Override
    public Promise<List<DmRollbackAction>> listByStatus(DmOperationContext ctx, DmRollbackStatus status, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(status, "status must not be null");

        return kernelAdapter.isAuthorized(ctx, "rollback-actions", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list rollback actions"));
                }
                return repository.listByStatus(ctx.getTenantId().getValue(), status, limit);
            });
    }

    private Promise<DmRollbackAction> loadAndValidateTenant(DmOperationContext ctx, String actionId) {
        return repository.findById(actionId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Rollback action not found: " + actionId));
                }
                DmRollbackAction a = opt.get();
                if (!a.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Rollback action does not belong to tenant"));
                }
                return Promise.of(a);
            });
    }
}
