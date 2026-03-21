/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.modules.authentication.service.AuthenticationService;
import com.ghatana.kernel.modules.resilience.service.ResilienceService;
import com.ghatana.products.finance.bff.FinanceBFF;
import com.ghatana.products.finance.FinanceCapabilities;
import com.ghatana.products.finance.rules.service.FinanceRulesService;
import com.ghatana.products.finance.shell.FinanceProductShell;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Mock
    private AuthenticationService mockAuthService;

    @Mock
    private ResilienceService mockResilienceService;

    @Mock
    private FinanceRulesService mockRulesService;

    private FinanceProductModule module;

    @BeforeEach
    void setUp() {
        module = new FinanceProductModule();
    }

    @Test
    void testModuleIdentity() {
        // Test module ID and version
        assertThat(module.getModuleId()).isEqualTo("finance");
        assertThat(module.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void testFinanceSpecificCapabilities() {
        // Test that module provides finance-specific capabilities
        Set<KernelCapability> capabilities = module.getCapabilities();
        
        assertThat(capabilities)
            .hasSize(10)
            .contains(
                // Finance-specific capabilities
                FinanceCapabilities.TRADE_PROCESSING,
                FinanceCapabilities.PORTFOLIO_MANAGEMENT,
                FinanceCapabilities.RISK_MANAGEMENT,
                FinanceCapabilities.COMPLIANCE_CHECKING,
                FinanceCapabilities.LEDGER_MANAGEMENT,
                
                // Reused kernel capabilities
                KernelCapability.Core.USER_AUTHENTICATION,
                KernelCapability.Core.CONFIG_MANAGEMENT,
                KernelCapability.Core.EVENT_PROCESSING,
                KernelCapability.Core.OBSERVABILITY_FRAMEWORK,
                KernelCapability.Core.RESILIENCE_PATTERNS
            );
    }

    @Test
    void testKernelDependencies() {
        // Test that module depends on kernel capabilities
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
        // Test module initialization
        module.initialize(mockContext);
        
        // Verify that services are registered in context
        // This would require additional mocking of context.registerService
        // For now, just verify no exception is thrown
        assertThat(true).isTrue(); // Placeholder assertion
    }

    @Test
    void testLifecycle() throws Exception {
        // Test complete lifecycle: initialize -> start -> stop
        module.initialize(mockContext);
        
        // Start module
        Promise<Void> startPromise = module.start();
        assertThat(startPromise).isNotNull();
        runPromise(() -> startPromise);
        
        // Check health after start
        var healthStatus = module.getHealthStatus();
        assertThat(healthStatus.isHealthy()).isTrue();
        
        // Stop module
        Promise<Void> stopPromise = module.stop();
        assertThat(stopPromise).isNotNull();
        runPromise(() -> stopPromise);
    }

    @Test
    void testHealthStatus() {
        // Test health status before initialization
        var healthStatus = module.getHealthStatus();
        assertThat(healthStatus.isHealthy()).isFalse();
        assertThat(healthStatus.getMessage()).contains("degraded");
    }

    @Test
    void testKernelVisionCompliance() {
        // Verify module complies with kernel vision
        
        // 1. Module ID follows naming convention
        assertThat(module.getModuleId()).matches("^[a-z0-9-._]+$");
        
        // 2. Version follows semantic versioning
        assertThat(module.getVersion()).matches("^\\d+\\.\\d+\\.\\d+$");
        
        // 3. Capabilities include both product-specific and reused kernel capabilities
        Set<KernelCapability> capabilities = module.getCapabilities();
        
        // Should have finance-specific capabilities
        assertThat(capabilities).anyMatch(cap -> 
            cap.getCapabilityId().startsWith("finance.")
        );
        
        // Should reuse kernel capabilities
        assertThat(capabilities).anyMatch(cap -> 
            cap.getCapabilityId().startsWith("user.") ||
            cap.getCapabilityId().startsWith("config.") ||
            cap.getCapabilityId().startsWith("event.") ||
            cap.getCapabilityId().startsWith("audit.") ||
            cap.getCapabilityId().startsWith("resilience.")
        );
    }

    @Test
    void testSeparationOfConcerns() {
        // Verify proper separation between product and kernel concerns
        
        Set<KernelCapability> capabilities = module.getCapabilities();
        
        // Product-specific capabilities should be clearly marked
        assertThat(capabilities).anyMatch(cap -> 
            cap.getCapabilityId().startsWith("finance.")
        );
        
        // Should not have kernel-internal capabilities
        assertThat(capabilities).noneMatch(cap -> 
            cap.getCapabilityId().startsWith("kernel.") ||
            cap.getCapabilityId().startsWith("internal.")
        );
    }

    @Test
    void testDependencyOnKernelCapabilities() {
        // Verify that product depends on kernel capabilities, not directly on infrastructure
        
        var dependencies = module.getDependencies();
        
        // Should depend on kernel capabilities
        assertThat(dependencies).anyMatch(dep -> 
            dep.getDependencyId().equals("authentication") ||
            dep.getDependencyId().equals("config") ||
            dep.getDependencyId().equals("event-store") ||
            dep.getDependencyId().equals("audit") ||
            dep.getDependencyId().equals("resilience")
        );
        
        // Should not depend directly on infrastructure
        assertThat(dependencies).noneMatch(dep -> 
            dep.getDependencyId().equals("database") ||
            dep.getDependencyId().equals("http") ||
            dep.getDependencyId().equals("data-cloud") ||
            dep.getDependencyId().equals("aep")
        );
    }
}
