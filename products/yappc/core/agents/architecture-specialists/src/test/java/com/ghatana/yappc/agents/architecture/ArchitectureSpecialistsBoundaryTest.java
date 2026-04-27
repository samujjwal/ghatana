package com.ghatana.yappc.agents.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit boundary tests for the architecture-specialists module.
 *
 * @doc.type class
 * @doc.purpose Enforce that architecture-specialist agents do not violate module boundaries
 * @doc.layer product
 * @doc.pattern ArchUnit
 */
class ArchitectureSpecialistsBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .importPackages("com.ghatana.yappc.agents.architecture");

    @Test
    void architectureSpecialistsShouldNotDependOnTestingSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.architecture..")
            .should().dependOnClassesThat().resideInAPackage("..agents.testing..")
            .because("Architecture specialists must not depend on testing specialists")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void architectureSpecialistsShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.architecture..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Agent modules must not depend on scaffold — they are different concerns")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void architectureSpecialistsShouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.architecture..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Agent modules must not depend on refactorer")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }
}
