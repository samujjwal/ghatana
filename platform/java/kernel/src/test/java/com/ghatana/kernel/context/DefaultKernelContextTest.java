package com.ghatana.kernel.context;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.event.EventHandler;
import com.ghatana.kernel.registry.KernelRegistry;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultKernelContext}.
 *
 * @doc.type test
 * @doc.purpose Unit tests for kernel context implementation
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("DefaultKernelContext Tests")
class DefaultKernelContextTest {

    private DefaultKernelContext context;
    private KernelRegistry mockRegistry;
    private KernelConfigResolver mockConfigResolver;
    private Eventloop eventloop;

    @BeforeEach
    void setUp() {
        mockRegistry = createMockRegistry();
        mockConfigResolver = createMockConfigResolver();
        eventloop = Eventloop.create();

        context = new DefaultKernelContext(mockRegistry, mockConfigResolver, eventloop, "1.0.0", "test");
    }

    @Test
    @DisplayName("Should return correct kernel metadata")
    void shouldReturnCorrectKernelMetadata() {
        assertEquals("1.0.0", context.getKernelVersion());
        assertEquals("test", context.getEnvironment());
    }

    @Test
    @DisplayName("Should return eventloop instance")
    void shouldReturnEventloopInstance() {
        assertSame(eventloop, context.getEventloop());
    }

    @Test
    @DisplayName("Should register and retrieve typed dependencies")
    void shouldRegisterAndRetrieveTypedDependencies() {
        TestService service = new TestService();

        context.registerDependency(TestService.class, service);

        assertTrue(context.hasDependency(TestService.class));
        assertSame(service, context.getDependency(TestService.class));
        assertSame(service, context.getOptionalDependency(TestService.class).orElseThrow());
    }

    @Test
    @DisplayName("Should throw exception for missing typed dependency")
    void shouldThrowExceptionForMissingTypedDependency() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> context.getDependency(TestService.class));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should return empty optional for missing optional dependency")
    void shouldReturnEmptyOptionalForMissingOptionalDependency() {
        Optional<TestService> optional = context.getOptionalDependency(TestService.class);
        assertTrue(optional.isEmpty());
    }

    @Test
    @DisplayName("Should register and retrieve named dependencies")
    void shouldRegisterAndRetrieveNamedDependencies() {
        TestService service = new TestService();

        context.registerDependency("my-service", service);

        assertSame(service, context.getDependency("my-service", TestService.class));
    }

    @Test
    @DisplayName("Should throw exception for missing named dependency")
    void shouldThrowExceptionForMissingNamedDependency() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> context.getDependency("missing-service", TestService.class));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should throw exception for type mismatch in named dependency")
    void shouldThrowExceptionForTypeMismatchInNamedDependency() {
        TestService service = new TestService();
        context.registerDependency("my-service", service);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> context.getDependency("my-service", String.class));
        assertTrue(exception.getMessage().contains("type mismatch"));
    }

    @Test
    @DisplayName("Should register and invoke event handlers")
    void shouldRegisterAndInvokeEventHandlers() {
        AtomicBoolean handled = new AtomicBoolean(false);
        AtomicReference<TestEvent> capturedEvent = new AtomicReference<>();

        EventHandler<TestEvent> handler = event -> {
            handled.set(true);
            capturedEvent.set(event);
        };

        context.registerEventHandler(TestEvent.class, handler);

        TestEvent event = new TestEvent("test-data");
        context.publishEvent(event);

        assertTrue(handled.get());
        assertEquals(event, capturedEvent.get());
    }

    @Test
    @DisplayName("Should unregister event handlers")
    void shouldUnregisterEventHandlers() {
        AtomicBoolean handled = new AtomicBoolean(false);

        EventHandler<TestEvent> handler = event -> {
            handled.set(true);
        };

        context.registerEventHandler(TestEvent.class, handler);
        context.unregisterEventHandler(TestEvent.class, handler);

        context.publishEvent(new TestEvent("test"));

        assertFalse(handled.get());
    }

    @Test
    @DisplayName("Should handle multiple event handlers")
    void shouldHandleMultipleEventHandlers() {
        AtomicBoolean handler1Called = new AtomicBoolean(false);
        AtomicBoolean handler2Called = new AtomicBoolean(false);

        context.registerEventHandler(TestEvent.class, event -> {
            handler1Called.set(true);
        });

        context.registerEventHandler(TestEvent.class, event -> {
            handler2Called.set(true);
        });

        context.publishEvent(new TestEvent("test"));

        assertTrue(handler1Called.get());
        assertTrue(handler2Called.get());
    }

    @Test
    @DisplayName("Should continue with other handlers when one throws")
    void shouldContinueWithOtherHandlersWhenOneThrows() {
        AtomicBoolean goodHandlerCalled = new AtomicBoolean(false);

        context.registerEventHandler(TestEvent.class, event -> {
            throw new RuntimeException("Handler error");
        });

        context.registerEventHandler(TestEvent.class, event -> {
            goodHandlerCalled.set(true);
        });

        // Should not throw
        assertDoesNotThrow(() -> context.publishEvent(new TestEvent("test")));
        assertTrue(goodHandlerCalled.get());
    }

    @Test
    @DisplayName("Should handle null events gracefully")
    void shouldHandleNullEventsGracefully() {
        assertDoesNotThrow(() -> context.publishEvent(null));
    }

    @Test
    @DisplayName("Should return available capabilities from registry")
    void shouldReturnAvailableCapabilitiesFromRegistry() {
        Set<KernelCapability> capabilities = context.getAvailableCapabilities();
        assertNotNull(capabilities);
        assertTrue(capabilities.contains(KernelCapability.Core.DATA_STORAGE));
    }

    @Test
    @DisplayName("Should check capability availability")
    void shouldCheckCapabilityAvailability() {
        assertTrue(context.hasCapability(KernelCapability.Core.DATA_STORAGE));
        assertFalse(context.hasCapability(new KernelCapability("nonexistent", "Non-existent", "Test", KernelCapability.CapabilityType.DATA_MANAGEMENT, java.util.Map.of())));
    }

    @Test
    @DisplayName("Should get config from resolver")
    void shouldGetConfigFromResolver() {
        String configValue = context.getConfig("test.key", String.class);
        assertEquals("test-value", configValue);
    }

    @Test
    @DisplayName("Should get optional config")
    void shouldGetOptionalConfig() {
        Optional<String> configValue = context.getOptionalConfig("optional.key", String.class);
        assertTrue(configValue.isPresent());
        assertEquals("optional-value", configValue.get());
    }

    @Test
    @DisplayName("Should return default tenant context")
    void shouldReturnDefaultTenantContext() {
        KernelTenantContext tenantContext = context.getTenantContext();

        assertNotNull(tenantContext);
        assertEquals("system", tenantContext.getTenantId());
        assertEquals(KernelTenantContext.TenantType.SYSTEM, tenantContext.getTenantType());
    }

    @Test
    @DisplayName("Should register and retrieve tenant context")
    void shouldRegisterAndRetrieveTenantContext() {
        KernelTenantContext customTenant = new KernelTenantContext(
            "tenant-123",
            KernelTenantContext.TenantType.ENTERPRISE,
            java.util.Map.of(),
            java.util.Set.of(),
            null,
            null
        );

        context.registerTenantContext("tenant-123", customTenant);

        KernelTenantContext retrieved = context.getTenantContext("tenant-123");
        assertEquals("tenant-123", retrieved.getTenantId());
        assertEquals(KernelTenantContext.TenantType.ENTERPRISE, retrieved.getTenantType());
    }

    @Test
    @DisplayName("Should throw exception for non-existent tenant")
    void shouldThrowExceptionForNonExistentTenant() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> context.getTenantContext("non-existent"));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should return registry")
    void shouldReturnRegistry() {
        assertSame(mockRegistry, context.getRegistry());
    }

    @Test
    @DisplayName("Should handle unknown event types gracefully")
    void shouldHandleUnknownEventTypesGracefully() {
        // Publish event with no handlers registered
        assertDoesNotThrow(() -> context.publishEvent(new UnhandledEvent()));
    }

    @Test
    @DisplayName("Should maintain separate handler lists for different event types")
    void shouldMaintainSeparateHandlerListsForDifferentEventTypes() {
        AtomicBoolean testHandlerCalled = new AtomicBoolean(false);
        AtomicBoolean otherHandlerCalled = new AtomicBoolean(false);

        context.registerEventHandler(TestEvent.class, event -> {
            testHandlerCalled.set(true);
        });

        context.registerEventHandler(OtherEvent.class, event -> {
            otherHandlerCalled.set(true);
        });

        context.publishEvent(new TestEvent("test"));

        assertTrue(testHandlerCalled.get());
        assertFalse(otherHandlerCalled.get());
    }

    // ==================== Test Helpers ====================

    private KernelRegistry createMockRegistry() {
        return new KernelRegistry() {
            @Override public void registerModule(com.ghatana.kernel.module.KernelModule module) {}
            @Override public void registerPlugin(com.ghatana.kernel.plugin.KernelPlugin plugin) {}
            @Override public void registerCapability(KernelCapability capability) {}
            @Override public boolean unregisterModule(String moduleId) { return false; }
            @Override public java.util.Optional<com.ghatana.kernel.module.KernelModule> getModule(String moduleId) { return java.util.Optional.empty(); }
            @Override public java.util.Optional<com.ghatana.kernel.plugin.KernelPlugin> getPlugin(String pluginId) { return java.util.Optional.empty(); }
            @Override public java.util.List<com.ghatana.kernel.module.KernelModule> getAllModules() { return java.util.List.of(); }
            @Override public java.util.List<com.ghatana.kernel.plugin.KernelPlugin> getAllPlugins() { return java.util.List.of(); }
            @Override public java.util.List<KernelCapability> getAllCapabilities() {
                return java.util.List.of(KernelCapability.Core.DATA_STORAGE);
            }
            @Override public java.util.List<com.ghatana.kernel.plugin.KernelPlugin> getPluginsByCapability(KernelCapability capability) { return java.util.List.of(); }
            @Override public java.util.List<com.ghatana.kernel.module.KernelModule> getModulesByCapability(KernelCapability capability) { return java.util.List.of(); }
            @Override public java.util.List<com.ghatana.kernel.module.KernelModule> getDependentModules(String moduleId) { return java.util.List.of(); }
            @Override public java.util.List<com.ghatana.kernel.module.KernelModule> resolveDependencies(com.ghatana.kernel.module.KernelModule module) { return java.util.List.of(); }
            @Override public boolean validateDependencies(com.ghatana.kernel.module.KernelModule module) { return true; }
            @Override public java.util.List<String> getDependencyValidationErrors(com.ghatana.kernel.module.KernelModule module) { return java.util.List.of(); }
            @Override public io.activej.promise.Promise<Void> startAllModules() { return io.activej.promise.Promise.complete(); }
            @Override public io.activej.promise.Promise<Void> stopAllModules() { return io.activej.promise.Promise.complete(); }
            @Override public boolean isModuleRegistered(String moduleId) { return false; }
            @Override public boolean isCapabilityAvailable(String capabilityId) { return false; }
        };
    }

    private KernelConfigResolver createMockConfigResolver() {
        return new KernelConfigResolver() {
            @Override public <T> T resolve(String key, Class<T> type, KernelTenantContext tenantContext) {
                if ("test.key".equals(key)) {
                    return type.cast("test-value");
                }
                throw new IllegalArgumentException("Config key not found: " + key);
            }
            @Override public <T> T resolveWithDefault(String key, Class<T> type, T defaultValue, KernelTenantContext tenantContext) {
                java.util.Optional<T> opt = resolveOptional(key, type, tenantContext);
                return opt.orElse(defaultValue);
            }
            @Override public <T> java.util.Optional<T> resolveOptional(String key, Class<T> type, KernelTenantContext tenantContext) {
                if ("optional.key".equals(key)) {
                    return java.util.Optional.of(type.cast("optional-value"));
                }
                if ("test.key".equals(key)) {
                    return java.util.Optional.of(type.cast("test-value"));
                }
                return java.util.Optional.empty();
            }
            @Override public void addConfigProvider(ConfigProvider provider) {}
            @Override public io.activej.promise.Promise<Void> reloadConfig(String tenantId) { return io.activej.promise.Promise.complete(); }
            @Override public java.util.List<String> getAvailableKeys(KernelTenantContext ctx) { return java.util.List.of(); }
        };
    }

    private static class TestService {
        public void doSomething() {}
    }

    private static class TestEvent {
        private final String data;
        public TestEvent(String data) { this.data = data; }
        public String getData() { return data; }
    }

    private static class OtherEvent {
    }

    private static class UnhandledEvent {
    }
}
