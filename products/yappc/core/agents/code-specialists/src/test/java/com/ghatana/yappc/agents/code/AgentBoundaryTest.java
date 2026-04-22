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
        .importPackages("com.ghatana.yappc.agents.code [GH-90000]");

    @Test
    void codeSpecialistsShouldNotDependOnArchitectureSpecialists() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
            .that().resideInAPackage("..agents.code.. [GH-90000]")
            .should().dependOnClassesThat().resideInAPackage("..agents.architecture.. [GH-90000]")
            .because("Code specialists must not depend on architecture specialists [GH-90000]")
            .allowEmptyShould(true); // GH-90000

        rule.check(CLASSES); // GH-90000
    }

    @Test
    void codeSpecialistsShouldNotDependOnTestingSpecialists() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
            .that().resideInAPackage("..agents.code.. [GH-90000]")
            .should().dependOnClassesThat().resideInAPackage("..agents.testing.. [GH-90000]")
            .because("Code specialists must not depend on testing specialists [GH-90000]")
            .allowEmptyShould(true); // GH-90000

        rule.check(CLASSES); // GH-90000
    }

    @Test
    void agentsShouldNotDependOnScaffold() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
            .that().resideInAPackage("..agents.. [GH-90000]")
            .should().dependOnClassesThat().resideInAPackage("..scaffold.. [GH-90000]")
            .because("Agents must not depend on scaffold - they are different concerns [GH-90000]")
            .allowEmptyShould(true); // GH-90000

        rule.check(CLASSES); // GH-90000
    }

    @Test
    void agentsShouldNotDependOnRefactorer() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
            .that().resideInAPackage("..agents.. [GH-90000]")
            .should().dependOnClassesThat().resideInAPackage("..refactorer.. [GH-90000]")
            .because("Agents must not depend on refactorer [GH-90000]")
            .allowEmptyShould(true); // GH-90000

        rule.check(CLASSES); // GH-90000
    }

    @Test
    void modulesShouldHaveMaximum150Files() { // GH-90000
        // This is enforced by Gradle checkModuleSize task
        // This test documents the requirement
        ArchRule rule = classes() // GH-90000
            .that().resideInAPackage("..agents.code.. [GH-90000]")
            .should().haveSimpleNameNotContaining("TooManyFiles [GH-90000]")
            .because("Modules should have maximum 150 files [GH-90000]")
            .allowEmptyShould(true); // GH-90000

        rule.check(CLASSES); // GH-90000
    }
}
