package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmTikTokAdsConnector;
import com.ghatana.digitalmarketing.domain.connector.DmTikTokAdsConnectorStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmTikTokAdsConnectorService}.
 *
 * @doc.type class
 * @doc.purpose Manages TikTok Ads connector lifecycle with authorization and tenant isolation (P3-003)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmTikTokAdsConnectorServiceImpl implements DmTikTokAdsConnectorService {

    private final DmTikTokAdsConnectorRepository connectorRepository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmTikTokAdsConnectorServiceImpl(
            DmTikTokAdsConnectorRepository connectorRepository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmTikTokAdsConnector> register(DmOperationContext ctx, RegisterTikTokAdsConnectorRequest request) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return kernelAdapter.isAuthorized(ctx, "connectors/tiktok-ads", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to register TikTok Ads connectors"));

                Instant now = Instant.now();
                DmTikTokAdsConnector connector = DmTikTokAdsConnector.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .displayName(request.displayName())
                    .advertiserId(request.advertiserId())
                    .accessToken(request.accessToken())
                    .status(DmTikTokAdsConnectorStatus.PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

                return connectorRepository.save(connector);
            });
    }

    @Override
    public Promise<DmTikTokAdsConnector> activate(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/tiktok-ads", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to activate TikTok Ads connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.activate()));
    }

    @Override
    public Promise<DmTikTokAdsConnector> suspend(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/tiktok-ads", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to suspend TikTok Ads connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.suspend()));
    }

    @Override
    public Promise<DmTikTokAdsConnector> reactivate(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/tiktok-ads", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to reactivate TikTok Ads connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.reactivate()));
    }

    @Override
    public Promise<DmTikTokAdsConnector> markAuthFailed(DmOperationContext ctx, String id, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/tiktok-ads", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to update TikTok Ads connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.markAuthFailed(reason)));
    }

    @Override
    public Promise<DmTikTokAdsConnector> disable(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "connectors/tiktok-ads", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to disable TikTok Ads connectors"));
                return loadOwned(ctx, id);
            })
            .then(c -> connectorRepository.update(c.disable()));
    }

    @Override
    public Promise<Optional<DmTikTokAdsConnector>> findById(DmOperationContext ctx, String id) {
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
    public Promise<List<DmTikTokAdsConnector>> listActive(DmOperationContext ctx, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return connectorRepository.findByStatus(
            ctx.getTenantId().getValue(), DmTikTokAdsConnectorStatus.ACTIVE, limit);
    }

    private Promise<DmTikTokAdsConnector> loadOwned(DmOperationContext ctx, String id) {
        return connectorRepository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NoSuchElementException("TikTok Ads connector not found: " + id));
                }
                DmTikTokAdsConnector c = opt.get();
                if (!c.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(
                        new NoSuchElementException("TikTok Ads connector not found: " + id));
                }
                return Promise.of(c);
            });
    }
}
