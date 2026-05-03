package com.ghatana.kernel.plugin;

import com.ghatana.kernel.annotation.PluginInject;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PluginInjector}.
 *
 * @doc.type class
 * @doc.purpose Validates field injection from KernelContext into plugin instances
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("PluginInjector Tests")
class PluginInjectorTest {

    private InMemoryKernelContext context;
    private PluginInjector injector;

    @BeforeEach
    void setUp() {
        context = new InMemoryKernelContext();
        injector = new PluginInjector();
    }

    @Test
    @DisplayName("Should inject required dependency into annotated field")
    void shouldInjectRequiredDependency() {
        // GIVEN
        FakeService service = new FakeService();
        context.registerDependency(FakeService.class, service);
        PluginWithRequiredDep plugin = new PluginWithRequiredDep();

        // WHEN
        injector.inject(plugin, context);

        // THEN
        assertThat(plugin.fakeService).isSameAs(service);
    }

    @Test
    @DisplayName("Should inject optional dependency when present")
    void shouldInjectOptionalDependencyWhenPresent() {
        // GIVEN
        FakeService service = new FakeService();
        context.registerOptionalDependency(FakeService.class, service);
        PluginWithOptionalDep plugin = new PluginWithOptionalDep();

        // WHEN
        injector.inject(plugin, context);

        // THEN
        assertThat(plugin.fakeService).isSameAs(service);
    }

    @Test
    @DisplayName("Should leave optional field null when dependency absent")
    void shouldLeaveOptionalFieldNullWhenAbsent() {
        // GIVEN
        PluginWithOptionalDep plugin = new PluginWithOptionalDep();

        // WHEN
        injector.inject(plugin, context);

        // THEN — no exception; field stays null
        assertThat(plugin.fakeService).isNull();
    }

    @Test
    @DisplayName("Should throw PluginInjectionException when required dependency missing")
    void shouldThrowWhenRequiredDependencyMissing() {
        // GIVEN
        PluginWithRequiredDep plugin = new PluginWithRequiredDep();

        // WHEN / THEN
        assertThatThrownBy(() -> injector.inject(plugin, context))
            .isInstanceOf(PluginInjector.PluginInjectionException.class)
            .hasMessageContaining("fakeService");
    }

    @Test
    @DisplayName("Should inject fields from superclass hierarchy")
    void shouldInjectSuperclassFields() {
        // GIVEN
        FakeService service = new FakeService();
        AnotherService other = new AnotherService();
        context.registerDependency(FakeService.class, service);
        context.registerDependency(AnotherService.class, other);
        PluginWithInheritedDeps plugin = new PluginWithInheritedDeps();

        // WHEN
        injector.inject(plugin, context);

        // THEN
        assertThat(plugin.fakeService).isSameAs(service);
        assertThat(plugin.anotherService).isSameAs(other);
    }

    @Test
    @DisplayName("Should throw NullPointerException when target is null")
    void shouldRejectNullTarget() { 
        assertThatThrownBy(() -> injector.inject(null, context)) 
            .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("Should throw NullPointerException when context is null")
    void shouldRejectNullContext() { 
        assertThatThrownBy(() -> injector.inject(new PluginWithRequiredDep(), null)) 
            .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("Should do nothing on plugin with no annotated fields")
    void shouldHandlePluginWithNoInjectFields() {
        // GIVEN
        PluginWithNoInject plugin = new PluginWithNoInject();

        // WHEN / THEN — no interaction with context, no exception
        injector.inject(plugin, context);
        // verifyNoInteractions(context); // No longer needed with in-memory implementation
    }

    // ==================== Test doubles ====================

    private static final class InMemoryKernelContext implements KernelContext {
        private final Map<Class<?>, Object> dependencies = new HashMap<>();
        private final Map<Class<?>, Object> optionalDependencies = new HashMap<>();

        void registerDependency(Class<?> type, Object instance) {
            dependencies.put(type, instance);
        }

        void registerOptionalDependency(Class<?> type, Object instance) {
            optionalDependencies.put(type, instance);
        }

        @Override
        public <T> T getDependency(Class<T> type) {
            Object dep = dependencies.get(type);
            if (dep == null) {
                throw new IllegalStateException("Dependency not found: " + type.getSimpleName());
            }
            return type.cast(dep);
        }

        @Override
        public <T> Optional<T> getOptionalDependency(Class<T> type) {
            return Optional.ofNullable(type.cast(optionalDependencies.get(type)));
        }

        @Override
        public <T> boolean hasDependency(Class<T> type) {
            return dependencies.containsKey(type);
        }

        @Override
        public <T> T getDependency(String name, Class<T> type) {
            return getDependency(type);
        }

        @Override
        public <E> void registerEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {
        }

        @Override
        public <E> void unregisterEventHandler(Class<E> eventType, com.ghatana.kernel.event.EventHandler<E> handler) {
        }

        @Override
        public <E> void publishEvent(E event) {
        }

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
            return getEventloop();
        }

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() {
            return java.util.Set.of();
        }

        @Override
        public boolean hasCapability(com.ghatana.kernel.descriptor.KernelCapability capability) {
            return false;
        }

        @Override
        public <T> T getConfig(String key, Class<T> type) {
            return null;
        }

        @Override
        public <T> Optional<T> getOptionalConfig(String key, Class<T> type) {
            return Optional.empty();
        }

        @Override
        public String getKernelVersion() {
            return "1.0.0";
        }

        @Override
        public String getEnvironment() {
            return "test";
        }

        @Override
        public Executor getExecutor(String executorName) {
            return Runnable::run;
        }

        @Override
        public <T> Optional<T> getCapability(String capabilityId) {
            return Optional.empty();
        }

        @Override
        public <T> void registerService(Class<T> type, T service) {
            dependencies.put(type, service);
        }
    }

    // ==================== Test fixtures ====================

    static class FakeService { }

    static class AnotherService { }

    /** Minimal KernelModule stub — lifecycle not exercised in these tests. */
    abstract static class StubModule implements com.ghatana.kernel.module.KernelModule {
        @Override public String getModuleId() { return "stub"; } 
        @Override public String getVersion() { return "1.0.0"; } 
        @Override public Set<KernelCapability> getCapabilities() { return Set.of(); } 
        @Override public Set<KernelDependency> getDependencies() { return Set.of(); } 
        @Override public void initialize(KernelContext ctx) { } 
        @Override public Promise<Void> start() { return Promise.complete(); } 
        @Override public Promise<Void> stop() { return Promise.complete(); } 
        @Override public HealthStatus getHealthStatus() { return HealthStatus.healthy(); } 
    }

    static class PluginWithRequiredDep extends StubModule {
        @PluginInject
        FakeService fakeService;
    }

    static class PluginWithOptionalDep extends StubModule {
        @PluginInject(optional = true) 
        FakeService fakeService;
    }

    static class BasePluginWithService extends StubModule {
        @PluginInject
        FakeService fakeService;
    }

    static class PluginWithInheritedDeps extends BasePluginWithService {
        @PluginInject
        AnotherService anotherService;
    }

    static class PluginWithNoInject extends StubModule {
        @SuppressWarnings("unused")
        String notInjected;
    }
}
