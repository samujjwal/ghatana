package com.ghatana.yappc.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit boundary tests for core modules.
 *
 * Enforces no forbidden imports between agents, scaffold, refactorer, lifecycle, infrastructure, and AEP adapter modules.
 *
 * @doc.type class
 * @doc.purpose Enforce no forbidden imports between core modules
 * @doc.layer product
 * @doc.pattern ArchUnit
 */
class CoreModuleBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .importPackages("com.ghatana.yappc");

    @Test
    void agentsShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..agents..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Agents must not depend on scaffold — they are separate concerns")
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
    void scaffoldShouldNotDependOnAgents() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..scaffold..")
            .should().dependOnClassesThat().resideInAPackage("..agents..")
            .because("Scaffold must not depend on agents")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void scaffoldShouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..scaffold..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Scaffold must not depend on refactorer")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void refactorerShouldNotDependOnAgents() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..refactorer..")
            .should().dependOnClassesThat().resideInAPackage("..agents..")
            .because("Refactorer must not depend on agents")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void refactorerShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..refactorer..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Refactorer must not depend on scaffold")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void lifecycleShouldNotDependOnAgents() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..lifecycle..")
            .should().dependOnClassesThat().resideInAPackage("..agents..")
            .because("Lifecycle must not depend on agents")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void lifecycleShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..lifecycle..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Lifecycle must not depend on scaffold")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void lifecycleShouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..lifecycle..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Lifecycle must not depend on refactorer")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void infrastructureShouldNotDependOnAgents() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..agents..")
            .because("Infrastructure must not depend on agents")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void infrastructureShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Infrastructure must not depend on scaffold")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void infrastructureShouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Infrastructure must not depend on refactorer")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void infrastructureAdaptersShouldNotDependOnAgents() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..infrastructure..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..agents..")
            .because("Infrastructure adapters must not depend on agents")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void aepAdaptersShouldNotDependOnAgents() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..aep..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..agents..")
            .because("AEP adapters must not depend on agents")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void aepAdaptersShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..aep..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("AEP adapters must not depend on scaffold")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void aepAdaptersShouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..aep..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("AEP adapters must not depend on refactorer")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }
}
