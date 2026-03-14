package com.ghatana.platform.testing;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Platform Boundary Rules
 * 
 * Enforces architectural boundaries between platform and product modules.
 * These rules prevent boundary drift and maintain clean separation of concerns.
 */
public class PlatformBoundaryRules {

    /**
     * Platform packages must not import from product packages.
     * This prevents circular dependencies and maintains clean architecture.
     */
    @ArchTest
    static final ArchRule PLATFORM_SHOULD_NOT_DEPEND_ON_PRODUCTS = 
        noClasses()
            .that().resideInAPackage("com.ghatana.platform..")
            .should().dependOnClassesThat().resideInAPackage("com.ghatana.products..")
            .because("Platform modules must not depend on product modules to maintain clean architecture");

    /**
     * Platform packages should only depend on allowed external packages.
     * This prevents dependency creep and maintains controlled boundaries.
     */
    @ArchTest
    static final ArchRule PLATFORM_SHOULD_ONLY_DEPEND_ON_ALLOWED_PACKAGES =
        classes()
            .that().resideInAPackage("com.ghatana.platform..")
            .should().onlyAccessClassesThat()
            .resideInAnyPackage(
                "com.ghatana.platform..",
                "java..",
                "javax..",
                "org.springframework..",
                "org.junit..",
                "org.mockito..",
                "org.assertj..",
                "io.activej..",
                "com.fasterxml.jackson..",
                "org.slf4j..",
                "org.apache.logging..",
                "org.postgresql..",
                "org.testcontainers..",
                "org.awaitility..",
                "com.jayway.jsonpath..",
                "org.lombok..",
                "org.jetbrains.annotations.."
            )
            .because("Platform modules should only depend on approved external packages");

    /**
     * Platform API interfaces should not depend on implementation details.
     * This maintains clean separation between API and implementation.
     */
    @ArchTest
    static final ArchRule PLATFORM_API_SHOULD_NOT_DEPEND_ON_IMPLEMENTATION =
        noClasses()
            .that().resideInAPackage("com.ghatana.platform..api")
            .and().haveSimpleNameEndingWith("Api")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..impl..", "..internal..", "..impl..")
            .because("API interfaces should not depend on implementation details");

    /**
     * Platform test classes should not access production internals.
     * This ensures tests validate public contracts, not implementation details.
     */
    @ArchTest
    static final ArchRule PLATFORM_TESTS_SHOULD_NOT_ACCESS_INTERNALS =
        noClasses()
            .that().resideInAPackage("..test..")
            .and().resideInAPackage("com.ghatana.platform..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..internal..", "..impl..")
            .because("Tests should validate public contracts, not implementation details");
}
