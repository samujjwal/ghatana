package com.ghatana.kernel.dependency;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validates dependency-cycle safety semantics in current kernel registry behavior. */
@DisplayName("Circular Dependency Detection Tests")
class CircularDependencyDetectionTest {

    @Test
    @DisplayName("D-4: required dependency cycle is rejected at registration boundary")
    void shouldRejectRequiredDependencyCycleAtRegistration() {
        KernelRegistryImpl registry = new KernelRegistryImpl();

        KernelModule moduleA = module("module-a", Set.of(
            new KernelDependency("module-b", "1.0.0", KernelDependency.DependencyType.MODULE, false)
        ));
        KernelModule moduleB = module("module-b", Set.of(
            new KernelDependency("module-a", "1.0.0", KernelDependency.DependencyType.MODULE, false)
        ));

        assertThatThrownBy(() -> registry.registerModule(moduleA))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing required module: module-b");

        assertThatThrownBy(() -> registry.registerModule(moduleB))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing required module: module-a");
    }

    @Test
    @DisplayName("D-4: optional back-edge does not block registration")
    void shouldAllowOptionalBackEdgeDependency() {
        KernelRegistryImpl registry = new KernelRegistryImpl();

        KernelModule moduleA = module("module-a", Set.of(
            new KernelDependency("module-b", "1.0.0", KernelDependency.DependencyType.MODULE, true)
        ));
        KernelModule moduleB = module("module-b", Set.of(
            new KernelDependency("module-a", "1.0.0", KernelDependency.DependencyType.MODULE, true)
        ));

        assertThatCode(() -> {
            registry.registerModule(moduleA);
            registry.registerModule(moduleB);
        }).doesNotThrowAnyException();
    }

    private static KernelModule module(String moduleId, Set<KernelDependency> dependencies) {
        return new KernelModule() {
            @Override
            public String getModuleId() {
                return moduleId;
            }

            @Override
            public String getVersion() {
                return "1.0.0";
            }

            @Override
            public Set<KernelCapability> getCapabilities() {
                return Set.of();
            }

            @Override
            public Set<KernelDependency> getDependencies() {
                return dependencies;
            }

            @Override
            public void initialize(KernelContext context) {
            }

            @Override
            public Promise<Void> start() {
                return Promise.complete();
            }

            @Override
            public Promise<Void> stop() {
                return Promise.complete();
            }

            @Override
            public HealthStatus getHealthStatus() {
                return HealthStatus.healthy();
            }
        };
    }
}
