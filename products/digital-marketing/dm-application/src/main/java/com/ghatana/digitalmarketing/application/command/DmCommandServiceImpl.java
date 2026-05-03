package com.ghatana.digitalmarketing.application.command;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmCommandService}.
 *
 * <p>All state-changing operations verify authorization via the kernel adapter.
 * Commands are scoped to a tenant; cross-tenant access is rejected.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS command store service implementation (DMOS-F2-003)
 * @doc.layer product
 * @doc.pattern ApplicationService, CQRS
 */
public final class DmCommandServiceImpl implements DmCommandService {

    private final DmCommandRepository commandRepository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmCommandServiceImpl(
            DmCommandRepository commandRepository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.commandRepository = Objects.requireNonNull(commandRepository, "commandRepository must not be null");
        this.kernelAdapter     = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmCommand> issue(DmOperationContext ctx, IssueCommandRequest request) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return kernelAdapter.isAuthorized(ctx, "commands/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to issue commands"));

                Instant now = Instant.now();
                DmCommand command = DmCommand.builder()
                    .id(UUID.randomUUID().toString())
                    .commandType(request.commandType())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .correlationId(ctx.getCorrelationId().getValue())
                    .issuedBy(ctx.getActor().getPrincipalId())
                    .serializedPayload(request.serializedPayload())
                    .status(DmCommandStatus.PENDING)
                    .attemptCount(0)
                    .createdAt(now)
                    .scheduledAt(now)
                    .build();

                return commandRepository.save(command);
            });
    }

    @Override
    public Promise<Optional<DmCommand>> findById(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return commandRepository.findById(id)
            .then(opt -> {
                if (opt.isPresent() && !opt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.of(Optional.empty());
                }
                return Promise.of(opt);
            });
    }

    @Override
    public Promise<List<DmCommand>> listPending(DmOperationContext ctx, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return commandRepository.findPending(ctx.getTenantId().getValue(), safeLimit);
    }

    @Override
    public Promise<DmCommand> markExecuting(DmOperationContext ctx, String commandId) {
        return loadOwnedCommand(ctx, commandId)
            .then(cmd -> commandRepository.update(cmd.markExecuting()));
    }

    @Override
    public Promise<DmCommand> markSucceeded(DmOperationContext ctx, String commandId) {
        return loadOwnedCommand(ctx, commandId)
            .then(cmd -> commandRepository.update(cmd.markSucceeded()));
    }

    @Override
    public Promise<DmCommand> markFailed(DmOperationContext ctx, String commandId, String failureReason) {
        return loadOwnedCommand(ctx, commandId)
            .then(cmd -> commandRepository.update(cmd.markFailed(failureReason)));
    }

    @Override
    public Promise<DmCommand> markRolledBack(DmOperationContext ctx, String commandId) {
        return loadOwnedCommand(ctx, commandId)
            .then(cmd -> {
                if (cmd.getStatus() != DmCommandStatus.FAILED)
                    return Promise.ofException(new IllegalStateException(
                        "Only FAILED commands can be rolled back; was: " + cmd.getStatus()));
                return commandRepository.update(cmd.markRolledBack());
            });
    }

    @Override
    public Promise<Long> countByStatus(DmOperationContext ctx, DmCommandStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return commandRepository.countByStatus(ctx.getTenantId().getValue(), status);
    }

    private Promise<DmCommand> loadOwnedCommand(DmOperationContext ctx, String commandId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (commandId == null || commandId.isBlank())
            return Promise.ofException(new IllegalArgumentException("commandId must not be blank"));

        return commandRepository.findById(commandId)
            .then(opt -> {
                if (opt.isEmpty())
                    return Promise.ofException(new NoSuchElementException("Command not found: " + commandId));
                DmCommand cmd = opt.get();
                if (!cmd.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Command not found: " + commandId));
                return Promise.of(cmd);
            });
    }
}
