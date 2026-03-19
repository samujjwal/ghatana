package com.ghatana.phr.kernel;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.KernelContext;
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

        // Should have 10 capabilities (5 PHR-specific + 5 shared)
        assertEquals(10, capabilities.size());

        // PHR-specific capabilities
        assertTrue(capabilities.contains(KernelCapability.Products.PATIENT_RECORDS));
        assertTrue(capabilities.contains(KernelCapability.Products.CONSENT_MANAGEMENT));
        assertTrue(capabilities.contains(KernelCapability.Products.FHIR_INTEROP));
        assertTrue(capabilities.contains(KernelCapability.Products.CLINICAL_DOCUMENTS));
        assertTrue(capabilities.contains(KernelCapability.Products.MEDICATION_MANAGEMENT));

        // Shared capabilities
        assertTrue(capabilities.contains(KernelCapability.Products.USER_AUTHENTICATION));
        assertTrue(capabilities.contains(KernelCapability.Products.DATA_STORAGE));
        assertTrue(capabilities.contains(KernelCapability.Products.API_FRAMEWORK));
        assertTrue(capabilities.contains(KernelCapability.Products.WORKFLOW_ENGINE));
        assertTrue(capabilities.contains(KernelCapability.Products.NOTIFICATION_SERVICE));
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

        Exception exception = assertThrows(Exception.class, startPromise::getResult);
        assertTrue(exception.getMessage().contains("not initialized"));
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
        assertEquals(10, status.getChecks().size()); // 9 services + module
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

        // Verify all 9 PHR services are checked
        assertTrue(status.getChecks().containsKey("patient"));
        assertTrue(status.getChecks().containsKey("consent"));
        assertTrue(status.getChecks().containsKey("document"));
        assertTrue(status.getChecks().containsKey("appointment"));
        assertTrue(status.getChecks().containsKey("medication"));
        assertTrue(status.getChecks().containsKey("billing"));
        assertTrue(status.getChecks().containsKey("fhir"));
        assertTrue(status.getChecks().containsKey("imaging"));
        assertTrue(status.getChecks().containsKey("referral"));

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
                        @Override public <R> java.util.Optional<R> resolveOptional(String key, Class<R> type, KernelTenantContext tenantContext) { return java.util.Optional.empty(); }
                        @Override public java.util.List<String> getConfigSources() { return java.util.List.of(); }
                        @Override public void reload() {}
                    });
                }
                throw new IllegalStateException("Dependency not found: " + type);
            }
            @Override public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) {
                return java.util.Optional.empty();
            }
            @Override public <T> boolean hasDependency(Class<T> type) {
                return type == KernelConfigResolver.class;
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
        };
    }
}
