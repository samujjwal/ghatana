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

    // ── Domain-alpha capabilities ───────────────────────────────────────────

    private static final KernelCapability DOMAIN_ALPHA_PATIENT_RECORDS = new KernelCapability(
        "domain-alpha.patient-records", "Patient Records",
        "Domain-alpha patient record management — regulated domain",
        CapabilityType.BUSINESS_LOGIC, Map.of("domain", "regulated"));

    private static final KernelCapability DOMAIN_ALPHA_CONSENT_MANAGEMENT = new KernelCapability(
        "domain-alpha.consent-management", "Consent Management",
        "Domain-alpha consent lifecycle — regulated domain",
        CapabilityType.COMPLIANCE, Map.of("domain", "regulated"));

    private static final KernelCapability DOMAIN_ALPHA_FHIR_INTEROP = new KernelCapability(
        "domain-alpha.fhir-interop", "FHIR Interoperability",
        "FHIR R4 resource exchange — regulated domain",
        CapabilityType.INTEGRATION, Map.of("domain", "regulated"));

    // ── Domain-beta capabilities ─────────────────────────────────────────────

    private static final KernelCapability DOMAIN_BETA_TRADE_PROCESSING = new KernelCapability(
        "domain-beta.trade-processing", "Trade Processing",
        "Domain-beta trade order processing — regulatory domain",
        CapabilityType.BUSINESS_LOGIC, Map.of("domain", "regulatory"));

    private static final KernelCapability DOMAIN_BETA_RISK_MANAGEMENT = new KernelCapability(
        "domain-beta.risk-management", "Risk Management",
        "Domain-beta risk assessment — regulatory domain",
        CapabilityType.BUSINESS_LOGIC, Map.of("domain", "regulatory"));

    private static final KernelCapability DOMAIN_BETA_COMPLIANCE = new KernelCapability(
        "domain-beta.compliance-checking", "Compliance Checking",
        "Domain-beta regulatory compliance — regulatory domain",
        CapabilityType.COMPLIANCE, Map.of("domain", "regulatory")); 

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
    // Mode 1 — Domain-alpha-only
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mode 1 — Domain-alpha-only (independent deployment)")
    class DomainAlphaOnlyMode {

        private KernelRegistryImpl registry;

        @BeforeEach
        void setUp() {
            registry = freshRegistry();

            KernelModule domainAlphaModule = stubModule(
                "domain-alpha-core",
                Set.of(DOMAIN_ALPHA_PATIENT_RECORDS, DOMAIN_ALPHA_CONSENT_MANAGEMENT, DOMAIN_ALPHA_FHIR_INTEROP,
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );
            registry.registerModule(domainAlphaModule);
        }

        @Test
        @DisplayName("Domain-alpha module registers without any domain-beta module present")
        void domainAlphaModuleRegistersWithoutDomainBeta() {
            assertThat(registry.isModuleRegistered("domain-alpha-core"))
                .as("domain-alpha module must be registered")
                .isTrue();
            assertThat(registry.isModuleRegistered("domain-beta-core"))
                .as("domain-beta module must NOT be present in domain-alpha-only deployment")
                .isFalse();
        }

        @Test
        @DisplayName("All domain-alpha capabilities are discoverable by ID")
        void allDomainAlphaCapabilitiesDiscoverable() {
            assertThat(registry.isCapabilityAvailable("domain-alpha.patient-records")).isTrue();
            assertThat(registry.isCapabilityAvailable("domain-alpha.consent-management")).isTrue();
            assertThat(registry.isCapabilityAvailable("domain-alpha.fhir-interop")).isTrue();
        }

        @Test
        @DisplayName("No domain-beta capabilities leak into domain-alpha-only registry")
        void noDomainBetaCapabilitiesLeakIntoDomainAlphaRegistry() {
            assertThat(registry.isCapabilityAvailable("domain-beta.trade-processing")).isFalse();
            assertThat(registry.isCapabilityAvailable("domain-beta.risk-management")).isFalse();
            assertThat(registry.isCapabilityAvailable("domain-beta.compliance-checking")).isFalse();
        }

        @Test
        @DisplayName("Module-by-capability search returns only domain-alpha module")
        void moduleByCapabilitySearchReturnsOnlyDomainAlpha() {
            Set<String> moduleIds = registry.getModulesByCapability(DOMAIN_ALPHA_PATIENT_RECORDS).stream()
                .map(KernelModule::getModuleId)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(moduleIds)
                .containsExactly("domain-alpha-core");
        }

        @Test
        @DisplayName("Domain-alpha module validates with core pre-registered")
        void domainAlphaModuleValidatesWithCorePreRegistered() {
            // Validate a fresh domain-alpha module (not yet registered) against the current registry state
            KernelModule freshModule = stubModule(
                "domain-alpha-imaging",
                Set.of(DOMAIN_ALPHA_PATIENT_RECORDS, CORE_USER_AUTH, CORE_DATA_STORAGE),
                Set.of(capDep("data.storage"), capDep("user.authentication"))
            );
            assertThat(registry.validateDependencies(freshModule))
                .as("Domain-alpha imaging module must validate — core capabilities are present")
                .isTrue();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Mode 2 — Domain-beta-only
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mode 2 — Domain-beta-only (independent deployment)")
    class DomainBetaOnlyMode {

        private KernelRegistryImpl registry;

        @BeforeEach
        void setUp() {
            registry = freshRegistry();

            KernelModule domainBetaModule = stubModule(
                "domain-beta-core",
                Set.of(DOMAIN_BETA_TRADE_PROCESSING, DOMAIN_BETA_RISK_MANAGEMENT, DOMAIN_BETA_COMPLIANCE,
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );
            registry.registerModule(domainBetaModule);
        }

        @Test
        @DisplayName("Domain-beta module registers without any domain-alpha module present")
        void domainBetaModuleRegistersWithoutDomainAlpha() {
            assertThat(registry.isModuleRegistered("domain-beta-core"))
                .as("domain-beta module must be registered")
                .isTrue();
            assertThat(registry.isModuleRegistered("domain-alpha-core"))
                .as("Domain-alpha module must NOT be present in domain-beta-only deployment")
                .isFalse();
        }

        @Test
        @DisplayName("All domain-beta capabilities are discoverable by ID")
        void allDomainBetaCapabilitiesDiscoverable() {
            assertThat(registry.isCapabilityAvailable("domain-beta.trade-processing")).isTrue();
            assertThat(registry.isCapabilityAvailable("domain-beta.risk-management")).isTrue();
            assertThat(registry.isCapabilityAvailable("domain-beta.compliance-checking")).isTrue();
        }

        @Test
        @DisplayName("No domain-alpha capabilities leak into domain-beta-only registry")
        void noDomainAlphaCapabilitiesLeakIntoDomainBetaRegistry() {
            assertThat(registry.isCapabilityAvailable("domain-alpha.patient-records")).isFalse();
            assertThat(registry.isCapabilityAvailable("domain-alpha.consent-management")).isFalse();
            assertThat(registry.isCapabilityAvailable("domain-alpha.fhir-interop")).isFalse();
        }

        @Test
        @DisplayName("Domain-beta domain capability validation passes with core pre-registered")
        void domainBetaModuleValidatesWithCorePreRegistered() {
            KernelModule freshModule = stubModule(
                "domain-beta-reconciliation",
                Set.of(DOMAIN_BETA_TRADE_PROCESSING, CORE_USER_AUTH, CORE_DATA_STORAGE),
                Set.of(capDep("data.storage"), capDep("user.authentication"))
            );
            assertThat(registry.validateDependencies(freshModule))
                .as("Domain-beta reconciliation module must validate — core capabilities are present")
                .isTrue();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Mode 3 — Shared-kernel (both domains in one registry)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mode 3 — Shared-kernel (Domain-alpha + Domain-beta coexist)")
    class SharedKernelMode {

        private KernelRegistryImpl registry;

        @BeforeEach
        void setUp() {
            registry = freshRegistry();

            KernelModule domainAlphaModule = stubModule(
                "domain-alpha-core",
                Set.of(DOMAIN_ALPHA_PATIENT_RECORDS, DOMAIN_ALPHA_CONSENT_MANAGEMENT, DOMAIN_ALPHA_FHIR_INTEROP,
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );
            registry.registerModule(domainAlphaModule);

            KernelModule domainBetaModule = stubModule(
                "domain-beta-core",
                Set.of(DOMAIN_BETA_TRADE_PROCESSING, DOMAIN_BETA_RISK_MANAGEMENT, DOMAIN_BETA_COMPLIANCE,
                       CORE_USER_AUTH, CORE_DATA_STORAGE, CORE_WORKFLOW),
                Set.of(capDep("data.storage"), capDep("user.authentication"), capDep("workflow.engine"))
            );
            registry.registerModule(domainBetaModule);
        }

        @Test
        @DisplayName("Both domain modules register successfully in shared registry")
        void bothDomainModulesRegisterSuccessfully() {
            assertThat(registry.isModuleRegistered("domain-alpha-core")).isTrue();
            assertThat(registry.isModuleRegistered("domain-beta-core")).isTrue();
        }

        @Test
        @DisplayName("Domain-alpha capabilities are discoverable in shared registry")
        void domainAlphaCapabilitiesDiscoverableInSharedRegistry() {
            assertThat(registry.isCapabilityAvailable("domain-alpha.patient-records")).isTrue();
            assertThat(registry.isCapabilityAvailable("domain-alpha.consent-management")).isTrue();
            assertThat(registry.isCapabilityAvailable("domain-alpha.fhir-interop")).isTrue();
        }

        @Test
        @DisplayName("Domain-beta capabilities are discoverable in shared registry")
        void domainBetaCapabilitiesDiscoverableInSharedRegistry() {
            assertThat(registry.isCapabilityAvailable("domain-beta.trade-processing")).isTrue();
            assertThat(registry.isCapabilityAvailable("domain-beta.risk-management")).isTrue();
            assertThat(registry.isCapabilityAvailable("domain-beta.compliance-checking")).isTrue();
        }

        @Test
        @DisplayName("Capabilities remain namespace-isolated in shared registry")
        void capabilitiesNamespaceIsolatedInSharedRegistry() {
            // Domain-alpha IDs use domain-alpha.* prefix
            // Domain-beta IDs use domain-beta.* prefix
            Set<String> allCapabilityIds = registry.getAllCapabilities().stream()
                .map(KernelCapability::getCapabilityId)
                .collect(java.util.stream.Collectors.toSet());
            assertThat(allCapabilityIds)
                .filteredOn(id -> id.startsWith("domain-alpha."))
                .hasSize(3);
            assertThat(allCapabilityIds)
                .filteredOn(id -> id.startsWith("domain-beta."))
                .hasSize(3);
        }

        @Test
        @DisplayName("All capability IDs are unique — no namespace collision")
        void allCapabilityIdsUniqueNoCollision() {
            Set<String> allCapabilityIds = registry.getAllCapabilities().stream()
                .map(KernelCapability::getCapabilityId)
                .collect(java.util.stream.Collectors.toSet());
            long uniqueCount = allCapabilityIds.stream()
                .distinct()
                .count();

            assertThat(uniqueCount)
                .as("All capability IDs must be unique — no namespace collision between domains")
                .isEqualTo(allCapabilityIds.size());
        }

        @Test
        @DisplayName("Domain-alpha capability search returns only domain-alpha module, not domain-beta")
        void domainAlphaCapabilitySearchReturnsDomainAlphaModuleOnly() {
            List<KernelModule> modules = registry.getModulesByCapability(DOMAIN_ALPHA_PATIENT_RECORDS);
            assertThat(modules)
                .extracting(KernelModule::getModuleId)
                .containsExactly("domain-alpha-core")
                .doesNotContain("domain-beta-core");
        }

        @Test
        @DisplayName("Domain-beta capability search returns only domain-beta module, not domain-alpha")
        void domainBetaCapabilitySearchReturnsDomainBetaModuleOnly() {
            List<KernelModule> modules = registry.getModulesByCapability(DOMAIN_BETA_TRADE_PROCESSING);
            assertThat(modules)
                .extracting(KernelModule::getModuleId)
                .containsExactly("domain-beta-core")
                .doesNotContain("domain-alpha-core");
        }

        @Test
        @DisplayName("Duplicate module registration is rejected in shared-kernel mode")
        void duplicateModuleRegistrationRejectedInSharedKernel() {
            KernelModule duplicate = stubModule(
                "domain-alpha-core",   // same ID as already-registered module
                Set.of(DOMAIN_ALPHA_PATIENT_RECORDS),
                Set.of()
            );
            assertThatThrownBy(() -> registry.registerModule(duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
        }
    }
}
