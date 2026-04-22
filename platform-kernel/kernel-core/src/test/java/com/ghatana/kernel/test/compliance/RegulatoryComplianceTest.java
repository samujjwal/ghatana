package com.ghatana.kernel.test.compliance;

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

/** Regulatory compliance validation tests for kernel dependency policy. */
@DisplayName("Regulatory Compliance Tests")
class RegulatoryComplianceTest {

    @Test
    @DisplayName("D-5: required external service dependencies are rejected when unresolved")
    void shouldRejectMissingRequiredExternalServiceDependency() {
        KernelRegistryImpl registry = new KernelRegistryImpl();

        KernelModule complianceModule = module(Set.of(
            new KernelDependency("audit-ledger", "1.0.0", KernelDependency.DependencyType.EXTERNAL_SERVICE, false)
        ));

        assertThatThrownBy(() -> registry.registerModule(complianceModule))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Required external service not available: audit-ledger");
    }

    @Test
    @DisplayName("D-5: optional external dependencies do not block registration")
    void shouldAllowOptionalExternalServiceDependency() {
        KernelRegistryImpl registry = new KernelRegistryImpl();

        KernelModule complianceModule = module(Set.of(
            new KernelDependency("audit-ledger", "1.0.0", KernelDependency.DependencyType.EXTERNAL_SERVICE, true)
        ));

        assertThatCode(() -> registry.registerModule(complianceModule)).doesNotThrowAnyException();
    }

    private static KernelModule module(Set<KernelDependency> dependencies) {
        return new KernelModule() {
            @Override
            public String getModuleId() {
                return "kernel-compliance";
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
