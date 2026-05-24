package com.ghatana.digitalmarketing.application.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCredentialRepository;
import com.ghatana.digitalmarketing.connector.googleads.GoogleAdsConnectorReadinessState;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.observability.TracingManager;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * DMOS-004: Google Ads connector command handler tests — OAuth, token refresh, retry,
 * DLQ, idempotency, external IDs, and compensation.
 *
 * @doc.type class
 * @doc.purpose Tests for GoogleAdsCampaignCreateCommandHandler covering idempotency,
 *             external ID persistence, retry, and compensation (DMOS-004)
 * @doc.layer test
 * @doc.pattern CommandHandlerTest
 */
@DisplayName("DMOS-004: GoogleAdsCampaignCreateCommandHandler")
class GoogleAdsCampaignCreateCommandHandlerTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String CONNECTOR_ID = "conn-google-1";
    private static final String CAMPAIGN_ID = "camp-1";
    private static final String ACCESS_TOKEN = "bearer-token-abc";
    private static final String EXTERNAL_CAMPAIGN_ID = "customers/123/campaigns/456";

    private EphemeralConnectorRepository connectorRepository;
    private EphemeralCredentialRepository credentialRepository;
    private EphemeralCampaignLinkRepository linkRepository;
    private EphemeralCampaignRepository campaignRepository;
    private RecordingApiClient apiClient;
    private ObjectMapper objectMapper;
    private DmosObservability observability;
    private GoogleAdsCampaignCreateCommandHandler handler;

    @BeforeEach
    void setUp() {
        connectorRepository = new EphemeralConnectorRepository();
        credentialRepository = new EphemeralCredentialRepository();
        linkRepository = new EphemeralCampaignLinkRepository();
        campaignRepository = new EphemeralCampaignRepository();
        apiClient = new RecordingApiClient();
        objectMapper = new ObjectMapper();
        observability = new DmosObservability(
            new Metrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
            TracingManager.createNoOp()
        );
        handler = new GoogleAdsCampaignCreateCommandHandler(
            connectorRepository,
            credentialRepository,
            linkRepository,
            campaignRepository,
            apiClient,
            objectMapper,
            observability
        );

        connectorRepository.save(activeGoogleAdsConnector());
        credentialRepository.save(validCredential());
        campaignRepository.save(launchedCampaign());
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("creates campaign and persists external ID link on success")
    void shouldCreateCampaignAndPersistExternalIdLink() {
        runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID)));

        assertThat(apiClient.createCallCount.get()).isEqualTo(1);
        assertThat(apiClient.lastAccessToken).isEqualTo(ACCESS_TOKEN);

        Optional<DmGoogleAdsCampaignLink> link =
            runPromise(() -> linkRepository.findByInternalCampaignId(CAMPAIGN_ID));

        assertThat(link).isPresent();
        assertThat(link.get().getExternalCampaignId()).isEqualTo(EXTERNAL_CAMPAIGN_ID);
        assertThat(link.get().getInternalCampaignId()).isEqualTo(CAMPAIGN_ID);
        assertThat(link.get().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(link.get().getConnectorId()).isEqualTo(CONNECTOR_ID);
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("returns early without calling Google Ads API when external ID link already exists (idempotent)")
    void shouldReturnEarlyWhenLinkAlreadyExistsIdempotent() {
        DmGoogleAdsCampaignLink existingLink = DmGoogleAdsCampaignLink.builder()
            .id("link-existing")
            .internalCampaignId(CAMPAIGN_ID)
            .externalCampaignId(EXTERNAL_CAMPAIGN_ID)
            .connectorId(CONNECTOR_ID)
            .tenantId(TENANT_ID)
            .createdAt(Instant.now())
            .build();
        runPromise(() -> linkRepository.save(existingLink));

        runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID)));

        assertThat(apiClient.createCallCount.get())
            .as("Google Ads API must NOT be called when link already exists")
            .isEqualTo(0);
    }

    @Test
    @DisplayName("idempotency holds across multiple duplicate command submissions")
    void idempotencyHoldsAcrossMultipleSubmissions() {
        runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID)));
        runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID)));
        runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID)));

        assertThat(apiClient.createCallCount.get())
            .as("Google Ads API must be called exactly once regardless of duplicate submissions")
            .isEqualTo(1);

        Optional<DmGoogleAdsCampaignLink> link =
            runPromise(() -> linkRepository.findByInternalCampaignId(CAMPAIGN_ID));
        assertThat(link).isPresent();
        assertThat(link.get().getExternalCampaignId()).isEqualTo(EXTERNAL_CAMPAIGN_ID);
    }

    // ── Retry ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("retries on transient 503 failure and succeeds when API recovers")
    void shouldRetryOnTransientFailureAndSucceed() {
        apiClient.setFailTransientCount(2);

        runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID)));

        assertThat(apiClient.createCallCount.get())
            .as("Must retry at least 3 times (2 failures + 1 success)")
            .isGreaterThanOrEqualTo(3);

        Optional<DmGoogleAdsCampaignLink> link =
            runPromise(() -> linkRepository.findByInternalCampaignId(CAMPAIGN_ID));
        assertThat(link).isPresent();
    }

    @Test
    @DisplayName("propagates exception after retry budget exhausted on persistent transient failure")
    void shouldFailAfterRetryBudgetExhausted() {
        apiClient.setFailTransientCount(Integer.MAX_VALUE);

        assertThatExceptionOfType(RuntimeException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID))));

        Optional<DmGoogleAdsCampaignLink> link =
            runPromise(() -> linkRepository.findByInternalCampaignId(CAMPAIGN_ID));
        assertThat(link)
            .as("External ID link must NOT be persisted when all retries fail")
            .isEmpty();
    }

    // ── OAuth / credential validation ─────────────────────────────────────────

    @Test
    @DisplayName("fails with NoSuchElementException when credential is not found")
    void shouldFailWhenCredentialNotFound() {
        credentialRepository.clear();

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID))));

        assertThat(apiClient.createCallCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("fails with IllegalStateException when credential is expired (token refresh not performed by handler)")
    void shouldFailWhenCredentialIsExpired() {
        credentialRepository.clear();
        credentialRepository.save(expiredCredential());

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID))));

        assertThat(apiClient.createCallCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("fails with NoSuchElementException when credential tenant mismatches command tenant")
    void shouldFailWhenCredentialTenantMismatch() {
        credentialRepository.clear();
        credentialRepository.save(credentialForWrongTenant());

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID))));

        assertThat(apiClient.createCallCount.get()).isEqualTo(0);
    }

    // ── Connector validation ─────────────────────────────────────────────────

    @Test
    @DisplayName("fails with NoSuchElementException when connector not found")
    void shouldFailWhenConnectorNotFound() {
        DmCommand commandWithUnknownConnector = buildCommandWithConnector(CAMPAIGN_ID, "conn-unknown");

        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(commandWithUnknownConnector)));
    }

    @Test
    @DisplayName("fails with IllegalStateException when connector is INACTIVE")
    void shouldFailWhenConnectorIsInactive() {
        connectorRepository.clear();
        connectorRepository.save(inactiveConnector());

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID))));
    }

    @Test
    @DisplayName("fails with IllegalArgumentException when connector is not GOOGLE_ADS type")
    void shouldFailWhenConnectorIsWrongType() {
        connectorRepository.clear();
        connectorRepository.save(metaAdsConnector());

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID))));
    }

    // ── Campaign validation ───────────────────────────────────────────────────

    @Test
    @DisplayName("fails with NoSuchElementException when campaign not found")
    void shouldFailWhenCampaignNotFound() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(buildCommand("camp-missing"))));
    }

    @Test
    @DisplayName("fails with IllegalStateException when campaign is not LAUNCHED")
    void shouldFailWhenCampaignNotLaunched() {
        campaignRepository.save(draftCampaign());

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(buildCommand("camp-draft"))));
    }

    @Test
    @DisplayName("fails with IllegalArgumentException when campaign type is not PAID_SEARCH")
    void shouldFailWhenCampaignTypeIsNotPaidSearch() {
        campaignRepository.save(displayCampaign());

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(buildCommand("camp-display"))));
    }

    // ── Compensation / no external ID leak on failure ─────────────────────────

    @Test
    @DisplayName("does not persist external ID link when Google Ads API returns failure (compensation guard)")
    void shouldNotPersistLinkWhenApiCallFails() {
        apiClient.setFailPermanent(true);

        assertThatExceptionOfType(RuntimeException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID))));

        Optional<DmGoogleAdsCampaignLink> link =
            runPromise(() -> linkRepository.findByInternalCampaignId(CAMPAIGN_ID));

        assertThat(link)
            .as("No external ID link must be persisted if API call failed (compensation guard)")
            .isEmpty();
    }

    @Test
    @DisplayName("external ID uniqueness enforced — second campaign with same internal ID does not overwrite first link")
    void shouldNotOverwriteExistingExternalIdLink() {
        runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID)));
        String firstExternalId = runPromise(() -> linkRepository.findByInternalCampaignId(CAMPAIGN_ID))
            .map(DmGoogleAdsCampaignLink::getExternalCampaignId)
            .orElseThrow();

        apiClient.setExternalIdSuffix("v2");
        runPromise(() -> handler.handle(buildCommand(CAMPAIGN_ID)));

        String secondExternalId = runPromise(() -> linkRepository.findByInternalCampaignId(CAMPAIGN_ID))
            .map(DmGoogleAdsCampaignLink::getExternalCampaignId)
            .orElseThrow();

        assertThat(secondExternalId)
            .as("Once a link exists, it must not be overwritten by a duplicate command")
            .isEqualTo(firstExternalId);

        assertThat(apiClient.createCallCount.get())
            .as("API must only be called once; second submission is idempotent")
            .isEqualTo(1);
    }

    // ── Wrong command type ────────────────────────────────────────────────────

    @Test
    @DisplayName("rejects command with wrong type immediately")
    void shouldRejectWrongCommandType() {
        DmCommand wrongType = buildCommandOfType(DmCommandType.GOOGLE_ADS_CAMPAIGN_ROLLBACK, CAMPAIGN_ID);

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> handler.handle(wrongType)));
    }

    // ── Builder helpers ───────────────────────────────────────────────────────

    private DmCommand buildCommand(String campaignId) {
        return buildCommandWithConnector(campaignId, CONNECTOR_ID);
    }

    private DmCommand buildCommandWithConnector(String campaignId, String connectorId) {
        GoogleAdsCampaignCreateCommandHandler.GoogleAdsCampaignCreatePayload payload =
            new GoogleAdsCampaignCreateCommandHandler.GoogleAdsCampaignCreatePayload(
                connectorId, campaignId, "50.00", "Mumbai", "plumbing services");
        try {
            return DmCommand.builder()
                .id("cmd-" + java.util.UUID.randomUUID())
                .commandType(DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE)
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .correlationId("corr-1")
                .issuedBy("user-1")
                .serializedPayload(objectMapper.writeValueAsString(payload))
                .status(DmCommandStatus.PENDING)
                .attemptCount(0)
                .createdAt(Instant.now())
                .scheduledAt(Instant.now())
                .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DmCommand buildCommandOfType(DmCommandType type, String campaignId) {
        GoogleAdsCampaignCreateCommandHandler.GoogleAdsCampaignCreatePayload payload =
            new GoogleAdsCampaignCreateCommandHandler.GoogleAdsCampaignCreatePayload(
                CONNECTOR_ID, campaignId, "50.00", "Mumbai", "plumbing services");
        try {
            return DmCommand.builder()
                .id("cmd-" + java.util.UUID.randomUUID())
                .commandType(type)
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .correlationId("corr-1")
                .issuedBy("user-1")
                .serializedPayload(objectMapper.writeValueAsString(payload))
                .status(DmCommandStatus.PENDING)
                .attemptCount(0)
                .createdAt(Instant.now())
                .scheduledAt(Instant.now())
                .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── Domain object factories ───────────────────────────────────────────────

    private DmConnectorConfig activeGoogleAdsConnector() {
        return DmConnectorConfig.builder()
            .id(CONNECTOR_ID).tenantId(TENANT_ID).workspaceId(WORKSPACE_ID)
            .name("Google Ads Production").connectorType(DmConnectorType.GOOGLE_ADS)
            .status(DmConnectorStatus.ACTIVE).settings(Map.of())
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private DmConnectorConfig inactiveConnector() {
        return DmConnectorConfig.builder()
            .id(CONNECTOR_ID).tenantId(TENANT_ID).workspaceId(WORKSPACE_ID)
            .name("Google Ads Inactive").connectorType(DmConnectorType.GOOGLE_ADS)
            .status(DmConnectorStatus.SUSPENDED).settings(Map.of())
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private DmConnectorConfig metaAdsConnector() {
        return DmConnectorConfig.builder()
            .id(CONNECTOR_ID).tenantId(TENANT_ID).workspaceId(WORKSPACE_ID)
            .name("Meta Ads").connectorType(DmConnectorType.META_ADS)
            .status(DmConnectorStatus.ACTIVE).settings(Map.of())
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private DmGoogleAdsCredential validCredential() {
        return DmGoogleAdsCredential.builder()
            .id("cred-1").tenantId(TENANT_ID).connectorId(CONNECTOR_ID)
            .accessToken(ACCESS_TOKEN).refreshToken("refresh-token-abc")
            .expiresAt(Instant.now().plusSeconds(3600)).scopes(List.of("ads"))
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private DmGoogleAdsCredential expiredCredential() {
        return DmGoogleAdsCredential.builder()
            .id("cred-expired").tenantId(TENANT_ID).connectorId(CONNECTOR_ID)
            .accessToken("expired-token").refreshToken("old-refresh")
            .expiresAt(Instant.now().minusSeconds(3600)).scopes(List.of("ads"))
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private DmGoogleAdsCredential credentialForWrongTenant() {
        return DmGoogleAdsCredential.builder()
            .id("cred-wrong-tenant").tenantId("tenant-other").connectorId(CONNECTOR_ID)
            .accessToken("token-other").refreshToken("refresh-other")
            .expiresAt(Instant.now().plusSeconds(3600)).scopes(List.of("ads"))
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private Campaign launchedCampaign() {
        return Campaign.builder()
            .id(CAMPAIGN_ID).workspaceId(DmWorkspaceId.of(WORKSPACE_ID))
            .name("Test Search Campaign").type(CampaignType.PAID_SEARCH)
            .status(CampaignStatus.LAUNCHED).createdAt(Instant.now())
            .updatedAt(Instant.now()).createdBy("user-1").build();
    }

    private Campaign draftCampaign() {
        return Campaign.builder()
            .id("camp-draft").workspaceId(DmWorkspaceId.of(WORKSPACE_ID))
            .name("Draft Campaign").type(CampaignType.PAID_SEARCH)
            .status(CampaignStatus.DRAFT).createdAt(Instant.now())
            .updatedAt(Instant.now()).createdBy("user-1").build();
    }

    private Campaign displayCampaign() {
        return Campaign.builder()
            .id("camp-display").workspaceId(DmWorkspaceId.of(WORKSPACE_ID))
            .name("Display Campaign").type(CampaignType.SOCIAL)
            .status(CampaignStatus.LAUNCHED).createdAt(Instant.now())
            .updatedAt(Instant.now()).createdBy("user-1").build();
    }

    // ── Test doubles ─────────────────────────────────────────────────────────

    static final class EphemeralConnectorRepository implements DmConnectorRepository {
        private final Map<String, DmConnectorConfig> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmConnectorConfig> save(DmConnectorConfig c) { store.put(c.getId(), c); return Promise.of(c); }

        @Override
        public Promise<Optional<DmConnectorConfig>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmConnectorConfig>> findByType(String tenantId, DmConnectorType type, int limit) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<DmConnectorConfig>> findByStatus(String tenantId, DmConnectorStatus status, int limit) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<DmConnectorConfig> update(DmConnectorConfig c) { store.put(c.getId(), c); return Promise.of(c); }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmConnectorStatus status) { return Promise.of(0L); }

        void clear() { store.clear(); }
    }

    static final class EphemeralCredentialRepository implements DmGoogleAdsCredentialRepository {
        private final Map<String, DmGoogleAdsCredential> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmGoogleAdsCredential> save(DmGoogleAdsCredential c) {
            store.put(c.getConnectorId(), c); return Promise.of(c);
        }

        @Override
        public Promise<Optional<DmGoogleAdsCredential>> findById(String id) {
            return Promise.of(store.values().stream().filter(c -> c.getId().equals(id)).findFirst());
        }

        @Override
        public Promise<Optional<DmGoogleAdsCredential>> findByConnectorId(String connectorId) {
            return Promise.of(Optional.ofNullable(store.get(connectorId)));
        }

        @Override
        public Promise<DmGoogleAdsCredential> update(DmGoogleAdsCredential c) {
            store.put(c.getConnectorId(), c); return Promise.of(c);
        }

        @Override
        public Promise<Void> delete(String id) { store.remove(id); return Promise.complete(); }

        void clear() { store.clear(); }
    }

    static final class EphemeralCampaignLinkRepository implements DmGoogleAdsCampaignLinkRepository {
        private final Map<String, DmGoogleAdsCampaignLink> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmGoogleAdsCampaignLink> save(DmGoogleAdsCampaignLink link) {
            store.put(link.getInternalCampaignId(), link); return Promise.of(link);
        }

        @Override
        public Promise<Optional<DmGoogleAdsCampaignLink>> findByInternalCampaignId(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }
    }

    static final class EphemeralCampaignRepository implements CampaignRepository {
        private final Map<String, Campaign> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Campaign> save(Campaign c) { store.put(c.getId(), c); return Promise.of(c); }

        @Override
        public Promise<Optional<Campaign>> findById(DmWorkspaceId workspaceId, String id) {
            Campaign c = store.get(id);
            if (c == null || !c.getWorkspaceId().equals(workspaceId)) return Promise.of(Optional.empty());
            return Promise.of(Optional.of(c));
        }

        @Override
        public Promise<List<Campaign>> listByWorkspace(DmWorkspaceId workspaceId, int limit, int offset) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<Long> countByWorkspace(DmWorkspaceId workspaceId) { return Promise.of(0L); }
    }

    static final class RecordingApiClient implements DmGoogleAdsCampaignApiClient {
        final AtomicInteger createCallCount = new AtomicInteger(0);
        volatile String lastAccessToken;
        private volatile int failTransientCount = 0;
        private volatile boolean failPermanent = false;
        private volatile String externalIdSuffix = "";

        void setFailTransientCount(int count) { this.failTransientCount = count; }
        void setFailPermanent(boolean fail) { this.failPermanent = fail; }
        void setExternalIdSuffix(String suffix) { this.externalIdSuffix = suffix; }

        @Override
        public Promise<String> createSearchCampaign(String accessToken, CreateGoogleSearchCampaignRequest request) {
            lastAccessToken = accessToken;
            int attempt = createCallCount.incrementAndGet();

            if (failPermanent) {
                return Promise.ofException(new RuntimeException("Permanent API failure: HTTP 403"));
            }
            if (failTransientCount > 0 && attempt <= failTransientCount) {
                return Promise.ofException(new RuntimeException("Transient 503: " + attempt));
            }
            return Promise.of(EXTERNAL_CAMPAIGN_ID + externalIdSuffix);
        }

        @Override
        public Promise<String> pauseCampaign(String accessToken, String externalCampaignId) {
            return Promise.of(externalCampaignId);
        }

        @Override
        public Promise<GoogleAdsConnectorReadinessState> checkReadiness(String accessToken) {
            return Promise.of(GoogleAdsConnectorReadinessState.READY);
        }
    }
}
