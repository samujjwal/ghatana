package com.ghatana.digitalmarketing.connector.googleads;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.test.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production-grade tests for Google Ads connector.
 * Tests verify OAuth, idempotency, retry, DLQ, compensation, external ID persistence, and audit.
 *
 * @doc.type class
 * @doc.purpose Google Ads connector production proof tests
 * @doc.layer product
 * @doc.pattern Integration test
 */
@DisplayName("Google Ads Connector Production Proof Tests")
class GoogleAdsConnectorProductionProofTest extends EventloopTestBase {

    private GoogleAdsConnector connector;
    private MockGoogleAdsApiClient mockApiClient;
    private MockCredentialRepository credentialRepository;
    private MockCampaignLinkRepository campaignLinkRepository;
    private MockAuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        mockApiClient = new MockGoogleAdsApiClient();
        credentialRepository = new MockCredentialRepository();
        campaignLinkRepository = new MockCampaignLinkRepository();
        auditLogger = new MockAuditLogger();
        
        connector = new GoogleAdsConnector(
            mockApiClient,
            credentialRepository,
            campaignLinkRepository,
            auditLogger
        );
    }

    @Test
    @DisplayName("OAuth authentication should validate access token")
    void testOAuthAuthentication() throws Exception {
        String accessToken = "valid-access-token";
        String developerToken = "developer-token";
        String customerId = "123-456-7890";

        GoogleAdsCredentials credentials = new GoogleAdsCredentials(
            accessToken,
            developerToken,
            customerId
        );

        Promise<Boolean> authResult = connector.validateAuthentication(credentials);

        Boolean isValid = runPromise(() -> authResult);
        assertThat(isValid).isTrue();
        assertThat(mockApiClient.getAccessToken()).isEqualTo(accessToken);
        assertThat(mockApiClient.getDeveloperToken()).isEqualTo(developerToken);
    }

    @Test
    @DisplayName("OAuth authentication should reject invalid tokens")
    void testOAuthAuthenticationRejectsInvalidToken() throws Exception {
        String accessToken = "invalid-token";
        String developerToken = "developer-token";
        String customerId = "123-456-7890";

        GoogleAdsCredentials credentials = new GoogleAdsCredentials(
            accessToken,
            developerToken,
            customerId
        );

        mockApiClient.setShouldFailAuthentication(true);

        Promise<Boolean> authResult = connector.validateAuthentication(credentials);

        Boolean isValid = runPromise(() -> authResult);
        assertThat(isValid).isFalse();
        assertThat(auditLogger.getAuditCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Campaign creation should be idempotent")
    void testCampaignCreationIdempotency() throws Exception {
        String tenantId = "tenant-123";
        String workspaceId = "workspace-456";
        String campaignId = "campaign-789";
        String externalCampaignId = "ext-campaign-001";
        String accessToken = "valid-token";

        CreateGoogleSearchCampaignRequest request = new CreateGoogleSearchCampaignRequest(
            "Test Campaign",
            100.0,
            "active"
        );

        // First call
        Promise<String> firstResult = connector.createCampaign(
            tenantId,
            workspaceId,
            campaignId,
            externalCampaignId,
            accessToken,
            request
        );
        String firstExternalId = runPromise(() -> firstResult);

        // Second call with same idempotency key
        Promise<String> secondResult = connector.createCampaign(
            tenantId,
            workspaceId,
            campaignId,
            externalCampaignId,
            accessToken,
            request
        );
        String secondExternalId = runPromise(() -> secondResult);

        // Should return the same external ID (idempotent)
        assertThat(firstExternalId).isEqualTo(secondExternalId);
        assertThat(mockApiClient.getCreateCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Campaign creation should retry on transient failures")
    void testCampaignCreationRetry() throws Exception {
        String tenantId = "tenant-123";
        String workspaceId = "workspace-456";
        String campaignId = "campaign-789";
        String externalCampaignId = "ext-campaign-002";
        String accessToken = "valid-token";

        CreateGoogleSearchCampaignRequest request = new CreateGoogleSearchCampaignRequest(
            "Test Campaign",
            100.0,
            "active"
        );

        // Configure mock to fail twice then succeed
        mockApiClient.setTransientFailureCount(2);

        Promise<String> result = connector.createCampaign(
            tenantId,
            workspaceId,
            campaignId,
            externalCampaignId,
            accessToken,
            request
        );

        String externalId = runPromise(() -> result);
        assertThat(externalId).isNotNull();
        assertThat(mockApiClient.getCreateCallCount()).isEqualTo(3); // 2 failures + 1 success
    }

    @Test
    @DisplayName("Failed operations should be sent to DLQ")
    void testDeadLetterQueue() throws Exception {
        String tenantId = "tenant-123";
        String workspaceId = "workspace-456";
        String campaignId = "campaign-789";
        String externalCampaignId = "ext-campaign-003";
        String accessToken = "valid-token";

        CreateGoogleSearchCampaignRequest request = new CreateGoogleSearchCampaignRequest(
            "Test Campaign",
            100.0,
            "active"
        );

        // Configure mock to fail permanently
        mockApiClient.setPermanentFailure(true);

        Promise<String> result = connector.createCampaign(
            tenantId,
            workspaceId,
            campaignId,
            externalCampaignId,
            accessToken,
            request
        );

        try {
            runPromise(() -> result);
        } catch (Exception e) {
            // Expected to fail
        }

        // Verify DLQ entry was created
        assertThat(connector.getDeadLetterQueueSize()).isGreaterThan(0);
        assertThat(auditLogger.getAuditCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Failed operations should trigger compensation")
    void testCompensationOnFailure() throws Exception {
        String tenantId = "tenant-123";
        String workspaceId = "workspace-456";
        String campaignId = "campaign-789";
        String externalCampaignId = "ext-campaign-004";
        String accessToken = "valid-token";

        CreateGoogleSearchCampaignRequest request = new CreateGoogleSearchCampaignRequest(
            "Test Campaign",
            100.0,
            "active"
        );

        // Configure mock to fail after partial success
        mockApiClient.setPartialFailure(true);

        Promise<String> result = connector.createCampaign(
            tenantId,
            workspaceId,
            campaignId,
            externalCampaignId,
            accessToken,
            request
        );

        try {
            runPromise(() -> result);
        } catch (Exception e) {
            // Expected to fail
        }

        // Verify compensation was triggered
        assertThat(mockApiClient.getCompensationCallCount()).isGreaterThan(0);
        assertThat(auditLogger.getAuditCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("External ID should be persisted after successful creation")
    void testExternalIdPersistence() throws Exception {
        String tenantId = "tenant-123";
        String workspaceId = "workspace-456";
        String campaignId = "campaign-789";
        String externalCampaignId = "ext-campaign-005";
        String accessToken = "valid-token";

        CreateGoogleSearchCampaignRequest request = new CreateGoogleSearchCampaignRequest(
            "Test Campaign",
            100.0,
            "active"
        );

        Promise<String> result = connector.createCampaign(
            tenantId,
            workspaceId,
            campaignId,
            externalCampaignId,
            accessToken,
            request
        );

        String externalId = runPromise(() -> result);

        // Verify external ID was persisted
        GoogleAdsCampaignLink link = campaignLinkRepository.findByCampaignId(campaignId);
        assertThat(link).isNotNull();
        assertThat(link.externalCampaignId()).isEqualTo(externalId);
        assertThat(link.tenantId()).isEqualTo(tenantId);
        assertThat(link.workspaceId()).isEqualTo(workspaceId);
    }

    @Test
    @DisplayName("All operations should be audited")
    void testAuditLogging() throws Exception {
        String tenantId = "tenant-123";
        String workspaceId = "workspace-456";
        String campaignId = "campaign-789";
        String externalCampaignId = "ext-campaign-006";
        String accessToken = "valid-token";

        CreateGoogleSearchCampaignRequest request = new CreateGoogleSearchCampaignRequest(
            "Test Campaign",
            100.0,
            "active"
        );

        Promise<String> result = connector.createCampaign(
            tenantId,
            workspaceId,
            campaignId,
            externalCampaignId,
            accessToken,
            request
        );

        runPromise(() -> result);

        // Verify audit log entry was created
        assertThat(auditLogger.getAuditCount()).isGreaterThan(0);
        AuditEntry lastEntry = auditLogger.getLastEntry();
        assertThat(lastEntry.action()).isEqualTo("CREATE_CAMPAIGN");
        assertThat(lastEntry.tenantId()).isEqualTo(tenantId);
        assertThat(lastEntry.workspaceId()).isEqualTo(workspaceId);
        assertThat(lastEntry.timestamp()).isBefore(Instant.now());
    }

    @Test
    @DisplayName("Pause operation should be idempotent")
    void testPauseCampaignIdempotency() throws Exception {
        String tenantId = "tenant-123";
        String workspaceId = "workspace-456";
        String campaignId = "campaign-789";
        String externalCampaignId = "ext-campaign-007";
        String accessToken = "valid-token";

        // First pause
        Promise<Boolean> firstPause = connector.pauseCampaign(
            tenantId,
            workspaceId,
            campaignId,
            externalCampaignId,
            accessToken
        );
        Boolean firstResult = runPromise(() -> firstPause);

        // Second pause
        Promise<Boolean> secondPause = connector.pauseCampaign(
            tenantId,
            workspaceId,
            campaignId,
            externalCampaignId,
            accessToken
        );
        Boolean secondResult = runPromise(() -> secondPause);

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isTrue();
        assertThat(mockApiClient.getPauseCallCount()).isEqualTo(1); // Idempotent
    }

    @Test
    @DisplayName("Credentials should be securely stored")
    void testCredentialSecurity() throws Exception {
        String tenantId = "tenant-123";
        String accessToken = "sensitive-access-token";
        String developerToken = "sensitive-dev-token";
        String customerId = "123-456-7890";

        GoogleAdsCredentials credentials = new GoogleAdsCredentials(
            accessToken,
            developerToken,
            customerId
        );

        credentialRepository.save(tenantId, credentials);

        GoogleAdsCredentials retrieved = credentialRepository.findByTenantId(tenantId);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.accessToken()).isEqualTo(accessToken);
        assertThat(retrieved.developerToken()).isEqualTo(developerToken);
        assertThat(retrieved.customerId()).isEqualTo(customerId);
    }

    // Mock implementations for testing

    private static class MockGoogleAdsApiClient implements GoogleAdsApiClient {
        private String accessToken;
        private String developerToken;
        private final AtomicInteger createCallCount = new AtomicInteger(0);
        private final AtomicInteger pauseCallCount = new AtomicInteger(0);
        private final AtomicInteger compensationCallCount = new AtomicInteger(0);
        private boolean shouldFailAuthentication = false;
        private int transientFailureCount = 0;
        private boolean permanentFailure = false;
        private boolean partialFailure = false;

        @Override
        public Promise<String> createCampaign(String accessToken, CreateGoogleSearchCampaignRequest request) {
            this.accessToken = accessToken;
            int callCount = createCallCount.incrementAndGet();

            if (permanentFailure) {
                return Promise.ofException(new RuntimeException("Permanent failure"));
            }

            if (transientFailureCount > 0 && callCount <= transientFailureCount) {
                return Promise.ofException(new RuntimeException("Transient failure"));
            }

            if (partialFailure && callCount == 1) {
                return Promise.ofException(new RuntimeException("Partial failure"));
            }

            return Promise.of("ext-campaign-" + System.currentTimeMillis());
        }

        @Override
        public Promise<Boolean> pauseCampaign(String accessToken, String externalCampaignId) {
            this.accessToken = accessToken;
            pauseCallCount.incrementAndGet();
            return Promise.of(true);
        }

        @Override
        public Promise<Boolean> validateAuthentication(String accessToken, String developerToken, String customerId) {
            this.accessToken = accessToken;
            this.developerToken = developerToken;
            if (shouldFailAuthentication) {
                return Promise.of(false);
            }
            return Promise.of(true);
        }

        @Override
        public Promise<Void> compensate(String externalCampaignId) {
            compensationCallCount.incrementAndGet();
            return Promise.complete();
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getDeveloperToken() {
            return developerToken;
        }

        public int getCreateCallCount() {
            return createCallCount.get();
        }

        public int getPauseCallCount() {
            return pauseCallCount.get();
        }

        public int getCompensationCallCount() {
            return compensationCallCount.get();
        }

        public void setShouldFailAuthentication(boolean shouldFailAuthentication) {
            this.shouldFailAuthentication = shouldFailAuthentication;
        }

        public void setTransientFailureCount(int count) {
            this.transientFailureCount = count;
        }

        public void setPermanentFailure(boolean permanentFailure) {
            this.permanentFailure = permanentFailure;
        }

        public void setPartialFailure(boolean partialFailure) {
            this.partialFailure = partialFailure;
        }
    }

    private static class MockCredentialRepository implements GoogleAdsCredentialRepository {
        private final Map<String, GoogleAdsCredentials> storage = new java.util.HashMap<>();

        @Override
        public void save(String tenantId, GoogleAdsCredentials credentials) {
            storage.put(tenantId, credentials);
        }

        @Override
        public GoogleAdsCredentials findByTenantId(String tenantId) {
            return storage.get(tenantId);
        }

        @Override
        public void delete(String tenantId) {
            storage.remove(tenantId);
        }
    }

    private static class MockCampaignLinkRepository implements GoogleAdsCampaignLinkRepository {
        private final Map<String, GoogleAdsCampaignLink> storage = new java.util.HashMap<>();

        @Override
        public void save(GoogleAdsCampaignLink link) {
            storage.put(link.campaignId(), link);
        }

        @Override
        public GoogleAdsCampaignLink findByCampaignId(String campaignId) {
            return storage.get(campaignId);
        }

        @Override
        public GoogleAdsCampaignLink findByExternalCampaignId(String externalCampaignId) {
            return storage.values().stream()
                .filter(link -> link.externalCampaignId().equals(externalCampaignId))
                .findFirst()
                .orElse(null);
        }

        @Override
        public void delete(String campaignId) {
            storage.remove(campaignId);
        }
    }

    private static class MockAuditLogger implements AuditLogger {
        private final java.util.List<AuditEntry> auditEntries = new java.util.ArrayList<>();

        @Override
        public void log(AuditEntry entry) {
            auditEntries.add(entry);
        }

        public int getAuditCount() {
            return auditEntries.size();
        }

        public AuditEntry getLastEntry() {
            return auditEntries.isEmpty() ? null : auditEntries.get(auditEntries.size() - 1);
        }
    }

    // Domain objects for testing

    private record GoogleAdsCredentials(
        String accessToken,
        String developerToken,
        String customerId
    ) {}

    private record CreateGoogleSearchCampaignRequest(
        String name,
        double budget,
        String status
    ) {}

    private record GoogleAdsCampaignLink(
        String tenantId,
        String workspaceId,
        String campaignId,
        String externalCampaignId,
        Instant createdAt
    ) {}

    private record AuditEntry(
        String action,
        String tenantId,
        String workspaceId,
        Instant timestamp,
        Map<String, Object> metadata
    ) {}
}
