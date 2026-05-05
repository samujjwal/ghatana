package com.ghatana.plugin.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit dependency-direction conformance tests for platform-plugins.
 *
 * <p>These tests enforce the forbidden dependency directions documented in
 * {@code docs/PLUGIN_PURITY_RULES.md}:</p>
 * <ul>
 *   <li>ARCH-010: no plugin imports from any product package.</li>
 *   <li>ARCH-011 through ARCH-017: no plugin implementation imports from any other
 *       plugin's implementation package — plugins must be independently loadable.</li>
 *   <li>P1-041: no product-specific business logic in platform plugins.</li>
 * </ul>
 *
 * <p>The canonical plugin packages are:</p>
 * <ul>
 *   <li>{@code com.ghatana.plugin.audit}</li>
 *   <li>{@code com.ghatana.plugin.compliance}</li>
 *   <li>{@code com.ghatana.plugin.consent}</li>
 *   <li>{@code com.ghatana.plugin.fraud}</li>
 *   <li>{@code com.ghatana.plugin.approval}</li>
 *   <li>{@code com.ghatana.plugin.ledger}</li>
 *   <li>{@code com.ghatana.plugin.risk}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit dependency-direction isolation tests for platform-plugins
 * @doc.layer platform
 * @doc.pattern ArchitecturalTest
 * @since 1.3.0
 */
@DisplayName("platform-plugins architectural boundary rules")
class PluginsArchitectureBoundaryTest {

    private static JavaClasses pluginClasses;

    @BeforeAll
    static void importClasses() {
        pluginClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.plugin");
    }

    // ── ARCH-010: plugins must not depend on products ─────────────────────────

    @Test
    @DisplayName("ARCH-010: platform-plugins must not import from any product package")
    void pluginsMustNotDependOnProducts() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.phr..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.finance..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.yappc..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.aep..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.datacloud..")
                .as("ARCH-010: platform-plugins must not depend on any product package — " +
                    "plugins are product-agnostic shared infrastructure");

        rule.check(pluginClasses);
    }

    // ── ARCH-011: audit plugin impl isolation ────────────────────────────────

    @Test
    @DisplayName("ARCH-011: audit plugin must not import impl classes from any other plugin")
    void auditPluginImplIsolation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin.audit..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.compliance..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.consent..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.fraud..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.approval..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.ledger..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.risk..")
                .as("ARCH-011: audit plugin must be independently loadable — " +
                    "it must not depend on any other plugin's implementation");

        rule.check(pluginClasses);
    }

    // ── ARCH-012: compliance plugin impl isolation ────────────────────────────

    @Test
    @DisplayName("ARCH-012: compliance plugin must not import impl classes from other plugins")
    void compliancePluginImplIsolation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin.compliance..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.consent..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.fraud..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.approval..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.ledger..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.risk..")
                .as("ARCH-012: compliance plugin must be independently loadable — " +
                    "it must not depend on consent, fraud, approval, ledger, or risk plugin implementations")
                .allowEmptyShould(true);

        rule.check(pluginClasses);
    }

    // ── ARCH-013: consent plugin impl isolation ───────────────────────────────

    @Test
    @DisplayName("ARCH-013: consent plugin must not import impl classes from other plugins")
    void consentPluginImplIsolation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin.consent..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.compliance..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.fraud..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.approval..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.ledger..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.risk..")
                .as("ARCH-013: consent plugin must be independently loadable — " +
                    "it must not depend on compliance, fraud, approval, ledger, or risk plugin implementations")
                .allowEmptyShould(true);

        rule.check(pluginClasses);
    }

    // ── ARCH-014: fraud detection plugin impl isolation ───────────────────────

    @Test
    @DisplayName("ARCH-014: fraud detection plugin must not import impl classes from other plugins")
    void fraudDetectionPluginImplIsolation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin.fraud..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.compliance..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.consent..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.approval..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.ledger..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.risk..")
                .as("ARCH-014: fraud detection plugin must be independently loadable — " +
                    "it must not depend on compliance, consent, approval, ledger, or risk plugin implementations")
                .allowEmptyShould(true);

        rule.check(pluginClasses);
    }

    // ── ARCH-015: human approval plugin impl isolation ────────────────────────

    @Test
    @DisplayName("ARCH-015: human approval plugin must not import impl classes from other plugins")
    void humanApprovalPluginImplIsolation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin.approval..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.compliance..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.consent..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.fraud..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.ledger..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.risk..")
                .as("ARCH-015: human approval plugin must be independently loadable — " +
                    "it must not depend on compliance, consent, fraud, ledger, or risk plugin implementations")
                .allowEmptyShould(true);

        rule.check(pluginClasses);
    }

    // ── ARCH-016: ledger plugin impl isolation ────────────────────────────────

    @Test
    @DisplayName("ARCH-016: ledger plugin must not import impl classes from other plugins")
    void ledgerPluginImplIsolation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin.ledger..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.compliance..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.consent..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.fraud..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.approval..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.risk..")
                .as("ARCH-016: ledger plugin must be independently loadable — " +
                    "it must not depend on compliance, consent, fraud, approval, or risk plugin implementations")
                .allowEmptyShould(true);

        rule.check(pluginClasses);
    }

    // ── ARCH-017: risk management plugin impl isolation ───────────────────────

    @Test
    @DisplayName("ARCH-017: risk management plugin must not import impl classes from other plugins")
    void riskManagementPluginImplIsolation() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin.risk..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.compliance..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.consent..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.fraud..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.approval..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.ledger..")
                .as("ARCH-017: risk management plugin must be independently loadable — " +
                    "it must not depend on compliance, consent, fraud, approval, or ledger plugin implementations")
                .allowEmptyShould(true);

        rule.check(pluginClasses);
    }

    // ── P1-041: plugins must not contain product-specific logic ──────────────────

    @Test
    @DisplayName("P1-041: platform-plugins must not reference product-specific domain classes")
    void pluginsMustNotContainProductSpecificLogic() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.digitalmarketing..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.phr..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.finance..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.yappc..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.aep..")
                .orShould().dependOnClassesThat().resideInAPackage("com.ghatana.datacloud..")
                .as("P1-041: platform-plugins must not contain product-specific logic — " +
                    "plugins are product-agnostic shared infrastructure");

        rule.check(pluginClasses);
    }

    @Test
    @DisplayName("P1-041: plugins must not contain product-specific business logic patterns")
    void pluginsMustNotContainProductBusinessLogic() {
        // Check for product-specific naming patterns in plugin code
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin..")
                .should().haveNameMatching(".*Marketing.*")
                .orShould().haveNameMatching(".*Campaign.*")
                .orShould().haveNameMatching(".*Strategy.*")
                .orShould().haveNameMatching(".*Budget.*")
                .orShould().haveNameMatching(".*Lead.*")
                .orShould().haveNameMatching(".*Patient.*")
                .orShould().haveNameMatching(".*Health.*")
                .orShould().haveNameMatching(".*Finance.*")
                .orShould().haveNameMatching(".*Yappc.*")
                .orShould().haveNameMatching(".*Agent.*")
                .as("P1-041: platform-plugins must not contain product-specific business logic — " +
                    "plugins should have generic, reusable names");

        rule.check(pluginClasses);
    }
}
