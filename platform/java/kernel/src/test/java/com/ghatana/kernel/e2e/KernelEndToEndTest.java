package com.ghatana.kernel.e2e;

import com.ghatana.finance.extension.*;
import com.ghatana.finance.kernel.FinanceKernelModule;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.DefaultKernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.phr.extension.HealthcareConsentKernelExtension;
import com.ghatana.phr.kernel.PhrKernelModule;
import com.ghatana.phr.plugin.FhirInteropKernelPlugin;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for complete kernel integration with PHR and Finance products.
 *
 * <p>Tests full system integration including kernel core, product modules,
 * extensions, and plugins working together.</p>
 *
 * @doc.type test
 * @doc.purpose End-to-end tests for kernel with PHR and Finance products
 * @doc.layer test
 * @author Ghatana Kernel Team
 */
@DisplayName("Kernel End-to-End Integration Tests")
class KernelEndToEndTest {

    private KernelRegistryImpl registry;
    private DefaultKernelContext context;
    private Eventloop eventloop;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
        eventloop = Eventloop.create();

        KernelConfigResolver configResolver = createMockConfigResolver();
        context = new DefaultKernelContext(registry, configResolver, eventloop, "1.0.0", "test");
        context.registerDependency(KernelConfigResolver.class, configResolver);
    }

    @Test
    @DisplayName("Should integrate PHR kernel module with full lifecycle")
    void shouldIntegratePhrKernelModuleWithFullLifecycle() {
        PhrKernelModule phrModule = new PhrKernelModule();

        // Register
        registry.registerModule(phrModule);
        assertTrue(registry.isModuleRegistered("phr-core"));

        // Initialize
        assertDoesNotThrow(() -> phrModule.initialize(context));

        // Start
        Promise<Void> startPromise = phrModule.start();
        assertDoesNotThrow(startPromise::getResult);

        // Health check
        HealthStatus status = phrModule.getHealthStatus();
        assertEquals(HealthStatus.Status.HEALTHY, status.getStatus());
        assertEquals(10, status.getChecks().size()); // 9 services + module

        // Stop
        Promise<Void> stopPromise = phrModule.stop();
        assertDoesNotThrow(stopPromise::getResult);
    }

    @Test
    @DisplayName("Should integrate Finance kernel module with full lifecycle")
    void shouldIntegrateFinanceKernelModuleWithFullLifecycle() {
        FinanceKernelModule financeModule = new FinanceKernelModule();

        // Register
        registry.registerModule(financeModule);
        assertTrue(registry.isModuleRegistered("finance-core"));

        // Initialize
        assertDoesNotThrow(() -> financeModule.initialize(context));

        // Start
        Promise<Void> startPromise = financeModule.start();
        assertDoesNotThrow(startPromise::getResult);

        // Health check
        HealthStatus status = financeModule.getHealthStatus();
        assertEquals(HealthStatus.Status.HEALTHY, status.getStatus());
        assertEquals(8, status.getChecks().size()); // 8 services

        // Stop
        Promise<Void> stopPromise = financeModule.stop();
        assertDoesNotThrow(stopPromise::getResult);
    }

    @Test
    @DisplayName("Should integrate both PHR and Finance modules together")
    void shouldIntegrateBothPhrAndFinanceModulesTogether() {
        PhrKernelModule phrModule = new PhrKernelModule();
        FinanceKernelModule financeModule = new FinanceKernelModule();

        // Register both
        registry.registerModule(phrModule);
        registry.registerModule(financeModule);

        assertTrue(registry.isModuleRegistered("phr-core"));
        assertTrue(registry.isModuleRegistered("finance-core"));

        // Initialize both
        phrModule.initialize(context);
        financeModule.initialize(context);

        // Start both in dependency order
        registry.startAllModules().getResult();

        // Verify both healthy
        HealthStatus phrStatus = phrModule.getHealthStatus();
        HealthStatus financeStatus = financeModule.getHealthStatus();

        assertEquals(HealthStatus.Status.HEALTHY, phrStatus.getStatus());
        assertEquals(HealthStatus.Status.HEALTHY, financeStatus.getStatus());

        // Aggregate health
        HealthStatus aggregate = registry.getAggregateHealthStatus();
        assertEquals(HealthStatus.Status.HEALTHY, aggregate.getStatus());

        // Stop both in reverse order
        registry.stopAllModules().getResult();
    }

    @Test
    @DisplayName("Should integrate PHR HealthcareConsent extension")
    void shouldIntegratePhrHealthcareConsentExtension() {
        PhrKernelModule phrModule = new PhrKernelModule();
        HealthcareConsentKernelExtension consentExtension = new HealthcareConsentKernelExtension();

        registry.registerModule(phrModule);

        // Initialize module first
        phrModule.initialize(context);

        // Extension callback
        consentExtension.onModuleInitialized(context);
        consentExtension.onModuleStarted(context);

        // Grant consent
        var consentPromise = consentExtension.grantConsent(
            "patient-e2e",
            HealthcareConsentKernelExtension.ConsentPurpose.TREATMENT,
            HealthcareConsentKernelExtension.ConsentScope.ALL_DATA,
            HealthcareConsentKernelExtension.ConsentDuration.ONE_YEAR
        );

        HealthcareConsentKernelExtension.ConsentRecord record = consentPromise.getResult();
        assertNotNull(record);
        assertEquals("patient-e2e", record.getPatientId());

        // Verify consent
        var verifyPromise = consentExtension.verifyConsent(
            "patient-e2e",
            HealthcareConsentKernelExtension.ConsentPurpose.TREATMENT,
            "EMR"
        );

        HealthcareConsentKernelExtension.ConsentVerification verification = verifyPromise.getResult();
        assertTrue(verification.isValid());
    }

    @Test
    @DisplayName("Should integrate FHIR Interop plugin")
    void shouldIntegrateFhirInteropPlugin() {
        FhirInteropKernelPlugin fhirPlugin = new FhirInteropKernelPlugin();

        registry.registerPlugin(fhirPlugin);

        // Verify plugin manifest
        assertEquals("fhir-interop-r4", fhirPlugin.getManifest().getPluginId());
        assertEquals("R4", fhirPlugin.getManifest().getCapability().getMetadataValue("fhir_version"));

        // Initialize
        fhirPlugin.initialize(context);

        // Install and start
        fhirPlugin.install().getResult();
        fhirPlugin.start().getResult();

        // Validate FHIR resource
        String patientJson = "{\"resourceType\":\"Patient\",\"id\":\"e2e-patient\"}";
        var validationPromise = fhirPlugin.validateResource("Patient", patientJson);
        var validationResult = validationPromise.getResult();

        assertTrue(validationResult.isValid());

        // Stop and uninstall
        fhirPlugin.stop().getResult();
        fhirPlugin.uninstall().getResult();
    }

    @Test
    @DisplayName("Should integrate Finance Dual Calendar extension")
    void shouldIntegrateFinanceDualCalendarExtension() {
        FinanceKernelModule financeModule = new FinanceKernelModule();
        DualCalendarKernelExtension calendarExtension = new DualCalendarKernelExtension();

        registry.registerModule(financeModule);
        financeModule.initialize(context);

        // Extension callbacks
        calendarExtension.onModuleInitialized(context);
        calendarExtension.onModuleStarted(context);

        // Convert BS to AD
        LocalDate adDate = calendarExtension.convertBsToAd(2081, 4, 15);
        assertNotNull(adDate);

        // Convert AD to BS
        DualCalendarKernelExtension.BsDate bsDate = calendarExtension.convertAdToBs(LocalDate.of(2024, 7, 29));
        assertNotNull(bsDate);
        assertTrue(bsDate.year >= 2080 && bsDate.year <= 2082);

        // Check leap year
        assertTrue(calendarExtension.isLeapYear(2024, DualCalendarKernelExtension.CalendarType.AD));
        assertFalse(calendarExtension.isLeapYear(2023, DualCalendarKernelExtension.CalendarType.AD));
    }

    @Test
    @DisplayName("Should integrate Finance Risk Management extension")
    void shouldIntegrateFinanceRiskManagementExtension() {
        FinanceKernelModule financeModule = new FinanceKernelModule();
        RiskManagementKernelExtension riskExtension = new RiskManagementKernelExtension();

        registry.registerModule(financeModule);
        financeModule.initialize(context);

        // Extension callbacks
        riskExtension.onModuleInitialized(context);
        riskExtension.onModuleStarted(context);

        // Calculate position risk
        var riskPromise = riskExtension.calculatePositionRisk(
            "pos-e2e",
            new BigDecimal("100"),
            new BigDecimal("150.00"),
            new BigDecimal("145.00")
        );

        RiskManagementKernelExtension.PositionRisk risk = riskPromise.getResult();
        assertNotNull(risk);
        assertEquals("pos-e2e", risk.getPositionId());
        assertTrue(risk.getUnrealizedPnL().compareTo(BigDecimal.ZERO) > 0);

        // Calculate portfolio risk
        Map<String, RiskManagementKernelExtension.PositionRisk> positions = Map.of(
            "pos-e2e", risk
        );

        var portfolioPromise = riskExtension.calculatePortfolioRisk("portfolio-e2e", positions);
        RiskManagementKernelExtension.PortfolioRisk portfolioRisk = portfolioPromise.getResult();

        assertNotNull(portfolioRisk);
        assertTrue(portfolioRisk.getPortfolioVaR().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should integrate Finance Compliance extension")
    void shouldIntegrateFinanceComplianceExtension() {
        FinanceKernelModule financeModule = new FinanceKernelModule();
        ComplianceKernelExtension complianceExtension = new ComplianceKernelExtension();

        registry.registerModule(financeModule);
        financeModule.initialize(context);

        // Extension callbacks
        complianceExtension.onModuleInitialized(context);
        complianceExtension.onModuleStarted(context);

        // Validate trade
        ComplianceKernelExtension.TradeDetails trade = new ComplianceKernelExtension.TradeDetails(
            "AAPL", "BUY", new BigDecimal("100"), new BigDecimal("150.00"), "trader-e2e"
        );

        var tradePromise = complianceExtension.validateTrade("trade-e2e", trade);
        ComplianceKernelExtension.ComplianceCheckResult tradeResult = tradePromise.getResult();

        assertTrue(tradeResult.isCompliant());

        // Validate PCI-DSS
        ComplianceKernelExtension.PaymentDetails payment = new ComplianceKernelExtension.PaymentDetails(
            "encrypted-pan", true, "***", "12/25"
        );

        var pciPromise = complianceExtension.validatePCICompliance("payment-e2e", payment);
        ComplianceKernelExtension.ComplianceCheckResult pciResult = pciPromise.getResult();

        assertTrue(pciResult.isCompliant());

        // Validate SOX
        Map<String, Object> soxData = Map.of(
            "cfo_approval", true,
            "internal_controls_tested", true
        );

        var soxPromise = complianceExtension.validateSOXControl("control-e2e", soxData);
        ComplianceKernelExtension.ComplianceCheckResult soxResult = soxPromise.getResult();

        assertTrue(soxResult.isCompliant());
    }

    @Test
    @DisplayName("Should handle full system with all products and extensions")
    void shouldHandleFullSystemWithAllProductsAndExtensions() {
        // Register all modules
        PhrKernelModule phrModule = new PhrKernelModule();
        FinanceKernelModule financeModule = new FinanceKernelModule();

        registry.registerModule(phrModule);
        registry.registerModule(financeModule);

        // Initialize all
        phrModule.initialize(context);
        financeModule.initialize(context);

        // Initialize extensions
        HealthcareConsentKernelExtension consentExt = new HealthcareConsentKernelExtension();
        FhirInteropKernelPlugin fhirPlugin = new FhirInteropKernelPlugin();
        DualCalendarKernelExtension calendarExt = new DualCalendarKernelExtension();
        RiskManagementKernelExtension riskExt = new RiskManagementKernelExtension();
        ComplianceKernelExtension complianceExt = new ComplianceKernelExtension();

        consentExt.onModuleInitialized(context);
        fhirPlugin.initialize(context);
        calendarExt.onModuleInitialized(context);
        riskExt.onModuleInitialized(context);
        complianceExt.onModuleInitialized(context);

        // Start all modules
        registry.startAllModules().getResult();

        // Start plugins
        fhirPlugin.install().getResult();
        fhirPlugin.start().getResult();

        // Start extensions
        consentExt.onModuleStarted(context);
        calendarExt.onModuleStarted(context);
        riskExt.onModuleStarted(context);
        complianceExt.onModuleStarted(context);

        // Verify all healthy
        HealthStatus aggregate = registry.getAggregateHealthStatus();
        assertEquals(HealthStatus.Status.HEALTHY, aggregate.getStatus());

        // Cross-product capability check
        Set<KernelCapability> allCapabilities = registry.getAllCapabilities();
        assertTrue(allCapabilities.stream().anyMatch(c -> c.getCapabilityId().equals("consent.management")));

        // Stop everything
        complianceExt.onModuleStopped(context);
        riskExt.onModuleStopped(context);
        calendarExt.onModuleStopped(context);
        fhirPlugin.stop().getResult();
        fhirPlugin.uninstall().getResult();
        consentExt.onModuleStopped(context);

        registry.stopAllModules().getResult();
    }

    @Test
    @DisplayName("Should maintain tenant isolation across products")
    void shouldMaintainTenantIsolationAcrossProducts() {
        // Create tenant contexts
        KernelTenantContext tenant1 = new KernelTenantContext(
            "tenant-1",
            KernelTenantContext.TenantType.ENTERPRISE,
            Map.of("region", "US"),
            Set.of("phr", "finance"),
            null,
            null
        );

        KernelTenantContext tenant2 = new KernelTenantContext(
            "tenant-2",
            KernelTenantContext.TenantType.ENTERPRISE,
            Map.of("region", "EU"),
            Set.of("phr"),
            null,
            null
        );

        context.registerTenantContext("tenant-1", tenant1);
        context.registerTenantContext("tenant-2", tenant2);

        // Verify tenant isolation
        assertEquals("tenant-1", context.getTenantContext("tenant-1").getTenantId());
        assertEquals("tenant-2", context.getTenantContext("tenant-2").getTenantId());

        // Different tenants should have different capabilities
        assertTrue(tenant1.getEnabledProducts().contains("finance"));
        assertFalse(tenant2.getEnabledProducts().contains("finance"));
    }

    // ==================== Test Helpers ====================

    private KernelConfigResolver createMockConfigResolver() {
        return new KernelConfigResolver() {
            @Override public <T> T resolve(String key, Class<T> type, KernelTenantContext tenantContext) {
                return null;
            }
            @Override public <T> java.util.Optional<T> resolveOptional(String key, Class<T> type, KernelTenantContext tenantContext) {
                return java.util.Optional.empty();
            }
            @Override public java.util.List<String> getConfigSources() {
                return java.util.List.of("test-source");
            }
            @Override public void reload() {}
        };
    }
}
