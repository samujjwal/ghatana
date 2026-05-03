package com.ghatana.yappc.agents.testing;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit boundary tests for the testing-specialists module.
 *
 * @doc.type class
 * @doc.purpose Enforce that testing-specialist agents do not violate module boundaries
 * @doc.layer product
 * @doc.pattern ArchUnit
 */
class TestingSpecialistsBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .importPackages("com.ghatana.yappc.agents.testing");

    @Test
    void testingSpecialistsShouldNotDependOnArchitectureSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.testing..")
            .should().dependOnClassesThat().resideInAPackage("..agents.architecture..")
            .because("Testing specialists must not depend on architecture specialists")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void testingSpecialistsShouldNotDependOnCodeSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.testing..")
            .should().dependOnClassesThat().resideInAPackage("..agents.code..")
            .because("Testing specialists must not depend on code specialists")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void testingSpecialistsShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.testing..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Agent modules must not depend on scaffold — they are different concerns")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void testingSpecialistsShouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.testing..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Agent modules must not depend on refactorer")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }
}
