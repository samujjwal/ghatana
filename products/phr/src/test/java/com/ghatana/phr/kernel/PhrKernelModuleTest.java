package com.ghatana.phr.kernel;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.phr.api.FhirController;
import com.ghatana.phr.api.NepalHieController;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.phr.hie.NepalHieIntegrationService;
import com.ghatana.phr.hl7.Hl7LabResultIntegrationService;
import com.ghatana.phr.kernel.service.DurablePhrNotificationSender;
import com.ghatana.phr.kernel.service.PhrNotificationSender;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.ConcurrentHashMap;
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
class PhrKernelModuleTest extends EventloopTestBase {

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
        assertNotNull(module.getServiceCatalog());
        assertNotNull(module.getServiceCatalog().clinical().patientRecords());
        assertNotNull(module.getServiceCatalog().clinical().clinicalDecisionSupport());
        assertNotNull(module.getServiceCatalog().administrative().appointments());
        assertNotNull(module.getServiceCatalog().patient().consent());
        assertNotNull(module.getServiceCatalog().emergency().emergencyAccess());
        assertNotNull(module.getServiceCatalog().emergency().emergencyReview());
        assertTrue(mockContext.getOptionalDependency(PhrNotificationSender.class).isPresent());
        assertTrue(mockContext.getOptionalDependency(PhrFhirR4Server.class).isPresent());
        assertTrue(mockContext.getOptionalDependency(FhirController.class).isPresent());
        assertTrue(mockContext.getOptionalDependency(NepalHieIntegrationService.class).isPresent());
        assertTrue(mockContext.getOptionalDependency(NepalHieController.class).isPresent());
        assertTrue(mockContext.getOptionalDependency(Hl7LabResultIntegrationService.class).isPresent());

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

        assertDoesNotThrow(() -> runPromise(module::start));

        // Starting again should be idempotent
        assertDoesNotThrow(() -> runPromise(module::start));
    }

    @Test
    @DisplayName("Should fail to start when not initialized")
    void shouldFailToStartWhenNotInitialized() {
        assertThrows(Exception.class, () -> runPromise(module::start));
    }

    @Test
    @DisplayName("Should stop successfully")
    void shouldStopSuccessfully() {
        module.initialize(mockContext);
        runPromise(module::start);

        assertDoesNotThrow(() -> runPromise(module::stop));

        // Stopping again should be idempotent
        assertDoesNotThrow(() -> runPromise(module::stop));
    }

    @Test
    @DisplayName("Should stop without error when not started")
    void shouldStopWithoutErrorWhenNotStarted() {
        assertDoesNotThrow(() -> runPromise(module::stop));
    }

    @Test
    @DisplayName("Should report healthy status when initialized and started")
    void shouldReportHealthyStatusWhenInitializedAndStarted() {
        module.initialize(mockContext);
        runPromise(module::start);

        HealthStatus status = module.getHealthStatus();

        assertEquals(HealthStatus.Status.HEALTHY, status.getStatus());
        assertTrue(status.getMessage().contains("operational"));
        assertEquals(21, status.getChecks().size());
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
        runPromise(module::start);

        HealthStatus status = module.getHealthStatus();

        // Verify all 4 implemented PHR services are checked (using actual service getName() values)
        assertTrue(status.getChecks().containsKey("patient-record"));
        assertTrue(status.getChecks().containsKey("consent-management"));
        assertTrue(status.getChecks().containsKey("document"));
        assertTrue(status.getChecks().containsKey("appointment"));
        assertTrue(status.getChecks().containsKey("clinical-decision-support"));
        assertTrue(status.getChecks().containsKey("phr-notification-outbox"));
        assertTrue(status.getChecks().containsKey("phr-notification-outbox-dispatcher"));
        assertTrue(status.getChecks().containsKey("phr-fhir-r4-server"));
        assertTrue(status.getChecks().containsKey("phr-nepal-hie-integration"));
        assertTrue(status.getChecks().containsKey("phr-hl7-lab-integration"));

        // All should be healthy after start
        status.getChecks().values().forEach(check ->
            assertEquals(HealthStatus.Status.HEALTHY, check.getStatus())
        );
    }

    @Test
    @DisplayName("Should register durable PHR notification sender")
    void shouldRegisterDurablePhrNotificationSender() {
        module.initialize(mockContext);

        PhrNotificationSender sender = mockContext.getOptionalDependency(PhrNotificationSender.class).orElseThrow();

        assertInstanceOf(DurablePhrNotificationSender.class, sender);
    }

    @Test
    @DisplayName("Should complete start promise when all services start")
    void shouldCompleteStartPromiseWhenAllServicesStart() {
        module.initialize(mockContext);

        Void result = runPromise(module::start);

        assertNull(result); // Promise<Void> returns null on success
    }

    @Test
    @DisplayName("Should complete stop promise when all services stop")
    void shouldCompleteStopPromiseWhenAllServicesStop() {
        module.initialize(mockContext);
        runPromise(module::start);

        Void result = runPromise(module::stop);

        assertNull(result);
    }

    @Test
    @DisplayName("Should handle concurrent operations safely")
    void shouldHandleConcurrentOperationsSafely() {
        module.initialize(mockContext);

        // Multiple starts should be safe
        runPromise(module::start);
        runPromise(module::start);
        runPromise(module::start);

        // Health should be healthy
        HealthStatus status = module.getHealthStatus();
        assertEquals(HealthStatus.Status.HEALTHY, status.getStatus());
    }

    // ==================== Test Helpers ====================

    private KernelContext createMockContext() {
        ConcurrentHashMap<Class<?>, Object> registeredServices = new ConcurrentHashMap<>();
        return new KernelContext() {
            @Override public <T> T getDependency(Class<T> type) {
                Object registered = registeredServices.get(type);
                if (registered != null) {
                    return type.cast(registered);
                }
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
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.DataResult> readData(com.ghatana.kernel.adapter.datacloud.DataReadRequest r) { return Promise.of(null); }
                        @Override public Promise<Void> writeData(com.ghatana.kernel.adapter.datacloud.DataWriteRequest r) { return Promise.complete(); }
                        @Override public Promise<Void> deleteData(com.ghatana.kernel.adapter.datacloud.DataDeleteRequest r) { return Promise.complete(); }
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.QueryResult> queryData(com.ghatana.kernel.adapter.datacloud.DataQueryRequest r) { return Promise.of(new com.ghatana.kernel.adapter.datacloud.QueryResult(java.util.List.of(), 0, false)); }
                        @Override public Promise<Void> createSchema(com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest r) { return Promise.complete(); }
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.SchemaInfo> getSchema(String datasetId) { return Promise.of(null); }
                        @Override public Promise<java.util.List<com.ghatana.kernel.adapter.datacloud.DatasetInfo>> listDatasets() { return Promise.of(java.util.List.of()); }
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.TransactionHandle> beginTransaction() { return Promise.of(null); }
                        @Override public Promise<Void> commitTransaction(com.ghatana.kernel.adapter.datacloud.TransactionHandle h) { return Promise.complete(); }
                        @Override public Promise<Void> rollbackTransaction(com.ghatana.kernel.adapter.datacloud.TransactionHandle h) { return Promise.complete(); }
                        @Override public Promise<com.ghatana.kernel.adapter.datacloud.DataStream> openStream(com.ghatana.kernel.adapter.datacloud.DataStreamRequest r) { return Promise.of(null); }
                    });
                }
                throw new IllegalStateException("Dependency not found: " + type);
            }
            @Override public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) {
                Object registered = registeredServices.get(type);
                if (registered != null) {
                    return java.util.Optional.of(type.cast(registered));
                }
                return java.util.Optional.empty();
            }
            @Override public <T> boolean hasDependency(Class<T> type) {
                return registeredServices.containsKey(type)
                    || type == KernelConfigResolver.class
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
            @Override public <T> void registerService(Class<T> type, T service) { registeredServices.put(type, service); }
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
