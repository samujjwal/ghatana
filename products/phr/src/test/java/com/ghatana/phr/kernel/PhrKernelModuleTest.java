package com.ghatana.phr.kernel;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PhrKernelModule}.
 *
 * @doc.type test
 * @doc.purpose Unit tests for PHR kernel module with 9 services
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("PhrKernelModule Tests")
class PhrKernelModuleTest {

    private PhrKernelModule module;
    private KernelContext mockContext;

    @BeforeEach
    void setUp() {
        module = new PhrKernelModule();
        mockContext = createMockContext();
    }

    @Test
    @DisplayName("Should return correct module metadata")
    void shouldReturnCorrectModuleMetadata() {
        assertEquals("phr-core", module.getModuleId());
        assertEquals("1.0.0", module.getVersion());
    }

    @Test
    @DisplayName("Should declare correct capabilities")
    void shouldDeclareCorrectCapabilities() {
        Set<KernelCapability> capabilities = module.getCapabilities();

        // 5 PHR-owned capabilities (PhrCapabilities) + 4 kernel-core capabilities
        assertEquals(9, capabilities.size());

        // PHR-owned capabilities — declared in PhrCapabilities, not KernelCapability.Products
        // (per KERNEL_CANONICALIZATION_DECISIONS §D1)
        assertTrue(capabilities.contains(PhrCapabilities.PATIENT_RECORDS));
        assertTrue(capabilities.contains(PhrCapabilities.CONSENT_MANAGEMENT));
        assertTrue(capabilities.contains(PhrCapabilities.FHIR_INTEROP));
        assertTrue(capabilities.contains(PhrCapabilities.CLINICAL_DOCUMENTS));
        assertTrue(capabilities.contains(PhrCapabilities.MEDICATION_MANAGEMENT));

        // Core kernel capabilities reused by PHR
        assertTrue(capabilities.contains(KernelCapability.Core.USER_AUTHENTICATION));
        assertTrue(capabilities.contains(KernelCapability.Core.DATA_STORAGE));
        assertTrue(capabilities.contains(KernelCapability.Core.API_FRAMEWORK));
        assertTrue(capabilities.contains(KernelCapability.Core.WORKFLOW_ENGINE));
    }

    @Test
    @DisplayName("Should declare correct dependencies")
    void shouldDeclareCorrectDependencies() {
        Set<KernelDependency> dependencies = module.getDependencies();

        // Should have 6 dependencies
        assertEquals(6, dependencies.size());

        // Check for required dependencies
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("kernel-core")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("data-storage")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("user-authentication")));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("workflow-engine")));

        // Check for optional FHIR dependency
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("fhir-server") && d.isOptional()));
        assertTrue(dependencies.stream().anyMatch(d -> d.getDependencyId().equals("hl7-interface") && d.isOptional()));
    }

    @Test
    @DisplayName("Should initialize successfully")
    void shouldInitializeSuccessfully() {
        assertDoesNotThrow(() -> module.initialize(mockContext));

        // Second initialization should throw
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> module.initialize(mockContext));
        assertTrue(exception.getMessage().contains("already initialized"));
    }

    @Test
    @DisplayName("Should throw when ConfigResolver not available during init")
    void shouldThrowWhenConfigResolverNotAvailableDuringInit() {
        KernelContext contextWithoutConfig = createMockContextWithoutConfig();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> module.initialize(contextWithoutConfig));
        assertTrue(exception.getMessage().contains("KernelConfigResolver"));
    }

    @Test
    @DisplayName("Should start successfully after initialization")
    void shouldStartSuccessfullyAfterInitialization() {
        module.initialize(mockContext);

        Promise<Void> startPromise = module.start();
        assertDoesNotThrow(startPromise::getResult);

        // Starting again should be idempotent
        Promise<Void> secondStart = module.start();
        assertDoesNotThrow(secondStart::getResult);
    }

    @Test
    @DisplayName("Should fail to start when not initialized")
    void shouldFailToStartWhenNotInitialized() {
        Promise<Void> startPromise = module.start();

        assertTrue(startPromise.isException());
        assertTrue(startPromise.getException().getMessage().contains("not initialized"));
    }

    @Test
    @DisplayName("Should stop successfully")
    void shouldStopSuccessfully() {
        module.initialize(mockContext);
        module.start().getResult();

        Promise<Void> stopPromise = module.stop();
        assertDoesNotThrow(stopPromise::getResult);

        // Stopping again should be idempotent
        Promise<Void> secondStop = module.stop();
        assertDoesNotThrow(secondStop::getResult);
    }

    @Test
    @DisplayName("Should stop without error when not started")
    void shouldStopWithoutErrorWhenNotStarted() {
        Promise<Void> stopPromise = module.stop();
        assertDoesNotThrow(stopPromise::getResult);
    }

    @Test
    @DisplayName("Should report healthy status when initialized and started")
    void shouldReportHealthyStatusWhenInitializedAndStarted() {
        module.initialize(mockContext);
        module.start().getResult();

        HealthStatus status = module.getHealthStatus();

        assertEquals(HealthStatus.Status.HEALTHY, status.getStatus());
        assertTrue(status.getMessage().contains("operational"));
        assertEquals(4, status.getChecks().size()); // 4 implemented services
    }

    @Test
    @DisplayName("Should report unhealthy status when not initialized")
    void shouldReportUnhealthyStatusWhenNotInitialized() {
        HealthStatus status = module.getHealthStatus();

        assertEquals(HealthStatus.Status.UNHEALTHY, status.getStatus());
        assertTrue(status.getMessage().contains("not initialized"));
    }

    @Test
    @DisplayName("Should report all service health checks")
    void shouldReportAllServiceHealthChecks() {
        module.initialize(mockContext);
        module.start().getResult();

        HealthStatus status = module.getHealthStatus();

        // Verify all 4 implemented PHR services are checked (using actual service getName() values)
        assertTrue(status.getChecks().containsKey("patient-record"));
        assertTrue(status.getChecks().containsKey("consent-management"));
        assertTrue(status.getChecks().containsKey("document"));
        assertTrue(status.getChecks().containsKey("appointment"));

        // All should be healthy after start
        status.getChecks().values().forEach(check ->
            assertEquals(HealthStatus.Status.HEALTHY, check.getStatus())
        );
    }

    @Test
    @DisplayName("Should complete start promise when all services start")
    void shouldCompleteStartPromiseWhenAllServicesStart() {
        module.initialize(mockContext);

        Promise<Void> startPromise = module.start();
        Void result = startPromise.getResult();

        assertNull(result); // Promise<Void> returns null on success
    }

    @Test
    @DisplayName("Should complete stop promise when all services stop")
    void shouldCompleteStopPromiseWhenAllServicesStop() {
        module.initialize(mockContext);
        module.start().getResult();

        Promise<Void> stopPromise = module.stop();
        Void result = stopPromise.getResult();

        assertNull(result);
    }

    @Test
    @DisplayName("Should handle concurrent operations safely")
    void shouldHandleConcurrentOperationsSafely() {
        module.initialize(mockContext);

        // Multiple starts should be safe
        Promise<Void> start1 = module.start();
        Promise<Void> start2 = module.start();
        Promise<Void> start3 = module.start();

        assertDoesNotThrow(start1::getResult);
        assertDoesNotThrow(start2::getResult);
        assertDoesNotThrow(start3::getResult);

        // Health should be healthy
        HealthStatus status = module.getHealthStatus();
        assertEquals(HealthStatus.Status.HEALTHY, status.getStatus());
    }

    // ==================== Test Helpers ====================

    private KernelContext createMockContext() {
        return new KernelContext() {
            @Override public <T> T getDependency(Class<T> type) {
                if (type == KernelConfigResolver.class) {
                    return type.cast(new KernelConfigResolver() {
                        @Override public <R> R resolve(String key, Class<R> type, KernelTenantContext tenantContext) { return null; }
                        @Override public <R> R resolveWithDefault(String key, Class<R> type, R defaultValue, KernelTenantContext tenantContext) { return defaultValue; }
                        @Override public <R> java.util.Optional<R> resolveOptional(String key, Class<R> type, KernelTenantContext tenantContext) { return java.util.Optional.empty(); }
                        @Override public void addConfigProvider(KernelConfigResolver.ConfigProvider provider) {}
                        @Override public Promise<Void> reloadConfig(String tenantId) { return Promise.complete(); }
                        @Override public java.util.List<String> getAvailableKeys(KernelTenantContext tenantContext) { return java.util.List.of(); }
                    });
                }
                if (type == com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.class) {
                    return type.cast(new com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter() {
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataResult> readData(com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataReadRequest r) { return Promise.of(null); }
                        @Override public Promise<Void> writeData(com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataWriteRequest r) { return Promise.complete(); }
                        @Override public Promise<Void> deleteData(com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataDeleteRequest r) { return Promise.complete(); }
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.QueryResult> queryData(com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataQueryRequest r) { return Promise.of(new com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.QueryResult(java.util.List.of(), 0, false)); }
                        @Override public Promise<Void> createSchema(com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.SchemaCreateRequest r) { return Promise.complete(); }
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.SchemaInfo> getSchema(String datasetId) { return Promise.of(null); }
                        @Override public Promise<java.util.List<com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DatasetInfo>> listDatasets() { return Promise.of(java.util.List.of()); }
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.TransactionHandle> beginTransaction() { return Promise.of(null); }
                        @Override public Promise<Void> commitTransaction(com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.TransactionHandle h) { return Promise.complete(); }
                        @Override public Promise<Void> rollbackTransaction(com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.TransactionHandle h) { return Promise.complete(); }
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataStream> openReadStream(com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataStreamRequest r) { return Promise.of(null); }
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataStream> openWriteStream(com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataStreamRequest r) { return Promise.of(null); }
                    });
                }
                throw new IllegalStateException("Dependency not found: " + type);
            }
            @Override public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) {
                return java.util.Optional.empty();
            }
            @Override public <T> boolean hasDependency(Class<T> type) {
                return type == KernelConfigResolver.class
                    || type == com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.class;
            }
            @Override public <T> T getDependency(String name, Class<T> type) { return null; }
            @Override public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void publishEvent(E event) {}
            @Override public KernelTenantContext getTenantContext() { return null; }
            @Override public KernelTenantContext getTenantContext(String tenantId) { return null; }
            @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
            @Override public java.util.Set<KernelCapability> getAvailableCapabilities() { return java.util.Set.of(); }
            @Override public boolean hasCapability(KernelCapability capability) { return false; }
            @Override public <T> T getConfig(String key, Class<T> type) { return null; }
            @Override public <T> java.util.Optional<T> getOptionalConfig(String key, Class<T> type) { return java.util.Optional.empty(); }
            @Override public String getKernelVersion() { return "1.0.0"; }
            @Override public String getEnvironment() { return "test"; }
            @Override public java.util.concurrent.Executor getExecutor(String executorName) { return Runnable::run; }
            @Override public <T> java.util.Optional<T> getCapability(String capabilityId) { return java.util.Optional.empty(); }
            @Override public <T> void registerService(Class<T> type, T service) {}
        };
    }

    private KernelContext createMockContextWithoutConfig() {
        return new KernelContext() {
            @Override public <T> T getDependency(Class<T> type) { throw new IllegalStateException("Not found"); }
            @Override public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) { return java.util.Optional.empty(); }
            @Override public <T> boolean hasDependency(Class<T> type) { return false; }
            @Override public <T> T getDependency(String name, Class<T> type) { return null; }
            @Override public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {}
            @Override public <E> void publishEvent(E event) {}
            @Override public KernelTenantContext getTenantContext() { return null; }
            @Override public KernelTenantContext getTenantContext(String tenantId) { return null; }
            @Override public io.activej.eventloop.Eventloop getEventloop() { return io.activej.eventloop.Eventloop.create(); }
            @Override public java.util.Set<KernelCapability> getAvailableCapabilities() { return java.util.Set.of(); }
            @Override public boolean hasCapability(KernelCapability capability) { return false; }
            @Override public <T> T getConfig(String key, Class<T> type) { return null; }
            @Override public <T> java.util.Optional<T> getOptionalConfig(String key, Class<T> type) { return java.util.Optional.empty(); }
            @Override public String getKernelVersion() { return "1.0.0"; }
            @Override public String getEnvironment() { return "test"; }
            @Override public java.util.concurrent.Executor getExecutor(String executorName) { return Runnable::run; }
            @Override public <T> java.util.Optional<T> getCapability(String capabilityId) { return java.util.Optional.empty(); }
            @Override public <T> void registerService(Class<T> type, T service) {}
        };
    }
}
