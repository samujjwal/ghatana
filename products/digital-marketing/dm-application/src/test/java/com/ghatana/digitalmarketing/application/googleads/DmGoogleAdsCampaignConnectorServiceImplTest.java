package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmGoogleAdsCampaignConnectorServiceImpl Tests")
class DmGoogleAdsCampaignConnectorServiceImplTest extends EventloopTestBase {

    private InMemoryConnectorRepository connectorRepository;
    private InMemoryCredentialRepository credentialRepository;
    private InMemoryCampaignLinkRepository linkRepository;
    private InMemoryCampaignRepository campaignRepository;
    private FakeCampaignApiClient apiClient;
    private DmGoogleAdsCampaignConnectorServiceImpl service;
    private DmOperationContext ctx;
    private DmConnectorConfig connector;
    private Campaign launchedCampaign;

    @BeforeEach
    void setUp() {
        connectorRepository = new InMemoryConnectorRepository();
        credentialRepository = new InMemoryCredentialRepository();
        linkRepository = new InMemoryCampaignLinkRepository();
        campaignRepository = new InMemoryCampaignRepository();
        apiClient = new FakeCampaignApiClient();
        service = new DmGoogleAdsCampaignConnectorServiceImpl(
            connectorRepository,
            credentialRepository,
            linkRepository,
            campaignRepository,
            apiClient,
            new AllowingKernelAdapter(true));

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();

        connector = DmConnectorConfig.builder()
            .id("conn-google")
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .name("Google Ads")
            .connectorType(DmConnectorType.GOOGLE_ADS)
            .status(DmConnectorStatus.ACTIVE)
            .settings(Map.of())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        runPromise(() -> connectorRepository.save(connector));

        DmGoogleAdsCredential credential = DmGoogleAdsCredential.builder()
            .id("cred-1")
            .tenantId("tenant-1")
            .connectorId(connector.getId())
            .accessToken("access-token")
            .refreshToken("refresh-token")
            .expiresAt(Instant.now().plusSeconds(3600))
            .scopes(java.util.List.of("scope"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        runPromise(() -> credentialRepository.save(credential));

        launchedCampaign = Campaign.builder()
            .id("camp-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .name("Search Leads")
            .type(CampaignType.PAID_SEARCH)
            .status(CampaignStatus.LAUNCHED)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("user-1")
            .build();
        runPromise(() -> campaignRepository.save(launchedCampaign));
    }

    @Test
    @DisplayName("createSearchCampaign creates external campaign and stores link")
    void createSearchCampaignSuccess() {
        DmGoogleAdsCampaignLink link = runPromise(() -> service.createSearchCampaign(
            ctx,
            new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                connector.getId(),
                launchedCampaign.getId(),
                new BigDecimal("75.50"),
                "Mumbai",
                "dental implants"
            )));

        assertThat(link.getExternalCampaignId()).isEqualTo("google-camp-123");
        assertThat(link.getInternalCampaignId()).isEqualTo(launchedCampaign.getId());
    }

    @Test
    @DisplayName("createSearchCampaign rejects unauthorized execution")
    void createSearchCampaignUnauthorized() {
        service = new DmGoogleAdsCampaignConnectorServiceImpl(
            connectorRepository,
            credentialRepository,
            linkRepository,
            campaignRepository,
            apiClient,
            new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.createSearchCampaign(
                ctx,
                new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                    connector.getId(),
                    launchedCampaign.getId(),
                    new BigDecimal("50"),
                    "Mumbai",
                    "keyword"
                ))));
    }

    @Test
    @DisplayName("createSearchCampaign requires active connector")
    void requiresActiveConnector() {
        DmConnectorConfig suspended = connector.toBuilder().status(DmConnectorStatus.SUSPENDED).build();
        runPromise(() -> connectorRepository.update(suspended));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.createSearchCampaign(
                ctx,
                new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                    connector.getId(),
                    launchedCampaign.getId(),
                    new BigDecimal("50"),
                    "Mumbai",
                    "keyword"
                ))));
    }

    @Test
    @DisplayName("createSearchCampaign requires launched paid search campaign")
    void requiresLaunchedPaidSearchCampaign() {
        Campaign draft = launchedCampaign.pause();
        runPromise(() -> campaignRepository.save(draft));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.createSearchCampaign(
                ctx,
                new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                    connector.getId(),
                    draft.getId(),
                    new BigDecimal("50"),
                    "Mumbai",
                    "keyword"
                ))));
    }

    @Test
    @DisplayName("createSearchCampaign requires non-expired credential")
    void requiresUnexpiredCredential() {
        DmGoogleAdsCredential expired = DmGoogleAdsCredential.builder()
            .id("cred-1")
            .tenantId("tenant-1")
            .connectorId(connector.getId())
            .accessToken("access-token")
            .refreshToken("refresh-token")
            .expiresAt(Instant.now().minusSeconds(60))
            .scopes(java.util.List.of("scope"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        runPromise(() -> credentialRepository.update(expired));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> service.createSearchCampaign(
                ctx,
                new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                    connector.getId(),
                    launchedCampaign.getId(),
                    new BigDecimal("50"),
                    "Mumbai",
                    "keyword"
                ))));
    }

    @Test
    @DisplayName("findByInternalCampaignId enforces tenant isolation")
    void findByInternalCampaignIdTenantIsolation() {
        DmGoogleAdsCampaignLink created = runPromise(() -> service.createSearchCampaign(
            ctx,
            new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                connector.getId(),
                launchedCampaign.getId(),
                new BigDecimal("25"),
                "Pune",
                "dentist"
            )));

        DmOperationContext otherTenant = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        Optional<DmGoogleAdsCampaignLink> visible = runPromise(() ->
            service.findByInternalCampaignId(otherTenant, created.getInternalCampaignId()));
        assertThat(visible).isEmpty();
    }

    @Test
    @DisplayName("findByInternalCampaignId returns link for same tenant")
    void findByInternalCampaignIdSuccess() {
        DmGoogleAdsCampaignLink created = runPromise(() -> service.createSearchCampaign(
            ctx,
            new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                connector.getId(),
                launchedCampaign.getId(),
                new BigDecimal("10"),
                "Delhi",
                "dentist"
            )));

        Optional<DmGoogleAdsCampaignLink> found = runPromise(() ->
            service.findByInternalCampaignId(ctx, created.getInternalCampaignId()));
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("findByInternalCampaignId rejects blank campaign id")
    void findByInternalCampaignIdBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.findByInternalCampaignId(ctx, "")));
    }

    @Test
    @DisplayName("createSearchCampaign rejects connector not found")
    void connectorNotFound() {
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.createSearchCampaign(
                ctx,
                new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                    "nonexistent-connector",
                    launchedCampaign.getId(),
                    new BigDecimal("50"),
                    "Mumbai",
                    "keyword"
                ))));
    }

    @Test
    @DisplayName("createSearchCampaign throws DmosConnectorDisabledException when connector is disabled")
    void createSearchCampaignConnectorDisabled() {
        service = new DmGoogleAdsCampaignConnectorServiceImpl(
            connectorRepository,
            credentialRepository,
            linkRepository,
            campaignRepository,
            apiClient,
            new AllowingKernelAdapter(true, false));

        assertThatExceptionOfType(DmosConnectorDisabledException.class)
            .isThrownBy(() -> runPromise(() -> service.createSearchCampaign(
                ctx,
                new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                    connector.getId(),
                    launchedCampaign.getId(),
                    new BigDecimal("50"),
                    "Mumbai",
                    "keyword"
                ))))
            .withMessageContaining("dmos.google_ads_connector.enabled");
    }

    @Test
    @DisplayName("createSearchCampaign rejects non-PAID_SEARCH campaign type")
    void requiresPaidSearchType() {
        Campaign contentCampaign = Campaign.builder()
            .id("camp-content-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .name("Content Campaign")
            .type(CampaignType.SOCIAL)
            .status(CampaignStatus.LAUNCHED)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("user-1")
            .build();
        runPromise(() -> campaignRepository.save(contentCampaign));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.createSearchCampaign(
                ctx,
                new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                    connector.getId(),
                    contentCampaign.getId(),
                    new BigDecimal("50"),
                    "Mumbai",
                    "keyword"
                ))));
    }

    static final class InMemoryConnectorRepository implements DmConnectorRepository {
        private final Map<String, DmConnectorConfig> byId = new ConcurrentHashMap<>();

        @Override
        public Promise<DmConnectorConfig> save(DmConnectorConfig connector) {
            byId.put(connector.getId(), connector);
            return Promise.of(connector);
        }

        @Override
        public Promise<Optional<DmConnectorConfig>> findById(String id) {
            return Promise.of(Optional.ofNullable(byId.get(id)));
        }

        @Override
        public Promise<java.util.List<DmConnectorConfig>> findByType(String tenantId, DmConnectorType type, int limit) {
            return Promise.of(java.util.List.of());
        }

        @Override
        public Promise<java.util.List<DmConnectorConfig>> findByStatus(String tenantId, DmConnectorStatus status, int limit) {
            return Promise.of(java.util.List.of());
        }

        @Override
        public Promise<DmConnectorConfig> update(DmConnectorConfig connector) {
            byId.put(connector.getId(), connector);
            return Promise.of(connector);
        }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmConnectorStatus status) {
            return Promise.of(0L);
        }
    }

    static final class InMemoryCredentialRepository implements DmGoogleAdsCredentialRepository {
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
            return Promise.of(byId.values().stream().filter(c -> c.getConnectorId().equals(connectorId)).findFirst());
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

    static final class InMemoryCampaignLinkRepository implements DmGoogleAdsCampaignLinkRepository {
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

    static final class InMemoryCampaignRepository implements CampaignRepository {
        private final Map<String, Campaign> byId = new ConcurrentHashMap<>();

        @Override
        public Promise<Campaign> save(Campaign campaign) {
            byId.put(campaign.getId(), campaign);
            return Promise.of(campaign);
        }

        @Override
        public Promise<Optional<Campaign>> findById(DmWorkspaceId workspaceId, String campaignId) {
            Campaign c = byId.get(campaignId);
            if (c == null || !c.getWorkspaceId().equals(workspaceId)) {
                return Promise.of(Optional.empty());
            }
            return Promise.of(Optional.of(c));
        }
    }

    static final class FakeCampaignApiClient implements DmGoogleAdsCampaignApiClient {
        @Override
        public Promise<String> createSearchCampaign(String accessToken, CreateGoogleSearchCampaignRequest request) {
            return Promise.of("google-camp-123");
        }
    }

    static final class AllowingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean allowed;
        private final boolean connectorEnabled;

        AllowingKernelAdapter(boolean allowed) {
            this(allowed, true);
        }

        AllowingKernelAdapter(boolean allowed, boolean connectorEnabled) {
            this.allowed = allowed;
            this.connectorEnabled = connectorEnabled;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(allowed);
        }

        @Override
        public Promise<Boolean> isFeatureEnabled(DmOperationContext context, String flagKey) {
            return Promise.of(connectorEnabled);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(true);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext context,
                String operationType,
                String subjectId,
                String description) {
            return Promise.of("approval-1");
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext context,
                String entityId,
                String action,
                Map<String, Object> attributes) {
            return Promise.of("audit-1");
        }
    }
}
