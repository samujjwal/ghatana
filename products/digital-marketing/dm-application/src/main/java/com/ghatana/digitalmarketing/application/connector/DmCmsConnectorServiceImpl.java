package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmCmsConnector;
import com.ghatana.digitalmarketing.domain.connector.DmCmsConnectorStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmCmsConnectorService}.
 *
 * @doc.type class
 * @doc.purpose Manages CMS connector lifecycle with authorization and tenant isolation (P3-003)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmCmsConnectorServiceImpl implements DmCmsConnectorService {

    private final DmCmsConnectorRepository connectorRepository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmCmsConnectorServiceImpl(
            DmCmsConnectorRepository connectorRepository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmCmsConnector> register(DmOperationContext ctx, RegisterCmsConnectorRequest request) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return kernelAdapter.isAuthorized(ctx, "connectors/cms", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to register CMS connectors"));

                Instant now = Instant.now();
                DmCmsConnector connector = DmCmsConnector.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .displayName(request.displayName())
                    .cmsType(request.cmsType())
                    .apiUrl(request.apiUrl())
                    .accessToken(request.accessToken())
                    .configuration(request.configuration())
                    .status(DmCmsConnectorStatus.PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

                return connectorRepository.save(connector);
            });
    }

    @Override
    public Promise<DmCmsConnector> activate(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/cms", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to activate CMS connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.activate()));
    }

    @Override
    public Promise<DmCmsConnector> suspend(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/cms", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to suspend CMS connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.suspend()));
    }

    @Override
    public Promise<DmCmsConnector> reactivate(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/cms", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to reactivate CMS connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.reactivate()));
    }

    @Override
    public Promise<DmCmsConnector> markAuthFailed(DmOperationContext ctx, String id, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/cms", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to update CMS connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.markFailed(reason)));
    }

    @Override
    public Promise<DmCmsConnector> disable(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/cms", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to disable CMS connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.disable()));
    }

    @Override
    public Promise<Optional<DmCmsConnector>> findById(DmOperationContext ctx, String id) {
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
    public Promise<List<DmCmsConnector>> listActive(DmOperationContext ctx, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return connectorRepository.findByStatus(
            ctx.getTenantId().getValue(), DmCmsConnectorStatus.ACTIVE, limit);
    }

    private Promise<DmCmsConnector> loadOwned(DmOperationContext ctx, String id) {
        return connectorRepository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NoSuchElementException("CMS connector not found: " + id));
                }
                DmCmsConnector c = opt.get();
                if (!c.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(
                        new NoSuchElementException("CMS connector not found: " + id));
                }
                return Promise.of(c);
            });
    }
}
