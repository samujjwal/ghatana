package com.ghatana.digitalmarketing.application.transparency;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of AI transparency timeline service.
 *
 * @doc.type class
 * @doc.purpose DMOS F1-025 service implementation for action log
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AiActionLogServiceImpl implements AiActionLogService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final AiActionLogRepository repository;

    public AiActionLogServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            AiActionLogRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<AiActionLogEntry> recordAction(DmOperationContext ctx, RecordActionCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "ai-action-log", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorized to write action log"));
                }
                String actor = command.actor() == null || command.actor().isBlank()
                    ? ctx.getActor().getPrincipalId()
                    : command.actor();
                String correlationId = command.correlationId() == null || command.correlationId().isBlank()
                    ? ctx.getCorrelationId().getValue()
                    : command.correlationId();
                AiActionLogEntry entry = new AiActionLogEntry(
                    UUID.randomUUID().toString(),
                    ctx.getWorkspaceId().getValue(),
                    correlationId,
                    command.actionType(),
                    command.status(),
                    actor,
                    command.initiatedByAi(),
                    command.confidence(),
                    command.evidenceLinks(),
                    command.policyChecks(),
                    command.summary(),
                    command.details(),
                    command.relatedEntityId(),
                    Instant.now(),
                    0L
                );
                return repository.save(entry);
            });
    }

    @Override
    public Promise<List<AiActionLogEntry>> listActions(DmOperationContext ctx, ListActionsQuery query) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(query, "query must not be null");

        int limit = query.limit() <= 0 ? 50 : Math.min(query.limit(), 200);

        return kernelAdapter.isAuthorized(ctx, "ai-action-log", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read action log"));
                }
                return repository.findByWorkspace(
                    ctx.getWorkspaceId().getValue(),
                    query.correlationId(),
                    query.relatedEntityId(),
                    limit
                );
            })
            .then(entries -> kernelAdapter.isAuthorized(ctx, "ai-action-log-sensitive", "read")
                .map(canReadSensitive -> canReadSensitive
                    ? entries
                    : entries.stream().map(AiActionLogEntry::redacted).toList()));
    }

    @Override
    public Promise<AiActionLogEntry> getAction(DmOperationContext ctx, String actionId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(actionId, "actionId must not be null");
        if (actionId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("actionId must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "ai-action-log", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read action log"));
                }
                return repository.findById(ctx.getWorkspaceId().getValue(), actionId)
                    .map(opt -> opt.orElseThrow(() ->
                        new NoSuchElementException("Action log entry not found: " + actionId)));
            })
            .then(entry -> kernelAdapter.isAuthorized(ctx, "ai-action-log-sensitive", "read")
                .map(canReadSensitive -> canReadSensitive ? entry : entry.redacted()));
    }
}
