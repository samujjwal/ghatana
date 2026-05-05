package com.ghatana.digitalmarketing.application.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCredentialRepository;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmCommandHandlerRegistry")
class DmCommandHandlerRegistryTest {

    @Test
    @DisplayName("allHandlers registers create and rollback handlers")
    void allHandlersRegistersExpectedHandlers() {
        DmCommandHandlerRegistry registry = new DmCommandHandlerRegistry(
            noop(DmConnectorRepository.class),
            noop(DmGoogleAdsCredentialRepository.class),
            noop(DmGoogleAdsCampaignLinkRepository.class),
            noop(CampaignRepository.class),
            noop(DmGoogleAdsCampaignApiClient.class),
            new ObjectMapper(),
            new DmosObservability(new com.ghatana.platform.observability.Metrics(new SimpleMeterRegistry()),
                com.ghatana.platform.observability.TracingManager.createNoOp())
        );

        Map<DmCommandType, DmCommandHandler> handlers = registry.allHandlers();

        assertThat(handlers).containsKeys(
            DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE,
            DmCommandType.GOOGLE_ADS_CAMPAIGN_ROLLBACK
        );
        assertThat(handlers.get(DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE))
            .isInstanceOf(GoogleAdsCampaignCreateCommandHandler.class);
        assertThat(handlers.get(DmCommandType.GOOGLE_ADS_CAMPAIGN_ROLLBACK))
            .isInstanceOf(GoogleAdsCampaignRollbackCommandHandler.class);
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void constructorRejectsNulls() {
        DmConnectorRepository connectorRepo = noop(DmConnectorRepository.class);
        DmGoogleAdsCredentialRepository credentialRepo = noop(DmGoogleAdsCredentialRepository.class);
        DmGoogleAdsCampaignLinkRepository linkRepo = noop(DmGoogleAdsCampaignLinkRepository.class);
        CampaignRepository campaignRepo = noop(CampaignRepository.class);
        DmGoogleAdsCampaignApiClient apiClient = noop(DmGoogleAdsCampaignApiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DmosObservability observability = new DmosObservability(
            new com.ghatana.platform.observability.Metrics(new SimpleMeterRegistry()),
            com.ghatana.platform.observability.TracingManager.createNoOp()
        );

        assertThatNullPointerException().isThrownBy(() ->
            new DmCommandHandlerRegistry(null, credentialRepo, linkRepo, campaignRepo, apiClient, objectMapper, observability));
        assertThatNullPointerException().isThrownBy(() ->
            new DmCommandHandlerRegistry(connectorRepo, null, linkRepo, campaignRepo, apiClient, objectMapper, observability));
        assertThatNullPointerException().isThrownBy(() ->
            new DmCommandHandlerRegistry(connectorRepo, credentialRepo, null, campaignRepo, apiClient, objectMapper, observability));
        assertThatNullPointerException().isThrownBy(() ->
            new DmCommandHandlerRegistry(connectorRepo, credentialRepo, linkRepo, null, apiClient, objectMapper, observability));
        assertThatNullPointerException().isThrownBy(() ->
            new DmCommandHandlerRegistry(connectorRepo, credentialRepo, linkRepo, campaignRepo, null, objectMapper, observability));
        assertThatNullPointerException().isThrownBy(() ->
            new DmCommandHandlerRegistry(connectorRepo, credentialRepo, linkRepo, campaignRepo, apiClient, null, observability));
        assertThatNullPointerException().isThrownBy(() ->
            new DmCommandHandlerRegistry(connectorRepo, credentialRepo, linkRepo, campaignRepo, apiClient, objectMapper, null));
    }

    @SuppressWarnings("unchecked")
    private static <T> T noop(Class<T> type) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, args) -> {
                throw new UnsupportedOperationException("Not used in this test: " + method.getName());
            }
        );
    }
}
