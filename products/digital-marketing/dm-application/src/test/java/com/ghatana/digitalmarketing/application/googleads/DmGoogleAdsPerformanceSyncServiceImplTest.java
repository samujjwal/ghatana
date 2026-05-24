package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import com.ghatana.digitalmarketing.domain.performance.DmCampaignPerformanceSnapshot;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmGoogleAdsPerformanceSyncServiceImpl Tests")
class DmGoogleAdsPerformanceSyncServiceImplTest extends EventloopTestBase {

    private EphemeralConnectorRepository connectorRepository;
    private EphemeralCredentialRepository credentialRepository;
    private EphemeralCampaignLinkRepository linkRepository;
    private EphemeralPerformanceSnapshotRepository snapshotRepository;
    private FakePerformanceApiClient performanceApiClient;
    private DmGoogleAdsPerformanceSyncServiceImpl service;
    private DmOperationContext ctx;
    private DmConnectorConfig connector;
    private DmGoogleAdsCampaignLink campaignLink;

    @BeforeEach
    void setUp() {
        connectorRepository = new EphemeralConnectorRepository();
        credentialRepository = new EphemeralCredentialRepository();
        linkRepository = new EphemeralCampaignLinkRepository();
        snapshotRepository = new EphemeralPerformanceSnapshotRepository();
        performanceApiClient = new FakePerformanceApiClient();
        service = new DmGoogleAdsPerformanceSyncServiceImpl(
            connectorRepository,
            credentialRepository,
            linkRepository,
            snapshotRepository,
            performanceApiClient,
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

        campaignLink = DmGoogleAdsCampaignLink.builder()
            .id("link-1")
            .tenantId("tenant-1")
            .connectorId(connector.getId())
            .internalCampaignId("camp-1")
            .externalCampaignId("external-camp-1")
            .createdAt(Instant.now())
            .build();
        runPromise(() -> linkRepository.save(campaignLink));
    }

    @Test
    @DisplayName("syncCampaignPerformance stores provider metrics snapshot")
    void syncCampaignPerformanceSuccess() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-31T23:59:59Z");

        DmCampaignPerformanceSnapshot snapshot = runPromise(() ->
            service.syncCampaignPerformance(
                ctx,
                new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                    connector.getId(),
                    campaignLink.getInternalCampaignId(),
                    start,
                    end
                )
            ));

        assertThat(snapshot.getTenantId()).isEqualTo("tenant-1");
        assertThat(snapshot.getExternalCampaignId()).isEqualTo("external-camp-1");
        assertThat(snapshot.getImpressions()).isEqualTo(1200);
        assertThat(snapshot.getClicks()).isEqualTo(84);
        assertThat(snapshot.getConversions()).isEqualTo(11);
        assertThat(snapshot.getPeriodStart()).isEqualTo(start);
        assertThat(snapshot.getPeriodEnd()).isEqualTo(end);
    }

    @Test
    @DisplayName("syncCampaignPerformance rejects unauthorized execution")
    void syncCampaignPerformanceUnauthorized() {
        service = new DmGoogleAdsPerformanceSyncServiceImpl(
            connectorRepository,
            credentialRepository,
            linkRepository,
            snapshotRepository,
            performanceApiClient,
            new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        connector.getId(),
                        campaignLink.getInternalCampaignId(),
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("syncCampaignPerformance requires active GOOGLE_ADS connector")
    void syncCampaignPerformanceRequiresActiveGoogleAdsConnector() {
        runPromise(() -> connectorRepository.update(
            connector.toBuilder().status(DmConnectorStatus.SUSPENDED).build()));

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        connector.getId(),
                        campaignLink.getInternalCampaignId(),
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("syncCampaignPerformance requires GOOGLE_ADS connector type")
    void syncCampaignPerformanceRequiresGoogleAdsType() {
        runPromise(() -> connectorRepository.update(
            connector.toBuilder().connectorType(DmConnectorType.META_ADS).build()));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        connector.getId(),
                        campaignLink.getInternalCampaignId(),
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("syncCampaignPerformance fails when connector does not exist")
    void syncCampaignPerformanceMissingConnector() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        "missing-connector",
                        campaignLink.getInternalCampaignId(),
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("syncCampaignPerformance requires non-expired credential")
    void syncCampaignPerformanceRequiresNonExpiredCredential() {
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
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        connector.getId(),
                        campaignLink.getInternalCampaignId(),
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("syncCampaignPerformance enforces campaign-link connector match")
    void syncCampaignPerformanceRequiresLinkConnectorMatch() {
        runPromise(() -> linkRepository.save(
            DmGoogleAdsCampaignLink.builder()
                .id("link-2")
                .tenantId("tenant-1")
                .connectorId("conn-other")
                .internalCampaignId(campaignLink.getInternalCampaignId())
                .externalCampaignId("external-camp-2")
                .createdAt(Instant.now())
                .build()));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        connector.getId(),
                        campaignLink.getInternalCampaignId(),
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("findLatestSnapshot enforces tenant isolation")
    void findLatestSnapshotTenantIsolation() {
        Instant start = Instant.parse("2026-02-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-28T23:59:59Z");
        runPromise(() -> service.syncCampaignPerformance(
            ctx,
            new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                connector.getId(),
                campaignLink.getInternalCampaignId(),
                start,
                end
            )));

        DmOperationContext otherTenant = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        Optional<DmCampaignPerformanceSnapshot> visible = runPromise(() ->
            service.findLatestSnapshot(otherTenant, campaignLink.getInternalCampaignId()));
        assertThat(visible).isEmpty();
    }

    @Test
    @DisplayName("syncCampaignPerformance fails when campaign link does not exist")
    void syncCampaignPerformanceMissingLink() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        connector.getId(),
                        "missing-campaign",
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("syncCampaignPerformance fails when connector credential is missing")
    void syncCampaignPerformanceMissingCredential() {
        runPromise(() -> credentialRepository.delete("cred-1"));

        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        connector.getId(),
                        campaignLink.getInternalCampaignId(),
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("syncCampaignPerformance fails when connector belongs to another tenant")
    void syncCampaignPerformanceConnectorTenantMismatch() {
        runPromise(() -> connectorRepository.update(
            connector.toBuilder().tenantId("tenant-2").build()));

        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        connector.getId(),
                        campaignLink.getInternalCampaignId(),
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("syncCampaignPerformance fails when credential belongs to another tenant")
    void syncCampaignPerformanceCredentialTenantMismatch() {
        DmGoogleAdsCredential otherTenantCredential = DmGoogleAdsCredential.builder()
            .id("cred-1")
            .tenantId("tenant-2")
            .connectorId(connector.getId())
            .accessToken("access-token")
            .refreshToken("refresh-token")
            .expiresAt(Instant.now().plusSeconds(3600))
            .scopes(java.util.List.of("scope"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        runPromise(() -> credentialRepository.update(otherTenantCredential));

        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        connector.getId(),
                        campaignLink.getInternalCampaignId(),
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("syncCampaignPerformance fails when campaign link belongs to another tenant")
    void syncCampaignPerformanceLinkTenantMismatch() {
        runPromise(() -> linkRepository.save(
            DmGoogleAdsCampaignLink.builder()
                .id("link-1")
                .tenantId("tenant-2")
                .connectorId(connector.getId())
                .internalCampaignId(campaignLink.getInternalCampaignId())
                .externalCampaignId(campaignLink.getExternalCampaignId())
                .createdAt(Instant.now())
                .build()));

        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() ->
                service.syncCampaignPerformance(
                    ctx,
                    new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                        connector.getId(),
                        campaignLink.getInternalCampaignId(),
                        Instant.now().minusSeconds(3600),
                        Instant.now()
                    )
                )));
    }

    @Test
    @DisplayName("findLatestSnapshot returns latest snapshot for tenant")
    void findLatestSnapshotSuccess() {
        Instant start = Instant.parse("2026-03-01T00:00:00Z");
        Instant end = Instant.parse("2026-03-31T23:59:59Z");
        runPromise(() -> service.syncCampaignPerformance(
            ctx,
            new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                connector.getId(),
                campaignLink.getInternalCampaignId(),
                start,
                end
            )));

        Optional<DmCampaignPerformanceSnapshot> snapshot = runPromise(() ->
            service.findLatestSnapshot(ctx, campaignLink.getInternalCampaignId()));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getExternalCampaignId()).isEqualTo(campaignLink.getExternalCampaignId());
    }

    @Test
    @DisplayName("findLatestSnapshot returns empty when snapshot belongs to another tenant")
    void findLatestSnapshotSnapshotTenantMismatch() {
        runPromise(() -> snapshotRepository.save(
            DmCampaignPerformanceSnapshot.builder()
                .id("snap-tenant-2")
                .tenantId("tenant-2")
                .externalCampaignId(campaignLink.getExternalCampaignId())
                .impressions(1)
                .clicks(1)
                .conversions(0)
                .costMicros(1000)
                .ctr(1.0)
                .cpc(1.0)
                .conversionRate(0.0)
                .periodStart(Instant.parse("2026-04-01T00:00:00Z"))
                .periodEnd(Instant.parse("2026-04-01T23:59:59Z"))
                .capturedAt(Instant.now())
                .build()));

        Optional<DmCampaignPerformanceSnapshot> snapshot = runPromise(() ->
            service.findLatestSnapshot(ctx, campaignLink.getInternalCampaignId()));
        assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("findLatestSnapshot returns empty when link not found")
    void findLatestSnapshotMissingLink() {
        Optional<DmCampaignPerformanceSnapshot> snapshot = runPromise(() ->
            service.findLatestSnapshot(ctx, "missing-campaign"));
        assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("findLatestSnapshot rejects blank campaign id")
    void findLatestSnapshotRejectsBlankCampaignId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.findLatestSnapshot(ctx, "   ")));
    }

    static final class EphemeralConnectorRepository implements DmConnectorRepository {
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

    static final class EphemeralPerformanceSnapshotRepository implements DmGoogleAdsPerformanceSnapshotRepository {
        private final Map<String, DmCampaignPerformanceSnapshot> byExternalCampaignId = new ConcurrentHashMap<>();

        @Override
        public Promise<DmCampaignPerformanceSnapshot> save(DmCampaignPerformanceSnapshot snapshot) {
            byExternalCampaignId.put(snapshot.getExternalCampaignId(), snapshot);
            return Promise.of(snapshot);
        }

        @Override
        public Promise<Optional<DmCampaignPerformanceSnapshot>> findLatestByExternalCampaignId(String externalCampaignId) {
            return Promise.of(Optional.ofNullable(byExternalCampaignId.get(externalCampaignId)));
        }
    }

    static final class FakePerformanceApiClient implements DmGoogleAdsPerformanceApiClient {
        @Override
        public Promise<CampaignPerformanceResponse> fetchCampaignPerformance(
                String accessToken,
                FetchCampaignPerformanceRequest request) {
            return Promise.of(new CampaignPerformanceResponse(
                1200,
                84,
                11,
                5600000,
                0.07,
                0.66,
                0.13
            ));
        }
    }

    static final class AllowingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean allowed;

        AllowingKernelAdapter(boolean allowed) {
            this.allowed = allowed;
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

        @Override
        public Promise<Boolean> isFeatureEnabled(DmOperationContext context, String flagKey) {
            return Promise.of(true);
        }
    }
}
