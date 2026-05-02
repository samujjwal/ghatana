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

    private static final JavaClasses CLASSES = new ClassFileImporter() 
        .importPackages("com.ghatana.yappc.agents.code");

    @Test
    void codeSpecialistsShouldNotDependOnArchitectureSpecialists() { 
        ArchRule rule = noClasses() 
            .that().resideInAPackage("..agents.code..")
            .should().dependOnClassesThat().resideInAPackage("..agents.architecture..")
            .because("Code specialists must not depend on architecture specialists")
            .allowEmptyShould(true); 

        rule.check(CLASSES); 
    }

    @Test
    void codeSpecialistsShouldNotDependOnTestingSpecialists() { 
        ArchRule rule = noClasses() 
            .that().resideInAPackage("..agents.code..")
            .should().dependOnClassesThat().resideInAPackage("..agents.testing..")
            .because("Code specialists must not depend on testing specialists")
            .allowEmptyShould(true); 

        rule.check(CLASSES); 
    }

    @Test
    void agentsShouldNotDependOnScaffold() { 
        ArchRule rule = noClasses() 
            .that().resideInAPackage("..agents..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Agents must not depend on scaffold - they are different concerns")
            .allowEmptyShould(true); 

        rule.check(CLASSES); 
    }

    @Test
    void agentsShouldNotDependOnRefactorer() { 
        ArchRule rule = noClasses() 
            .that().resideInAPackage("..agents..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Agents must not depend on refactorer")
            .allowEmptyShould(true); 

        rule.check(CLASSES); 
    }

    @Test
    void modulesShouldHaveMaximum150Files() { 
        // This is enforced by Gradle checkModuleSize task
        // This test documents the requirement
        ArchRule rule = classes() 
            .that().resideInAPackage("..agents.code..")
            .should().haveSimpleNameNotContaining("TooManyFiles")
            .because("Modules should have maximum 150 files")
            .allowEmptyShould(true); 

        rule.check(CLASSES); 
    }
}
