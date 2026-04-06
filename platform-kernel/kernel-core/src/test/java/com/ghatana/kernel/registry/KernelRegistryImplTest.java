package com.ghatana.kernel.registry;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link KernelRegistryImpl}.
 *
 * @doc.type test
 * @doc.purpose Unit tests for kernel registry implementation
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("KernelRegistryImpl Tests")
class KernelRegistryImplTest {

    private KernelRegistryImpl registry;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
    }

    @Test
    @DisplayName("Should register and retrieve module")
    void shouldRegisterAndRetrieveModule() {
        KernelModule module = createTestModule("test-module", "1.0.0");

        registry.registerModule(module);

        Optional<KernelModule> retrieved = registry.getModule("test-module");
        assertTrue(retrieved.isPresent());
        assertEquals("test-module", retrieved.get().getModuleId());
        assertEquals("1.0.0", retrieved.get().getVersion());
    }

    @Test
    @DisplayName("Should throw exception when registering duplicate module")
    void shouldThrowExceptionWhenRegisteringDuplicateModule() {
        KernelModule module = createTestModule("test-module", "1.0.0");
        registry.registerModule(module);

        KernelModule duplicate = createTestModule("test-module", "2.0.0");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> registry.registerModule(duplicate));
        assertTrue(exception.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("Should unregister module")
    void shouldUnregisterModule() {
        KernelModule module = createTestModule("test-module", "1.0.0");
        registry.registerModule(module);

        boolean unregistered = registry.unregisterModule("test-module");

        assertTrue(unregistered);
        assertFalse(registry.getModule("test-module").isPresent());
        assertFalse(registry.isModuleRegistered("test-module"));
    }

    @Test
    @DisplayName("Should return false when unregistering non-existent module")
    void shouldReturnFalseWhenUnregisteringNonExistentModule() {
        boolean unregistered = registry.unregisterModule("non-existent");
        assertFalse(unregistered);
    }

    @Test
    @DisplayName("Should get all modules")
    void shouldGetAllModules() {
        registry.registerModule(createTestModule("module-1", "1.0.0"));
        registry.registerModule(createTestModule("module-2", "1.0.0"));
        registry.registerModule(createTestModule("module-3", "1.0.0"));

        assertEquals(3, registry.getAllModules().size());
    }

    @Test
    @DisplayName("Should check module registration correctly")
    void shouldCheckModuleRegistrationCorrectly() {
        registry.registerModule(createTestModule("registered", "1.0.0"));

        assertTrue(registry.isModuleRegistered("registered"));
        assertFalse(registry.isModuleRegistered("not-registered"));
    }

    @Test
    @DisplayName("Should register and retrieve capability")
    void shouldRegisterAndRetrieveCapability() {
        KernelCapability capability = new KernelCapability("test.cap", "Test", "", KernelCapability.CapabilityType.DATA_MANAGEMENT, java.util.Map.of());

        registry.registerCapability(capability);

        assertTrue(registry.isCapabilityAvailable("test.cap"));
        assertEquals(1, registry.getAllCapabilities().size());
    }

    @Test
    @DisplayName("Should get modules by capability")
    void shouldGetModulesByCapability() {
        KernelCapability cap = KernelCapability.Core.DATA_STORAGE;
        KernelModule module = createTestModuleWithCapability("storage-module", cap);

        registry.registerModule(module);
        registry.registerCapability(cap);

        assertEquals(1, registry.getModulesByCapability(cap).size());
    }

    @Test
    @DisplayName("Should validate dependencies successfully")
    void shouldValidateDependenciesSuccessfully() {
        // Register dependency first
        KernelDependency dep = new KernelDependency("dep-module", "1.0.0",
            KernelDependency.DependencyType.MODULE, false);
        KernelModule depModule = createTestModule("dep-module", "1.0.0");
        registry.registerModule(depModule);

        KernelModule module = createTestModuleWithDependency("test-module", dep);

        assertTrue(registry.validateDependencies(module));
        assertTrue(registry.getDependencyValidationErrors(module).isEmpty());
    }

    @Test
    @DisplayName("Should detect missing dependencies")
    void shouldDetectMissingDependencies() {
        KernelDependency dep = new KernelDependency("missing-module", "1.0.0",
            KernelDependency.DependencyType.MODULE, false);
        KernelModule module = createTestModuleWithDependency("test-module", dep);

        assertFalse(registry.validateDependencies(module));
        assertEquals(1, registry.getDependencyValidationErrors(module).size());
        assertTrue(registry.getDependencyValidationErrors(module).get(0).contains("missing-module"));
    }

    @Test
    @DisplayName("Should skip validation for optional dependencies")
    void shouldSkipValidationForOptionalDependencies() {
        KernelDependency dep = new KernelDependency("optional-module", "1.0.0",
            KernelDependency.DependencyType.EXTERNAL_SERVICE, true);
        KernelModule module = createTestModuleWithDependency("test-module", dep);

        assertTrue(registry.validateDependencies(module));
    }

    @Test
    @DisplayName("Should resolve dependencies in correct order")
    void shouldResolveDependenciesInCorrectOrder() {
        // Create modules with dependencies
        KernelDependency depA = new KernelDependency("module-a", "1.0.0",
            KernelDependency.DependencyType.MODULE, false);
        KernelDependency depB = new KernelDependency("module-b", "1.0.0",
            KernelDependency.DependencyType.MODULE, false);

        KernelModule moduleA = createTestModule("module-a", "1.0.0");
        KernelModule moduleB = createTestModuleWithDependency("module-b", depA);
        KernelModule moduleC = createTestModuleWithDependencies("module-c", Set.of(depA, depB));

        registry.registerModule(moduleA);
        registry.registerModule(moduleB);
        registry.registerModule(moduleC);

        var resolved = registry.resolveDependencies(moduleC);

        // Dependencies should come first
        assertEquals(2, resolved.size());
        assertEquals("module-a", resolved.get(0).getModuleId());
        assertEquals("module-b", resolved.get(1).getModuleId());
    }

    @Test
    @DisplayName("Should get dependent modules")
    void shouldGetDependentModules() {
        KernelDependency dep = new KernelDependency("base-module", "1.0.0",
            KernelDependency.DependencyType.MODULE, false);

        KernelModule baseModule = createTestModule("base-module", "1.0.0");
        KernelModule dependentModule = createTestModuleWithDependency("dependent", dep);

        registry.registerModule(baseModule);
        registry.registerModule(dependentModule);

        var dependents = registry.getDependentModules("base-module");

        assertEquals(1, dependents.size());
        assertEquals("dependent", dependents.get(0).getModuleId());
    }

    @Test
    @DisplayName("Should register and lookup typed dependencies")
    void shouldRegisterAndLookupTypedDependencies() {
        TestService service = new TestService();

        registry.registerDependency(TestService.class, service);

        assertTrue(registry.hasDependency(TestService.class));
        assertSame(service, registry.getDependency(TestService.class));
        assertSame(service, registry.getOptionalDependency(TestService.class).orElseThrow());
    }

    @Test
    @DisplayName("Should throw exception for missing dependency")
    void shouldThrowExceptionForMissingDependency() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> registry.getDependency(TestService.class));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should return empty optional for missing dependency")
    void shouldReturnEmptyOptionalForMissingDependency() {
        assertTrue(registry.getOptionalDependency(TestService.class).isEmpty());
    }

    @Test
    @DisplayName("Should register and lookup named dependencies")
    void shouldRegisterAndLookupNamedDependencies() {
        TestService service = new TestService();

        registry.registerDependency("my-service", service);

        assertSame(service, registry.getDependency("my-service", TestService.class));
    }

    @Test
    @DisplayName("Should throw exception for type mismatch in named dependency")
    void shouldThrowExceptionForTypeMismatchInNamedDependency() {
        TestService service = new TestService();
        registry.registerDependency("my-service", service);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> registry.getDependency("my-service", String.class));
        assertTrue(exception.getMessage().contains("type mismatch"));
    }

    @Test
    @DisplayName("Should calculate aggregate health status")
    void shouldCalculateAggregateHealthStatus() {
        KernelModule healthyModule = createTestModuleWithHealth("healthy", HealthStatus.healthy());
        registry.registerModule(healthyModule);

        HealthStatus aggregate = registry.getAggregateHealthStatus();

        assertEquals(HealthStatus.Status.HEALTHY, aggregate.getStatus());
        assertTrue(aggregate.getChecks().containsKey("healthy"));
    }

    @Test
    @DisplayName("Should detect degraded health in aggregate")
    void shouldDetectDegradedHealthInAggregate() {
        KernelModule healthyModule = createTestModuleWithHealth("healthy", HealthStatus.healthy());
        KernelModule unhealthyModule = createTestModuleWithHealth("unhealthy",
            HealthStatus.unhealthy("error"));

        registry.registerModule(healthyModule);
        registry.registerModule(unhealthyModule);

        HealthStatus aggregate = registry.getAggregateHealthStatus();

        assertEquals(HealthStatus.Status.DEGRADED, aggregate.getStatus());
    }

    @Test
    @DisplayName("Should validate capability dependencies")
    void shouldValidateCapabilityDependencies() {
        KernelCapability cap = new KernelCapability("test.cap", "Test", "", KernelCapability.CapabilityType.DATA_MANAGEMENT, java.util.Map.of());
        registry.registerCapability(cap);

        KernelDependency dep = new KernelDependency("test.cap", "1.0.0",
            KernelDependency.DependencyType.CAPABILITY, false);
        KernelModule module = createTestModuleWithDependency("test-module", dep);

        assertTrue(registry.validateDependencies(module));
    }

    @Test
    @DisplayName("Should detect missing capability dependencies")
    void shouldDetectMissingCapabilityDependencies() {
        KernelDependency dep = new KernelDependency("missing.cap", "1.0.0",
            KernelDependency.DependencyType.CAPABILITY, false);
        KernelModule module = createTestModuleWithDependency("test-module", dep);

        assertFalse(registry.validateDependencies(module));
        assertTrue(registry.getDependencyValidationErrors(module).get(0).contains("missing.cap"));
    }

    // ==================== Test Helpers ====================

    private KernelModule createTestModule(String id, String version) {
        return new KernelModule() {
            @Override public String getModuleId() { return id; }
            @Override public String getVersion() { return version; }
            @Override public Set<KernelCapability> getCapabilities() { return Set.of(); }
            @Override public Set<KernelDependency> getDependencies() { return Set.of(); }
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {}
            @Override public Promise<Void> start() { return Promise.complete(); }
            @Override public Promise<Void> stop() { return Promise.complete(); }
            @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); }
        };
    }

    private KernelModule createTestModuleWithCapability(String id, KernelCapability capability) {
        return new KernelModule() {
            @Override public String getModuleId() { return id; }
            @Override public String getVersion() { return "1.0.0"; }
            @Override public Set<KernelCapability> getCapabilities() { return Set.of(capability); }
            @Override public Set<KernelDependency> getDependencies() { return Set.of(); }
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {}
            @Override public Promise<Void> start() { return Promise.complete(); }
            @Override public Promise<Void> stop() { return Promise.complete(); }
            @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); }
        };
    }

    private KernelModule createTestModuleWithDependency(String id, KernelDependency dependency) {
        return new KernelModule() {
            @Override public String getModuleId() { return id; }
            @Override public String getVersion() { return "1.0.0"; }
            @Override public Set<KernelCapability> getCapabilities() { return Set.of(); }
            @Override public Set<KernelDependency> getDependencies() { return Set.of(dependency); }
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {}
            @Override public Promise<Void> start() { return Promise.complete(); }
            @Override public Promise<Void> stop() { return Promise.complete(); }
            @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); }
        };
    }

    private KernelModule createTestModuleWithDependencies(String id, Set<KernelDependency> deps) {
        return new KernelModule() {
            @Override public String getModuleId() { return id; }
            @Override public String getVersion() { return "1.0.0"; }
            @Override public Set<KernelCapability> getCapabilities() { return Set.of(); }
            @Override public Set<KernelDependency> getDependencies() { return deps; }
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {}
            @Override public Promise<Void> start() { return Promise.complete(); }
            @Override public Promise<Void> stop() { return Promise.complete(); }
            @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); }
        };
    }

    private KernelModule createTestModuleWithHealth(String id, HealthStatus status) {
        return new KernelModule() {
            @Override public String getModuleId() { return id; }
            @Override public String getVersion() { return "1.0.0"; }
            @Override public Set<KernelCapability> getCapabilities() { return Set.of(); }
            @Override public Set<KernelDependency> getDependencies() { return Set.of(); }
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {}
            @Override public Promise<Void> start() { return Promise.complete(); }
            @Override public Promise<Void> stop() { return Promise.complete(); }
            @Override public HealthStatus getHealthStatus() { return status; }
        };
    }

    private static class TestService {
        public void doSomething() {}
    }
}
