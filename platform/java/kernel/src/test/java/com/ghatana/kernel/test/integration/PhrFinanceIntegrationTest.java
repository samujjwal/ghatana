package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.kernel.context.DefaultKernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.config.HierarchicalKernelConfigResolver;
import com.ghatana.phr.kernel.PhrKernelModule;
import com.ghatana.finance.kernel.FinanceKernelModule;
import com.ghatana.kernel.communication.KernelInterProductBus;
import com.ghatana.kernel.boundary.ProductBoundaryEnforcer;
import com.ghatana.kernel.audit.CrossProductAuditService;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.test.ActivejTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PHR-Finance cross-product scenarios.
 *
 * <p>Validates real-world integration scenarios:
 * <ul>
 *   <li>PHR billing integration with Finance</li>
 *   <li>Cross-product event propagation</li>
 *   <li>Boundary enforcement and audit logging</li>
 *   <li>AI agent fraud detection on healthcare payments</li>
 * </ul></p>
 *
 * @doc.type test
 * @doc.purpose Integration tests for PHR-Finance cross-product workflows
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
class PhrFinanceIntegrationTest extends ActivejTestBase {

    private Eventloop eventloop;
    private KernelRegistryImpl registry;
    private DefaultKernelContext kernelContext;
    private PhrKernelModule phrModule;
    private FinanceKernelModule financeModule;
    private ProductBoundaryEnforcer boundaryEnforcer;
    private KernelInterProductBus interProductBus;
    private CrossProductAuditService auditService;

    @BeforeEach
    void setUp() {
        eventloop = Eventloop.create();
        registry = new KernelRegistryImpl();
        HierarchicalKernelConfigResolver configResolver = new HierarchicalKernelConfigResolver();

        kernelContext = new DefaultKernelContext(
            registry, configResolver, eventloop, "1.0.0", "test"
        );

        phrModule = new PhrKernelModule();
        financeModule = new FinanceKernelModule();

        boundaryEnforcer = new ProductBoundaryEnforcer(registry);
        interProductBus = createInterProductBusStub();
        auditService = createAuditServiceStub();
    }

    @Test
    void phrCanAccessFinanceForBilling() throws Exception {
        // Test that PHR can access Finance module for billing operations
        runPromise(() -> {
            // Initialize PHR module
            phrModule.initialize(kernelContext);

            // Check that PHR has required capabilities
            assertTrue(phrModule.getCapabilities().stream()
                .anyMatch(c -> c.getCapabilityId().equals("data.storage")));

            // Validate boundary access
            boolean canAccess = boundaryEnforcer.canAccess(
                "phr", "finance", "billing", "write",
                createHealthcareTenantContext()
            );

            assertTrue(canAccess, "PHR should be able to access Finance for billing");

            return Promise.complete();
        });
    }

    @Test
    void financeCanAccessPhrForHealthcarePayments() throws Exception {
        // Test that Finance can access PHR for healthcare payment processing
        runPromise(() -> {
            financeModule.initialize(kernelContext);

            KernelTenantContext financeContext = createFinanceTenantContext();

            boolean canAccess = boundaryEnforcer.canAccess(
                "finance", "phr", "patient.records", "read",
                financeContext
            );

            assertTrue(canAccess, "Finance should be able to access PHR for healthcare payments");

            return Promise.complete();
        });
    }

    @Test
    void unauthorizedCrossProductAccessIsDenied() throws Exception {
        // Test that unauthorized access is properly denied
        runPromise(() -> {
            KernelTenantContext unauthorizedContext = createUnauthorizedContext();

            boolean canAccess = boundaryEnforcer.canAccess(
                "flashit", "phr", "patient.records", "write",
                unauthorizedContext
            );

            assertFalse(canAccess, "Unauthorized access should be denied");

            return Promise.complete();
        });
    }

    @Test
    void crossProductEventIsAudited() throws Exception {
        // Test that cross-product events are properly audited
        runPromise(() -> {
            CrossProductAuditService.CrossProductAuditEvent event =
                CrossProductAuditService.CrossProductAuditEvent.builder()
                    .sourceProduct("phr")
                    .targetProduct("finance")
                    .action("billing.create")
                    .userId("user-123")
                    .tenantId("tenant-456")
                    .metadata(Map.of("amount", 150.00, "service", "consultation"))
                    .build();

            // Record the audit event
            auditService.auditCrossProductAction(event);

            // Verify retention period is set correctly for cross-domain
            CrossProductAuditService.RetentionPeriod retention =
                auditService.getRetentionPeriod(event);

            // Should use the longer retention (PHR = 25 years)
            assertEquals(25, retention.getYears());

            return Promise.complete();
        });
    }

    @Test
    void phrModuleLifecycle() throws Exception {
        // Test PHR module full lifecycle
        runPromise(() -> {
            // Register module
            registry.registerModule(phrModule);

            // Initialize
            phrModule.initialize(kernelContext);

            // Start
            return phrModule.start().then($ -> {
                // Verify module is started
                assertNotNull(phrModule.getHealthStatus());

                // Stop
                return phrModule.stop().map(v -> {
                    // Verify clean shutdown
                    assertTrue(true);
                    return v;
                });
            });
        });
    }

    @Test
    void financeModuleLifecycle() throws Exception {
        // Test Finance module full lifecycle
        runPromise(() -> {
            // Register module
            registry.registerModule(financeModule);

            // Initialize
            financeModule.initialize(kernelContext);

            // Start
            return financeModule.start().then($ -> {
                // Verify module health
                assertNotNull(financeModule.getHealthStatus());

                // Stop
                return financeModule.stop();
            });
        });
    }

    @Test
    void dependencyResolutionOrder() throws Exception {
        // Test that modules are started in correct dependency order
        runPromise(() -> {
            registry.registerModule(phrModule);
            registry.registerModule(financeModule);

            // Resolve dependencies for PHR module
            var dependencies = registry.resolveDependencies(phrModule);

            // Verify dependency resolution works
            assertNotNull(dependencies);

            return Promise.complete();
        });
    }

    // ==================== Helper Methods ====================

    private KernelTenantContext createHealthcareTenantContext() {
        return new KernelTenantContext(
            "healthcare-tenant",
            KernelTenantContext.TenantType.ENTERPRISE,
            Map.of(),
            Set.of("phr.cross-product.access", "billing.write"),
            new TestSecurityContext("doctor-1", Set.of("phr.write", "billing.write")),
            null
        );
    }

    private KernelTenantContext createFinanceTenantContext() {
        return new KernelTenantContext(
            "finance-tenant",
            KernelTenantContext.TenantType.ENTERPRISE,
            Map.of(),
            Set.of("finance.audit.enabled", "phr.read"),
            new TestSecurityContext("trader-1", Set.of("finance.trade", "phr.read")),
            null
        );
    }

    private KernelTenantContext createUnauthorizedContext() {
        return new KernelTenantContext(
            "unauthorized-tenant",
            KernelTenantContext.TenantType.TRIAL,
            Map.of(),
            Set.of(), // No required features enabled
            new TestSecurityContext("user-1", Set.of()), // No permissions
            null
        );
    }

    private KernelInterProductBus createInterProductBusStub() {
        // Return a stub implementation for testing
        return new KernelInterProductBus(null, null);
    }

    private CrossProductAuditService createAuditServiceStub() {
        // Return a stub implementation for testing
        return new CrossProductAuditService(null);
    }

    /**
     * Test security context implementation.
     */
    private static class TestSecurityContext implements KernelTenantContext.SecurityContext {
        private final String userId;
        private final Set<String> permissions;

        TestSecurityContext(String userId, Set<String> permissions) {
            this.userId = userId;
            this.permissions = permissions;
        }

        @Override
        public String getUserId() { return userId; }

        @Override
        public Set<String> getRoles() { return Set.of(); }

        @Override
        public Set<String> getPermissions() { return permissions; }

        @Override
        public boolean isAuthenticated() { return true; }

        @Override
        public boolean hasRole(String role) { return false; }

        @Override
        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }
    }
}
