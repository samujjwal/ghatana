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
@DisplayName("DefaultKernelContext Tests [GH-90000]")
class DefaultKernelContextTest {

    private DefaultKernelContext context;
    private KernelRegistry mockRegistry;
    private KernelConfigResolver mockConfigResolver;
    private Eventloop eventloop;

    @BeforeEach
    void setUp() { // GH-90000
        mockRegistry = createMockRegistry(); // GH-90000
        mockConfigResolver = createMockConfigResolver(); // GH-90000
        eventloop = Eventloop.create(); // GH-90000

        context = new DefaultKernelContext(mockRegistry, mockConfigResolver, eventloop, "1.0.0", "test"); // GH-90000
    }

    @Test
    @DisplayName("Should return correct kernel metadata [GH-90000]")
    void shouldReturnCorrectKernelMetadata() { // GH-90000
        assertEquals("1.0.0", context.getKernelVersion()); // GH-90000
        assertEquals("test", context.getEnvironment()); // GH-90000
    }

    @Test
    @DisplayName("Should return eventloop instance [GH-90000]")
    void shouldReturnEventloopInstance() { // GH-90000
        assertSame(eventloop, context.getEventloop()); // GH-90000
    }

    @Test
    @DisplayName("Should register and retrieve typed dependencies [GH-90000]")
    void shouldRegisterAndRetrieveTypedDependencies() { // GH-90000
        TestService service = new TestService(); // GH-90000

        context.registerDependency(TestService.class, service); // GH-90000

        assertTrue(context.hasDependency(TestService.class)); // GH-90000
        assertSame(service, context.getDependency(TestService.class)); // GH-90000
        assertSame(service, context.getOptionalDependency(TestService.class).orElseThrow()); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception for missing typed dependency [GH-90000]")
    void shouldThrowExceptionForMissingTypedDependency() { // GH-90000
        IllegalStateException exception = assertThrows(IllegalStateException.class, // GH-90000
            () -> context.getDependency(TestService.class)); // GH-90000
        assertTrue(exception.getMessage().contains("not found [GH-90000]"));
    }

    @Test
    @DisplayName("Should return empty optional for missing optional dependency [GH-90000]")
    void shouldReturnEmptyOptionalForMissingOptionalDependency() { // GH-90000
        Optional<TestService> optional = context.getOptionalDependency(TestService.class); // GH-90000
        assertTrue(optional.isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should register and retrieve named dependencies [GH-90000]")
    void shouldRegisterAndRetrieveNamedDependencies() { // GH-90000
        TestService service = new TestService(); // GH-90000

        context.registerDependency("my-service", service); // GH-90000

        assertSame(service, context.getDependency("my-service", TestService.class)); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception for missing named dependency [GH-90000]")
    void shouldThrowExceptionForMissingNamedDependency() { // GH-90000
        IllegalStateException exception = assertThrows(IllegalStateException.class, // GH-90000
            () -> context.getDependency("missing-service", TestService.class)); // GH-90000
        assertTrue(exception.getMessage().contains("not found [GH-90000]"));
    }

    @Test
    @DisplayName("Should throw exception for type mismatch in named dependency [GH-90000]")
    void shouldThrowExceptionForTypeMismatchInNamedDependency() { // GH-90000
        TestService service = new TestService(); // GH-90000
        context.registerDependency("my-service", service); // GH-90000

        IllegalStateException exception = assertThrows(IllegalStateException.class, // GH-90000
            () -> context.getDependency("my-service", String.class)); // GH-90000
        assertTrue(exception.getMessage().contains("type mismatch [GH-90000]"));
    }

    @Test
    @DisplayName("Should register and invoke event handlers [GH-90000]")
    void shouldRegisterAndInvokeEventHandlers() { // GH-90000
        AtomicBoolean handled = new AtomicBoolean(false); // GH-90000
        AtomicReference<TestEvent> capturedEvent = new AtomicReference<>(); // GH-90000

        EventHandler<TestEvent> handler = event -> {
            handled.set(true); // GH-90000
            capturedEvent.set(event); // GH-90000
        };

        context.registerEventHandler(TestEvent.class, handler); // GH-90000

        TestEvent event = new TestEvent("test-data [GH-90000]");
        context.publishEvent(event); // GH-90000

        assertTrue(handled.get()); // GH-90000
        assertEquals(event, capturedEvent.get()); // GH-90000
    }

    @Test
    @DisplayName("Should unregister event handlers [GH-90000]")
    void shouldUnregisterEventHandlers() { // GH-90000
        AtomicBoolean handled = new AtomicBoolean(false); // GH-90000

        EventHandler<TestEvent> handler = event -> {
            handled.set(true); // GH-90000
        };

        context.registerEventHandler(TestEvent.class, handler); // GH-90000
        context.unregisterEventHandler(TestEvent.class, handler); // GH-90000

        context.publishEvent(new TestEvent("test [GH-90000]"));

        assertFalse(handled.get()); // GH-90000
    }

    @Test
    @DisplayName("Should handle multiple event handlers [GH-90000]")
    void shouldHandleMultipleEventHandlers() { // GH-90000
        AtomicBoolean handler1Called = new AtomicBoolean(false); // GH-90000
        AtomicBoolean handler2Called = new AtomicBoolean(false); // GH-90000

        context.registerEventHandler(TestEvent.class, event -> { // GH-90000
            handler1Called.set(true); // GH-90000
        });

        context.registerEventHandler(TestEvent.class, event -> { // GH-90000
            handler2Called.set(true); // GH-90000
        });

        context.publishEvent(new TestEvent("test [GH-90000]"));

        assertTrue(handler1Called.get()); // GH-90000
        assertTrue(handler2Called.get()); // GH-90000
    }

    @Test
    @DisplayName("Should continue with other handlers when one throws [GH-90000]")
    void shouldContinueWithOtherHandlersWhenOneThrows() { // GH-90000
        AtomicBoolean goodHandlerCalled = new AtomicBoolean(false); // GH-90000

        context.registerEventHandler(TestEvent.class, event -> { // GH-90000
            throw new RuntimeException("Handler error [GH-90000]");
        });

        context.registerEventHandler(TestEvent.class, event -> { // GH-90000
            goodHandlerCalled.set(true); // GH-90000
        });

        // Should not throw
        assertDoesNotThrow(() -> context.publishEvent(new TestEvent("test [GH-90000]")));
        assertTrue(goodHandlerCalled.get()); // GH-90000
    }

    @Test
    @DisplayName("Should handle null events gracefully [GH-90000]")
    void shouldHandleNullEventsGracefully() { // GH-90000
        assertDoesNotThrow(() -> context.publishEvent(null)); // GH-90000
    }

    @Test
    @DisplayName("Should return available capabilities from registry [GH-90000]")
    void shouldReturnAvailableCapabilitiesFromRegistry() { // GH-90000
        Set<KernelCapability> capabilities = context.getAvailableCapabilities(); // GH-90000
        assertNotNull(capabilities); // GH-90000
        assertTrue(capabilities.contains(KernelCapability.Core.DATA_STORAGE)); // GH-90000
    }

    @Test
    @DisplayName("Should check capability availability [GH-90000]")
    void shouldCheckCapabilityAvailability() { // GH-90000
        assertTrue(context.hasCapability(KernelCapability.Core.DATA_STORAGE)); // GH-90000
        assertFalse(context.hasCapability(new KernelCapability("nonexistent", "Non-existent", "Test", KernelCapability.CapabilityType.DATA_MANAGEMENT, java.util.Map.of()))); // GH-90000
    }

    @Test
    @DisplayName("Should get config from resolver [GH-90000]")
    void shouldGetConfigFromResolver() { // GH-90000
        String configValue = context.getConfig("test.key", String.class); // GH-90000
        assertEquals("test-value", configValue); // GH-90000
    }

    @Test
    @DisplayName("Should get optional config [GH-90000]")
    void shouldGetOptionalConfig() { // GH-90000
        Optional<String> configValue = context.getOptionalConfig("optional.key", String.class); // GH-90000
        assertTrue(configValue.isPresent()); // GH-90000
        assertEquals("optional-value", configValue.get()); // GH-90000
    }

    @Test
    @DisplayName("Should return default tenant context [GH-90000]")
    void shouldReturnDefaultTenantContext() { // GH-90000
        KernelTenantContext tenantContext = context.getTenantContext(); // GH-90000

        assertNotNull(tenantContext); // GH-90000
        assertEquals("system", tenantContext.getTenantId()); // GH-90000
        assertEquals(KernelTenantContext.TenantType.SYSTEM, tenantContext.getTenantType()); // GH-90000
    }

    @Test
    @DisplayName("Should register and retrieve tenant context [GH-90000]")
    void shouldRegisterAndRetrieveTenantContext() { // GH-90000
        KernelTenantContext customTenant = new KernelTenantContext( // GH-90000
            "tenant-123",
            KernelTenantContext.TenantType.ENTERPRISE,
            java.util.Map.of(), // GH-90000
            java.util.Set.of(), // GH-90000
            null,
            null
        );

        context.registerTenantContext("tenant-123", customTenant); // GH-90000

        KernelTenantContext retrieved = context.getTenantContext("tenant-123 [GH-90000]");
        assertEquals("tenant-123", retrieved.getTenantId()); // GH-90000
        assertEquals(KernelTenantContext.TenantType.ENTERPRISE, retrieved.getTenantType()); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception for non-existent tenant [GH-90000]")
    void shouldThrowExceptionForNonExistentTenant() { // GH-90000
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, // GH-90000
            () -> context.getTenantContext("non-existent [GH-90000]"));
        assertTrue(exception.getMessage().contains("not found [GH-90000]"));
    }

    @Test
    @DisplayName("Should return registry [GH-90000]")
    void shouldReturnRegistry() { // GH-90000
        assertSame(mockRegistry, context.getRegistry()); // GH-90000
    }

    @Test
    @DisplayName("Should handle unknown event types gracefully [GH-90000]")
    void shouldHandleUnknownEventTypesGracefully() { // GH-90000
        // Publish event with no handlers registered
        assertDoesNotThrow(() -> context.publishEvent(new UnhandledEvent())); // GH-90000
    }

    @Test
    @DisplayName("Should maintain separate handler lists for different event types [GH-90000]")
    void shouldMaintainSeparateHandlerListsForDifferentEventTypes() { // GH-90000
        AtomicBoolean testHandlerCalled = new AtomicBoolean(false); // GH-90000
        AtomicBoolean otherHandlerCalled = new AtomicBoolean(false); // GH-90000

        context.registerEventHandler(TestEvent.class, event -> { // GH-90000
            testHandlerCalled.set(true); // GH-90000
        });

        context.registerEventHandler(OtherEvent.class, event -> { // GH-90000
            otherHandlerCalled.set(true); // GH-90000
        });

        context.publishEvent(new TestEvent("test [GH-90000]"));

        assertTrue(testHandlerCalled.get()); // GH-90000
        assertFalse(otherHandlerCalled.get()); // GH-90000
    }

    // ==================== Test Helpers ====================

    private KernelRegistry createMockRegistry() { // GH-90000
        return new KernelRegistry() { // GH-90000
            @Override public void registerModule(com.ghatana.kernel.module.KernelModule module) {} // GH-90000
            @Override public void registerPlugin(com.ghatana.kernel.plugin.KernelPlugin plugin) {} // GH-90000
            @Override public void registerCapability(KernelCapability capability) {} // GH-90000
            @Override public boolean unregisterModule(String moduleId) { return false; } // GH-90000
            @Override public java.util.Optional<com.ghatana.kernel.module.KernelModule> getModule(String moduleId) { return java.util.Optional.empty(); } // GH-90000
            @Override public java.util.Optional<com.ghatana.kernel.plugin.KernelPlugin> getPlugin(String pluginId) { return java.util.Optional.empty(); } // GH-90000
            @Override public java.util.List<com.ghatana.kernel.module.KernelModule> getAllModules() { return java.util.List.of(); } // GH-90000
            @Override public java.util.List<com.ghatana.kernel.plugin.KernelPlugin> getAllPlugins() { return java.util.List.of(); } // GH-90000
            @Override public java.util.List<KernelCapability> getAllCapabilities() { // GH-90000
                return java.util.List.of(KernelCapability.Core.DATA_STORAGE); // GH-90000
            }
            @Override public java.util.List<com.ghatana.kernel.plugin.KernelPlugin> getPluginsByCapability(KernelCapability capability) { return java.util.List.of(); } // GH-90000
            @Override public java.util.List<com.ghatana.kernel.module.KernelModule> getModulesByCapability(KernelCapability capability) { return java.util.List.of(); } // GH-90000
            @Override public java.util.List<com.ghatana.kernel.module.KernelModule> getDependentModules(String moduleId) { return java.util.List.of(); } // GH-90000
            @Override public java.util.List<com.ghatana.kernel.module.KernelModule> resolveDependencies(com.ghatana.kernel.module.KernelModule module) { return java.util.List.of(); } // GH-90000
            @Override public boolean validateDependencies(com.ghatana.kernel.module.KernelModule module) { return true; } // GH-90000
            @Override public java.util.List<String> getDependencyValidationErrors(com.ghatana.kernel.module.KernelModule module) { return java.util.List.of(); } // GH-90000
            @Override public io.activej.promise.Promise<Void> startAllModules() { return io.activej.promise.Promise.complete(); } // GH-90000
            @Override public io.activej.promise.Promise<Void> stopAllModules() { return io.activej.promise.Promise.complete(); } // GH-90000
            @Override public boolean isModuleRegistered(String moduleId) { return false; } // GH-90000
            @Override public boolean isCapabilityAvailable(String capabilityId) { return false; } // GH-90000
        };
    }

    private KernelConfigResolver createMockConfigResolver() { // GH-90000
        return new KernelConfigResolver() { // GH-90000
            @Override public <T> T resolve(String key, Class<T> type, KernelTenantContext tenantContext) { // GH-90000
                if ("test.key".equals(key)) { // GH-90000
                    return type.cast("test-value [GH-90000]");
                }
                throw new IllegalArgumentException("Config key not found: " + key); // GH-90000
            }
            @Override public <T> T resolveWithDefault(String key, Class<T> type, T defaultValue, KernelTenantContext tenantContext) { // GH-90000
                java.util.Optional<T> opt = resolveOptional(key, type, tenantContext); // GH-90000
                return opt.orElse(defaultValue); // GH-90000
            }
            @Override public <T> java.util.Optional<T> resolveOptional(String key, Class<T> type, KernelTenantContext tenantContext) { // GH-90000
                if ("optional.key".equals(key)) { // GH-90000
                    return java.util.Optional.of(type.cast("optional-value [GH-90000]"));
                }
                if ("test.key".equals(key)) { // GH-90000
                    return java.util.Optional.of(type.cast("test-value [GH-90000]"));
                }
                return java.util.Optional.empty(); // GH-90000
            }
            @Override public void addConfigProvider(ConfigProvider provider) {} // GH-90000
            @Override public io.activej.promise.Promise<Void> reloadConfig(String tenantId) { return io.activej.promise.Promise.complete(); } // GH-90000
            @Override public java.util.List<String> getAvailableKeys(KernelTenantContext ctx) { return java.util.List.of(); } // GH-90000
        };
    }

    private static class TestService {
        public void doSomething() {} // GH-90000
    }

    private static class TestEvent {
        private final String data;
        public TestEvent(String data) { this.data = data; } // GH-90000
        public String getData() { return data; } // GH-90000
    }

    private static class OtherEvent {
    }

    private static class UnhandledEvent {
    }
}
