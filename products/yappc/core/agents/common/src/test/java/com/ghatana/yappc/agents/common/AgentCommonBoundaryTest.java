package com.ghatana.yappc.agents.common;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit boundary tests for the agents common module.
 *
 * @doc.type class
 * @doc.purpose Enforce that common shared types do not import from higher-level or sibling specialist modules
 * @doc.layer product
 * @doc.pattern ArchUnit
 */
class AgentCommonBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .importPackages("com.ghatana.yappc.agents.common");

    @Test
    void commonShouldNotDependOnCodeSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.common..")
            .should().dependOnClassesThat().resideInAPackage("..agents.code..")
            .because("Common is a shared base — specialist modules depend on common, not vice versa")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void commonShouldNotDependOnArchitectureSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.common..")
            .should().dependOnClassesThat().resideInAPackage("..agents.architecture..")
            .because("Common is a shared base — specialist modules depend on common, not vice versa")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void commonShouldNotDependOnTestingSpecialists() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.common..")
            .should().dependOnClassesThat().resideInAPackage("..agents.testing..")
            .because("Common is a shared base — specialist modules depend on common, not vice versa")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void commonShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.common..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Agent common must not depend on scaffold — they are different concerns")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void commonShouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents.common..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Agent common must not depend on refactorer")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }
}
