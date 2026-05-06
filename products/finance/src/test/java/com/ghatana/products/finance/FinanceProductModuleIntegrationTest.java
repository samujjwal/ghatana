/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.ghatana.finance.service.TransactionService;
import com.ghatana.finance.kernel.FinanceCapabilities;
import com.ghatana.kernel.ai.AgentOrchestrator;
import com.ghatana.kernel.ai.AutonomyManager;
import com.ghatana.kernel.ai.ModelGovernanceService;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.products.finance.bff.FinanceBFF;
import com.ghatana.products.finance.shell.FinanceProductShell;
import io.activej.promise.Promise;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Integration tests for FinanceProductModule.
 *
 * <p>Tests the finance product module integration with kernel capabilities,
 * service registration, and cross-domain functionality. Verifies compliance
 * with kernel vision and proper separation of concerns.</p>
 *
 * @doc.type test
 * @doc.purpose Finance product module integration tests - kernel integration, services, domains
 * @doc.layer product
 * @doc.pattern IntegrationTest
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
public class FinanceProductModuleIntegrationTest extends EventloopTestBase {

    @Mock
    private KernelContext mockContext;

    @Mock
    private Executor mockExecutor;

    private FinanceProductModule module;

    @BeforeEach
    void setUp() {
        module = new FinanceProductModule();
    }

    @Test
    void testModuleIdentity() {
        assertThat(module.getModuleId()).isEqualTo("finance");
        assertThat(module.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void testFinanceSpecificCapabilities() {
        Set<KernelCapability> capabilities = module.getCapabilities();

        assertThat(capabilities)
            .hasSize(10)
            .contains(
                FinanceCapabilities.TRADE_PROCESSING,
                FinanceCapabilities.PORTFOLIO_MANAGEMENT,
                FinanceCapabilities.RISK_MANAGEMENT,
                FinanceCapabilities.COMPLIANCE_CHECKING,
                FinanceCapabilities.LEDGER_MANAGEMENT,
                KernelCapability.Core.USER_AUTHENTICATION,
                KernelCapability.Core.CONFIG_MANAGEMENT,
                KernelCapability.Core.EVENT_PROCESSING,
                KernelCapability.Core.OBSERVABILITY_FRAMEWORK,
                KernelCapability.Core.RESILIENCE_PATTERNS
            );
    }

    @Test
    void testKernelDependencies() {
        var dependencies = module.getDependencies();

        assertThat(dependencies)
            .hasSize(8)
            .anyMatch(dep -> dep.getDependencyId().equals("authentication"))
            .anyMatch(dep -> dep.getDependencyId().equals("config"))
            .anyMatch(dep -> dep.getDependencyId().equals("event-store"))
            .anyMatch(dep -> dep.getDependencyId().equals("audit"))
            .anyMatch(dep -> dep.getDependencyId().equals("resilience"))
            .anyMatch(dep -> dep.getDependencyId().equals("data.storage"))
            .anyMatch(dep -> dep.getDependencyId().equals("observability.framework"))
            .anyMatch(dep -> dep.getDependencyId().equals("security.framework"));
    }

    @Test
    void testInitialization() {
        module.initialize(mockContext);

        verify(mockContext).registerService(eq(FinanceAiRuntimeService.class), any(FinanceAiRuntimeService.class));
        verify(mockContext).registerService(eq(FinanceTransactionRuntimeService.class), any(FinanceTransactionRuntimeService.class));
        verify(mockContext).registerService(eq(AgentOrchestrator.class), any(FinanceAiRuntimeService.class));
        verify(mockContext).registerService(eq(ModelGovernanceService.class), any(FinanceAiRuntimeService.class));
        verify(mockContext).registerService(eq(AutonomyManager.class), any(FinanceAiRuntimeService.class));
        // productShell and bff are now registered in start() after transactionService is available
    }

    @Test
    void testLifecycle() throws Exception {
        module.initialize(mockContext);

        Promise<Void> startPromise = module.start();
        assertThat(startPromise).isNotNull();
        runPromise(() -> startPromise);

        verify(mockContext).registerService(eq(TransactionService.class), any(TransactionService.class));
        verify(mockContext).registerService(eq(FinanceProductShell.class), any(FinanceProductShell.class));
        verify(mockContext).registerService(eq(FinanceBFF.class), any(FinanceBFF.class));

        var healthStatus = module.getHealthStatus();
        assertThat(healthStatus.isHealthy()).isTrue();

        Promise<Void> stopPromise = module.stop();
        assertThat(stopPromise).isNotNull();
        runPromise(() -> stopPromise);
    }

    @Test
    void testHealthStatus() {
        var healthStatus = module.getHealthStatus();
        assertThat(healthStatus.isHealthy()).isFalse();
        assertThat(healthStatus.getMessage()).contains("degraded");
    }

    @Test
    void testKernelVisionCompliance() {
        assertThat(module.getModuleId()).matches("^[a-z0-9-._]+$");
        assertThat(module.getVersion()).matches("^\\d+\\.\\d+\\.\\d+$");

        Set<KernelCapability> capabilities = module.getCapabilities();

        assertThat(capabilities).anyMatch(cap -> cap.getCapabilityId().startsWith("finance."));
        assertThat(capabilities).anyMatch(cap ->
            cap.getCapabilityId().startsWith("user.")
                || cap.getCapabilityId().startsWith("config.")
                || cap.getCapabilityId().startsWith("event.")
                || cap.getCapabilityId().startsWith("audit.")
                || cap.getCapabilityId().startsWith("resilience.")
        );
    }

    @Test
    void testSeparationOfConcerns() {
        Set<KernelCapability> capabilities = module.getCapabilities();

        assertThat(capabilities).anyMatch(cap -> cap.getCapabilityId().startsWith("finance."));
        assertThat(capabilities).noneMatch(cap ->
            cap.getCapabilityId().startsWith("kernel.")
                || cap.getCapabilityId().startsWith("internal.")
        );
    }

    @Test
    void testDependencyOnKernelCapabilities() {
        var dependencies = module.getDependencies();

        assertThat(dependencies).anyMatch(dep ->
            dep.getDependencyId().equals("authentication")
                || dep.getDependencyId().equals("config")
                || dep.getDependencyId().equals("event-store")
                || dep.getDependencyId().equals("audit")
                || dep.getDependencyId().equals("resilience")
        );

        assertThat(dependencies).noneMatch(dep ->
            dep.getDependencyId().equals("database")
                || dep.getDependencyId().equals("http")
                || dep.getDependencyId().equals("data-cloud")
                || dep.getDependencyId().equals("aep")
        );
    }
}
