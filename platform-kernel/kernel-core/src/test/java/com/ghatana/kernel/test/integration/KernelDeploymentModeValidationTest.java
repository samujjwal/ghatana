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
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Deployment-mode validation tests — Backlog D3.
 *
 * <p>Verifies that the kernel registry (a generic, domain-neutral host) can operate // GH-90000
 * in three distinct deployment modes without cross-domain coupling:</p>
 *
 * <ol>
 *   <li><b>Independent — healthcare only</b>: PHR capabilities registered, no finance
 *       modules present. The kernel must start, serve PHR queries, and remain
 *       entirely free of finance-domain code paths.</li>
 *   <li><b>Independent — finance only</b>: Finance capabilities registered, no
 *       healthcare modules present. Mirror of the above.</li>
 *   <li><b>Shared-kernel</b>: Both PHR and finance modules coexist in a single
 *       registry instance. Capabilities must remain namespace-isolated (PHR IDs // GH-90000
 *       use {@code phr.*}, finance IDs use {@code finance.*}). No capability ID
 *       collision must occur.</li>
 * </ol>
 *
 * <p>Tests use local anonymous stub modules rather than the product modules in
 * {@code products/phr} and {@code products/finance}, because those subprojects
 * are not on the kernel test classpath. The stubs declare identical capability
 * IDs to the real modules, so the behavioural assertions remain valid.</p>
 *
 * <p>This test is intentionally synchronous (no ActiveJ Eventloop required) as // GH-90000
 * it exercises only the registration and discovery APIs of
 * {@link com.ghatana.kernel.registry.KernelRegistryImpl}.</p>
 *
 * @doc.type test
 * @doc.purpose Deployment mode isolation validation (Backlog D3) — independent vs shared-kernel // GH-90000
 * @doc.layer test
 * @doc.pattern Integration Test
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
@DisplayName("Kernel Deployment Mode Validation (Backlog D3)")
class KernelDeploymentModeValidationTest {

    // ── Core capabilities (domain-agnostic) ───────────────────────────────── // GH-90000

    private static final KernelCapability CORE_DATA_STORAGE = new KernelCapability( // GH-90000
        "data.storage", "Data Storage",
        "Core data storage — shared by all product modules",
        CapabilityType.DATA_MANAGEMENT, Map.of()); // GH-90000

    private static final KernelCapability CORE_USER_AUTH = new KernelCapability( // GH-90000
        "user.authentication", "User Authentication",
        "Core user authentication — shared by all product modules",
        CapabilityType.SECURITY, Map.of()); // GH-90000

    private static final KernelCapability CORE_WORKFLOW = new KernelCapability( // GH-90000
        "workflow.engine", "Workflow Engine",
        "Core workflow engine — shared by all product modules",
        CapabilityType.WORKFLOW, Map.of()); // GH-90000

    // ── Healthcare (PHR) capabilities ─────────────────────────────────────── // GH-90000

    private static final KernelCapability PHR_PATIENT_RECORDS = new KernelCapability( // GH-90000
        "phr.patient-records", "Patient Records",
        "PHR patient record management — healthcare domain",
        CapabilityType.BUSINESS_LOGIC, Map.of("domain", "healthcare")); // GH-90000

    private static final KernelCapability PHR_CONSENT_MANAGEMENT = new KernelCapability( // GH-90000
        "phr.consent-management", "Consent Management",
        "PHR consent lifecycle — healthcare domain",
        CapabilityType.COMPLIANCE, Map.of("domain", "healthcare")); // GH-90000

    private static final KernelCapability PHR_FHIR_INTEROP = new KernelCapability( // GH-90000
        "phr.fhir-interop", "FHIR Interoperability",
        "FHIR R4 resource exchange — healthcare domain",
        CapabilityType.INTEGRATION, Map.of("domain", "healthcare")); // GH-90000

    // ── Finance capabilities ─────────────────────────────────────────────────

    private static final KernelCapability FINANCE_TRADE_PROCESSING = new KernelCapability( // GH-90000
        "finance.trade-processing", "Trade Processing",
        "Finance trade order processing — finance domain",
        CapabilityType.BUSINESS_LOGIC, Map.of("domain", "finance")); // GH-90000

    private static final KernelCapability FINANCE_RISK_MANAGEMENT = new KernelCapability( // GH-90000
        "finance.risk-management", "Risk Management",
        "Finance risk assessment — finance domain",
        CapabilityType.BUSINESS_LOGIC, Map.of("domain", "finance")); // GH-90000

    private static final KernelCapability FINANCE_COMPLIANCE = new KernelCapability( // GH-90000
        "finance.compliance-checking", "Compliance Checking",
        "Finance regulatory compliance — finance domain",
        CapabilityType.COMPLIANCE, Map.of("domain", "finance")); // GH-90000

    // ── Helper: pre-started registry with core capabilities ─────────────────

    private KernelRegistryImpl freshRegistry() { // GH-90000
        KernelRegistryImpl registry = new KernelRegistryImpl(); // GH-90000
        // Core capabilities are always present (provided by the kernel itself) // GH-90000
        registry.registerCapability(CORE_DATA_STORAGE); // GH-90000
        registry.registerCapability(CORE_USER_AUTH); // GH-90000
        registry.registerCapability(CORE_WORKFLOW); // GH-90000
        return registry;
    }

    // ── Stub module factory ──────────────────────────────────────────────────

    private static KernelModule stubModule( // GH-90000
            String id,
            Set<KernelCapability> capabilities,
            Set<KernelDependency> dependencies) {

        return new KernelModule() { // GH-90000
            @Override public String getModuleId()                       { return id; } // GH-90000
            @Override public String getVersion()                        { return "1.0.0"; } // GH-90000
            @Override public Set<KernelCapability> getCapabilities()    { return capabilities; } // GH-90000
            @Override public Set<KernelDependency> getDependencies()    { return dependencies; } // GH-90000
            @Override public void initialize(KernelContext ctx)         { /* no-op for deployment test */ } // GH-90000
            @Override public Promise<Void> start()                      { return Promise.complete(); } // GH-90000
            @Override public Promise<Void> stop()                       { return Promise.complete(); } // GH-90000
            @Override public HealthStatus getHealthStatus()             { // GH-90000
                return HealthStatus.builder() // GH-90000
                    .withStatus(HealthStatus.Status.HEALTHY) // GH-90000
                    .withMessage(id + " healthy").build(); // GH-90000
            }
        };
    }

    /** Dependency declaration on a kernel CAPABILITY (by capability-ID string). */ // GH-90000
    private static KernelDependency capDep(String capabilityId) { // GH-90000
        return new KernelDependency(capabilityId, "1.0.0", // GH-90000
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
        void setUp() { // GH-90000
            registry = freshRegistry(); // GH-90000

            KernelModule healthcareModule = stubModule( // GH-90000
                "phr-core",
                Set.of(PHR_PATIENT_RECORDS, PHR_CONSENT_MANAGEMENT, PHR_FHIR_INTEROP, // GH-90000
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );
            registry.registerModule(healthcareModule); // GH-90000
        }

        @Test
        @DisplayName("PHR module registers without any finance module present")
        void phrModuleRegistersWithoutFinance() { // GH-90000
            assertThat(registry.isModuleRegistered("phr-core"))
                .as("healthcare module must be registered")
                .isTrue(); // GH-90000

            assertThat(registry.isModuleRegistered("finance-core"))
                .as("finance module must NOT be present in healthcare-only deployment")
                .isFalse(); // GH-90000
        }

        @Test
        @DisplayName("All PHR capabilities are discoverable by ID")
        void allPhrCapabilitiesAreDiscoverable() { // GH-90000
            assertThat(registry.isCapabilityAvailable("phr.patient-records")).isTrue();
            assertThat(registry.isCapabilityAvailable("phr.consent-management")).isTrue();
            assertThat(registry.isCapabilityAvailable("phr.fhir-interop")).isTrue();
        }

        @Test
        @DisplayName("No finance capabilities leak into healthcare-only registry")
        void noFinanceCapabilitiesLeakIntoHealthcareOnlyRegistry() { // GH-90000
            assertThat(registry.isCapabilityAvailable("finance.trade-processing")).isFalse();
            assertThat(registry.isCapabilityAvailable("finance.risk-management")).isFalse();
            assertThat(registry.isCapabilityAvailable("finance.compliance-checking")).isFalse();
        }

        @Test
        @DisplayName("Module-by-capability search returns only PHR module")
        void capabilitySearchReturnsPhrModuleOnly() { // GH-90000
            List<KernelModule> modules = registry.getModulesByCapability(PHR_PATIENT_RECORDS); // GH-90000
            assertThat(modules) // GH-90000
                .hasSize(1) // GH-90000
                .extracting(KernelModule::getModuleId) // GH-90000
                .containsExactly("phr-core");
        }

        @Test
        @DisplayName("Dependency validation passes with core capabilities pre-registered")
        void dependencyValidationPassesWithCorePrereg() { // GH-90000
            // Validate a fresh PHR module (not yet registered) against the current registry state // GH-90000
            KernelModule candidate = stubModule( // GH-90000
                "phr-imaging",
                Set.of(PHR_FHIR_INTEROP), // GH-90000
                Set.of(capDep("data.storage"), capDep("user.authentication"))
            );
            assertThat(registry.validateDependencies(candidate)) // GH-90000
                .as("PHR imaging module must validate — core capabilities are present")
                .isTrue(); // GH-90000
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
        void setUp() { // GH-90000
            registry = freshRegistry(); // GH-90000

            KernelModule financeModule = stubModule( // GH-90000
                "finance-core",
                Set.of(FINANCE_TRADE_PROCESSING, FINANCE_RISK_MANAGEMENT, FINANCE_COMPLIANCE, // GH-90000
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );
            registry.registerModule(financeModule); // GH-90000
        }

        @Test
        @DisplayName("Finance module registers without any PHR module present")
        void financeModuleRegistersWithoutPhr() { // GH-90000
            assertThat(registry.isModuleRegistered("finance-core"))
                .as("finance module must be registered")
                .isTrue(); // GH-90000

            assertThat(registry.isModuleRegistered("phr-core"))
                .as("PHR module must NOT be present in finance-only deployment")
                .isFalse(); // GH-90000
        }

        @Test
        @DisplayName("All finance capabilities are discoverable by ID")
        void allFinanceCapabilitiesAreDiscoverable() { // GH-90000
            assertThat(registry.isCapabilityAvailable("finance.trade-processing")).isTrue();
            assertThat(registry.isCapabilityAvailable("finance.risk-management")).isTrue();
            assertThat(registry.isCapabilityAvailable("finance.compliance-checking")).isTrue();
        }

        @Test
        @DisplayName("No PHR capabilities leak into finance-only registry")
        void noPhrCapabilitiesLeakIntoFinanceOnlyRegistry() { // GH-90000
            assertThat(registry.isCapabilityAvailable("phr.patient-records")).isFalse();
            assertThat(registry.isCapabilityAvailable("phr.consent-management")).isFalse();
            assertThat(registry.isCapabilityAvailable("phr.fhir-interop")).isFalse();
        }

        @Test
        @DisplayName("Finance domain capability validation passes with core pre-registered")
        void financeDomainValidationPassesWithCorePrereg() { // GH-90000
            KernelModule candidate = stubModule( // GH-90000
                "finance-reconciliation",
                Set.of(FINANCE_COMPLIANCE), // GH-90000
                Set.of(capDep("data.storage"), capDep("workflow.engine"))
            );
            assertThat(registry.validateDependencies(candidate)) // GH-90000
                .as("finance reconciliation module must validate — core capabilities are present")
                .isTrue(); // GH-90000
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Mode 3 — Shared-kernel (both domains in one registry) // GH-90000
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mode 3 — Shared-kernel (PHR + Finance coexist)")
    class SharedKernelMode {

        private KernelRegistryImpl registry;

        @BeforeEach
        void setUp() { // GH-90000
            registry = freshRegistry(); // GH-90000

            KernelModule healthcareModule = stubModule( // GH-90000
                "phr-core",
                Set.of(PHR_PATIENT_RECORDS, PHR_CONSENT_MANAGEMENT, PHR_FHIR_INTEROP, // GH-90000
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );
            KernelModule financeModule = stubModule( // GH-90000
                "finance-core",
                Set.of(FINANCE_TRADE_PROCESSING, FINANCE_RISK_MANAGEMENT, FINANCE_COMPLIANCE, // GH-90000
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );

            registry.registerModule(healthcareModule); // GH-90000
            registry.registerModule(financeModule); // GH-90000
        }

        @Test
        @DisplayName("Both modules coexist in the same registry without conflict")
        void bothModulesCoexistWithoutConflict() { // GH-90000
            assertThat(registry.isModuleRegistered("phr-core")).isTrue();
            assertThat(registry.isModuleRegistered("finance-core")).isTrue();
        }

        @Test
        @DisplayName("All domain capabilities are simultaneously discoverable")
        void allDomainCapabilitiesAreSimultaneouslyDiscoverable() { // GH-90000
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
        void phrAndFinanceCapabilityIdNamespacesDoNotCollide() { // GH-90000
            // Every registered capability must have a unique ID
            List<KernelCapability> allCapabilities = registry.getAllCapabilities(); // GH-90000

            long uniqueCount = allCapabilities.stream() // GH-90000
                .map(KernelCapability::getCapabilityId) // GH-90000
                .distinct() // GH-90000
                .count(); // GH-90000

            assertThat(uniqueCount) // GH-90000
                .as("All capability IDs must be unique — no namespace collision between PHR and Finance")
                .isEqualTo(allCapabilities.size()); // GH-90000
        }

        @Test
        @DisplayName("PHR capability search returns only PHR module, not finance")
        void phrCapabilitySearchReturnsPhrModuleOnly() { // GH-90000
            List<KernelModule> modules = registry.getModulesByCapability(PHR_PATIENT_RECORDS); // GH-90000
            assertThat(modules) // GH-90000
                .extracting(KernelModule::getModuleId) // GH-90000
                .containsExactly("phr-core")
                .doesNotContain("finance-core");
        }

        @Test
        @DisplayName("Finance capability search returns only finance module, not PHR")
        void financeCapabilitySearchReturnsFinanceModuleOnly() { // GH-90000
            List<KernelModule> modules = registry.getModulesByCapability(FINANCE_TRADE_PROCESSING); // GH-90000
            assertThat(modules) // GH-90000
                .extracting(KernelModule::getModuleId) // GH-90000
                .containsExactly("finance-core")
                .doesNotContain("phr-core");
        }

        @Test
        @DisplayName("Duplicate module registration is rejected in shared-kernel mode")
        void duplicateModuleRegistrationRejectedInSharedKernel() { // GH-90000
            KernelModule duplicate = stubModule( // GH-90000
                "phr-core",   // same ID as already-registered module
                Set.of(PHR_PATIENT_RECORDS), // GH-90000
                Set.of() // GH-90000
            );
            assertThatThrownBy(() -> registry.registerModule(duplicate)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
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
        void phrCapabilityIdsMustHavePhrPrefix() { // GH-90000
            Set<KernelCapability> phrCaps = Set.of( // GH-90000
                PHR_PATIENT_RECORDS, PHR_CONSENT_MANAGEMENT, PHR_FHIR_INTEROP);

            assertThat(phrCaps) // GH-90000
                .allSatisfy(cap -> // GH-90000
                    assertThat(cap.getCapabilityId()) // GH-90000
                        .as("PHR capability '%s' must start with 'phr.' per namespace contract", // GH-90000
                            cap.getCapabilityId()) // GH-90000
                        .startsWith("phr."));
        }

        @Test
        @DisplayName("Finance capability IDs must be prefixed with 'finance.'")
        void financeCapabilityIdsMustHaveFinancePrefix() { // GH-90000
            Set<KernelCapability> financeCaps = Set.of( // GH-90000
                FINANCE_TRADE_PROCESSING, FINANCE_RISK_MANAGEMENT, FINANCE_COMPLIANCE);

            assertThat(financeCaps) // GH-90000
                .allSatisfy(cap -> // GH-90000
                    assertThat(cap.getCapabilityId()) // GH-90000
                        .as("Finance capability '%s' must start with 'finance.' per namespace contract", // GH-90000
                            cap.getCapabilityId()) // GH-90000
                        .startsWith("finance."));
        }

        @Test
        @DisplayName("Core capability IDs must NOT be prefixed with any product domain name")
        void coreCapabilityIdsMustBeGeneric() { // GH-90000
            Set<KernelCapability> coreCaps = Set.of( // GH-90000
                CORE_DATA_STORAGE, CORE_USER_AUTH, CORE_WORKFLOW);

            Set<String> productPrefixes = Set.of("phr.", "finance.", "aura.", "flashit."); // GH-90000

            assertThat(coreCaps).allSatisfy(cap -> // GH-90000
                productPrefixes.forEach(prefix -> // GH-90000
                    assertThat(cap.getCapabilityId()) // GH-90000
                        .as("Core capability '%s' must not carry a product-domain prefix", // GH-90000
                            cap.getCapabilityId()) // GH-90000
                        .doesNotStartWith(prefix))); // GH-90000
        }

        @Test
        @DisplayName("Missing required module dependency detected by validator")
        void missingRequiredModuleFailsValidation() { // GH-90000
            KernelRegistryImpl registry = freshRegistry(); // GH-90000

            // module-a needs module-b (MODULE dependency), but module-b is not registered // GH-90000
            KernelModule moduleA = stubModule( // GH-90000
                "module-a",
                Set.of(PHR_PATIENT_RECORDS), // GH-90000
                Set.of(new KernelDependency( // GH-90000
                    "module-b", "1.0.0",
                    KernelDependency.DependencyType.MODULE, false))
            );

            List<String> errors = registry.getDependencyValidationErrors(moduleA); // GH-90000
            assertThat(errors) // GH-90000
                .as("Validator must report missing required MODULE dependency")
                .hasSize(1) // GH-90000
                .first().asString() // GH-90000
                .contains("module-b");
        }

        @Test
        @DisplayName("Optional module dependency silently absent is allowed by validator")
        void optionalMissingModulePassesValidation() { // GH-90000
            KernelRegistryImpl registry = freshRegistry(); // GH-90000

            KernelModule moduleA = stubModule( // GH-90000
                "module-a",
                Set.of(PHR_PATIENT_RECORDS), // GH-90000
                Set.of(new KernelDependency( // GH-90000
                    "module-optional", "1.0.0",
                    KernelDependency.DependencyType.MODULE, true))
            );

            // Optional dependency is absent → validation must still pass (no errors) // GH-90000
            assertThat(registry.validateDependencies(moduleA)) // GH-90000
                .as("Optional dependency absent must not block registration")
                .isTrue(); // GH-90000
        }
    }
}
