package com.ghatana.digitalmarketing.application.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient;
import com.ghatana.digitalmarketing.connector.googleads.GoogleAdsConnectorReadinessState;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.observability.TracingManager;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCredentialRepository;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("GoogleAdsCampaignRollbackCommandHandler")
class GoogleAdsCampaignRollbackCommandHandlerTest extends EventloopTestBase {

    private EphemeralCampaignLinkRepository linkRepository;
    private EphemeralCredentialRepository credentialRepository;
    private RecordingApiClient apiClient;
    private ObjectMapper objectMapper;
    private DmosObservability observability;
    private GoogleAdsCampaignRollbackCommandHandler handler;

    @BeforeEach
    void setUp() {
        linkRepository = new EphemeralCampaignLinkRepository();
        credentialRepository = new EphemeralCredentialRepository();
        apiClient = new RecordingApiClient();
        objectMapper = new ObjectMapper();
        observability = new DmosObservability(
            new Metrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
            TracingManager.createNoOp()
        );
        handler = new GoogleAdsCampaignRollbackCommandHandler(
            linkRepository,
            credentialRepository,
            apiClient,
            objectMapper,
            observability
        );
    }

    @Test
    @DisplayName("handle succeeds when link and credential are valid")
    void shouldSucceedWithValidLinkAndCredential() {
        String internalId = "camp-1";
        String externalId = "customers/123/campaigns/456";
        String connectorId = "conn-1";
        String tenantId = "tenant-1";
        String accessToken = "token-abc";

        DmGoogleAdsCampaignLink link = DmGoogleAdsCampaignLink.builder()
            .id("link-1")
            .internalCampaignId(internalId)
            .externalCampaignId(externalId)
            .connectorId(connectorId)
            .tenantId(tenantId)
            .createdAt(Instant.now())
            .build();
        linkRepository.save(link);

        DmGoogleAdsCredential credential = DmGoogleAdsCredential.builder()
            .id("cred-1")
            .tenantId(tenantId)
            .connectorId(connectorId)
            .accessToken(accessToken)
            .refreshToken("refresh")
            .expiresAt(Instant.now().plusSeconds(3600))
            .scopes(java.util.List.of("scope"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        credentialRepository.save(credential);

        DmCommand command = buildCommand(internalId, tenantId);
        runPromise(() -> handler.handle(command));

        assertThat(apiClient.pauseCalled).isTrue();
        assertThat(apiClient.lastAccessToken).isEqualTo(accessToken);
        assertThat(apiClient.lastExternalCampaignId).isEqualTo(externalId);
    }

    @Test
    @DisplayName("handle fails when campaign link not found")
    void shouldFailWhenCampaignLinkNotFound() {
        DmCommand command = buildCommand("camp-missing", "tenant-1");

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(command)));
    }

    @Test
    @DisplayName("handle fails when credential not found")
    void shouldFailWhenCredentialNotFound() {
        String internalId = "camp-1";
        String externalId = "customers/123/campaigns/456";
        String connectorId = "conn-1";
        String tenantId = "tenant-1";

        DmGoogleAdsCampaignLink link = DmGoogleAdsCampaignLink.builder()
            .id("link-1")
            .internalCampaignId(internalId)
            .externalCampaignId(externalId)
            .connectorId(connectorId)
            .tenantId(tenantId)
            .createdAt(Instant.now())
            .build();
        linkRepository.save(link);

        DmCommand command = buildCommand(internalId, tenantId);

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(command)));
    }

    @Test
    @DisplayName("handle fails when credential tenant mismatch")
    void shouldFailWhenCredentialTenantMismatch() {
        String internalId = "camp-1";
        String externalId = "customers/123/campaigns/456";
        String connectorId = "conn-1";
        String tenantId = "tenant-1";

        DmGoogleAdsCampaignLink link = DmGoogleAdsCampaignLink.builder()
            .id("link-3")
            .internalCampaignId(internalId)
            .externalCampaignId(externalId)
            .connectorId(connectorId)
            .tenantId(tenantId)
            .createdAt(Instant.now())
            .build();
        linkRepository.save(link);

        DmGoogleAdsCredential credential = DmGoogleAdsCredential.builder()
            .id("cred-3")
            .tenantId("tenant-2")
            .connectorId(connectorId)
            .accessToken("token-abc")
            .refreshToken("refresh")
            .expiresAt(Instant.now().plusSeconds(3600))
            .scopes(java.util.List.of("scope"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        credentialRepository.save(credential);

        DmCommand command = buildCommand(internalId, tenantId);

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(command)));
    }

    private DmCommand buildCommand(String internalCampaignId, String tenantId) {
        GoogleAdsCampaignRollbackCommandHandler.GoogleAdsCampaignRollbackPayload payload =
            new GoogleAdsCampaignRollbackCommandHandler.GoogleAdsCampaignRollbackPayload(
                internalCampaignId,
                "ROLLBACK_REASON"
            );

        try {
            String serialized = objectMapper.writeValueAsString(payload);
            return DmCommand.builder()
                .id("cmd-1")
                .commandType(DmCommandType.GOOGLE_ADS_CAMPAIGN_ROLLBACK)
                .tenantId(tenantId)
                .workspaceId("ws-1")
                .correlationId("corr-1")
                .issuedBy("user-1")
                .serializedPayload(serialized)
                .status(DmCommandStatus.PENDING)
                .attemptCount(0)
                .createdAt(Instant.now())
                .scheduledAt(Instant.now())
                .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static final class EphemeralCampaignLinkRepository implements DmGoogleAdsCampaignLinkRepository {
        private final Map<String, DmGoogleAdsCampaignLink> byCampaignId = new ConcurrentHashMap<>();

        @Override
        public Promise<DmGoogleAdsCampaignLink> save(DmGoogleAdsCampaignLink link) {
            byCampaignId.put(link.getInternalCampaignId(), link);
            return Promise.of(link);
        }

        @Override
        public Promise<Optional<DmGoogleAdsCampaignLink>> findByInternalCampaignId(String internalCampaignId) {
            return Promise.of(Optional.ofNullable(byCampaignId.get(internalCampaignId)));
        }
    }

    static final class EphemeralCredentialRepository implements DmGoogleAdsCredentialRepository {
        private final Map<String, DmGoogleAdsCredential> byId = new ConcurrentHashMap<>();

        @Override
        public Promise<DmGoogleAdsCredential> save(DmGoogleAdsCredential credential) {
            byId.put(credential.getId(), credential);
            return Promise.of(credential);
        }

        @Override
        public Promise<Optional<DmGoogleAdsCredential>> findById(String id) {
            return Promise.of(Optional.ofNullable(byId.get(id)));
        }

        @Override
        public Promise<Optional<DmGoogleAdsCredential>> findByConnectorId(String connectorId) {
            return Promise.of(byId.values().stream()
                .filter(c -> c.getConnectorId().equals(connectorId))
                .findFirst());
        }

        @Override
        public Promise<DmGoogleAdsCredential> update(DmGoogleAdsCredential credential) {
            byId.put(credential.getId(), credential);
            return Promise.of(credential);
        }

        @Override
        public Promise<Void> delete(String id) {
            byId.remove(id);
            return Promise.complete();
        }
    }

    static final class RecordingApiClient implements DmGoogleAdsCampaignApiClient {
        boolean pauseCalled = false;
        String lastAccessToken;
        String lastExternalCampaignId;

        @Override
        public Promise<String> createSearchCampaign(String accessToken,
            DmGoogleAdsCampaignApiClient.CreateGoogleSearchCampaignRequest request) {
            return Promise.of("google-camp-123");
        }

        @Override
        public Promise<String> pauseCampaign(String accessToken, String externalCampaignId) {
            pauseCalled = true;
            lastAccessToken = accessToken;
            lastExternalCampaignId = externalCampaignId;
            return Promise.of(externalCampaignId);
        }

        @Override
        public Promise<GoogleAdsConnectorReadinessState> checkReadiness(String accessToken) {
            return Promise.of(GoogleAdsConnectorReadinessState.READY);
        }
    }

}
