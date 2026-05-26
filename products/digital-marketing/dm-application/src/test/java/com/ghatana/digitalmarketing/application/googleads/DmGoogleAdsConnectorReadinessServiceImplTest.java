package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.connector.googleads.GoogleAdsConnectorReadinessState;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmGoogleAdsConnectorReadinessServiceImpl}.
 *
 * @doc.type class
 * @doc.purpose Verifies Google Ads readiness persistence for cockpit and launch gating
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmGoogleAdsConnectorReadinessServiceImpl Tests")
class DmGoogleAdsConnectorReadinessServiceImplTest extends EventloopTestBase {

    private EphemeralConnectorRepository connectorRepository;
    private EphemeralCredentialRepository credentialRepository;
    private StubGoogleAdsClient googleAdsClient;
    private DmGoogleAdsConnectorReadinessServiceImpl service;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        connectorRepository = new EphemeralConnectorRepository();
        credentialRepository = new EphemeralCredentialRepository();
        googleAdsClient = new StubGoogleAdsClient();
        service = new DmGoogleAdsConnectorReadinessServiceImpl(
            connectorRepository,
            credentialRepository,
            googleAdsClient,
            new AllowingKernelAdapter(true)
        );
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("missing credentials persist NOT_READY and keep connector non-operational")
    void missingCredentialsPersistNotReady() {
        DmConnectorConfig connector = saveConnector(DmConnectorStatus.PENDING);

        DmGoogleAdsConnectorReadiness readiness = runPromise(() -> service.checkReadiness(ctx, connector.getId()));

        assertThat(readiness.readinessState()).isEqualTo(GoogleAdsConnectorReadinessState.NOT_READY);
        assertThat(readiness.connectorStatus()).isEqualTo(DmConnectorStatus.PENDING);
        assertThat(readiness.reason()).contains("missing Google Ads OAuth credential");
        assertThat(connectorRepository.store.get(connector.getId()).getLastHealthCheckAt()).isNotNull();
    }

    @Test
    @DisplayName("revoked credential persists AUTH_FAILED")
    void revokedCredentialPersistsAuthFailed() {
        DmConnectorConfig connector = saveConnector(DmConnectorStatus.ACTIVE);
        saveCredential(connector.getId(), "tenant-1", true, Instant.now().plusSeconds(3600));

        DmGoogleAdsConnectorReadiness readiness = runPromise(() -> service.checkReadiness(ctx, connector.getId()));

        assertThat(readiness.readinessState()).isEqualTo(GoogleAdsConnectorReadinessState.AUTH_FAILED);
        assertThat(readiness.connectorStatus()).isEqualTo(DmConnectorStatus.AUTH_FAILED);
        assertThat(readiness.reason()).contains("revoked");
    }

    @Test
    @DisplayName("expired credential persists AUTH_FAILED")
    void expiredCredentialPersistsAuthFailed() {
        DmConnectorConfig connector = saveConnector(DmConnectorStatus.ACTIVE);
        saveCredential(connector.getId(), "tenant-1", false, Instant.now().minusSeconds(60));

        DmGoogleAdsConnectorReadiness readiness = runPromise(() -> service.checkReadiness(ctx, connector.getId()));

        assertThat(readiness.readinessState()).isEqualTo(GoogleAdsConnectorReadinessState.AUTH_FAILED);
        assertThat(readiness.connectorStatus()).isEqualTo(DmConnectorStatus.AUTH_FAILED);
        assertThat(readiness.reason()).contains("expired");
    }

    @Test
    @DisplayName("rate limit persists pending readiness with observable reason")
    void rateLimitPersistsPendingReadiness() {
        DmConnectorConfig connector = saveConnector(DmConnectorStatus.ACTIVE);
        saveCredential(connector.getId(), "tenant-1", false, Instant.now().plusSeconds(3600));
        googleAdsClient.readinessState = GoogleAdsConnectorReadinessState.RATE_LIMITED;

        DmGoogleAdsConnectorReadiness readiness = runPromise(() -> service.checkReadiness(ctx, connector.getId()));

        assertThat(readiness.readinessState()).isEqualTo(GoogleAdsConnectorReadinessState.RATE_LIMITED);
        assertThat(readiness.connectorStatus()).isEqualTo(DmConnectorStatus.PENDING);
        assertThat(readiness.reason()).contains("rate limited");
    }

    @Test
    @DisplayName("disabled connector reports environment blocked without calling remote API")
    void disabledConnectorReportsEnvironmentBlocked() {
        DmConnectorConfig connector = saveConnector(DmConnectorStatus.DISABLED);
        saveCredential(connector.getId(), "tenant-1", false, Instant.now().plusSeconds(3600));

        DmGoogleAdsConnectorReadiness readiness = runPromise(() -> service.checkReadiness(ctx, connector.getId()));

        assertThat(readiness.readinessState()).isEqualTo(GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED);
        assertThat(readiness.connectorStatus()).isEqualTo(DmConnectorStatus.DISABLED);
        assertThat(googleAdsClient.readinessChecks).isZero();
    }

    @Test
    @DisplayName("healthy Google Ads check activates connector")
    void healthyGoogleAdsCheckActivatesConnector() {
        DmConnectorConfig connector = saveConnector(DmConnectorStatus.PENDING);
        saveCredential(connector.getId(), "tenant-1", false, Instant.now().plusSeconds(3600));
        googleAdsClient.readinessState = GoogleAdsConnectorReadinessState.READY;

        DmGoogleAdsConnectorReadiness readiness = runPromise(() -> service.checkReadiness(ctx, connector.getId()));

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.connectorStatus()).isEqualTo(DmConnectorStatus.ACTIVE);
        assertThat(connectorRepository.store.get(connector.getId()).isOperational()).isTrue();
    }

    @Test
    @DisplayName("cross-tenant connector lookup fails closed")
    void crossTenantConnectorLookupFailsClosed() {
        DmConnectorConfig connector = saveConnector(DmConnectorStatus.PENDING);
        DmOperationContext otherTenant = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of("ws-2"))
            .actor(ActorRef.user("user-2"))
            .correlationId(DmCorrelationId.of("corr-2"))
            .idempotencyKey(DmIdempotencyKey.of("idk-2"))
            .build();

        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.checkReadiness(otherTenant, connector.getId())));
    }

    private DmConnectorConfig saveConnector(DmConnectorStatus status) {
        Instant now = Instant.now();
        DmConnectorConfig connector = DmConnectorConfig.builder()
            .id("connector-" + connectorRepository.store.size())
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .name("Google Ads")
            .connectorType(DmConnectorType.GOOGLE_ADS)
            .status(status)
            .settings(Map.of("customerId", "123"))
            .externalAccountId("customers/123")
            .createdAt(now)
            .updatedAt(now)
            .build();
        runPromise(() -> connectorRepository.save(connector));
        return connector;
    }

    private void saveCredential(String connectorId, String tenantId, boolean revoked, Instant expiresAt) {
        Instant now = Instant.now();
        DmGoogleAdsCredential credential = DmGoogleAdsCredential.builder()
            .id("credential-" + connectorId)
            .tenantId(tenantId)
            .connectorId(connectorId)
            .accessToken("access-token")
            .refreshToken("refresh-token")
            .expiresAt(expiresAt)
            .scopes(List.of("https://www.googleapis.com/auth/adwords"))
            .createdAt(now)
            .updatedAt(now)
            .revoked(revoked)
            .revokedAt(revoked ? now : null)
            .build();
        runPromise(() -> credentialRepository.save(credential));
    }

    private static final class EphemeralConnectorRepository implements DmConnectorRepository {
        private final ConcurrentHashMap<String, DmConnectorConfig> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmConnectorConfig> save(DmConnectorConfig connector) {
            store.put(connector.getId(), connector);
            return Promise.of(connector);
        }

        @Override
        public Promise<Optional<DmConnectorConfig>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmConnectorConfig>> findByType(String tenantId, DmConnectorType type, int limit) {
            return Promise.of(store.values().stream()
                .filter(connector -> connector.getTenantId().equals(tenantId))
                .filter(connector -> connector.getConnectorType() == type)
                .limit(limit)
                .toList());
        }

        @Override
        public Promise<List<DmConnectorConfig>> findByStatus(String tenantId, DmConnectorStatus status, int limit) {
            List<DmConnectorConfig> result = new ArrayList<>();
            for (DmConnectorConfig connector : store.values()) {
                if (connector.getTenantId().equals(tenantId) && connector.getStatus() == status) {
                    result.add(connector);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<DmConnectorConfig> update(DmConnectorConfig connector) {
            store.put(connector.getId(), connector);
            return Promise.of(connector);
        }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmConnectorStatus status) {
            long count = store.values().stream()
                .filter(connector -> connector.getTenantId().equals(tenantId))
                .filter(connector -> connector.getStatus() == status)
                .count();
            return Promise.of(count);
        }
    }

    private static final class EphemeralCredentialRepository implements DmGoogleAdsCredentialRepository {
        private final ConcurrentHashMap<String, DmGoogleAdsCredential> byId = new ConcurrentHashMap<>();

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
                .filter(credential -> credential.getConnectorId().equals(connectorId))
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

    private static final class StubGoogleAdsClient implements DmGoogleAdsCampaignApiClient {
        private GoogleAdsConnectorReadinessState readinessState = GoogleAdsConnectorReadinessState.READY;
        private int readinessChecks;

        @Override
        public Promise<String> createSearchCampaign(String accessToken, CreateGoogleSearchCampaignRequest request) {
            return Promise.of("customers/123/campaigns/456");
        }

        @Override
        public Promise<String> pauseCampaign(String accessToken, String externalCampaignId) {
            return Promise.of(externalCampaignId);
        }

        @Override
        public Promise<GoogleAdsConnectorReadinessState> checkReadiness(String accessToken) {
            readinessChecks += 1;
            return Promise.of(readinessState);
        }
    }

    private static final class AllowingKernelAdapter implements DigitalMarketingKernelAdapter {
        private final boolean authorized;

        private AllowingKernelAdapter(boolean authorized) {
            this.authorized = authorized;
        }

        @Override public void start() {}
        @Override public void stop() {}
        @Override public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) { return Promise.of(authorized); }
        @Override public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) { return Promise.of(true); }
        @Override public Promise<String> requestApproval(DmOperationContext context, String operationType, String subjectId, String description) { return Promise.of("approval-1"); }
        @Override public Promise<String> recordAudit(DmOperationContext context, String entityId, String action, Map<String, Object> attributes) { return Promise.of("audit-1"); }
    }
}
