package com.ghatana.digitalmarketing.application.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCredentialRepository;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;

import java.util.Map;
import java.util.Objects;

/**
 * Registry for DMOS command handlers.
 *
 * <p>This class provides a single point for registering all command handlers with the
 * {@link DmCommandDispatcher}. Handlers are registered at application startup time.</p>
 *
 * @doc.type class
 * @doc.purpose Central registry for DMOS command handlers (DMOS-P1-008)
 * @doc.layer product
 * @doc.pattern Registry, Factory
 */
public final class DmCommandHandlerRegistry {

    private final DmConnectorRepository connectorRepository;
    private final DmGoogleAdsCredentialRepository credentialRepository;
    private final DmGoogleAdsCampaignLinkRepository linkRepository;
    private final CampaignRepository campaignRepository;
    private final DmGoogleAdsCampaignApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final DmosObservability observability;

    public DmCommandHandlerRegistry(
            DmConnectorRepository connectorRepository,
            DmGoogleAdsCredentialRepository credentialRepository,
            DmGoogleAdsCampaignLinkRepository linkRepository,
            CampaignRepository campaignRepository,
            DmGoogleAdsCampaignApiClient apiClient,
            ObjectMapper objectMapper,
            DmosObservability observability) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "credentialRepository must not be null");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository must not be null");
        this.campaignRepository = Objects.requireNonNull(campaignRepository, "campaignRepository must not be null");
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.observability = Objects.requireNonNull(observability, "observability must not be null");
    }

    /**
     * Returns a map of all registered command handlers.
     *
     * <p>Handlers are registered by command type. Add new handlers here as they are
     * implemented (DMOS-P1-008, DMOS-P1-011).</p>
     *
     * @return map of command type → handler
     */
    public Map<DmCommandType, DmCommandHandler> allHandlers() {
        GoogleAdsCampaignCreateCommandHandler googleAdsCreateHandler = new GoogleAdsCampaignCreateCommandHandler(
            connectorRepository,
            credentialRepository,
            linkRepository,
            campaignRepository,
            apiClient,
            objectMapper,
            observability
        );

        GoogleAdsCampaignRollbackCommandHandler googleAdsRollbackHandler = new GoogleAdsCampaignRollbackCommandHandler(
            linkRepository,
            apiClient,
            objectMapper,
            observability
        );

        return Map.of(
            DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE, googleAdsCreateHandler,
            DmCommandType.GOOGLE_ADS_CAMPAIGN_ROLLBACK, googleAdsRollbackHandler
        );
    }
}
