/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.kernel.core;

import com.ghatana.kernel.context.DefaultKernelContext;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.AbstractKernelModule;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.registry.KernelRegistry;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive module loading tests covering dynamic loading, unloading,
 * and dependency resolution.
 *
 * @doc.type class
 * @doc.purpose Validates dynamic module loading, unloading, and dependency resolution
 * @doc.layer kernel
 * @doc.pattern ModuleTest
 */
@DisplayName("Module Loading Tests")
class ModuleLoadingTest extends EventloopTestBase {

    // =========================================================================
    // Module Registration
    // =========================================================================

    @Nested
    @DisplayName("Module Registration")
    class ModuleRegistrationTests {

        @Test
        @DisplayName("should register module successfully")
        void shouldRegisterModuleSuccessfully() {
            KernelRegistry registry = new KernelRegistryImpl();
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");

            registry.register(module);

            assertThat(registry.isRegistered("test-module")).isTrue();
            assertThat(registry.getModule("test-module")).isNotNull();
        }

        @Test
        @DisplayName("should prevent duplicate module registration")
        void shouldPreventDuplicateModuleRegistration() {
            KernelRegistry registry = new KernelRegistryImpl();
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");

            registry.register(module);

            assertThatThrownBy(() -> registry.register(module))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should unregister module successfully")
        void shouldUnregisterModuleSuccessfully() {
            KernelRegistry registry = new KernelRegistryImpl();
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");

            registry.register(module);
            registry.unregister("test-module");

            assertThat(registry.isRegistered("test-module")).isFalse();
        }

        @Test
        @DisplayName("should handle unregistration of non-existent module")
        void shouldHandleUnregistrationOfNonExistentModule() {
            KernelRegistry registry = new KernelRegistryImpl();

            assertThatThrownBy(() -> registry.unregister("non-existent"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // Module Loading
    // =========================================================================

    @Nested
    @DisplayName("Module Loading")
    class ModuleLoadingTests {

        @Test
        @DisplayName("should load module with initialization")
        void shouldLoadModuleWithInitialization() {
            KernelRegistry registry = new KernelRegistryImpl();
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            registry.register(module);

            KernelContext context = DefaultKernelContext.builder().build();
            registry.load("test-module", context);

            assertThat(module.isInitialized()).isTrue();
            assertThat(module.getContext()).isNotNull();
        }

        @Test
        @DisplayName("should start module after loading")
        void shouldStartModuleAfterLoading() {
            KernelRegistry registry = new KernelRegistryImpl();
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            registry.register(module);

            KernelContext context = DefaultKernelContext.builder().build();
            registry.load("test-module", context);

            runPromise(() -> registry.start("test-module"));

            assertThat(module.isRunning()).isTrue();
        }

        @Test
        @DisplayName("should stop module before unloading")
        void shouldStopModuleBeforeUnloading() {
            KernelRegistry registry = new KernelRegistryImpl();
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            registry.register(module);

            KernelContext context = DefaultKernelContext.builder().build();
            registry.load("test-module", context);
            runPromise(() -> registry.start("test-module"));

            runPromise(() -> registry.stop("test-module"));

            assertThat(module.isRunning()).isFalse();
        }

        @Test
        @DisplayName("should unload module completely")
        void shouldUnloadModuleCompletely() {
            KernelRegistry registry = new KernelRegistryImpl();
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            registry.register(module);

            KernelContext context = DefaultKernelContext.builder().build();
            registry.load("test-module", context);
            runPromise(() -> registry.start("test-module"));

            registry.unload("test-module");

            assertThat(module.isInitialized()).isFalse();
            assertThat(module.isRunning()).isFalse();
        }
    }

    // =========================================================================
    // Dependency Resolution
    // =========================================================================

    @Nested
    @DisplayName("Dependency Resolution")
    class DependencyResolutionTests {

        @Test
        @DisplayName("should resolve module dependencies")
        void shouldResolveModuleDependencies() {
            KernelRegistry registry = new KernelRegistryImpl();

            // Register dependency first
            TestKernelModule dependency = new TestKernelModule("dependency-module", "1.0.0");
            registry.register(dependency);

            // Register module with dependency
            KernelDependency dep = KernelDependency.builder()
                    .dependencyId("dependency-module")
                    .type(KernelDependency.DependencyType.MODULE)
                    .required(true)
                    .build();

            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            module.setDependencies(Set.of(dep));
            registry.register(module);

            // Load and start dependency first
            KernelContext context = DefaultKernelContext.builder().build();
            registry.load("dependency-module", context);
            runPromise(() -> registry.start("dependency-module"));

            // Load and start dependent module
            registry.load("test-module", context);
            runPromise(() -> registry.start("test-module"));

            assertThat(module.isRunning()).isTrue();
        }

        @Test
        @DisplayName("should fail when required dependency is missing")
        void shouldFailWhenRequiredDependencyIsMissing() {
            KernelRegistry registry = new KernelRegistryImpl();

            KernelDependency dep = KernelDependency.builder()
                    .dependencyId("missing-dependency")
                    .type(KernelDependency.DependencyType.MODULE)
                    .required(true)
                    .build();

            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            module.setDependencies(Set.of(dep));
            registry.register(module);

            KernelContext context = DefaultKernelContext.builder().build();

            assertThatThrownBy(() -> registry.load("test-module", context))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should handle optional dependencies gracefully")
        void shouldHandleOptionalDependenciesGracefully() {
            KernelRegistry registry = new KernelRegistryImpl();

            KernelDependency dep = KernelDependency.builder()
                    .dependencyId("optional-dependency")
                    .type(KernelDependency.DependencyType.MODULE)
                    .required(false)
                    .build();

            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            module.setDependencies(Set.of(dep));
            registry.register(module);

            KernelContext context = DefaultKernelContext.builder().build();
            registry.load("test-module", context);

            // Should load successfully even without optional dependency
            assertThat(module.isInitialized()).isTrue();
        }

        @Test
        @DisplayName("should detect circular dependencies")
        void shouldDetectCircularDependencies() {
            KernelRegistry registry = new KernelRegistryImpl();

            // Module A depends on Module B
            KernelDependency depA = KernelDependency.builder()
                    .dependencyId("module-b")
                    .type(KernelDependency.DependencyType.MODULE)
                    .required(true)
                    .build();

            TestKernelModule moduleA = new TestKernelModule("module-a", "1.0.0");
            moduleA.setDependencies(Set.of(depA));

            // Module B depends on Module A (circular)
            KernelDependency depB = KernelDependency.builder()
                    .dependencyId("module-a")
                    .type(KernelDependency.DependencyType.MODULE)
                    .required(true)
                    .build();

            TestKernelModule moduleB = new TestKernelModule("module-b", "1.0.0");
            moduleB.setDependencies(Set.of(depB));

            registry.register(moduleA);
            registry.register(moduleB);

            KernelContext context = DefaultKernelContext.builder().build();

            assertThatThrownBy(() -> {
                registry.load("module-a", context);
                registry.load("module-b", context);
                runPromise(() -> registry.start("module-a"));
            }).isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // Capability Resolution
    // =========================================================================

    @Nested
    @DisplayName("Capability Resolution")
    class CapabilityResolutionTests {

        @Test
        @DisplayName("should resolve module by capability")
        void shouldResolveModuleByCapability() {
            KernelRegistry registry = new KernelRegistryImpl();

            KernelCapability capability = KernelCapability.builder()
                    .capabilityId("data-storage")
                    .name("Data Storage")
                    .version("1.0.0")
                    .build();

            TestKernelModule module = new TestKernelModule("data-module", "1.0.0");
            module.setCapabilities(Set.of(capability));
            registry.register(module);

            Set<KernelModule> modules = registry.findByCapability(capability);

            assertThat(modules).containsExactly(module);
        }

        @Test
        @DisplayName("should resolve multiple modules by capability")
        void shouldResolveMultipleModulesByCapability() {
            KernelRegistry registry = new KernelRegistryImpl();

            KernelCapability capability = KernelCapability.builder()
                    .capabilityId("data-storage")
                    .name("Data Storage")
                    .version("1.0.0")
                    .build();

            TestKernelModule module1 = new TestKernelModule("data-module-1", "1.0.0");
            module1.setCapabilities(Set.of(capability));

            TestKernelModule module2 = new TestKernelModule("data-module-2", "1.0.0");
            module2.setCapabilities(Set.of(capability));

            registry.register(module1);
            registry.register(module2);

            Set<KernelModule> modules = registry.findByCapability(capability);

            assertThat(modules).hasSize(2);
            assertThat(modules).containsExactlyInAnyOrder(module1, module2);
        }

        @Test
        @DisplayName("should handle capability-based dependencies")
        void shouldHandleCapabilityBasedDependencies() {
            KernelRegistry registry = new KernelRegistryImpl();

            KernelCapability capability = KernelCapability.builder()
                    .capabilityId("data-storage")
                    .name("Data Storage")
                    .version("1.0.0")
                    .build();

            // Register provider module
            TestKernelModule provider = new TestKernelModule("data-module", "1.0.0");
            provider.setCapabilities(Set.of(capability));
            registry.register(provider);

            // Register consumer with capability dependency
            KernelDependency dep = KernelDependency.builder()
                    .dependencyId("data-storage")
                    .type(KernelDependency.DependencyType.CAPABILITY)
                    .required(true)
                    .build();

            TestKernelModule consumer = new TestKernelModule("consumer-module", "1.0.0");
            consumer.setDependencies(Set.of(dep));
            registry.register(consumer);

            KernelContext context = DefaultKernelContext.builder().build();
            registry.load("data-module", context);
            runPromise(() -> registry.start("data-module"));

            registry.load("consumer-module", context);
            runPromise(() -> registry.start("consumer-module"));

            assertThat(consumer.isRunning()).isTrue();
        }
    }

    // =========================================================================
    // Module Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("Module Lifecycle")
    class ModuleLifecycleTests {

        @Test
        @DisplayName("should handle module restart")
        void shouldHandleModuleRestart() {
            KernelRegistry registry = new KernelRegistryImpl();
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            registry.register(module);

            KernelContext context = DefaultKernelContext.builder().build();
            registry.load("test-module", context);

            // First start
            runPromise(() -> registry.start("test-module"));
            assertThat(module.isRunning()).isTrue();
            assertThat(module.getStartCount()).isEqualTo(1);

            // Stop
            runPromise(() -> registry.stop("test-module"));
            assertThat(module.isRunning()).isFalse();

            // Restart
            runPromise(() -> registry.start("test-module"));
            assertThat(module.isRunning()).isTrue();
            assertThat(module.getStartCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should handle module reload")
        void shouldHandleModuleReload() {
            KernelRegistry registry = new KernelRegistryImpl();
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            registry.register(module);

            KernelContext context = DefaultKernelContext.builder().build();
            registry.load("test-module", context);
            runPromise(() -> registry.start("test-module"));

            // Reload
            registry.unload("test-module");
            registry.load("test-module", context);
            runPromise(() -> registry.start("test-module"));

            assertThat(module.isInitialized()).isTrue();
            assertThat(module.isRunning()).isTrue();
        }

        @Test
        @DisplayName("should propagate health status changes")
        void shouldPropagateHealthStatusChanges() {
            KernelRegistry registry = new KernelRegistryImpl();
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            registry.register(module);

            KernelContext context = DefaultKernelContext.builder().build();
            registry.load("test-module", context);
            runPromise(() -> registry.start("test-module"));

            module.setHealthStatus(HealthStatus.UNHEALTHY);

            HealthStatus status = registry.getModuleHealth("test-module");
            assertThat(status).isEqualTo(HealthStatus.UNHEALTHY);
        }
    }

    // =========================================================================
    // Test Helper Class
    // =========================================================================

    static class TestKernelModule extends AbstractKernelModule {
        private final String moduleId;
        private final String version;
        private Set<KernelCapability> capabilities = Set.of();
        private Set<KernelDependency> dependencies = Set.of();
        private KernelContext context;
        private boolean initialized = false;
        private boolean running = false;
        private HealthStatus healthStatus = HealthStatus.UNKNOWN;
        private final AtomicInteger startCount = new AtomicInteger(0);

        TestKernelModule(String moduleId, String version) {
            this.moduleId = moduleId;
            this.version = version;
        }

        @Override
        public String getModuleId() {
            return moduleId;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public Set<KernelCapability> getCapabilities() {
            return capabilities;
        }

        @Override
        public Set<KernelDependency> getDependencies() {
            return dependencies;
        }

        @Override
        public void initialize(KernelContext context) {
            this.context = context;
            this.initialized = true;
        }

        @Override
        public Promise<Void> start() {
            if (!initialized) {
                return Promise.ofException(new IllegalStateException("Module not initialized"));
            }
            running = true;
            healthStatus = HealthStatus.HEALTHY;
            startCount.incrementAndGet();
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            running = false;
            healthStatus = HealthStatus.STOPPED;
            return Promise.complete();
        }

        @Override
        public HealthStatus getHealthStatus() {
            return healthStatus;
        }

        void setCapabilities(Set<KernelCapability> capabilities) {
            this.capabilities = capabilities;
        }

        void setDependencies(Set<KernelDependency> dependencies) {
            this.dependencies = dependencies;
        }

        void setHealthStatus(HealthStatus healthStatus) {
            this.healthStatus = healthStatus;
        }

        KernelContext getContext() {
            return context;
        }

        boolean isInitialized() {
            return initialized;
        }

        boolean isRunning() {
            return running;
        }

        int getStartCount() {
            return startCount.get();
        }
    }
}
