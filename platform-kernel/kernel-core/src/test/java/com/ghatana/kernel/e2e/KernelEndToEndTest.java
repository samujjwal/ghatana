package com.ghatana.kernel.e2e;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end kernel lifecycle validation using kernel-scoped test modules. */
@DisplayName("Kernel End-to-End Tests")
class KernelEndToEndTest extends EventloopTestBase {

    @Test
    @DisplayName("D-2: starts and stops registered modules in dependency-safe order")
    void shouldStartAndStopModulesInDependencyOrder() {
        KernelRegistryImpl registry = new KernelRegistryImpl();
        List<String> lifecycleEvents = new ArrayList<>();

        KernelModule storage = new TestKernelModule(
            "kernel-storage",
            Set.of(),
            lifecycleEvents
        );

        KernelModule api = new TestKernelModule(
            "kernel-api",
            Set.of(new KernelDependency("kernel-storage", "1.0.0", KernelDependency.DependencyType.MODULE, false)),
            lifecycleEvents
        );

        registry.registerModule(storage);
        registry.registerModule(api);

        runPromise(registry::startAllModules);
        runPromise(registry::stopAllModules);

        assertThat(lifecycleEvents)
            .containsExactly("start:kernel-storage", "start:kernel-api", "stop:kernel-api", "stop:kernel-storage");
    }

    private static final class TestKernelModule implements KernelModule {
        private final String id;
        private final Set<KernelDependency> dependencies;
        private final List<String> lifecycleEvents;

        private TestKernelModule(String id, Set<KernelDependency> dependencies, List<String> lifecycleEvents) {
            this.id = id;
            this.dependencies = dependencies;
            this.lifecycleEvents = lifecycleEvents;
        }

        @Override
        public String getModuleId() {
            return id;
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
            // No-op for this end-to-end lifecycle test.
        }

        @Override
        public Promise<Void> start() {
            lifecycleEvents.add("start:" + id);
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            lifecycleEvents.add("stop:" + id);
            return Promise.complete();
        }

        @Override
        public HealthStatus getHealthStatus() {
            return HealthStatus.healthy();
        }
    }
}
