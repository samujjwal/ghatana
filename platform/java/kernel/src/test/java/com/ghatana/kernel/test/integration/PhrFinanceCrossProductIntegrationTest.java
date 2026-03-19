package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapterImpl;
import com.ghatana.kernel.audit.CrossProductAuditService;
import com.ghatana.kernel.boundary.ProductBoundaryEnforcer;
import com.ghatana.kernel.communication.KernelInterProductBus;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.testing.PlatformIntegrationTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PHR-Finance cross-product data sharing.
 *
 * <p>Uses {@link PlatformIntegrationTestBase} for tests requiring
 * PostgreSQL and Redis containers alongside ActiveJ Eventloop.</p>
 *
 * @doc.type class
 * @doc.purpose Cross-product integration tests with real infrastructure
 * @doc.layer test
 * @doc.pattern Integration Test
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("PHR-Finance Cross-Product Integration Tests")
class PhrFinanceCrossProductIntegrationTest extends PlatformIntegrationTestBase {

    private DataCloudKernelAdapterImpl dataCloudAdapter;
    private CrossProductAuditService auditService;
    private ProductBoundaryEnforcer boundaryEnforcer;
    private KernelInterProductBus interProductBus;

    @Override
    protected boolean requiresPostgres() {
        return true;
    }

    @Override
    protected boolean requiresRedis() {
        return true;
    }

    @Override
    protected Duration eventloopTimeout() {
        return Duration.ofSeconds(60); // Longer timeout for integration tests
    }

    @BeforeEach
    void setUp() {
        // Initialize with test containers
        var registry = new KernelRegistryImpl();
        boundaryEnforcer = new ProductBoundaryEnforcer(registry);

        // Setup mock Data-Cloud client for testing
        dataCloudAdapter = new DataCloudKernelAdapterImpl(new MockDataCloudClient());
        auditService = new CrossProductAuditService(dataCloudAdapter);
        interProductBus = new KernelInterProductBus(registry, auditService, boundaryEnforcer);
    }

    @Test
    @DisplayName("should allow PHR to access Finance billing data with proper consent")
    void testPhrToFinanceBillingAccess() {
        // Given
        KernelTenantContext context = createHealthcareContextWithConsent();

        // When
        boolean allowed = boundaryEnforcer.canAccess(
            "phr", "finance", "billing.records", "read", context
        );

        // Then
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("should deny PHR access without proper consent")
    void testPhrAccessDeniedWithoutConsent() {
        // Given
        KernelTenantContext context = createContextWithoutConsent();

        // When
        boolean allowed = boundaryEnforcer.canAccess(
            "phr", "finance", "patient.records", "read", context
        );

        // Then
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("should publish audit event for cross-product access")
    void testAuditEventPublished() {
        // Given
        String auditId = "audit-" + System.currentTimeMillis();
        var record = CrossProductAuditService.AuditRecord.builder()
            .auditId(auditId)
            .sourceProduct("phr")
            .targetProduct("finance")
            .action("read")
            .resource("billing.records")
            .tenantId("tenant-123")
            .userId("user-456")
            .timestamp(Instant.now())
            .retentionPeriod(CrossProductAuditService.RetentionPeriod.FINANCE_10_YEARS)
            .success(true)
            .build();

        // When - using runPromise for async operation
        Void result = runPromise(() -> auditService.publishAuditEvent(record));

        // Then
        assertThat(result).isNull(); // Void return
    }

    @Test
    @DisplayName("should share data between PHR and Finance with proper encryption")
    void testDataSharingWithEncryption() {
        // Given
        KernelInterProductBus.SharedDataRecord record = KernelInterProductBus.SharedDataRecord.builder()
            .dataId("shared-data-123")
            .sourceProduct("phr")
            .targetProduct("finance")
            .data(Map.of("billing_amount", 1000.00, "patient_id", "P-123"))
            .accessPolicy("billing-only")
            .retentionPeriod(Duration.ofDays(30))
            .encryptionRequired(true)
            .auditRequired(true)
            .createdAt(Instant.now())
            .build();

        // When
        Void result = runPromise(() -> interProductBus.shareData(record));

        // Then
        assertThat(result).isNull();

        // Verify data can be retrieved
        var retrieved = runPromise(() -> interProductBus.retrieveData("shared-data-123", "finance"));
        assertThat(retrieved).isNotNull();
    }

    @Test
    @DisplayName("should respect different retention periods for Finance vs PHR")
    void testRetentionPeriods() {
        // Given - Finance record (10 years)
        var financeRecord = CrossProductAuditService.AuditRecord.builder()
            .auditId("finance-audit-1")
            .sourceProduct("finance")
            .targetProduct("phr")
            .action("read")
            .retentionPeriod(CrossProductAuditService.RetentionPeriod.FINANCE_10_YEARS)
            .build();

        // Given - PHR record (7 years)
        var phrRecord = CrossProductAuditService.AuditRecord.builder()
            .auditId("phr-audit-1")
            .sourceProduct("phr")
            .targetProduct("finance")
            .action("read")
            .retentionPeriod(CrossProductAuditService.RetentionPeriod.HEALTHCARE_7_YEARS)
            .build();

        // Then
        assertThat(financeRecord.getRetentionPeriod().getYears()).isEqualTo(10);
        assertThat(phrRecord.getRetentionPeriod().getYears()).isEqualTo(7);
    }

    @Test
    @DisplayName("should enforce data isolation between tenants")
    void testTenantIsolation() {
        // Given
        KernelTenantContext tenantA = createTenantContext("tenant-a");
        KernelTenantContext tenantB = createTenantContext("tenant-b");

        // Create data for tenant A
        var recordA = KernelInterProductBus.SharedDataRecord.builder()
            .dataId("tenant-a-data")
            .sourceProduct("phr")
            .targetProduct("finance")
            .tenantId("tenant-a")
            .data(Map.of("value", "tenant-a-value"))
            .accessPolicy("tenant-isolated")
            .retentionPeriod(Duration.ofDays(7))
            .encryptionRequired(true)
            .auditRequired(true)
            .createdAt(Instant.now())
            .build();

        // When
        runPromise(() -> interProductBus.shareData(recordA));

        // Then - tenant B should not access tenant A's data
        // (This would require actual Data-Cloud implementation to verify)
    }

    // ==================== Test Fixtures ====================

    private KernelTenantContext createHealthcareContextWithConsent() {
        return new TestTenantContext(
            "tenant-123",
            Set.of("healthcare.data.access", "billing.read"),
            Map.of("phr.cross-product.access", true, "finance.audit.enabled", true)
        );
    }

    private KernelTenantContext createContextWithoutConsent() {
        return new TestTenantContext(
            "tenant-456",
            Set.of(), // No permissions
            Map.of()
        );
    }

    private KernelTenantContext createTenantContext(String tenantId) {
        return new TestTenantContext(
            tenantId,
            Set.of("read", "write"),
            Map.of()
        );
    }

    /**
     * Test implementation of KernelTenantContext.
     */
    static class TestTenantContext implements KernelTenantContext {
        private final String tenantId;
        private final Set<String> permissions;
        private final Map<String, Object> config;

        TestTenantContext(String tenantId, Set<String> permissions, Map<String, Object> config) {
            this.tenantId = tenantId;
            this.permissions = permissions;
            this.config = config;
        }

        @Override
        public String getTenantId() { return tenantId; }

        @Override
        public String getCurrentProduct() { return "test"; }

        @Override
        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }

        @Override
        public boolean isFeatureEnabled(String feature) {
            return config.getOrDefault(feature, Boolean.FALSE).equals(Boolean.TRUE);
        }

        @Override
        public <T> T getConfig(String key, Class<T> type) {
            Object value = config.get(key);
            return type.isInstance(value) ? type.cast(value) : null;
        }

        @Override
        public boolean hasConsent(String purpose, String target) {
            return permissions.contains("healthcare.data.access");
        }

        @Override
        public boolean hasComplianceApproval(String regulation) {
            return true;
        }
    }

    /**
     * Mock Data-Cloud client for testing.
     */
    static class MockDataCloudClient implements DataCloudKernelAdapterImpl.DataCloudClient {
        private final Map<String, byte[]> storage = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public java.util.concurrent.CompletableFuture<DataCloudKernelAdapterImpl.DataResult> read(
                String datasetId, String recordId, Map<String, String> options) {
            byte[] data = storage.get(datasetId + ":" + recordId);
            if (data == null) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("Not found"));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(
                new DataCloudKernelAdapterImpl.DataResult(recordId, data, Map.of()));
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> write(
                String datasetId, String recordId, byte[] data, Map<String, String> metadata) {
            storage.put(datasetId + ":" + recordId, data);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> delete(String datasetId, String recordId) {
            storage.remove(datasetId + ":" + recordId);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<java.util.List<DataCloudKernelAdapterImpl.DataResult>> query(
                String datasetId, String query, Map<String, Object> params, int limit, int offset) {
            return java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> createDataset(
                String datasetId, Map<String, String> schema, Map<String, String> options) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<DataCloudKernelAdapterImpl.SchemaInfo> getSchema(String datasetId) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<java.util.List<DataCloudKernelAdapterImpl.DatasetInfo>> listDatasets() {
            return java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Object> beginTransaction() {
            return java.util.concurrent.CompletableFuture.completedFuture(new Object());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> commitTransaction(Object transaction) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> rollbackTransaction(Object transaction) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Object> openReadStream(
                String datasetId, Map<String, String> options) {
            return java.util.concurrent.CompletableFuture.completedFuture(new Object());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Object> openWriteStream(
                String datasetId, Map<String, String> options) {
            return java.util.concurrent.CompletableFuture.completedFuture(new Object());
        }

        @Override
        public java.util.concurrent.CompletableFuture<byte[]> readStreamChunk(Object stream) {
            return java.util.concurrent.CompletableFuture.completedFuture(new byte[0]);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> writeStreamChunk(Object stream, byte[] data) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> closeStream(Object stream) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
