/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.observability;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.modules.observability.service.ObservabilityService;
import com.ghatana.kernel.test.EventloopTestBase;
import com.ghatana.platform.config.ConfigManager;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ObservabilityKernelModule.
 *
 * @doc.type test
 * @doc.purpose Validate ObservabilityKernelModule kernel compliance
 * @doc.layer test
 * @doc.pattern UnitTest
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("ObservabilityKernelModule Tests")
public class ObservabilityKernelModuleTest extends EventloopTestBase {

    private ObservabilityKernelModule module;
    private KernelContext context;

    @BeforeEach
    void setUp() {
        module = new ObservabilityKernelModule();
        context = createTestContext();
    }

    @Test
    @DisplayName("Should have generic module ID")
    void shouldHaveGenericModuleId() {
        assertEquals("observability", module.getModuleId());
        assertFalse(module.getModuleId().contains("finance"));
    }

    @Test
    @DisplayName("Should have generic capabilities only")
    void shouldHaveGenericCapabilitiesOnly() {
        Set<KernelCapability> capabilities = module.getCapabilities();
        assertFalse(capabilities.isEmpty());

        for (KernelCapability cap : capabilities) {
            assertFalse(cap.getCapabilityId().contains("finance"));
        }
    }

    @Test
    @DisplayName("Should initialize and start successfully")
    void shouldInitializeAndStartSuccessfully() {
        assertDoesNotThrow(() -> module.initialize(context));

        ObservabilityService service = context.getService(ObservabilityService.class);
        assertNotNull(service);

        Promise<Void> startPromise = module.start();
        await(startPromise);
        assertTrue(startPromise.isResult());

        HealthStatus status = module.getHealthStatus();
        assertTrue(status.isHealthy());
    }

    private KernelContext createTestContext() {
        return new TestKernelContext();
    }

    private static class TestKernelContext implements KernelContext {
        private final Map<Class<?>, Object> services = new java.util.concurrent.ConcurrentHashMap<>();

        @Override public String getKernelId() { return "test-kernel"; }
        @Override public String getTenantId() { return "test-tenant"; }
        @Override public <T> void registerService(Class<T> sc, T s) { services.put(sc, s); }
        @Override @SuppressWarnings("unchecked") public <T> T getService(Class<T> sc) { return (T) services.get(sc); }
        @Override public ConfigManager getConfig() { return ConfigManager.createDefault("test"); }
        @Override public java.util.concurrent.Executor getExecutor(String n) { return java.util.concurrent.Executors.newSingleThreadExecutor(); }
        @Override public boolean hasCapability(String c) { return true; }
        @Override public Set<KernelCapability> getAvailableCapabilities() { return Set.of(); }
    }
}
