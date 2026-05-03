package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import com.ghatana.digitalmarketing.domain.performance.DmCampaignPerformanceSnapshot;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmGoogleAdsPerformanceSyncService}.
 *
 * @doc.type class
 * @doc.purpose Syncs Google Ads performance metrics with tenant-safe connector and credential guardrails (DMOS-F2-009)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmGoogleAdsPerformanceSyncServiceImpl implements DmGoogleAdsPerformanceSyncService {

    private final DmConnectorRepository connectorRepository;
    private final DmGoogleAdsCredentialRepository credentialRepository;
    private final DmGoogleAdsCampaignLinkRepository linkRepository;
    private final DmGoogleAdsPerformanceSnapshotRepository snapshotRepository;
    private final DmGoogleAdsPerformanceApiClient performanceApiClient;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmGoogleAdsPerformanceSyncServiceImpl(
            DmConnectorRepository connectorRepository,
            DmGoogleAdsCredentialRepository credentialRepository,
            DmGoogleAdsCampaignLinkRepository linkRepository,
            DmGoogleAdsPerformanceSnapshotRepository snapshotRepository,
            DmGoogleAdsPerformanceApiClient performanceApiClient,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "credentialRepository must not be null");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository must not be null");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository must not be null");
        this.performanceApiClient = Objects.requireNonNull(performanceApiClient, "performanceApiClient must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmCampaignPerformanceSnapshot> syncCampaignPerformance(
            DmOperationContext ctx,
            SyncCampaignPerformanceRequest request) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return kernelAdapter.isAuthorized(ctx, "connectors/*", "execute")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to sync connector performance"));
                }
                return requireValidConnector(ctx, request.connectorId());
            })
            .then(connector -> requireCampaignLink(ctx, request.internalCampaignId())
                .then(link -> {
                    if (!link.getConnectorId().equals(connector.getId())) {
                        return Promise.ofException(new IllegalArgumentException("Campaign link does not belong to connector"));
                    }
                    return requireValidCredential(ctx, connector.getId())
                        .then(credential -> fetchAndPersistSnapshot(ctx, request, link, credential));
                }));
    }

    @Override
    public Promise<Optional<DmCampaignPerformanceSnapshot>> findLatestSnapshot(
            DmOperationContext ctx,
            String internalCampaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (internalCampaignId == null || internalCampaignId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("internalCampaignId must not be blank"));
        }

        return linkRepository.findByInternalCampaignId(internalCampaignId)
            .then(linkOpt -> {
                if (linkOpt.isEmpty() || !linkOpt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.of(Optional.empty());
                }
                return snapshotRepository.findLatestByExternalCampaignId(linkOpt.get().getExternalCampaignId())
                    .then(snapshotOpt -> {
                        if (snapshotOpt.isPresent()
                            && !snapshotOpt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                            return Promise.of(Optional.empty());
                        }
                        return Promise.of(snapshotOpt);
                    });
            });
    }

    private Promise<DmCampaignPerformanceSnapshot> fetchAndPersistSnapshot(
            DmOperationContext ctx,
            SyncCampaignPerformanceRequest request,
            DmGoogleAdsCampaignLink link,
            DmGoogleAdsCredential credential) {
        DmGoogleAdsPerformanceApiClient.FetchCampaignPerformanceRequest providerRequest =
            new DmGoogleAdsPerformanceApiClient.FetchCampaignPerformanceRequest(
                link.getExternalCampaignId(),
                request.periodStart(),
                request.periodEnd()
            );

        return performanceApiClient.fetchCampaignPerformance(credential.getAccessToken(), providerRequest)
            .then(response -> snapshotRepository.save(
                DmCampaignPerformanceSnapshot.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .externalCampaignId(link.getExternalCampaignId())
                    .impressions(response.impressions())
                    .clicks(response.clicks())
                    .conversions(response.conversions())
                    .costMicros(response.costMicros())
                    .ctr(response.ctr())
                    .cpc(response.cpc())
                    .conversionRate(response.conversionRate())
                    .periodStart(request.periodStart())
                    .periodEnd(request.periodEnd())
                    .capturedAt(Instant.now())
                    .build()
            ));
    }

    private Promise<DmConnectorConfig> requireValidConnector(DmOperationContext ctx, String connectorId) {
        return connectorRepository.findById(connectorId)
            .then(opt -> {
                if (opt.isEmpty() || !opt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new NoSuchElementException("Connector not found: " + connectorId));
                }
                DmConnectorConfig connector = opt.get();
                if (connector.getConnectorType() != DmConnectorType.GOOGLE_ADS) {
                    return Promise.ofException(new IllegalArgumentException("Connector must be GOOGLE_ADS"));
                }
                if (connector.getStatus() != DmConnectorStatus.ACTIVE) {
                    return Promise.ofException(new IllegalStateException("Connector must be ACTIVE"));
                }
                return Promise.of(connector);
            });
    }

    private Promise<DmGoogleAdsCampaignLink> requireCampaignLink(DmOperationContext ctx, String internalCampaignId) {
        return linkRepository.findByInternalCampaignId(internalCampaignId)
            .then(opt -> {
                if (opt.isEmpty() || !opt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new NoSuchElementException("Campaign link not found: " + internalCampaignId));
                }
                return Promise.of(opt.get());
            });
    }

    private Promise<DmGoogleAdsCredential> requireValidCredential(DmOperationContext ctx, String connectorId) {
        return credentialRepository.findByConnectorId(connectorId)
            .then(opt -> {
                if (opt.isEmpty() || !opt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new NoSuchElementException("Credential not found for connector: " + connectorId));
                }
                DmGoogleAdsCredential credential = opt.get();
                if (credential.isExpired()) {
                    return Promise.ofException(new IllegalStateException("Credential is expired"));
                }
                return Promise.of(credential);
            });
    }
}
