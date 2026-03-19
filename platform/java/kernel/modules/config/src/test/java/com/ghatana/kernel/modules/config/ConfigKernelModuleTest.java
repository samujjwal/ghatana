/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.config;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.modules.config.service.ConfigService;
import com.ghatana.kernel.test.EventloopTestBase;
import com.ghatana.platform.config.ConfigManager;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ConfigKernelModule.
 *
 * <p>Validates kernel purity, generic capabilities, and production-grade
 * functionality of the configuration module.</p>
 *
 * @doc.type test
 * @doc.purpose Validate ConfigKernelModule kernel compliance and functionality
 * @doc.layer test
 * @doc.pattern UnitTest
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("ConfigKernelModule Tests")
public class ConfigKernelModuleTest extends EventloopTestBase {

    private ConfigKernelModule module;
    private KernelContext context;

    @BeforeEach
    void setUp() {
        module = new ConfigKernelModule();
        context = createTestContext();
    }

    // ==================== Kernel Compliance Tests ====================

    @Test
    @DisplayName("Should have generic module ID")
    void shouldHaveGenericModuleId() {
        String moduleId = module.getModuleId();

        assertNotNull(moduleId);
        assertEquals("config", moduleId);
        assertFalse(moduleId.contains("finance"), "Module ID must not contain product-specific terms");
        assertFalse(moduleId.contains("trading"), "Module ID must not contain product-specific terms");
    }

    @Test
    @DisplayName("Should have generic capabilities only")
    void shouldHaveGenericCapabilitiesOnly() {
        Set<KernelCapability> capabilities = module.getCapabilities();

        assertNotNull(capabilities);
        assertFalse(capabilities.isEmpty());

        for (KernelCapability capability : capabilities) {
            String capabilityId = capability.getCapabilityId();
            assertFalse(capabilityId.contains("finance"), "Capability must not be finance-specific: " + capabilityId);
            assertFalse(capabilityId.contains("trade"), "Capability must not be trade-specific: " + capabilityId);

            // Verify capability is from Core class (generic)
            assertTrue(
                capabilityId.equals("config.management"),
                "Capability must be generic: " + capabilityId
            );
        }
    }

    @Test
    @DisplayName("Should have no product-specific dependencies")
    void shouldHaveNoProductSpecificDependencies() {
        Set<KernelDependency> dependencies = module.getDependencies();

        assertNotNull(dependencies);

        for (KernelDependency dependency : dependencies) {
            String capabilityId = dependency.getRequiredCapabilityId();
            assertFalse(capabilityId.contains("finance"), "Dependency must not be finance-specific");
            assertFalse(capabilityId.contains("trading"), "Dependency must not be trade-specific");
        }
    }

    @Test
    @DisplayName("Should return correct version")
    void shouldReturnCorrectVersion() {
        String version = module.getVersion();

        assertNotNull(version);
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"), "Version must follow semver format");
    }

    // ==================== Lifecycle Tests ====================

    @Test
    @DisplayName("Should initialize successfully")
    void shouldInitializeSuccessfully() {
        assertDoesNotThrow(() -> module.initialize(context));

        ConfigService service = context.getService(ConfigService.class);
        assertNotNull(service, "Config service should be registered");
    }

    @Test
    @DisplayName("Should start and stop successfully")
    void shouldStartAndStopSuccessfully() {
        module.initialize(context);

        Promise<Void> startPromise = module.start();
        await(startPromise);
        assertTrue(startPromise.isResult(), "Start should complete successfully");

        Promise<Void> stopPromise = module.stop();
        await(stopPromise);
        assertTrue(stopPromise.isResult(), "Stop should complete successfully");
    }

    @Test
    @DisplayName("Should report healthy status when running")
    void shouldReportHealthyStatusWhenRunning() {
        module.initialize(context);
        await(module.start());

        HealthStatus status = module.getHealthStatus();

        assertNotNull(status);
        assertTrue(status.isHealthy(), "Module should be healthy when running");
    }

    @Test
    @DisplayName("Should report unhealthy status when not initialized")
    void shouldReportUnhealthyStatusWhenNotInitialized() {
        HealthStatus status = module.getHealthStatus();

        assertNotNull(status);
        assertFalse(status.isHealthy(), "Module should be unhealthy when not initialized");
    }

    // ==================== Service Integration Tests ====================

    @Test
    @DisplayName("Should register config service with context")
    void shouldRegisterConfigServiceWithContext() {
        module.initialize(context);

        ConfigService service = context.getService(ConfigService.class);
        assertNotNull(service);
        assertTrue(service instanceof ConfigService);
    }

    @Test
    @DisplayName("Should provide functional config service after start")
    void shouldProvideFunctionalConfigServiceAfterStart() {
        module.initialize(context);
        await(module.start());

        ConfigService service = context.getService(ConfigService.class);
        assertTrue(service.isHealthy(), "Service should be healthy after start");
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Should handle multiple initialize calls gracefully")
    void shouldHandleMultipleInitializeCallsGracefully() {
        module.initialize(context);
        assertDoesNotThrow(() -> module.initialize(context));
    }

    @Test
    @DisplayName("Should handle stop without start gracefully")
    void shouldHandleStopWithoutStartGracefully() {
        module.initialize(context);
        assertDoesNotThrow(() -> await(module.stop()));
    }

    // ==================== Private Helper Methods ====================

    private KernelContext createTestContext() {
        return new TestKernelContext();
    }

    /**
     * Test implementation of KernelContext for unit testing.
     */
    private static class TestKernelContext implements KernelContext {
        private final Map<Class<?>, Object> services = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String getKernelId() {
            return "test-kernel";
        }

        @Override
        public String getTenantId() {
            return "test-tenant";
        }

        @Override
        public <T> void registerService(Class<T> serviceClass, T service) {
            services.put(serviceClass, service);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getService(Class<T> serviceClass) {
            return (T) services.get(serviceClass);
        }

        @Override
        public ConfigManager getConfig() {
            return ConfigManager.createDefault("test");
        }

        @Override
        public java.util.concurrent.Executor getExecutor(String name) {
            return java.util.concurrent.Executors.newSingleThreadExecutor();
        }

        @Override
        public boolean hasCapability(String capabilityId) {
            return true;
        }

        @Override
        public Set<KernelCapability> getAvailableCapabilities() {
            return Set.of();
        }
    }
}
