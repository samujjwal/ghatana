package com.ghatana.digitalmarketing.application.funnel;

import com.ghatana.digitalmarketing.application.funnel.DemoWorkspaceService.ProvisionDemoWorkspaceCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.funnel.DemoWorkspace;
import com.ghatana.digitalmarketing.domain.funnel.DemoWorkspaceStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of DemoWorkspaceService.
 *
 * @doc.type class
 * @doc.purpose Demo workspace service implementation (P3-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public class DemoWorkspaceServiceImpl implements DemoWorkspaceService {

    private final DemoWorkspaceRepository repository;

    public DemoWorkspaceServiceImpl(DemoWorkspaceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<DemoWorkspace> provision(DmOperationContext ctx, ProvisionDemoWorkspaceCommand command) {
        String workspaceId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(command.trialDuration());

        DemoWorkspace workspace = DemoWorkspace.builder()
            .id(workspaceId)
            .tenantId(ctx.getTenantId().getValue())
            .workspaceId(ctx.getWorkspaceId().getValue())
            .leadId(command.leadId())
            .templateId(command.templateId())
            .status(DemoWorkspaceStatus.PROVISIONED)
            .templateConfig(command.templateConfig())
            .createdAt(now)
            .expiresAt(expiresAt)
            .build();

        return repository.save(workspace);
    }

    @Override
    public Promise<DemoWorkspace> activate(DmOperationContext ctx, String workspaceId) {
        return repository.findById(workspaceId)
            .then(workspaceOpt -> {
                if (workspaceOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Demo workspace not found: " + workspaceId));
                }
                DemoWorkspace workspace = workspaceOpt.get();
                DemoWorkspace activated = workspace.activate();
                return repository.save(activated);
            });
    }

    @Override
    public Promise<DemoWorkspace> deactivate(DmOperationContext ctx, String workspaceId, String reason) {
        return repository.findById(workspaceId)
            .then(workspaceOpt -> {
                if (workspaceOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Demo workspace not found: " + workspaceId));
                }
                DemoWorkspace workspace = workspaceOpt.get();
                DemoWorkspace deactivated = workspace.deactivate(reason);
                return repository.save(deactivated);
            });
    }

    @Override
    public Promise<DemoWorkspace> expire(DmOperationContext ctx, String workspaceId) {
        return repository.findById(workspaceId)
            .then(workspaceOpt -> {
                if (workspaceOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Demo workspace not found: " + workspaceId));
                }
                DemoWorkspace workspace = workspaceOpt.get();
                DemoWorkspace expired = workspace.expire();
                return repository.save(expired);
            });
    }

    @Override
    public Promise<Optional<DemoWorkspace>> findById(DmOperationContext ctx, String workspaceId) {
        return repository.findById(workspaceId);
    }

    @Override
    public Promise<java.util.List<DemoWorkspace>> findByLeadId(DmOperationContext ctx, String leadId) {
        return repository.findByLeadId(leadId);
    }

    @Override
    public Promise<java.util.List<DemoWorkspace>> list(DmOperationContext ctx) {
        return repository.listByTenant(ctx.getTenantId().getValue());
    }
}
