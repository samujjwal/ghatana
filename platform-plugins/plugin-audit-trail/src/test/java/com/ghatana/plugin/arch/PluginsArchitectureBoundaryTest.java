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
 * <p>These tests enforce the forbidden dependency directions:</p>
 * <ul>
 *   <li>ARCH-010: {@code platform-plugins} SPI interfaces must not import product packages</li>
 *   <li>ARCH-011: plugin implementations must not import product packages</li>
 *   <li>ARCH-012: plugin implementations must not import across plugin boundaries
 *       (e.g. audit plugin must not import billing plugin)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit dependency-direction tests for platform-plugins boundary
 * @doc.layer platform
 * @doc.pattern ArchitecturalTest
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
                .as("ARCH-010: platform-plugins must not depend on any product package — " +
                    "plugins are product-agnostic shared infrastructure");

        rule.check(pluginClasses);
    }

    // ── ARCH-011: audit plugin must not import billing plugin ─────────────────

    @Test
    @DisplayName("ARCH-011: audit plugin must not import billing plugin classes")
    void auditPluginMustNotDependOnBillingPlugin() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin.audit..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.billing..")
                .as("ARCH-011: audit plugin must not depend on billing plugin — " +
                    "plugins must be independently usable");

        rule.check(pluginClasses);
    }

    // ── ARCH-012: billing plugin must not import fraud plugin ─────────────────

    @Test
    @DisplayName("ARCH-012: billing plugin must not import fraud-detection plugin classes")
    void billingPluginMustNotDependOnFraudPlugin() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.plugin.billing..")
                .should().dependOnClassesThat().resideInAPackage("com.ghatana.plugin.fraud..")
                .as("ARCH-012: billing plugin must not depend on fraud-detection plugin")
                .allowEmptyShould(true); // Allow when billing plugin is not in current module

        rule.check(pluginClasses);
    }
}
