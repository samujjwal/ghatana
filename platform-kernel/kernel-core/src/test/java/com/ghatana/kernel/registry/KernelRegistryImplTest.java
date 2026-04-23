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
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
    }

    @Test
    @DisplayName("Should register and retrieve module")
    void shouldRegisterAndRetrieveModule() { // GH-90000
        KernelModule module = createTestModule("test-module", "1.0.0"); // GH-90000

        registry.registerModule(module); // GH-90000

        Optional<KernelModule> retrieved = registry.getModule("test-module");
        assertTrue(retrieved.isPresent()); // GH-90000
        assertEquals("test-module", retrieved.get().getModuleId()); // GH-90000
        assertEquals("1.0.0", retrieved.get().getVersion()); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception when registering duplicate module")
    void shouldThrowExceptionWhenRegisteringDuplicateModule() { // GH-90000
        KernelModule module = createTestModule("test-module", "1.0.0"); // GH-90000
        registry.registerModule(module); // GH-90000

        KernelModule duplicate = createTestModule("test-module", "2.0.0"); // GH-90000
        IllegalStateException exception = assertThrows(IllegalStateException.class, // GH-90000
            () -> registry.registerModule(duplicate)); // GH-90000
        assertTrue(exception.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("Should unregister module")
    void shouldUnregisterModule() { // GH-90000
        KernelModule module = createTestModule("test-module", "1.0.0"); // GH-90000
        registry.registerModule(module); // GH-90000

        boolean unregistered = registry.unregisterModule("test-module");

        assertTrue(unregistered); // GH-90000
        assertFalse(registry.getModule("test-module").isPresent());
        assertFalse(registry.isModuleRegistered("test-module"));
    }

    @Test
    @DisplayName("Should return false when unregistering non-existent module")
    void shouldReturnFalseWhenUnregisteringNonExistentModule() { // GH-90000
        boolean unregistered = registry.unregisterModule("non-existent");
        assertFalse(unregistered); // GH-90000
    }

    @Test
    @DisplayName("Should get all modules")
    void shouldGetAllModules() { // GH-90000
        registry.registerModule(createTestModule("module-1", "1.0.0")); // GH-90000
        registry.registerModule(createTestModule("module-2", "1.0.0")); // GH-90000
        registry.registerModule(createTestModule("module-3", "1.0.0")); // GH-90000

        assertEquals(3, registry.getAllModules().size()); // GH-90000
    }

    @Test
    @DisplayName("Should check module registration correctly")
    void shouldCheckModuleRegistrationCorrectly() { // GH-90000
        registry.registerModule(createTestModule("registered", "1.0.0")); // GH-90000

        assertTrue(registry.isModuleRegistered("registered"));
        assertFalse(registry.isModuleRegistered("not-registered"));
    }

    @Test
    @DisplayName("Should register and retrieve capability")
    void shouldRegisterAndRetrieveCapability() { // GH-90000
        KernelCapability capability = new KernelCapability("test.cap", "Test", "", KernelCapability.CapabilityType.DATA_MANAGEMENT, java.util.Map.of()); // GH-90000

        registry.registerCapability(capability); // GH-90000

        assertTrue(registry.isCapabilityAvailable("test.cap"));
        assertEquals(1, registry.getAllCapabilities().size()); // GH-90000
    }

    @Test
    @DisplayName("Should get modules by capability")
    void shouldGetModulesByCapability() { // GH-90000
        KernelCapability cap = KernelCapability.Core.DATA_STORAGE;
        KernelModule module = createTestModuleWithCapability("storage-module", cap); // GH-90000

        registry.registerModule(module); // GH-90000
        registry.registerCapability(cap); // GH-90000

        assertEquals(1, registry.getModulesByCapability(cap).size()); // GH-90000
    }

    @Test
    @DisplayName("Should validate dependencies successfully")
    void shouldValidateDependenciesSuccessfully() { // GH-90000
        // Register dependency first
        KernelDependency dep = new KernelDependency("dep-module", "1.0.0", // GH-90000
            KernelDependency.DependencyType.MODULE, false);
        KernelModule depModule = createTestModule("dep-module", "1.0.0"); // GH-90000
        registry.registerModule(depModule); // GH-90000

        KernelModule module = createTestModuleWithDependency("test-module", dep); // GH-90000

        assertTrue(registry.validateDependencies(module)); // GH-90000
        assertTrue(registry.getDependencyValidationErrors(module).isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should detect missing dependencies")
    void shouldDetectMissingDependencies() { // GH-90000
        KernelDependency dep = new KernelDependency("missing-module", "1.0.0", // GH-90000
            KernelDependency.DependencyType.MODULE, false);
        KernelModule module = createTestModuleWithDependency("test-module", dep); // GH-90000

        assertFalse(registry.validateDependencies(module)); // GH-90000
        assertEquals(1, registry.getDependencyValidationErrors(module).size()); // GH-90000
        assertTrue(registry.getDependencyValidationErrors(module).get(0).contains("missing-module"));
    }

    @Test
    @DisplayName("Should skip validation for optional dependencies")
    void shouldSkipValidationForOptionalDependencies() { // GH-90000
        KernelDependency dep = new KernelDependency("optional-module", "1.0.0", // GH-90000
            KernelDependency.DependencyType.EXTERNAL_SERVICE, true);
        KernelModule module = createTestModuleWithDependency("test-module", dep); // GH-90000

        assertTrue(registry.validateDependencies(module)); // GH-90000
    }

    @Test
    @DisplayName("Should resolve dependencies in correct order")
    void shouldResolveDependenciesInCorrectOrder() { // GH-90000
        // Create modules with dependencies
        KernelDependency depA = new KernelDependency("module-a", "1.0.0", // GH-90000
            KernelDependency.DependencyType.MODULE, false);
        KernelDependency depB = new KernelDependency("module-b", "1.0.0", // GH-90000
            KernelDependency.DependencyType.MODULE, false);

        KernelModule moduleA = createTestModule("module-a", "1.0.0"); // GH-90000
        KernelModule moduleB = createTestModuleWithDependency("module-b", depA); // GH-90000
        KernelModule moduleC = createTestModuleWithDependencies("module-c", Set.of(depA, depB)); // GH-90000

        registry.registerModule(moduleA); // GH-90000
        registry.registerModule(moduleB); // GH-90000
        registry.registerModule(moduleC); // GH-90000

        var resolved = registry.resolveDependencies(moduleC); // GH-90000

        // Dependencies should come first
        assertEquals(2, resolved.size()); // GH-90000
        assertEquals("module-a", resolved.get(0).getModuleId()); // GH-90000
        assertEquals("module-b", resolved.get(1).getModuleId()); // GH-90000
    }

    @Test
    @DisplayName("Should get dependent modules")
    void shouldGetDependentModules() { // GH-90000
        KernelDependency dep = new KernelDependency("base-module", "1.0.0", // GH-90000
            KernelDependency.DependencyType.MODULE, false);

        KernelModule baseModule = createTestModule("base-module", "1.0.0"); // GH-90000
        KernelModule dependentModule = createTestModuleWithDependency("dependent", dep); // GH-90000

        registry.registerModule(baseModule); // GH-90000
        registry.registerModule(dependentModule); // GH-90000

        var dependents = registry.getDependentModules("base-module");

        assertEquals(1, dependents.size()); // GH-90000
        assertEquals("dependent", dependents.get(0).getModuleId()); // GH-90000
    }

    @Test
    @DisplayName("Should register and lookup typed dependencies")
    void shouldRegisterAndLookupTypedDependencies() { // GH-90000
        TestService service = new TestService(); // GH-90000

        registry.registerDependency(TestService.class, service); // GH-90000

        assertTrue(registry.hasDependency(TestService.class)); // GH-90000
        assertSame(service, registry.getDependency(TestService.class)); // GH-90000
        assertSame(service, registry.getOptionalDependency(TestService.class).orElseThrow()); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception for missing dependency")
    void shouldThrowExceptionForMissingDependency() { // GH-90000
        IllegalStateException exception = assertThrows(IllegalStateException.class, // GH-90000
            () -> registry.getDependency(TestService.class)); // GH-90000
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should return empty optional for missing dependency")
    void shouldReturnEmptyOptionalForMissingDependency() { // GH-90000
        assertTrue(registry.getOptionalDependency(TestService.class).isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should register and lookup named dependencies")
    void shouldRegisterAndLookupNamedDependencies() { // GH-90000
        TestService service = new TestService(); // GH-90000

        registry.registerDependency("my-service", service); // GH-90000

        assertSame(service, registry.getDependency("my-service", TestService.class)); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception for type mismatch in named dependency")
    void shouldThrowExceptionForTypeMismatchInNamedDependency() { // GH-90000
        TestService service = new TestService(); // GH-90000
        registry.registerDependency("my-service", service); // GH-90000

        IllegalStateException exception = assertThrows(IllegalStateException.class, // GH-90000
            () -> registry.getDependency("my-service", String.class)); // GH-90000
        assertTrue(exception.getMessage().contains("type mismatch"));
    }

    @Test
    @DisplayName("Should calculate aggregate health status")
    void shouldCalculateAggregateHealthStatus() { // GH-90000
        KernelModule healthyModule = createTestModuleWithHealth("healthy", HealthStatus.healthy()); // GH-90000
        registry.registerModule(healthyModule); // GH-90000

        HealthStatus aggregate = registry.getAggregateHealthStatus(); // GH-90000

        assertEquals(HealthStatus.Status.HEALTHY, aggregate.getStatus()); // GH-90000
        assertTrue(aggregate.getChecks().containsKey("healthy"));
    }

    @Test
    @DisplayName("Should detect degraded health in aggregate")
    void shouldDetectDegradedHealthInAggregate() { // GH-90000
        KernelModule healthyModule = createTestModuleWithHealth("healthy", HealthStatus.healthy()); // GH-90000
        KernelModule unhealthyModule = createTestModuleWithHealth("unhealthy", // GH-90000
            HealthStatus.unhealthy("error"));

        registry.registerModule(healthyModule); // GH-90000
        registry.registerModule(unhealthyModule); // GH-90000

        HealthStatus aggregate = registry.getAggregateHealthStatus(); // GH-90000

        assertEquals(HealthStatus.Status.DEGRADED, aggregate.getStatus()); // GH-90000
    }

    @Test
    @DisplayName("Should validate capability dependencies")
    void shouldValidateCapabilityDependencies() { // GH-90000
        KernelCapability cap = new KernelCapability("test.cap", "Test", "", KernelCapability.CapabilityType.DATA_MANAGEMENT, java.util.Map.of()); // GH-90000
        registry.registerCapability(cap); // GH-90000

        KernelDependency dep = new KernelDependency("test.cap", "1.0.0", // GH-90000
            KernelDependency.DependencyType.CAPABILITY, false);
        KernelModule module = createTestModuleWithDependency("test-module", dep); // GH-90000

        assertTrue(registry.validateDependencies(module)); // GH-90000
    }

    @Test
    @DisplayName("Should detect missing capability dependencies")
    void shouldDetectMissingCapabilityDependencies() { // GH-90000
        KernelDependency dep = new KernelDependency("missing.cap", "1.0.0", // GH-90000
            KernelDependency.DependencyType.CAPABILITY, false);
        KernelModule module = createTestModuleWithDependency("test-module", dep); // GH-90000

        assertFalse(registry.validateDependencies(module)); // GH-90000
        assertTrue(registry.getDependencyValidationErrors(module).get(0).contains("missing.cap"));
    }

    // ==================== Test Helpers ====================

    private KernelModule createTestModule(String id, String version) { // GH-90000
        return new KernelModule() { // GH-90000
            @Override public String getModuleId() { return id; } // GH-90000
            @Override public String getVersion() { return version; } // GH-90000
            @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } // GH-90000
            @Override public Set<KernelDependency> getDependencies() { return Set.of(); } // GH-90000
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {} // GH-90000
            @Override public Promise<Void> start() { return Promise.complete(); } // GH-90000
            @Override public Promise<Void> stop() { return Promise.complete(); } // GH-90000
            @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } // GH-90000
        };
    }

    private KernelModule createTestModuleWithCapability(String id, KernelCapability capability) { // GH-90000
        return new KernelModule() { // GH-90000
            @Override public String getModuleId() { return id; } // GH-90000
            @Override public String getVersion() { return "1.0.0"; } // GH-90000
            @Override public Set<KernelCapability> getCapabilities() { return Set.of(capability); } // GH-90000
            @Override public Set<KernelDependency> getDependencies() { return Set.of(); } // GH-90000
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {} // GH-90000
            @Override public Promise<Void> start() { return Promise.complete(); } // GH-90000
            @Override public Promise<Void> stop() { return Promise.complete(); } // GH-90000
            @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } // GH-90000
        };
    }

    private KernelModule createTestModuleWithDependency(String id, KernelDependency dependency) { // GH-90000
        return new KernelModule() { // GH-90000
            @Override public String getModuleId() { return id; } // GH-90000
            @Override public String getVersion() { return "1.0.0"; } // GH-90000
            @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } // GH-90000
            @Override public Set<KernelDependency> getDependencies() { return Set.of(dependency); } // GH-90000
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {} // GH-90000
            @Override public Promise<Void> start() { return Promise.complete(); } // GH-90000
            @Override public Promise<Void> stop() { return Promise.complete(); } // GH-90000
            @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } // GH-90000
        };
    }

    private KernelModule createTestModuleWithDependencies(String id, Set<KernelDependency> deps) { // GH-90000
        return new KernelModule() { // GH-90000
            @Override public String getModuleId() { return id; } // GH-90000
            @Override public String getVersion() { return "1.0.0"; } // GH-90000
            @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } // GH-90000
            @Override public Set<KernelDependency> getDependencies() { return deps; } // GH-90000
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {} // GH-90000
            @Override public Promise<Void> start() { return Promise.complete(); } // GH-90000
            @Override public Promise<Void> stop() { return Promise.complete(); } // GH-90000
            @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } // GH-90000
        };
    }

    private KernelModule createTestModuleWithHealth(String id, HealthStatus status) { // GH-90000
        return new KernelModule() { // GH-90000
            @Override public String getModuleId() { return id; } // GH-90000
            @Override public String getVersion() { return "1.0.0"; } // GH-90000
            @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } // GH-90000
            @Override public Set<KernelDependency> getDependencies() { return Set.of(); } // GH-90000
            @Override public void initialize(com.ghatana.kernel.context.KernelContext ctx) {} // GH-90000
            @Override public Promise<Void> start() { return Promise.complete(); } // GH-90000
            @Override public Promise<Void> stop() { return Promise.complete(); } // GH-90000
            @Override public HealthStatus getHealthStatus() { return status; } // GH-90000
        };
    }

    private static class TestService {
        public void doSomething() {} // GH-90000
    }
}
