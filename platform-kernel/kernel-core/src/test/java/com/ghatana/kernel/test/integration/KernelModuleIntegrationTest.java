package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration tests for kernel module interactions. */
@DisplayName("Kernel Module Integration Tests")
public class KernelModuleIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("D-3: required module dependency must be present during registration")
    void shouldRejectMissingRequiredModuleDependency() {
        KernelRegistryImpl registry = new KernelRegistryImpl();
        KernelModule dependent = module(
            "kernel-dependent",
            Set.of(new KernelDependency("kernel-missing", "1.0.0", KernelDependency.DependencyType.MODULE, false))
        );

        assertThatThrownBy(() -> registry.registerModule(dependent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing required module: kernel-missing");
    }

    @Test
    @DisplayName("D-3: resolveDependencies returns transitive dependencies without self")
    void shouldResolveTransitiveDependencies() {
        KernelRegistryImpl registry = new KernelRegistryImpl();

        KernelModule storage = module("kernel-storage", Set.of());
        KernelModule audit = module(
            "kernel-audit",
            Set.of(new KernelDependency("kernel-storage", "1.0.0", KernelDependency.DependencyType.MODULE, false))
        );
        KernelModule api = module(
            "kernel-api",
            Set.of(new KernelDependency("kernel-audit", "1.0.0", KernelDependency.DependencyType.MODULE, false))
        );

        registry.registerModule(storage);
        registry.registerModule(audit);
        registry.registerModule(api);

        List<KernelModule> resolved = registry.resolveDependencies(api);
        assertThat(resolved)
            .extracting(KernelModule::getModuleId)
            .containsExactly("kernel-storage", "kernel-audit");
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
