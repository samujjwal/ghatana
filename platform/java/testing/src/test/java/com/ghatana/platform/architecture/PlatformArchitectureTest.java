/*
 * Copyright (c) 2026 Ghatana. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Platform-wide architectural constraint enforcement tests. */
@DisplayName("Platform Architecture Rules")
class PlatformArchitectureTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importClasses() {
        productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ghatana");
    }

    @Test
    @DisplayName("D-8: platform and kernel code do not depend on product namespaces")
    void platformAndKernelMustNotDependOnProducts() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("com.ghatana.platform..", "com.ghatana.kernel..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.ghatana.datacloud..",
                "com.ghatana.aep..",
                "com.ghatana.yappc..",
                "com.ghatana.guardian..",
                "com.ghatana.finance..",
                "com.ghatana.phr.."
            )
            .as("Shared platform/kernel modules must remain product-agnostic");

        rule.allowEmptyShould(true).check(productionClasses);
    }

    @Test
    @DisplayName("D-8: non-test runtime packages must not depend on JUnit APIs")
    void runtimePackagesMustNotDependOnJUnit() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("com.ghatana.platform..", "com.ghatana.kernel..")
            .and().resideOutsideOfPackages("..testing..", "..test..")
            .should().dependOnClassesThat().resideInAnyPackage("org.junit..", "org.assertj..")
            .as("Runtime source must not leak test-only dependencies");

        rule.allowEmptyShould(true).check(productionClasses);
    }

    @Test
    @DisplayName("D-8: core platform code avoids CompletableFuture usage")
    void platformCodeShouldAvoidCompletableFuture() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("com.ghatana.platform..", "com.ghatana.kernel..")
            .and().resideOutsideOfPackages("..bridge..", "..adapters..", "..testing..")
            .should().dependOnClassesThat().areAssignableTo(CompletableFuture.class)
            .as("Platform and kernel runtime should use ActiveJ Promise-first async flows");

        rule.allowEmptyShould(true).check(productionClasses);
    }
}
