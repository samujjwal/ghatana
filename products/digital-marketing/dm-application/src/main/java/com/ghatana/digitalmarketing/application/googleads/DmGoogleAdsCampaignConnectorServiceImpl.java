package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.application.DmosFeatureFlags;
import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmGoogleAdsCampaignConnectorService}.
 *
 * @doc.type class
 * @doc.purpose Executes Google Search campaign creation flow with connector, credential, and campaign guardrails (DMOS-F2-008)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmGoogleAdsCampaignConnectorServiceImpl implements DmGoogleAdsCampaignConnectorService {

    private final DmConnectorRepository connectorRepository;
    private final DmGoogleAdsCredentialRepository credentialRepository;
    private final DmGoogleAdsCampaignLinkRepository linkRepository;
    private final CampaignRepository campaignRepository;
    private final DmGoogleAdsCampaignApiClient apiClient;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmGoogleAdsCampaignConnectorServiceImpl(
            DmConnectorRepository connectorRepository,
            DmGoogleAdsCredentialRepository credentialRepository,
            DmGoogleAdsCampaignLinkRepository linkRepository,
            CampaignRepository campaignRepository,
            DmGoogleAdsCampaignApiClient apiClient,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "credentialRepository must not be null");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository must not be null");
        this.campaignRepository = Objects.requireNonNull(campaignRepository, "campaignRepository must not be null");
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmGoogleAdsCampaignLink> createSearchCampaign(
            DmOperationContext ctx,
            CreateSearchCampaignRequest request) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return kernelAdapter.isFeatureEnabled(ctx, DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED)
            .then(connectorEnabled -> {
                if (!connectorEnabled) {
                    return Promise.ofException(
                        new UnsupportedOperationException("Google Ads connector is currently disabled (dmos.google_ads_connector.enabled=false)"));
                }
                return kernelAdapter.isAuthorized(ctx, "connectors/*", "execute");
            })
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Not authorized to execute connector actions"));
                }
                return requireValidConnector(ctx, request.connectorId());
            })
            .then(connector -> requireLaunchedPaidSearchCampaign(ctx, request.internalCampaignId())
                .then(campaign -> requireValidCredential(ctx, connector.getId())
                    .then(credential -> {
                        DmGoogleAdsCampaignApiClient.CreateGoogleSearchCampaignRequest providerRequest =
                            new DmGoogleAdsCampaignApiClient.CreateGoogleSearchCampaignRequest(
                                campaign.getName(),
                                request.dailyBudget(),
                                request.serviceArea(),
                                request.keywordTheme()
                            );

                        return apiClient.createSearchCampaign(credential.getAccessToken(), providerRequest)
                            .then(externalCampaignId -> linkRepository.save(
                                DmGoogleAdsCampaignLink.builder()
                                    .id(UUID.randomUUID().toString())
                                    .tenantId(ctx.getTenantId().getValue())
                                    .connectorId(connector.getId())
                                    .internalCampaignId(campaign.getId())
                                    .externalCampaignId(externalCampaignId)
                                    .createdAt(Instant.now())
                                    .build()
                            ));
                    })));
    }

    @Override
    public Promise<Optional<DmGoogleAdsCampaignLink>> findByInternalCampaignId(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (campaignId == null || campaignId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("campaignId must not be blank"));
        }

        return linkRepository.findByInternalCampaignId(campaignId)
            .then(opt -> {
                if (opt.isPresent() && !opt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.of(Optional.empty());
                }
                return Promise.of(opt);
            });
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

    private Promise<Campaign> requireLaunchedPaidSearchCampaign(DmOperationContext ctx, String campaignId) {
        return campaignRepository.findById(ctx.getWorkspaceId(), campaignId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Campaign not found: " + campaignId));
                }
                Campaign campaign = opt.get();
                if (campaign.getType() != CampaignType.PAID_SEARCH) {
                    return Promise.ofException(new IllegalArgumentException("Campaign must be PAID_SEARCH"));
                }
                if (campaign.getStatus() != CampaignStatus.LAUNCHED) {
                    return Promise.ofException(new IllegalStateException("Campaign must be LAUNCHED"));
                }
                return Promise.of(campaign);
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
