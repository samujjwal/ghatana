package com.ghatana.integration.crossservice;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture boundary enforcement tests for cross-product dependency rules.
 *
 * <p>Enforces the one-way dependency rule documented in the Ghatana monorepo architecture:
 * <ul>
 *   <li>AEP may depend on Data Cloud (for pipeline registry, event streaming)</li>
 *   <li>Data Cloud must NOT depend on AEP (Data Cloud is infrastructure, AEP is product)</li>
 *   <li>YAPPC may depend on Data Cloud (for repository adapters)</li>
 *   <li>Data Cloud must NOT depend on YAPPC</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Architectural boundary enforcement for cross-product dependency rules
 * @doc.layer integration
 * @doc.pattern ArchUnit, ArchitectureTest
 */
@DisplayName("Cross-product architecture boundary rules")
class CrossProductBoundaryArchTest {

    private static JavaClasses ALL_PRODUCT_CLASSES;

    /**
     * Excludes third-party library classes from JAR entries while keeping all ghatana product
     * classes. This prevents ArchUnit from loading the entire transitive dependency graph
     * (which causes OOM), while still scanning all product source classes.
     */
    private static final ImportOption ONLY_GHATANA_FROM_JARS =
            location -> !location.isJar() || location.asURI().toString().contains("/com/ghatana/");

    @BeforeAll
    static void importClasses() {
        ALL_PRODUCT_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ONLY_GHATANA_FROM_JARS)
                .importPackages(
                        "com.ghatana.datacloud",
                        "com.ghatana.aep",
                        "com.ghatana.yappc",
                        "com.ghatana.orchestrator");
    }

    @Test
    @DisplayName("Data Cloud must not depend on AEP packages")
    void dataCloud_mustNotDependOn_aep() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.ghatana.datacloud..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.ghatana.aep..", "com.ghatana.orchestrator..")
                .because("Data Cloud is infrastructure and must not depend on AEP (product-layer). " +
                        "Dependency direction: AEP → Data Cloud, not Data Cloud → AEP.");

        rule.check(ALL_PRODUCT_CLASSES);
    }

    @Test
    @DisplayName("Data Cloud must not depend on YAPPC packages")
    void dataCloud_mustNotDependOn_yappc() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.ghatana.datacloud..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.ghatana.yappc..")
                .because("Data Cloud is infrastructure and must not depend on YAPPC. " +
                        "Dependency direction: YAPPC → Data Cloud, not Data Cloud → YAPPC.");

        rule.check(ALL_PRODUCT_CLASSES);
    }

    @Test
    @DisplayName("AEP must not depend on YAPPC packages")
    void aep_mustNotDependOn_yappc() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.ghatana.aep..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.ghatana.yappc..")
                .because("AEP and YAPPC are peer products with no defined dependency between them. " +
                        "Cross-product calls must go through platform contracts or shared SPI.");

        rule.check(ALL_PRODUCT_CLASSES);
    }

    @Test
    @DisplayName("YAPPC must not depend on AEP product-internal packages")
    void yappc_mustNotDependOn_aepInternal() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.ghatana.yappc..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.ghatana.aep..")
                .because("YAPPC must not depend on AEP product-internal packages. " +
                        "Cross-product calls must go through platform contracts or shared SPI.");

        rule.check(ALL_PRODUCT_CLASSES);
    }
}
