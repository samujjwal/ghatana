package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Integration tests for cross-product module dependency resolution. */
@DisplayName("Cross-Domain Module Integration Tests")
class PhrFinanceIntegrationTest {

    @Test
    @DisplayName("D-6: kernel resolves dependencies across domain-module IDs")
    void shouldResolveCrossModuleDependenciesByContract() {
        KernelRegistryImpl registry = new KernelRegistryImpl();

        KernelModule moduleA = module("domain-a-kernel", Set.of());
        KernelModule moduleB = module(
            "domain-b-kernel",
            Set.of(new KernelDependency("domain-a-kernel", "1.0.0", KernelDependency.DependencyType.MODULE, false))
        );

        registry.registerModule(moduleA);
        registry.registerModule(moduleB);

        List<KernelModule> resolved = registry.resolveDependencies(moduleB);
        assertThat(resolved).extracting(KernelModule::getModuleId).containsExactly("domain-a-kernel");
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
