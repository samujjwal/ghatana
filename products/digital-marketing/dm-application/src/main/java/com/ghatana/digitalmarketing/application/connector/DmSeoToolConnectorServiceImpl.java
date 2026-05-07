package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmSeoToolConnector;
import com.ghatana.digitalmarketing.domain.connector.DmSeoToolConnectorStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmSeoToolConnectorService}.
 *
 * @doc.type class
 * @doc.purpose Manages SEO tool connector lifecycle with authorization and tenant isolation (P3-003)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmSeoToolConnectorServiceImpl implements DmSeoToolConnectorService {

    private final DmSeoToolConnectorRepository connectorRepository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmSeoToolConnectorServiceImpl(
            DmSeoToolConnectorRepository connectorRepository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmSeoToolConnector> register(DmOperationContext ctx, RegisterSeoToolConnectorRequest request) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return kernelAdapter.isAuthorized(ctx, "connectors/seo", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to register SEO tool connectors"));

                Instant now = Instant.now();
                DmSeoToolConnector connector = DmSeoToolConnector.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .displayName(request.displayName())
                    .seoToolType(request.seoToolType())
                    .apiUrl(request.apiUrl())
                    .accessToken(request.accessToken())
                    .configuration(request.configuration())
                    .status(DmSeoToolConnectorStatus.PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

                return connectorRepository.save(connector);
            });
    }

    @Override
    public Promise<DmSeoToolConnector> activate(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/seo", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to activate SEO tool connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.activate()));
    }

    @Override
    public Promise<DmSeoToolConnector> suspend(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/seo", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to suspend SEO tool connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.suspend()));
    }

    @Override
    public Promise<DmSeoToolConnector> reactivate(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/seo", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to reactivate SEO tool connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.reactivate()));
    }

    @Override
    public Promise<DmSeoToolConnector> markAuthFailed(DmOperationContext ctx, String id, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/seo", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to update SEO tool connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.markFailed(reason)));
    }

    @Override
    public Promise<DmSeoToolConnector> disable(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/seo", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to disable SEO tool connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.disable()));
    }

    @Override
    public Promise<Optional<DmSeoToolConnector>> findById(DmOperationContext ctx, String id) {
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
    public Promise<List<DmSeoToolConnector>> listActive(DmOperationContext ctx, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return connectorRepository.findByStatus(
            ctx.getTenantId().getValue(), DmSeoToolConnectorStatus.ACTIVE, limit);
    }

    private Promise<DmSeoToolConnector> loadOwned(DmOperationContext ctx, String id) {
        return connectorRepository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NoSuchElementException("SEO tool connector not found: " + id));
                }
                DmSeoToolConnector c = opt.get();
                if (!c.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(
                        new NoSuchElementException("SEO tool connector not found: " + id));
                }
                return Promise.of(c);
            });
    }
}
