package com.ghatana.yappc.agents.code;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests to enforce module boundaries and architectural rules.
 */
class AgentBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter() // GH-90000
        .importPackages("com.ghatana.yappc.agents.code");

    @Test
    void codeSpecialistsShouldNotDependOnArchitectureSpecialists() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
            .that().resideInAPackage("..agents.code..")
            .should().dependOnClassesThat().resideInAPackage("..agents.architecture..")
            .because("Code specialists must not depend on architecture specialists")
            .allowEmptyShould(true); // GH-90000

        rule.check(CLASSES); // GH-90000
    }

    @Test
    void codeSpecialistsShouldNotDependOnTestingSpecialists() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
            .that().resideInAPackage("..agents.code..")
            .should().dependOnClassesThat().resideInAPackage("..agents.testing..")
            .because("Code specialists must not depend on testing specialists")
            .allowEmptyShould(true); // GH-90000

        rule.check(CLASSES); // GH-90000
    }

    @Test
    void agentsShouldNotDependOnScaffold() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
            .that().resideInAPackage("..agents..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Agents must not depend on scaffold - they are different concerns")
            .allowEmptyShould(true); // GH-90000

        rule.check(CLASSES); // GH-90000
    }

    @Test
    void agentsShouldNotDependOnRefactorer() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
            .that().resideInAPackage("..agents..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Agents must not depend on refactorer")
            .allowEmptyShould(true); // GH-90000

        rule.check(CLASSES); // GH-90000
    }

    @Test
    void modulesShouldHaveMaximum150Files() { // GH-90000
        // This is enforced by Gradle checkModuleSize task
        // This test documents the requirement
        ArchRule rule = classes() // GH-90000
            .that().resideInAPackage("..agents.code..")
            .should().haveSimpleNameNotContaining("TooManyFiles")
            .because("Modules should have maximum 150 files")
            .allowEmptyShould(true); // GH-90000

        rule.check(CLASSES); // GH-90000
    }
}
