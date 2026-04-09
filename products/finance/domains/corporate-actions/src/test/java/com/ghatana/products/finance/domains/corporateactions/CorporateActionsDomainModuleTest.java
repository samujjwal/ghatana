/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.corporateactions;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for CorporateActionsDomainModule.
 *
 * @doc.type test
 * @doc.purpose Verify Corporate Actions domain module behavior
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
@DisplayName("Corporate Actions Domain Module Tests")
class CorporateActionsDomainModuleTest {

    private CorporateActionsDomainModule module;
    private KernelContext mockContext;

    @BeforeEach
    void setUp() {
        module = new CorporateActionsDomainModule();
        mockContext = mock(KernelContext.class);
    }

    @Test
    @DisplayName("Should implement KernelModule interface")
    void shouldImplementKernelModule() {
        assertThat(module).isInstanceOf(KernelModule.class);
    }

    @Test
    @DisplayName("Should return correct module ID")
    void shouldReturnCorrectModuleId() {
        assertThat(module.getModuleId()).isEqualTo("finance-corporate-actions");
    }

    @Test
    @DisplayName("Should return correct version")
    void shouldReturnCorrectVersion() {
        assertThat(module.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Should declare all required capabilities")
    void shouldDeclareAllRequiredCapabilities() {
        Set<KernelCapability> capabilities = module.getCapabilities();

        assertThat(capabilities).isNotEmpty();

        // Check for specific product capabilities
        assertThat(capabilities.stream()
            .map(KernelCapability::getCapabilityId)
            .anyMatch(id -> id.equals("finance.corporate.actions.processing")))
            .isTrue();
        assertThat(capabilities.stream()
            .map(KernelCapability::getCapabilityId)
            .anyMatch(id -> id.equals("finance.dividend.management")))
            .isTrue();
        assertThat(capabilities.stream()
            .map(KernelCapability::getCapabilityId)
            .anyMatch(id -> id.equals("finance.event.notification")))
            .isTrue();
    }

    @Test
    @DisplayName("Should include core kernel capabilities")
    void shouldIncludeCoreKernelCapabilities() {
        Set<KernelCapability> capabilities = module.getCapabilities();

        assertThat(capabilities.stream()
            .map(KernelCapability::getCapabilityId)
            .anyMatch(id -> id.equals("data.storage")))
            .isTrue();
        assertThat(capabilities.stream()
            .map(KernelCapability::getCapabilityId)
            .anyMatch(id -> id.equals("event.processing")))
            .isTrue();
    }

    @Test
    @DisplayName("Should declare all required dependencies")
    void shouldDeclareAllRequiredDependencies() {
        Set<KernelDependency> dependencies = module.getDependencies();

        assertThat(dependencies).isNotEmpty();

        assertThat(dependencies.stream()
            .map(KernelDependency::getCapabilityId)
            .anyMatch(id -> id.equals("user.authentication")))
            .isTrue();
        assertThat(dependencies.stream()
            .map(KernelDependency::getCapabilityId)
            .anyMatch(id -> id.equals("data.storage")))
            .isTrue();
    }

    @Test
    @DisplayName("Should declare dependency on OMS")
    void shouldDeclareDependencyOnOms() {
        Set<KernelDependency> dependencies = module.getDependencies();

        assertThat(dependencies.stream()
            .map(KernelDependency::getCapabilityId)
            .anyMatch(id -> id.equals("finance.reference.data")))
            .isTrue();
    }

    @Test
    @DisplayName("Should declare dependency on PMS")
    void shouldDeclareDependencyOnPms() {
        Set<KernelDependency> dependencies = module.getDependencies();

        assertThat(dependencies.stream()
            .map(KernelDependency::getCapabilityId)
            .anyMatch(id -> id.equals("observability.framework")))
            .isTrue();
    }

    @Test
    @DisplayName("Should initialize without errors")
    void shouldInitializeWithoutErrors() {
        module.initialize(mockContext);
    }

    @Test
    @DisplayName("Should start successfully and update health status")
    void shouldStartSuccessfullyAndUpdateHealthStatus() {
        module.initialize(mockContext);
        module.start();

        HealthStatus status = module.getHealthStatus();
        assertThat(status.isHealthy()).isTrue();
        assertThat(status.getMessage()).contains("operational");
    }

    @Test
    @DisplayName("Should stop successfully and update health status")
    void shouldStopSuccessfullyAndUpdateHealthStatus() {
        module.initialize(mockContext);
        module.start();
        module.stop();

        HealthStatus status = module.getHealthStatus();
        assertThat(status.isHealthy()).isFalse();
        assertThat(status.getMessage()).contains("not started");
    }

    @Test
    @DisplayName("Should report unhealthy before start")
    void shouldReportUnhealthyBeforeStart() {
        module.initialize(mockContext);

        HealthStatus status = module.getHealthStatus();
        assertThat(status.isHealthy()).isFalse();
    }
}
