package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmConnectorService}.
 *
 * @doc.type class
 * @doc.purpose Manages connector lifecycle with authorization and tenant isolation (DMOS-F2-006)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmConnectorServiceImpl implements DmConnectorService {

    private final DmConnectorRepository connectorRepository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmConnectorServiceImpl(
            DmConnectorRepository connectorRepository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmConnectorConfig> register(DmOperationContext ctx, RegisterConnectorRequest request) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return kernelAdapter.isAuthorized(ctx, "connectors/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to register connectors"));

                Instant now = Instant.now();
                DmConnectorConfig connector = DmConnectorConfig.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .name(request.name())
                    .connectorType(request.connectorType())
                    .settings(request.settings())
                    .externalAccountId(request.externalAccountId())
                    .status(DmConnectorStatus.PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

                return connectorRepository.save(connector);
            });
    }

    @Override
    public Promise<DmConnectorConfig> activate(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to activate connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.activate()));
    }

    @Override
    public Promise<DmConnectorConfig> suspend(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to suspend connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.suspend()));
    }

    @Override
    public Promise<DmConnectorConfig> reactivate(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to reactivate connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.reactivate()));
    }

    @Override
    public Promise<DmConnectorConfig> markAuthFailed(DmOperationContext ctx, String id, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to update connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.markAuthFailed(reason)));
    }

    @Override
    public Promise<DmConnectorConfig> disable(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to disable connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.disable()));
    }

    @Override
    public Promise<Optional<DmConnectorConfig>> findById(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return connectorRepository.findById(id)
            .then(opt -> {
                if (opt.isPresent() && !opt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.of(Optional.empty());
                }
                return Promise.of(opt);
            });
    }

    @Override
    public Promise<List<DmConnectorConfig>> findByType(DmOperationContext ctx, DmConnectorType type, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(type, "type must not be null");
        return connectorRepository.findByType(ctx.getTenantId().getValue(), type, limit);
    }

    @Override
    public Promise<List<DmConnectorConfig>> listActive(DmOperationContext ctx, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return connectorRepository.findByStatus(
            ctx.getTenantId().getValue(), DmConnectorStatus.ACTIVE, limit);
    }

    @Override
    public Promise<Long> countByStatus(DmOperationContext ctx, DmConnectorStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return connectorRepository.countByStatus(ctx.getTenantId().getValue(), status);
    }

    private Promise<DmConnectorConfig> loadOwned(DmOperationContext ctx, String id) {
        return connectorRepository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NoSuchElementException("Connector not found: " + id));
                }
                DmConnectorConfig c = opt.get();
                if (!c.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(
                        new NoSuchElementException("Connector not found: " + id));
                }
                return Promise.of(c);
            });
    }
}
