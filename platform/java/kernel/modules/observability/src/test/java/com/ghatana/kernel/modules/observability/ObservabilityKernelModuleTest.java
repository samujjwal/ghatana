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
import com.ghatana.platform.testing.activej.EventloopTestBase;
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

        ObservabilityService service = context.getDependency(ObservabilityService.class);
        assertNotNull(service);

        Promise<Void> startPromise = module.start();
        runPromise(() -> startPromise);
        assertTrue(startPromise.isResult());

        HealthStatus status = module.getHealthStatus();
        assertTrue(status.isHealthy());
    }

    private KernelContext createTestContext() {
        return new TestKernelContext();
    }

            /**
     * Test implementation of KernelContext for unit testing.
     */
    private static class TestKernelContext implements KernelContext {
        private final java.util.concurrent.ConcurrentHashMap<Class<?>, Object> services =
                new java.util.concurrent.ConcurrentHashMap<>();

        // ---- Dependency Lookup ----

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getDependency(Class<T> type) {
            T result = (T) services.get(type);
            if (result == null) throw new IllegalStateException("No service: " + type.getName());
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> java.util.Optional<T> getOptionalDependency(Class<T> type) {
            return java.util.Optional.ofNullable((T) services.get(type));
        }

        @Override
        public <T> boolean hasDependency(Class<T> type) {
            return services.containsKey(type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getDependency(String name, Class<T> type) {
            return (T) services.get(type);
        }

        // ---- Event System ----

        @Override
        public <E> void registerEventHandler(Class<E> eventType,
                com.ghatana.kernel.event.EventHandler<E> handler) { /* no-op */ }

        @Override
        public <E> void unregisterEventHandler(Class<E> eventType,
                com.ghatana.kernel.event.EventHandler<E> handler) { /* no-op */ }

        @Override
        public <E> void publishEvent(E event) { /* no-op */ }

        // ---- Tenant & Runtime ----

        @Override
        public com.ghatana.kernel.context.KernelTenantContext getTenantContext() {
            return null;
        }

        @Override
        public com.ghatana.kernel.context.KernelTenantContext getTenantContext(String tenantId) {
            return null;
        }

        @Override
        public io.activej.eventloop.Eventloop getEventloop() {
            return io.activej.eventloop.Eventloop.create();
        }

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() {
            return java.util.Set.of();
        }

        @Override
        public boolean hasCapability(com.ghatana.kernel.descriptor.KernelCapability capability) {
            return true;
        }

        @Override
        public <T> T getConfig(String key, Class<T> type) {
            return null;
        }

        @Override
        public <T> java.util.Optional<T> getOptionalConfig(String key, Class<T> type) {
            return java.util.Optional.empty();
        }

        @Override
        public String getKernelVersion() {
            return "test-1.0.0";
        }

        @Override
        public String getEnvironment() {
            return "test";
        }

        @Override
        public java.util.concurrent.Executor getExecutor(String executorName) {
            return java.util.concurrent.ForkJoinPool.commonPool();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> java.util.Optional<T> getCapability(String capabilityId) {
            return java.util.Optional.empty();
        }

        @Override
        public <T> void registerService(Class<T> type, T service) {
            services.put(type, service);
        }

        // ---- Test Helper (not part of interface) ----

        @SuppressWarnings("unchecked")
        public <T> T getService(Class<T> serviceClass) {
            return (T) services.get(serviceClass);
        }
    }
}
