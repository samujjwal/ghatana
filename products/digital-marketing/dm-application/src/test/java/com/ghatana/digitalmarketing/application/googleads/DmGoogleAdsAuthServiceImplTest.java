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
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link DmGoogleAdsAuthServiceImpl}.
 *
 * @doc.type class
 * @doc.purpose Verifies Google Ads OAuth account connection lifecycle (DMOS-F2-007)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmGoogleAdsAuthServiceImpl Tests")
class DmGoogleAdsAuthServiceImplTest extends EventloopTestBase {

    private InMemoryCredentialRepository credentialRepository;
    private InMemoryConnectorRepository connectorRepository;
    private FakeOAuthClient oauthClient;
    private DmGoogleAdsAuthServiceImpl service;
    private DmOperationContext ctx;
    private DmConnectorConfig googleConnector;

    @BeforeEach
    void setUp() {
        credentialRepository = new InMemoryCredentialRepository();
        connectorRepository = new InMemoryConnectorRepository();
        oauthClient = new FakeOAuthClient();
        service = new DmGoogleAdsAuthServiceImpl(
            credentialRepository,
            connectorRepository,
            oauthClient,
            new AllowingKernelAdapter(true));

        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();

        googleConnector = DmConnectorConfig.builder()
            .id("conn-google")
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .name("Google Ads")
            .connectorType(DmConnectorType.GOOGLE_ADS)
            .status(DmConnectorStatus.PENDING)
            .settings(Map.of())
            .externalAccountId("acc-1")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        runPromise(() -> connectorRepository.save(googleConnector));
    }

    @Test
    @DisplayName("initiateOAuthFlow returns provider authorization URL")
    void initiateOAuthFlowSuccess() {
        String url = runPromise(() -> service.initiateOAuthFlow(
            ctx,
            googleConnector.getId(),
            "https://example.com/callback"));

        assertThat(url)
            .contains("oauth.example")
            .contains("redirect_uri=https://example.com/callback")
            .contains("tenant-1");
    }

    @Test
    @DisplayName("initiateOAuthFlow rejects non Google Ads connector")
    void initiateOAuthFlowRejectsWrongConnectorType() {
        DmConnectorConfig wrong = googleConnector.toBuilder()
            .id("conn-meta")
            .connectorType(DmConnectorType.META_ADS)
            .build();
        runPromise(() -> connectorRepository.save(wrong));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.initiateOAuthFlow(
                ctx,
                wrong.getId(),
                "https://example.com/callback")));
    }

    @Test
    @DisplayName("exchangeCode persists a new credential")
    void exchangeCodeCreatesCredential() {
        DmGoogleAdsCredential credential = runPromise(() -> service.exchangeCode(
            ctx,
            googleConnector.getId(),
            "auth-code-123",
            "https://example.com/callback"));

        assertThat(credential.getId()).isNotBlank();
        assertThat(credential.getTenantId()).isEqualTo("tenant-1");
        assertThat(credential.getConnectorId()).isEqualTo(googleConnector.getId());
        assertThat(credential.getAccessToken()).isEqualTo("access-auth-code-123");
        assertThat(credential.getRefreshToken()).isEqualTo("refresh-auth-code-123");
        assertThat(credential.getScopes()).contains("https://www.googleapis.com/auth/adwords");
    }

    @Test
    @DisplayName("exchangeCode updates existing connector credential")
    void exchangeCodeUpdatesExistingCredential() {
        DmGoogleAdsCredential first = runPromise(() -> service.exchangeCode(
            ctx,
            googleConnector.getId(),
            "auth-code-123",
            "https://example.com/callback"));
        DmGoogleAdsCredential second = runPromise(() -> service.exchangeCode(
            ctx,
            googleConnector.getId(),
            "auth-code-456",
            "https://example.com/callback"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getAccessToken()).isEqualTo("access-auth-code-456");
        assertThat(second.getRefreshToken()).isEqualTo("refresh-auth-code-456");
    }

    @Test
    @DisplayName("refreshToken updates access token and expiry")
    void refreshTokenSuccess() {
        DmGoogleAdsCredential created = runPromise(() -> service.exchangeCode(
            ctx,
            googleConnector.getId(),
            "auth-code-123",
            "https://example.com/callback"));

        DmGoogleAdsCredential refreshed = runPromise(() -> service.refreshToken(ctx, created.getId()));

        assertThat(refreshed.getAccessToken()).isEqualTo("access-refreshed");
        assertThat(refreshed.getRefreshToken()).isEqualTo("refresh-refreshed");
        assertThat(refreshed.getExpiresAt()).isAfter(Instant.now().plusSeconds(3000));
    }

    @Test
    @DisplayName("revokeAccess revokes at provider and deletes credential")
    void revokeAccessSuccess() {
        DmGoogleAdsCredential created = runPromise(() -> service.exchangeCode(
            ctx,
            googleConnector.getId(),
            "auth-code-123",
            "https://example.com/callback"));

        runPromise(() -> service.revokeAccess(ctx, created.getId()));

        Optional<DmGoogleAdsCredential> fromRepo = runPromise(() -> credentialRepository.findById(created.getId()));
        assertThat(fromRepo).isEmpty();
        assertThat(oauthClient.revokedTokens).contains("access-auth-code-123");
    }

    @Test
    @DisplayName("operations fail when unauthorized")
    void unauthorized() {
        service = new DmGoogleAdsAuthServiceImpl(
            credentialRepository,
            connectorRepository,
            oauthClient,
            new AllowingKernelAdapter(false));

        assertThatExceptionOfType(SecurityException.class)
            .isThrownBy(() -> runPromise(() -> service.initiateOAuthFlow(
                ctx,
                googleConnector.getId(),
                "https://example.com/callback")));
    }

    @Test
    @DisplayName("exchangeCode enforces tenant isolation on connector")
    void exchangeCodeTenantIsolation() {
        DmConnectorConfig foreign = googleConnector.toBuilder()
            .id("conn-foreign")
            .tenantId("tenant-2")
            .build();
        runPromise(() -> connectorRepository.save(foreign));

        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.exchangeCode(
                ctx,
                foreign.getId(),
                "auth-code-123",
                "https://example.com/callback")));
    }

    @Test
    @DisplayName("refreshToken rejects unknown credential")
    void refreshUnknownCredential() {
        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.refreshToken(ctx, "missing-credential")));
    }

    @Test
    @DisplayName("initiateOAuthFlow rejects blank connector id")
    void initiateRejectsBlankConnectorId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.initiateOAuthFlow(ctx, " ", "https://example.com/callback")));
    }

    @Test
    @DisplayName("initiateOAuthFlow rejects blank redirect URI")
    void initiateRejectsBlankRedirectUri() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.initiateOAuthFlow(ctx, googleConnector.getId(), " ")));
    }

    @Test
    @DisplayName("exchangeCode rejects blank input values")
    void exchangeRejectsBlankValues() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.exchangeCode(ctx, "", "code", "https://example.com/callback")));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.exchangeCode(ctx, googleConnector.getId(), "", "https://example.com/callback")));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.exchangeCode(ctx, googleConnector.getId(), "code", "")));
    }

    @Test
    @DisplayName("refreshToken rejects blank credential id")
    void refreshRejectsBlankCredentialId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.refreshToken(ctx, " ")));
    }

    @Test
    @DisplayName("revokeAccess rejects blank credential id")
    void revokeRejectsBlankCredentialId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> service.revokeAccess(ctx, " ")));
    }

    @Test
    @DisplayName("refreshToken enforces tenant isolation for credential")
    void refreshTenantIsolation() {
        DmGoogleAdsCredential foreign = DmGoogleAdsCredential.builder()
            .id("cred-foreign")
            .tenantId("tenant-2")
            .connectorId(googleConnector.getId())
            .accessToken("access")
            .refreshToken("refresh")
            .expiresAt(Instant.now().plusSeconds(60))
            .scopes(List.of("scope"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        runPromise(() -> credentialRepository.save(foreign));

        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.refreshToken(ctx, foreign.getId())));
    }

    @Test
    @DisplayName("exchangeCode rejects existing connector credential from another tenant")
    void exchangeRejectsForeignExistingCredential() {
        DmGoogleAdsCredential foreign = DmGoogleAdsCredential.builder()
            .id("cred-foreign")
            .tenantId("tenant-2")
            .connectorId(googleConnector.getId())
            .accessToken("access")
            .refreshToken("refresh")
            .expiresAt(Instant.now().plusSeconds(60))
            .scopes(List.of("scope"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        runPromise(() -> credentialRepository.save(foreign));

        assertThatExceptionOfType(java.util.NoSuchElementException.class)
            .isThrownBy(() -> runPromise(() -> service.exchangeCode(
                ctx,
                googleConnector.getId(),
                "auth-code-123",
                "https://example.com/callback")));
    }

    @Test
    @DisplayName("service methods reject null context")
    void methodsRejectNullContext() {
        assertThatNullPointerException()
            .isThrownBy(() -> runPromise(() -> service.initiateOAuthFlow(null, googleConnector.getId(), "https://example.com/callback")));
        assertThatNullPointerException()
            .isThrownBy(() -> runPromise(() -> service.exchangeCode(null, googleConnector.getId(), "code", "https://example.com/callback")));
        assertThatNullPointerException()
            .isThrownBy(() -> runPromise(() -> service.refreshToken(null, "cred")));
        assertThatNullPointerException()
            .isThrownBy(() -> runPromise(() -> service.revokeAccess(null, "cred")));
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
        public Promise<List<DmConnectorConfig>> findByType(String tenantId, DmConnectorType type, int limit) {
            return Promise.of(byId.values().stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getConnectorType() == type)
                .limit(Math.max(0, limit))
                .toList());
        }

        @Override
        public Promise<List<DmConnectorConfig>> findByStatus(String tenantId, DmConnectorStatus status, int limit) {
            return Promise.of(byId.values().stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getStatus() == status)
                .limit(Math.max(0, limit))
                .toList());
        }

        @Override
        public Promise<DmConnectorConfig> update(DmConnectorConfig connector) {
            byId.put(connector.getId(), connector);
            return Promise.of(connector);
        }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmConnectorStatus status) {
            return Promise.of(byId.values().stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getStatus() == status)
                .count());
        }
    }

    static final class FakeOAuthClient implements DmGoogleAdsOAuthClient {
        private final List<String> revokedTokens = new ArrayList<>();

        @Override
        public String buildAuthorizationUrl(String redirectUri, String state) {
            return "https://oauth.example/auth?redirect_uri=" + redirectUri + "&state=" + state;
        }

        @Override
        public Promise<OAuthTokenResponse> exchangeAuthorizationCode(String code, String redirectUri) {
            return Promise.of(new OAuthTokenResponse(
                "access-" + code,
                "refresh-" + code,
                3600,
                List.of("https://www.googleapis.com/auth/adwords")
            ));
        }

        @Override
        public Promise<OAuthTokenResponse> refreshAccessToken(String refreshToken) {
            return Promise.of(new OAuthTokenResponse(
                "access-refreshed",
                "refresh-refreshed",
                7200,
                List.of("https://www.googleapis.com/auth/adwords")
            ));
        }

        @Override
        public Promise<Void> revokeAccessToken(String accessToken) {
            revokedTokens.add(accessToken);
            return Promise.complete();
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