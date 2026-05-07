package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmYouTubeCtvConnector;
import com.ghatana.digitalmarketing.domain.connector.DmYouTubeCtvConnectorStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmYouTubeCtvConnectorService}.
 *
 * @doc.type class
 * @doc.purpose Manages YouTube/CTV connector lifecycle with authorization and tenant isolation (P3-003)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmYouTubeCtvConnectorServiceImpl implements DmYouTubeCtvConnectorService {

    private final DmYouTubeCtvConnectorRepository connectorRepository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmYouTubeCtvConnectorServiceImpl(
            DmYouTubeCtvConnectorRepository connectorRepository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmYouTubeCtvConnector> register(DmOperationContext ctx, RegisterYouTubeCtvConnectorRequest request) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return kernelAdapter.isAuthorized(ctx, "connectors/youtube-ctv", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to register YouTube/CTV connectors"));

                Instant now = Instant.now();
                DmYouTubeCtvConnector connector = DmYouTubeCtvConnector.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .displayName(request.displayName())
                    .channelId(request.channelId())
                    .accessToken(request.accessToken())
                    .status(DmYouTubeCtvConnectorStatus.PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

                return connectorRepository.save(connector);
            });
    }

    @Override
    public Promise<DmYouTubeCtvConnector> activate(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/youtube-ctv", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to activate YouTube/CTV connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.activate()));
    }

    @Override
    public Promise<DmYouTubeCtvConnector> suspend(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/youtube-ctv", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to suspend YouTube/CTV connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.suspend()));
    }

    @Override
    public Promise<DmYouTubeCtvConnector> reactivate(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/youtube-ctv", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to reactivate YouTube/CTV connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.reactivate()));
    }

    @Override
    public Promise<DmYouTubeCtvConnector> markAuthFailed(DmOperationContext ctx, String id, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/youtube-ctv", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to update YouTube/CTV connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.markFailed(reason)));
    }

    @Override
    public Promise<DmYouTubeCtvConnector> disable(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/youtube-ctv", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to disable YouTube/CTV connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.disable()));
    }

    @Override
    public Promise<Optional<DmYouTubeCtvConnector>> findById(DmOperationContext ctx, String id) {
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
    public Promise<List<DmYouTubeCtvConnector>> listActive(DmOperationContext ctx, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return connectorRepository.findByStatus(
            ctx.getTenantId().getValue(), DmYouTubeCtvConnectorStatus.ACTIVE, limit);
    }

    private Promise<DmYouTubeCtvConnector> loadOwned(DmOperationContext ctx, String id) {
        return connectorRepository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NoSuchElementException("YouTube/CTV connector not found: " + id));
                }
                DmYouTubeCtvConnector c = opt.get();
                if (!c.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(
                        new NoSuchElementException("YouTube/CTV connector not found: " + id));
                }
                return Promise.of(c);
            });
    }
}
