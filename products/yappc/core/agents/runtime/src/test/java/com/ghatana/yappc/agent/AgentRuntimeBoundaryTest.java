package com.ghatana.yappc.agent;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit boundary tests for the agents runtime module.
 *
 * @doc.type class
 * @doc.purpose Enforce that runtime agent base does not depend on higher-level specialist modules or product layers
 * @doc.layer product
 * @doc.pattern ArchUnit
 */
class AgentRuntimeBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .importPackages("com.ghatana.yappc.agent");

    @Test
    void runtimeShouldNotDependOnCodeSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ghatana.yappc.agent..")
            .should().dependOnClassesThat().resideInAPackage("..agents.code..")
            .because("Runtime is a base module; specialists must depend on runtime, not the other way around")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void runtimeShouldNotDependOnArchitectureSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ghatana.yappc.agent..")
            .should().dependOnClassesThat().resideInAPackage("..agents.architecture..")
            .because("Runtime is a base module; specialists must depend on runtime, not the other way around")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void runtimeShouldNotDependOnTestingSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ghatana.yappc.agent..")
            .should().dependOnClassesThat().resideInAPackage("..agents.testing..")
            .because("Runtime is a base module; specialists must depend on runtime, not the other way around")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void runtimeShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ghatana.yappc.agent..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Runtime must not depend on scaffold — they are different concerns")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void runtimeShouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ghatana.yappc.agent..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Runtime must not depend on refactorer")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }
}
