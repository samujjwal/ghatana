package com.ghatana.digitalmarketing.application.crm;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.crm.DmCrmIntegration;
import com.ghatana.digitalmarketing.domain.crm.DmCrmIntegrationStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmCrmIntegrationService}.
 *
 * @doc.type class
 * @doc.purpose Manages lifecycle of external CRM integrations (DMOS-F4-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmCrmIntegrationServiceImpl implements DmCrmIntegrationService {

    private final DmCrmIntegrationRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmCrmIntegrationServiceImpl(
            DmCrmIntegrationRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmCrmIntegration> create(DmOperationContext ctx, CreateCrmIntegrationCommand cmd) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(cmd, "cmd must not be null");
        return kernelAdapter.isAuthorized(ctx, "crm-integration", "create")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to create CRM integration"));
                Instant now = Instant.now();
                DmCrmIntegration integration = DmCrmIntegration.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .crmProvider(cmd.crmProvider())
                    .displayName(cmd.displayName())
                    .apiEndpoint(cmd.apiEndpoint())
                    .credentialRef(cmd.credentialRef())
                    .status(DmCrmIntegrationStatus.PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
                return repository.save(integration);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "crm.create",
                Map.of("provider", (Object) saved.getCrmProvider()))
                .map(__ -> saved));
    }

    @Override
    public Promise<DmCrmIntegration> activate(DmOperationContext ctx, String integrationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(integrationId, "integrationId must not be null");
        return kernelAdapter.isAuthorized(ctx, "crm-integration", "activate")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to activate CRM integration"));
                return repository.findById(integrationId);
            })
            .then(opt -> {
                DmCrmIntegration existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Integration not found: " + integrationId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Integration does not belong to tenant"));
                return repository.update(existing.activate());
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "crm.activate",
                Map.of("tenantId", (Object) updated.getTenantId()))
                .map(__ -> updated));
    }

    @Override
    public Promise<DmCrmIntegration> recordSync(DmOperationContext ctx, String integrationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(integrationId, "integrationId must not be null");
        return kernelAdapter.isAuthorized(ctx, "crm-integration", "sync")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to sync CRM integration"));
                return repository.findById(integrationId);
            })
            .then(opt -> {
                DmCrmIntegration existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Integration not found: " + integrationId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Integration does not belong to tenant"));
                return repository.update(existing.recordSync());
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "crm.sync",
                Map.of("tenantId", (Object) updated.getTenantId()))
                .map(__ -> updated));
    }

    @Override
    public Promise<DmCrmIntegration> markFailed(DmOperationContext ctx, String integrationId, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(integrationId, "integrationId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        return kernelAdapter.isAuthorized(ctx, "crm-integration", "update")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to update CRM integration"));
                return repository.findById(integrationId);
            })
            .then(opt -> {
                DmCrmIntegration existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Integration not found: " + integrationId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Integration does not belong to tenant"));
                return repository.update(existing.markFailed(reason));
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "crm.failed",
                Map.of("reason", (Object) reason))
                .map(__ -> updated));
    }

    @Override
    public Promise<Optional<DmCrmIntegration>> findById(DmOperationContext ctx, String integrationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(integrationId, "integrationId must not be null");
        return repository.findById(integrationId)
            .map(opt -> opt.filter(i -> i.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmCrmIntegration>> listByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.listByTenant(ctx.getTenantId().getValue());
    }
}
