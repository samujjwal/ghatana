/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.kernel.core;

import com.ghatana.kernel.contracts.KernelContract;
import com.ghatana.kernel.context.DefaultKernelContext;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.AbstractKernelModule;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive kernel abstraction tests covering core interfaces,
 * lifecycle management, and resource allocation.
 *
 * @doc.type class
 * @doc.purpose Validates core kernel interfaces, lifecycle management, and resource allocation
 * @doc.layer kernel
 * @doc.pattern KernelTest
 */
@DisplayName("Kernel Abstraction Tests")
class KernelAbstractionTest extends EventloopTestBase {

    // =========================================================================
    // Kernel Module Interface Tests
    // =========================================================================

    @Nested
    @DisplayName("Kernel Module Interface")
    class KernelModuleInterfaceTests {

        @Test
        @DisplayName("should implement module identity correctly")
        void shouldImplementModuleIdentityCorrectly() {
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");

            assertThat(module.getModuleId()).isEqualTo("test-module");
            assertThat(module.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("should declare capabilities correctly")
        void shouldDeclareCapabilitiesCorrectly() {
            KernelCapability capability = KernelCapability.builder()
                    .capabilityId("data-storage")
                    .name("Data Storage")
                    .version("1.0.0")
                    .build();

            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            module.setCapabilities(Set.of(capability));

            assertThat(module.getCapabilities()).containsExactly(capability);
            assertThat(module.hasCapability(capability)).isTrue();
        }

        @Test
        @DisplayName("should declare dependencies correctly")
        void shouldDeclareDependenciesCorrectly() {
            KernelDependency dependency = KernelDependency.builder()
                    .dependencyId("auth-service")
                    .type(KernelDependency.DependencyType.MODULE)
                    .required(true)
                    .build();

            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            module.setDependencies(Set.of(dependency));

            assertThat(module.getDependencies()).containsExactly(dependency);
            assertThat(module.hasDependency("auth-service")).isTrue();
        }

        @Test
        @DisplayName("should report health status correctly")
        void shouldReportHealthStatusCorrectly() {
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");

            assertThat(module.getHealthStatus()).isEqualTo(HealthStatus.UNKNOWN);

            module.setHealthStatus(HealthStatus.HEALTHY);
            assertThat(module.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
        }
    }

    // =========================================================================
    // Lifecycle Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle Management")
    class LifecycleManagementTests {

        @Test
        @DisplayName("should initialize module with context")
        void shouldInitializeModuleWithContext() {
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            KernelContext context = DefaultKernelContext.builder()
                    .tenantId("tenant-123")
                    .build();

            module.initialize(context);

            assertThat(module.getContext()).isNotNull();
            assertThat(module.getContext().getTenantId()).isEqualTo("tenant-123");
            assertThat(module.isInitialized()).isTrue();
        }

        @Test
        @DisplayName("should start module asynchronously")
        void shouldStartModuleAsynchronously() {
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            KernelContext context = DefaultKernelContext.builder().build();
            module.initialize(context);

            runPromise(module::start);

            assertThat(module.isRunning()).isTrue();
            assertThat(module.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
        }

        @Test
        @DisplayName("should stop module asynchronously")
        void shouldStopModuleAsynchronously() {
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            KernelContext context = DefaultKernelContext.builder().build();
            module.initialize(context);
            runPromise(module::start);

            runPromise(module::stop);

            assertThat(module.isRunning()).isFalse();
            assertThat(module.getHealthStatus()).isEqualTo(HealthStatus.STOPPED);
        }

        @Test
        @DisplayName("should handle lifecycle transitions correctly")
        void shouldHandleLifecycleTransitionsCorrectly() {
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            KernelContext context = DefaultKernelContext.builder().build();

            // Initialize -> Start -> Stop -> Start (restart)
            module.initialize(context);
            assertThat(module.isInitialized()).isTrue();

            runPromise(module::start);
            assertThat(module.isRunning()).isTrue();

            runPromise(module::stop);
            assertThat(module.isRunning()).isFalse();

            runPromise(module::start);
            assertThat(module.isRunning()).isTrue();
        }

        @Test
        @DisplayName("should prevent start without initialization")
        void shouldPreventStartWithoutInitialization() {
            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");

            assertThatThrownBy(() -> runPromise(module::start))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // Resource Allocation Tests
    // =========================================================================

    @Nested
    @DisplayName("Resource Allocation")
    class ResourceAllocationTests {

        @Test
        @DisplayName("should allocate resources during start")
        void shouldAllocateResourcesDuringStart() {
            ResourceTrackingModule module = new ResourceTrackingModule("test-module", "1.0.0");
            KernelContext context = DefaultKernelContext.builder().build();
            module.initialize(context);

            runPromise(module::start);

            assertThat(module.getResourceCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should deallocate resources during stop")
        void shouldDeallocateResourcesDuringStop() {
            ResourceTrackingModule module = new ResourceTrackingModule("test-module", "1.0.0");
            KernelContext context = DefaultKernelContext.builder().build();
            module.initialize(context);
            runPromise(module::start);

            int resourceCount = module.getResourceCount();
            runPromise(module::stop);

            assertThat(module.getResourceCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle resource allocation failures gracefully")
        void shouldHandleResourceAllocationFailuresGracefully() {
            FailingResourceModule module = new FailingResourceModule("test-module", "1.0.0");
            KernelContext context = DefaultKernelContext.builder().build();
            module.initialize(context);

            assertThatThrownBy(() -> runPromise(module::start))
                    .isInstanceOf(RuntimeException.class);

            // Module should remain in initialized state
            assertThat(module.isRunning()).isFalse();
        }

        @Test
        @DisplayName("should limit resource allocation")
        void shouldLimitResourceAllocation() {
            LimitedResourceModule module = new LimitedResourceModule("test-module", "1.0.0", 10);
            KernelContext context = DefaultKernelContext.builder().build();
            module.initialize(context);

            runPromise(module::start);

            assertThat(module.getResourceCount()).isLessThanOrEqualTo(10);
        }
    }

    // =========================================================================
    // Kernel Contract Tests
    // =========================================================================

    @Nested
    @DisplayName("Kernel Contract")
    class KernelContractTests {

        @Test
        @DisplayName("should validate contract structure")
        void shouldValidateContractStructure() {
            TestKernelContract contract = new TestKernelContract(
                    "contract-1",
                    "Test Contract",
                    "1.0.0",
                    KernelContract.ContractFamily.API,
                    Map.of("key", "value")
            );

            assertThat(contract.getContractId()).isEqualTo("contract-1");
            assertThat(contract.getName()).isEqualTo("Test Contract");
            assertThat(contract.getVersion()).isEqualTo("1.0.0");
            assertThat(contract.getFamily()).isEqualTo(KernelContract.ContractFamily.API);
            assertThat(contract.getMetadata()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("should enforce required fields")
        void shouldEnforceRequiredFields() {
            assertThatThrownBy(() -> new TestKernelContract(null, "Test", "1.0.0",
                    KernelContract.ContractFamily.API, Map.of()))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> new TestKernelContract("id", null, "1.0.0",
                    KernelContract.ContractFamily.API, Map.of()))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> new TestKernelContract("id", "Test", null,
                    KernelContract.ContractFamily.API, Map.of()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should support all contract families")
        void shouldSupportAllContractFamilies() {
            for (KernelContract.ContractFamily family : KernelContract.ContractFamily.values()) {
                TestKernelContract contract = new TestKernelContract(
                        "contract-" + family.name(),
                        "Contract",
                        "1.0.0",
                        family,
                        Map.of()
                );

                assertThat(contract.getFamily()).isEqualTo(family);
                assertThat(contract.getFamily().getKey()).isNotNull();
            }
        }
    }

    // =========================================================================
    // Dependency Resolution Tests
    // =========================================================================

    @Nested
    @DisplayName("Dependency Resolution")
    class DependencyResolutionTests {

        @Test
        @DisplayName("should resolve module dependencies")
        void shouldResolveModuleDependencies() {
            KernelDependency dependency = KernelDependency.builder()
                    .dependencyId("auth-service")
                    .type(KernelDependency.DependencyType.MODULE)
                    .required(true)
                    .build();

            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            module.setDependencies(Set.of(dependency));

            assertThat(module.hasDependency("auth-service")).isTrue();
        }

        @Test
        @DisplayName("should resolve capability dependencies")
        void shouldResolveCapabilityDependencies() {
            KernelDependency dependency = KernelDependency.builder()
                    .dependencyId("data-storage")
                    .type(KernelDependency.DependencyType.CAPABILITY)
                    .required(true)
                    .build();

            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            module.setDependencies(Set.of(dependency));

            assertThat(module.hasDependency("data-storage")).isTrue();
        }

        @Test
        @DisplayName("should handle optional dependencies")
        void shouldHandleOptionalDependencies() {
            KernelDependency dependency = KernelDependency.builder()
                    .dependencyId("optional-service")
                    .type(KernelDependency.DependencyType.MODULE)
                    .required(false)
                    .build();

            TestKernelModule module = new TestKernelModule("test-module", "1.0.0");
            module.setDependencies(Set.of(dependency));

            assertThat(module.hasDependency("optional-service")).isTrue();
            assertThat(dependency.isRequired()).isFalse();
        }
    }

    // =========================================================================
    // Test Helper Classes
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
    }

    static class ResourceTrackingModule extends AbstractKernelModule {
        private final String moduleId;
        private final String version;
        private int resourceCount = 0;
        private boolean initialized = false;
        private boolean running = false;

        ResourceTrackingModule(String moduleId, String version) {
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
            return Set.of();
        }

        @Override
        public Set<KernelDependency> getDependencies() {
            return Set.of();
        }

        @Override
        public void initialize(KernelContext context) {
            initialized = true;
        }

        @Override
        public Promise<Void> start() {
            if (!initialized) {
                return Promise.ofException(new IllegalStateException("Module not initialized"));
            }
            // Allocate resources
            for (int i = 0; i < 5; i++) {
                resourceCount++;
            }
            running = true;
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            resourceCount = 0;
            running = false;
            return Promise.complete();
        }

        @Override
        public HealthStatus getHealthStatus() {
            return running ? HealthStatus.HEALTHY : HealthStatus.STOPPED;
        }

        int getResourceCount() {
            return resourceCount;
        }
    }

    static class FailingResourceModule extends AbstractKernelModule {
        private final String moduleId;
        private final String version;

        FailingResourceModule(String moduleId, String version) {
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
            return Set.of();
        }

        @Override
        public Set<KernelDependency> getDependencies() {
            return Set.of();
        }

        @Override
        public void initialize(KernelContext context) {
            // No-op
        }

        @Override
        public Promise<Void> start() {
            return Promise.ofException(new RuntimeException("Resource allocation failed"));
        }

        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }

        @Override
        public HealthStatus getHealthStatus() {
            return HealthStatus.UNHEALTHY;
        }
    }

    static class LimitedResourceModule extends AbstractKernelModule {
        private final String moduleId;
        private final String version;
        private final int maxResources;
        private int resourceCount = 0;
        private boolean initialized = false;

        LimitedResourceModule(String moduleId, String version, int maxResources) {
            this.moduleId = moduleId;
            this.version = version;
            this.maxResources = maxResources;
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
            return Set.of();
        }

        @Override
        public Set<KernelDependency> getDependencies() {
            return Set.of();
        }

        @Override
        public void initialize(KernelContext context) {
            initialized = true;
        }

        @Override
        public Promise<Void> start() {
            if (!initialized) {
                return Promise.ofException(new IllegalStateException("Module not initialized"));
            }
            // Allocate resources up to limit
            for (int i = 0; i < maxResources; i++) {
                resourceCount++;
            }
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            resourceCount = 0;
            return Promise.complete();
        }

        @Override
        public HealthStatus getHealthStatus() {
            return HealthStatus.HEALTHY;
        }

        int getResourceCount() {
            return resourceCount;
        }
    }

    static class TestKernelContract extends KernelContract {
        TestKernelContract(String contractId, String name, String version,
                          ContractFamily family, Map<String, String> metadata) {
            super(contractId, name, version, family, metadata);
        }
    }
}
