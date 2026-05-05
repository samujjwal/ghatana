/**
 * P1-042: ArchUnit test to prevent test-only utilities in production code.
 *
 * <p>These tests enforce that production code does not depend on test-only utilities,
 * which could lead to runtime failures or security issues in production.</p>
 *
 * @doc.type class
 * @doc.purpose ArchUnit test to prevent test-only utilities in production code (P1-042)
 * @doc.layer product
 * @doc.pattern ArchitecturalTest
 */
package com.ghatana.digitalmarketing.application.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("P1-042: Test-only utility isolation tests")
class TestOnlyUtilityIsolationTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.digitalmarketing");
    }

    @Test
    @DisplayName("P1-042: production code must not import from test packages")
    void productionCodeMustNotImportFromTestPackages() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.digitalmarketing..")
                .should().dependOnClassesThat().resideInAPackage("..test..")
                .as("P1-042: production code must not import from test packages — " +
                    "test-only utilities should not be used in production");

        rule.check(productionClasses);
    }

    @Test
    @DisplayName("P1-042: production code must not use test-only utilities")
    void productionCodeMustNotUseTestOnlyUtilities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.digitalmarketing..")
                .should().dependOnClassesThat().haveSimpleNameMatching(".*Test.*")
                .orShould().dependOnClassesThat().haveSimpleNameMatching(".*Mock.*")
                .orShould().dependOnClassesThat().haveSimpleNameMatching(".*Stub.*")
                .orShould().dependOnClassesThat().haveSimpleNameMatching(".*Fake.*")
                .orShould().dependOnClassesThat().haveSimpleNameMatching(".*Dummy.*")
                .as("P1-042: production code must not use test-only utilities — " +
                    "test doubles should not be used in production");

        rule.check(productionClasses);
    }

    @Test
    @DisplayName("P1-042: production code must not use test framework classes")
    void productionCodeMustNotUseTestFrameworkClasses() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.digitalmarketing..")
                .should().dependOnClassesThat().resideInAPackage("org.junit..")
                .orShould().dependOnClassesThat().resideInAPackage("org.mockito..")
                .orShould().dependOnClassesThat().resideInAPackage("org.assertj..")
                .orShould().dependOnClassesThat().resideInAPackage("org.testcontainers..")
                .as("P1-042: production code must not use test framework classes — " +
                    "test frameworks should not be used in production");

        rule.check(productionClasses);
    }

    @Test
    @DisplayName("P1-042: production code must not use test-specific annotations")
    void productionCodeMustNotUseTestSpecificAnnotations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.digitalmarketing..")
                .should().beAnnotatedWith("org.junit.jupiter.api.Test")
                .orShould().beAnnotatedWith("org.junit.jupiter.api.BeforeEach")
                .orShould().beAnnotatedWith("org.junit.jupiter.api.BeforeAll")
                .orShould().beAnnotatedWith("org.junit.jupiter.api.AfterEach")
                .orShould().beAnnotatedWith("org.junit.jupiter.api.AfterAll")
                .as("P1-042: production code must not use test-specific annotations — " +
                    "test annotations should not be used in production");

        rule.check(productionClasses);
    }
}
