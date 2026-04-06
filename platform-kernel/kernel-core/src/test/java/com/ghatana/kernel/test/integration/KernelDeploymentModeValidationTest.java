package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelCapability.CapabilityType;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Deployment-mode validation tests — Backlog D3.
 *
 * <p>Verifies that the kernel registry (a generic, domain-neutral host) can operate
 * in three distinct deployment modes without cross-domain coupling:</p>
 *
 * <ol>
 *   <li><b>Independent — healthcare only</b>: PHR capabilities registered, no finance
 *       modules present. The kernel must start, serve PHR queries, and remain
 *       entirely free of finance-domain code paths.</li>
 *   <li><b>Independent — finance only</b>: Finance capabilities registered, no
 *       healthcare modules present. Mirror of the above.</li>
 *   <li><b>Shared-kernel</b>: Both PHR and finance modules coexist in a single
 *       registry instance. Capabilities must remain namespace-isolated (PHR IDs
 *       use {@code phr.*}, finance IDs use {@code finance.*}). No capability ID
 *       collision must occur.</li>
 * </ol>
 *
 * <p>Tests use local anonymous stub modules rather than the product modules in
 * {@code products/phr} and {@code products/finance}, because those subprojects
 * are not on the kernel test classpath. The stubs declare identical capability
 * IDs to the real modules, so the behavioural assertions remain valid.</p>
 *
 * <p>This test is intentionally synchronous (no ActiveJ Eventloop required) as
 * it exercises only the registration and discovery APIs of
 * {@link com.ghatana.kernel.registry.KernelRegistryImpl}.</p>
 *
 * @doc.type test
 * @doc.purpose Deployment mode isolation validation (Backlog D3) — independent vs shared-kernel
 * @doc.layer test
 * @doc.pattern Integration Test
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
@DisplayName("Kernel Deployment Mode Validation (Backlog D3)")
class KernelDeploymentModeValidationTest {

    // ── Core capabilities (domain-agnostic) ─────────────────────────────────

    private static final KernelCapability CORE_DATA_STORAGE = new KernelCapability(
        "data.storage", "Data Storage",
        "Core data storage — shared by all product modules",
        CapabilityType.DATA_MANAGEMENT, Map.of());

    private static final KernelCapability CORE_USER_AUTH = new KernelCapability(
        "user.authentication", "User Authentication",
        "Core user authentication — shared by all product modules",
        CapabilityType.SECURITY, Map.of());

    private static final KernelCapability CORE_WORKFLOW = new KernelCapability(
        "workflow.engine", "Workflow Engine",
        "Core workflow engine — shared by all product modules",
        CapabilityType.WORKFLOW, Map.of());

    // ── Healthcare (PHR) capabilities ───────────────────────────────────────

    private static final KernelCapability PHR_PATIENT_RECORDS = new KernelCapability(
        "phr.patient-records", "Patient Records",
        "PHR patient record management — healthcare domain",
        CapabilityType.BUSINESS_LOGIC, Map.of("domain", "healthcare"));

    private static final KernelCapability PHR_CONSENT_MANAGEMENT = new KernelCapability(
        "phr.consent-management", "Consent Management",
        "PHR consent lifecycle — healthcare domain",
        CapabilityType.COMPLIANCE, Map.of("domain", "healthcare"));

    private static final KernelCapability PHR_FHIR_INTEROP = new KernelCapability(
        "phr.fhir-interop", "FHIR Interoperability",
        "FHIR R4 resource exchange — healthcare domain",
        CapabilityType.INTEGRATION, Map.of("domain", "healthcare"));

    // ── Finance capabilities ─────────────────────────────────────────────────

    private static final KernelCapability FINANCE_TRADE_PROCESSING = new KernelCapability(
        "finance.trade-processing", "Trade Processing",
        "Finance trade order processing — finance domain",
        CapabilityType.BUSINESS_LOGIC, Map.of("domain", "finance"));

    private static final KernelCapability FINANCE_RISK_MANAGEMENT = new KernelCapability(
        "finance.risk-management", "Risk Management",
        "Finance risk assessment — finance domain",
        CapabilityType.BUSINESS_LOGIC, Map.of("domain", "finance"));

    private static final KernelCapability FINANCE_COMPLIANCE = new KernelCapability(
        "finance.compliance-checking", "Compliance Checking",
        "Finance regulatory compliance — finance domain",
        CapabilityType.COMPLIANCE, Map.of("domain", "finance"));

    // ── Helper: pre-started registry with core capabilities ─────────────────

    private KernelRegistryImpl freshRegistry() {
        KernelRegistryImpl registry = new KernelRegistryImpl();
        // Core capabilities are always present (provided by the kernel itself)
        registry.registerCapability(CORE_DATA_STORAGE);
        registry.registerCapability(CORE_USER_AUTH);
        registry.registerCapability(CORE_WORKFLOW);
        return registry;
    }

    // ── Stub module factory ──────────────────────────────────────────────────

    private static KernelModule stubModule(
            String id,
            Set<KernelCapability> capabilities,
            Set<KernelDependency> dependencies) {

        return new KernelModule() {
            @Override public String getModuleId()                       { return id; }
            @Override public String getVersion()                        { return "1.0.0"; }
            @Override public Set<KernelCapability> getCapabilities()    { return capabilities; }
            @Override public Set<KernelDependency> getDependencies()    { return dependencies; }
            @Override public void initialize(KernelContext ctx)         { /* no-op for deployment test */ }
            @Override public Promise<Void> start()                      { return Promise.complete(); }
            @Override public Promise<Void> stop()                       { return Promise.complete(); }
            @Override public HealthStatus getHealthStatus()             {
                return HealthStatus.builder()
                    .withStatus(HealthStatus.Status.HEALTHY)
                    .withMessage(id + " healthy").build();
            }
        };
    }

    /** Dependency declaration on a kernel CAPABILITY (by capability-ID string). */
    private static KernelDependency capDep(String capabilityId) {
        return new KernelDependency(capabilityId, "1.0.0",
            KernelDependency.DependencyType.CAPABILITY, false);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Mode 1 — Healthcare-only
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mode 1 — Healthcare-only (independent deployment)")
    class HealthcareOnlyMode {

        private KernelRegistryImpl registry;

        @BeforeEach
        void setUp() {
            registry = freshRegistry();

            KernelModule healthcareModule = stubModule(
                "phr-core",
                Set.of(PHR_PATIENT_RECORDS, PHR_CONSENT_MANAGEMENT, PHR_FHIR_INTEROP,
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );
            registry.registerModule(healthcareModule);
        }

        @Test
        @DisplayName("PHR module registers without any finance module present")
        void phrModuleRegistersWithoutFinance() {
            assertThat(registry.isModuleRegistered("phr-core"))
                .as("healthcare module must be registered")
                .isTrue();

            assertThat(registry.isModuleRegistered("finance-core"))
                .as("finance module must NOT be present in healthcare-only deployment")
                .isFalse();
        }

        @Test
        @DisplayName("All PHR capabilities are discoverable by ID")
        void allPhrCapabilitiesAreDiscoverable() {
            assertThat(registry.isCapabilityAvailable("phr.patient-records")).isTrue();
            assertThat(registry.isCapabilityAvailable("phr.consent-management")).isTrue();
            assertThat(registry.isCapabilityAvailable("phr.fhir-interop")).isTrue();
        }

        @Test
        @DisplayName("No finance capabilities leak into healthcare-only registry")
        void noFinanceCapabilitiesLeakIntoHealthcareOnlyRegistry() {
            assertThat(registry.isCapabilityAvailable("finance.trade-processing")).isFalse();
            assertThat(registry.isCapabilityAvailable("finance.risk-management")).isFalse();
            assertThat(registry.isCapabilityAvailable("finance.compliance-checking")).isFalse();
        }

        @Test
        @DisplayName("Module-by-capability search returns only PHR module")
        void capabilitySearchReturnsPhrModuleOnly() {
            List<KernelModule> modules = registry.getModulesByCapability(PHR_PATIENT_RECORDS);
            assertThat(modules)
                .hasSize(1)
                .extracting(KernelModule::getModuleId)
                .containsExactly("phr-core");
        }

        @Test
        @DisplayName("Dependency validation passes with core capabilities pre-registered")
        void dependencyValidationPassesWithCorePrereg() {
            // Validate a fresh PHR module (not yet registered) against the current registry state
            KernelModule candidate = stubModule(
                "phr-imaging",
                Set.of(PHR_FHIR_INTEROP),
                Set.of(capDep("data.storage"), capDep("user.authentication"))
            );
            assertThat(registry.validateDependencies(candidate))
                .as("PHR imaging module must validate — core capabilities are present")
                .isTrue();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Mode 2 — Finance-only
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mode 2 — Finance-only (independent deployment)")
    class FinanceOnlyMode {

        private KernelRegistryImpl registry;

        @BeforeEach
        void setUp() {
            registry = freshRegistry();

            KernelModule financeModule = stubModule(
                "finance-core",
                Set.of(FINANCE_TRADE_PROCESSING, FINANCE_RISK_MANAGEMENT, FINANCE_COMPLIANCE,
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );
            registry.registerModule(financeModule);
        }

        @Test
        @DisplayName("Finance module registers without any PHR module present")
        void financeModuleRegistersWithoutPhr() {
            assertThat(registry.isModuleRegistered("finance-core"))
                .as("finance module must be registered")
                .isTrue();

            assertThat(registry.isModuleRegistered("phr-core"))
                .as("PHR module must NOT be present in finance-only deployment")
                .isFalse();
        }

        @Test
        @DisplayName("All finance capabilities are discoverable by ID")
        void allFinanceCapabilitiesAreDiscoverable() {
            assertThat(registry.isCapabilityAvailable("finance.trade-processing")).isTrue();
            assertThat(registry.isCapabilityAvailable("finance.risk-management")).isTrue();
            assertThat(registry.isCapabilityAvailable("finance.compliance-checking")).isTrue();
        }

        @Test
        @DisplayName("No PHR capabilities leak into finance-only registry")
        void noPhrCapabilitiesLeakIntoFinanceOnlyRegistry() {
            assertThat(registry.isCapabilityAvailable("phr.patient-records")).isFalse();
            assertThat(registry.isCapabilityAvailable("phr.consent-management")).isFalse();
            assertThat(registry.isCapabilityAvailable("phr.fhir-interop")).isFalse();
        }

        @Test
        @DisplayName("Finance domain capability validation passes with core pre-registered")
        void financeDomainValidationPassesWithCorePrereg() {
            KernelModule candidate = stubModule(
                "finance-reconciliation",
                Set.of(FINANCE_COMPLIANCE),
                Set.of(capDep("data.storage"), capDep("workflow.engine"))
            );
            assertThat(registry.validateDependencies(candidate))
                .as("finance reconciliation module must validate — core capabilities are present")
                .isTrue();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Mode 3 — Shared-kernel (both domains in one registry)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mode 3 — Shared-kernel (PHR + Finance coexist)")
    class SharedKernelMode {

        private KernelRegistryImpl registry;

        @BeforeEach
        void setUp() {
            registry = freshRegistry();

            KernelModule healthcareModule = stubModule(
                "phr-core",
                Set.of(PHR_PATIENT_RECORDS, PHR_CONSENT_MANAGEMENT, PHR_FHIR_INTEROP,
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );
            KernelModule financeModule = stubModule(
                "finance-core",
                Set.of(FINANCE_TRADE_PROCESSING, FINANCE_RISK_MANAGEMENT, FINANCE_COMPLIANCE,
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );

            registry.registerModule(healthcareModule);
            registry.registerModule(financeModule);
        }

        @Test
        @DisplayName("Both modules coexist in the same registry without conflict")
        void bothModulesCoexistWithoutConflict() {
            assertThat(registry.isModuleRegistered("phr-core")).isTrue();
            assertThat(registry.isModuleRegistered("finance-core")).isTrue();
        }

        @Test
        @DisplayName("All domain capabilities are simultaneously discoverable")
        void allDomainCapabilitiesAreSimultaneouslyDiscoverable() {
            // PHR capabilities
            assertThat(registry.isCapabilityAvailable("phr.patient-records")).isTrue();
            assertThat(registry.isCapabilityAvailable("phr.consent-management")).isTrue();
            assertThat(registry.isCapabilityAvailable("phr.fhir-interop")).isTrue();

            // Finance capabilities
            assertThat(registry.isCapabilityAvailable("finance.trade-processing")).isTrue();
            assertThat(registry.isCapabilityAvailable("finance.risk-management")).isTrue();
            assertThat(registry.isCapabilityAvailable("finance.compliance-checking")).isTrue();

            // Core capabilities remain present
            assertThat(registry.isCapabilityAvailable("data.storage")).isTrue();
            assertThat(registry.isCapabilityAvailable("user.authentication")).isTrue();
            assertThat(registry.isCapabilityAvailable("workflow.engine")).isTrue();
        }

        @Test
        @DisplayName("PHR capability IDs do not collide with finance capability IDs")
        void phrAndFinanceCapabilityIdNamespacesDoNotCollide() {
            // Every registered capability must have a unique ID
            List<KernelCapability> allCapabilities = registry.getAllCapabilities();

            long uniqueCount = allCapabilities.stream()
                .map(KernelCapability::getCapabilityId)
                .distinct()
                .count();

            assertThat(uniqueCount)
                .as("All capability IDs must be unique — no namespace collision between PHR and Finance")
                .isEqualTo(allCapabilities.size());
        }

        @Test
        @DisplayName("PHR capability search returns only PHR module, not finance")
        void phrCapabilitySearchReturnsPhrModuleOnly() {
            List<KernelModule> modules = registry.getModulesByCapability(PHR_PATIENT_RECORDS);
            assertThat(modules)
                .extracting(KernelModule::getModuleId)
                .containsExactly("phr-core")
                .doesNotContain("finance-core");
        }

        @Test
        @DisplayName("Finance capability search returns only finance module, not PHR")
        void financeCapabilitySearchReturnsFinanceModuleOnly() {
            List<KernelModule> modules = registry.getModulesByCapability(FINANCE_TRADE_PROCESSING);
            assertThat(modules)
                .extracting(KernelModule::getModuleId)
                .containsExactly("finance-core")
                .doesNotContain("phr-core");
        }

        @Test
        @DisplayName("Duplicate module registration is rejected in shared-kernel mode")
        void duplicateModuleRegistrationRejectedInSharedKernel() {
            KernelModule duplicate = stubModule(
                "phr-core",   // same ID as already-registered module
                Set.of(PHR_PATIENT_RECORDS),
                Set.of()
            );
            assertThatThrownBy(() -> registry.registerModule(duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Cross-cutting: capability namespace contract
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Capability namespace isolation contract")
    class CapabilityNamespaceContract {

        @Test
        @DisplayName("PHR capability IDs must be prefixed with 'phr.'")
        void phrCapabilityIdsMustHavePhrPrefix() {
            Set<KernelCapability> phrCaps = Set.of(
                PHR_PATIENT_RECORDS, PHR_CONSENT_MANAGEMENT, PHR_FHIR_INTEROP);

            assertThat(phrCaps)
                .allSatisfy(cap ->
                    assertThat(cap.getCapabilityId())
                        .as("PHR capability '%s' must start with 'phr.' per namespace contract",
                            cap.getCapabilityId())
                        .startsWith("phr."));
        }

        @Test
        @DisplayName("Finance capability IDs must be prefixed with 'finance.'")
        void financeCapabilityIdsMustHaveFinancePrefix() {
            Set<KernelCapability> financeCaps = Set.of(
                FINANCE_TRADE_PROCESSING, FINANCE_RISK_MANAGEMENT, FINANCE_COMPLIANCE);

            assertThat(financeCaps)
                .allSatisfy(cap ->
                    assertThat(cap.getCapabilityId())
                        .as("Finance capability '%s' must start with 'finance.' per namespace contract",
                            cap.getCapabilityId())
                        .startsWith("finance."));
        }

        @Test
        @DisplayName("Core capability IDs must NOT be prefixed with any product domain name")
        void coreCapabilityIdsMustBeGeneric() {
            Set<KernelCapability> coreCaps = Set.of(
                CORE_DATA_STORAGE, CORE_USER_AUTH, CORE_WORKFLOW);

            Set<String> productPrefixes = Set.of("phr.", "finance.", "aura.", "flashit.");

            assertThat(coreCaps).allSatisfy(cap ->
                productPrefixes.forEach(prefix ->
                    assertThat(cap.getCapabilityId())
                        .as("Core capability '%s' must not carry a product-domain prefix",
                            cap.getCapabilityId())
                        .doesNotStartWith(prefix)));
        }

        @Test
        @DisplayName("Missing required module dependency detected by validator")
        void missingRequiredModuleFailsValidation() {
            KernelRegistryImpl registry = freshRegistry();

            // module-a needs module-b (MODULE dependency), but module-b is not registered
            KernelModule moduleA = stubModule(
                "module-a",
                Set.of(PHR_PATIENT_RECORDS),
                Set.of(new KernelDependency(
                    "module-b", "1.0.0",
                    KernelDependency.DependencyType.MODULE, false))
            );

            List<String> errors = registry.getDependencyValidationErrors(moduleA);
            assertThat(errors)
                .as("Validator must report missing required MODULE dependency")
                .hasSize(1)
                .first().asString()
                .contains("module-b");
        }

        @Test
        @DisplayName("Optional module dependency silently absent is allowed by validator")
        void optionalMissingModulePassesValidation() {
            KernelRegistryImpl registry = freshRegistry();

            KernelModule moduleA = stubModule(
                "module-a",
                Set.of(PHR_PATIENT_RECORDS),
                Set.of(new KernelDependency(
                    "module-optional", "1.0.0",
                    KernelDependency.DependencyType.MODULE, true))
            );

            // Optional dependency is absent → validation must still pass (no errors)
            assertThat(registry.validateDependencies(moduleA))
                .as("Optional dependency absent must not block registration")
                .isTrue();
        }
    }
}
