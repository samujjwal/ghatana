package com.ghatana.digitalmarketing.application.workflow;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowExecution;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStatus;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStep;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmWorkflowService}.
 *
 * <p>All state-changing operations verify authorization via the kernel adapter.
 * Workflow executions are scoped to a tenant; cross-tenant access is rejected.</p>
 *
 * @doc.type class
 * @doc.purpose Implements durable workflow lifecycle management with authorization and tenant isolation (DMOS-F2-004)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmWorkflowServiceImpl implements DmWorkflowService {

    private final DmWorkflowRepository workflowRepository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmWorkflowServiceImpl(DmWorkflowRepository workflowRepository,
                                  DigitalMarketingKernelAdapter kernelAdapter) {
        this.workflowRepository = Objects.requireNonNull(workflowRepository, "workflowRepository must not be null");
        this.kernelAdapter      = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmWorkflowExecution> initiate(DmOperationContext ctx, InitiateWorkflowRequest request) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(request, "request must not be null");
        return kernelAdapter.isAuthorized(ctx, "workflows/*", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to initiate workflows"));
                }
                DmWorkflowExecution execution = DmWorkflowExecution.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .name(request.name())
                    .correlationId(request.correlationId())
                    .steps(request.steps())
                    .currentStepIndex(0)
                    .status(DmWorkflowStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();
                return workflowRepository.save(execution);
            });
    }

    @Override
    public Promise<DmWorkflowExecution> start(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        requireNonBlank(id, "id");
        return kernelAdapter.isAuthorized(ctx, "workflows/*", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to start workflows"));
                }
                return loadOwnedExecution(ctx, id)
                    .then(execution -> workflowRepository.update(execution.start()));
            });
    }

    @Override
    public Promise<DmWorkflowExecution> advanceStep(DmOperationContext ctx, String id,
                                                     DmWorkflowStep completedStep) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        requireNonBlank(id, "id");
        Objects.requireNonNull(completedStep, "completedStep must not be null");
        return kernelAdapter.isAuthorized(ctx, "workflows/*", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to advance workflows"));
                }
                return loadOwnedExecution(ctx, id)
                    .then(execution -> workflowRepository.update(execution.advanceStep(completedStep)));
            });
    }

    @Override
    public Promise<DmWorkflowExecution> complete(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        requireNonBlank(id, "id");
        return kernelAdapter.isAuthorized(ctx, "workflows/*", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to complete workflows"));
                }
                return loadOwnedExecution(ctx, id)
                    .then(execution -> workflowRepository.update(execution.complete()));
            });
    }

    @Override
    public Promise<DmWorkflowExecution> fail(DmOperationContext ctx, String id, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        requireNonBlank(id, "id");
        Objects.requireNonNull(reason, "reason must not be null");
        return kernelAdapter.isAuthorized(ctx, "workflows/*", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to fail workflows"));
                }
                return loadOwnedExecution(ctx, id)
                    .then(execution -> workflowRepository.update(execution.fail(reason)));
            });
    }

    @Override
    public Promise<DmWorkflowExecution> pause(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        requireNonBlank(id, "id");
        return kernelAdapter.isAuthorized(ctx, "workflows/*", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to pause workflows"));
                }
                return loadOwnedExecution(ctx, id)
                    .then(execution -> workflowRepository.update(execution.pause()));
            });
    }

    @Override
    public Promise<DmWorkflowExecution> resume(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        requireNonBlank(id, "id");
        return kernelAdapter.isAuthorized(ctx, "workflows/*", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to resume workflows"));
                }
                return loadOwnedExecution(ctx, id)
                    .then(execution -> workflowRepository.update(execution.resume()));
            });
    }

    @Override
    public Promise<DmWorkflowExecution> rollback(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        requireNonBlank(id, "id");
        return kernelAdapter.isAuthorized(ctx, "workflows/*", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to rollback workflows"));
                }
                return loadOwnedExecution(ctx, id)
                    .then(execution -> workflowRepository.update(execution.rollback()));
            });
    }

    @Override
    public Promise<Optional<DmWorkflowExecution>> findById(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        requireNonBlank(id, "id");
        String tenantId = ctx.getTenantId().getValue();
        return workflowRepository.findById(id)
            .then(opt -> Promise.of(opt.filter(e -> tenantId.equals(e.getTenantId()))));
    }

    @Override
    public Promise<List<DmWorkflowExecution>> listActive(DmOperationContext ctx, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return workflowRepository.findActive(ctx.getTenantId().getValue(), limit);
    }

    @Override
    public Promise<Long> countByStatus(DmOperationContext ctx, DmWorkflowStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return workflowRepository.countByStatus(ctx.getTenantId().getValue(), status);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Promise<DmWorkflowExecution> loadOwnedExecution(DmOperationContext ctx, String id) {
        String tenantId = ctx.getTenantId().getValue();
        return workflowRepository.findById(id)
            .then(opt -> {
                DmWorkflowExecution execution = opt.orElseThrow(
                    () -> new NoSuchElementException("Workflow not found: " + id));
                if (!tenantId.equals(execution.getTenantId())) {
                    throw new NoSuchElementException("Workflow not found: " + id);
                }
                return Promise.of(execution);
            });
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be null or blank");
    }
}
