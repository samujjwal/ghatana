package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmMetaAdsConnector;
import com.ghatana.digitalmarketing.domain.connector.DmMetaAdsConnectorStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmMetaAdsConnectorService}.
 *
 * @doc.type class
 * @doc.purpose Manages lifecycle of Meta Ads connector integrations (DMOS-F4-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmMetaAdsConnectorServiceImpl implements DmMetaAdsConnectorService {

    private final DmMetaAdsConnectorRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmMetaAdsConnectorServiceImpl(
            DmMetaAdsConnectorRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmMetaAdsConnector> register(DmOperationContext ctx, RegisterMetaAdsConnectorCommand cmd) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(cmd, "cmd must not be null");
        return kernelAdapter.isAuthorized(ctx, "meta-ads-connector", "create")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to register Meta Ads connector"));
                Instant now = Instant.now();
                DmMetaAdsConnector connector = DmMetaAdsConnector.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .displayName(cmd.displayName())
                    .appId(cmd.appId())
                    .accountId(cmd.accountId())
                    .accessToken(cmd.accessToken())
                    .status(DmMetaAdsConnectorStatus.PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
                return repository.save(connector);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "connector.register",
                Map.of("tenantId", (Object) saved.getTenantId()))
                .map(__ -> saved));
    }

    @Override
    public Promise<DmMetaAdsConnector> activate(DmOperationContext ctx, String connectorId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(connectorId, "connectorId must not be null");
        return kernelAdapter.isAuthorized(ctx, "meta-ads-connector", "activate")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to activate Meta Ads connector"));
                return repository.findById(connectorId);
            })
            .then(opt -> {
                DmMetaAdsConnector existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Connector not found: " + connectorId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Connector does not belong to tenant"));
                return repository.update(existing.activate());
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "connector.activate",
                Map.of("tenantId", (Object) updated.getTenantId()))
                .map(__ -> updated));
    }

    @Override
    public Promise<DmMetaAdsConnector> markFailed(DmOperationContext ctx, String connectorId, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(connectorId, "connectorId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        return kernelAdapter.isAuthorized(ctx, "meta-ads-connector", "update")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to update Meta Ads connector"));
                return repository.findById(connectorId);
            })
            .then(opt -> {
                DmMetaAdsConnector existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Connector not found: " + connectorId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Connector does not belong to tenant"));
                return repository.update(existing.markFailed(reason));
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "connector.failed",
                Map.of("reason", (Object) reason))
                .map(__ -> updated));
    }

    @Override
    public Promise<DmMetaAdsConnector> disconnect(DmOperationContext ctx, String connectorId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(connectorId, "connectorId must not be null");
        return kernelAdapter.isAuthorized(ctx, "meta-ads-connector", "disconnect")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to disconnect Meta Ads connector"));
                return repository.findById(connectorId);
            })
            .then(opt -> {
                DmMetaAdsConnector existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Connector not found: " + connectorId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Connector does not belong to tenant"));
                DmMetaAdsConnector disconnected = existing.toBuilder()
                    .status(DmMetaAdsConnectorStatus.DISCONNECTED)
                    .updatedAt(Instant.now())
                    .build();
                return repository.update(disconnected);
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "connector.disconnect",
                Map.of("tenantId", (Object) updated.getTenantId()))
                .map(__ -> updated));
    }

    @Override
    public Promise<Optional<DmMetaAdsConnector>> findById(DmOperationContext ctx, String connectorId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(connectorId, "connectorId must not be null");
        return repository.findById(connectorId)
            .map(opt -> opt.filter(c -> c.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmMetaAdsConnector>> listByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.listByTenant(ctx.getTenantId().getValue());
    }
}
