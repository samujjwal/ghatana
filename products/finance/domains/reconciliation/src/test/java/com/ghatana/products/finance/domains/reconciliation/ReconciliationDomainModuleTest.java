/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.reconciliation;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for ReconciliationDomainModule.
 *
 * @doc.type test
 * @doc.purpose Verify Reconciliation domain module behavior
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
@DisplayName("Reconciliation Domain Module Tests")
class ReconciliationDomainModuleTest {

    private ReconciliationDomainModule module;
    private KernelContext mockContext;

    @BeforeEach
    void setUp() {
        module = new ReconciliationDomainModule();
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
        assertThat(module.getModuleId()).isEqualTo("finance-reconciliation");
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
        assertThat(capabilities.stream()
            .map(KernelCapability::getId)
            .anyMatch(id -> id.equals("finance.trade.reconciliation")))
            .isTrue();
        assertThat(capabilities.stream()
            .map(KernelCapability::getId)
            .anyMatch(id -> id.equals("finance.position.reconciliation")))
            .isTrue();
    }

    @Test
    @DisplayName("Should declare all required dependencies")
    void shouldDeclareAllRequiredDependencies() {
        Set<KernelDependency> dependencies = module.getDependencies();

        assertThat(dependencies).isNotEmpty();
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
        module.start().get();

        HealthStatus status = module.getHealthStatus();
        assertThat(status.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("Should stop successfully and update health status")
    void shouldStopSuccessfullyAndUpdateHealthStatus() {
        module.initialize(mockContext);
        module.start().get();
        module.stop().get();

        HealthStatus status = module.getHealthStatus();
        assertThat(status.isHealthy()).isFalse();
    }
}
