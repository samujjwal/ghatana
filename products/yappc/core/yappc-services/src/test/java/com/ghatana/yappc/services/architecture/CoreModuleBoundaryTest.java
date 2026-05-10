package com.ghatana.yappc.services.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Core Module Boundary Tests
 * 
 * Enforces architectural boundaries between core modules to prevent
 * unwanted dependencies and maintain clean separation of concerns.
 * 
 * Forbidden imports between core modules:
 * - agents (agent orchestration and execution)
 * - scaffold (scaffold packs and templates)
 * - refactorer (automated refactoring)
 * - lifecycle (lifecycle management)
 * - infrastructure (infrastructure concerns)
 * - AEP adapter (AEP integration)
 * 
 * @doc.type test
 * @doc.purpose ArchUnit boundary tests for core modules
 * @doc.layer core
 * @doc.pattern Architecture Boundary Test
 */
@DisplayName("Core Module Architecture Boundary Tests")
class CoreModuleBoundaryTest {

    @Test
    @DisplayName("CM-1: agents module must not depend on scaffold, refactorer, or AEP adapter")
    void agents_mustNotDependOnForbiddenModules() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.ghatana.yappc");

        noClasses()
                .that().resideInAnyPackage("com.ghatana.yappc.agents..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.ghatana.yappc.scaffold..",
                        "com.ghatana.yappc.refactorer..",
                        "com.ghatana.yappc.aep..")
                .because("Agents should be independent of scaffolding, refactoring, and AEP concerns")
                .check(imported);
    }

    @Test
    @DisplayName("CM-2: scaffold module must not depend on refactorer or AEP adapter")
    void scaffold_mustNotDependOnForbiddenModules() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.ghatana.yappc");

        noClasses()
                .that().resideInAnyPackage("com.ghatana.yappc.scaffold..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.ghatana.yappc.refactorer..",
                        "com.ghatana.yappc.aep..")
                .because("Scaffolding should be independent of refactoring and AEP concerns")
                .allowEmptyShould(true)
                .check(imported);
    }

    @Test
    @DisplayName("CM-3: refactorer module must not depend on scaffold or AEP adapter")
    void refactorer_mustNotDependOnForbiddenModules() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.ghatana.yappc");

        noClasses()
                .that().resideInAnyPackage("com.ghatana.yappc.refactorer..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.ghatana.yappc.scaffold..",
                        "com.ghatana.yappc.aep..")
                .because("Refactoring should be independent of scaffolding and AEP concerns")
                .allowEmptyShould(true)
                .check(imported);
    }

    @Test
    @DisplayName("CM-4: lifecycle module must not depend on scaffold, refactorer, or AEP adapter")
    void lifecycle_mustNotDependOnForbiddenModules() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.ghatana.yappc");

        noClasses()
                .that().resideInAnyPackage("com.ghatana.yappc.lifecycle..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.ghatana.yappc.scaffold..",
                        "com.ghatana.yappc.refactorer..",
                        "com.ghatana.yappc.aep..")
                .because("Lifecycle management should be independent of scaffolding, refactoring, and AEP concerns")
                .allowEmptyShould(true)
                .check(imported);
    }

    @Test
    @DisplayName("CM-5: infrastructure module must not depend on scaffold, refactorer, lifecycle, or AEP adapter")
    void infrastructure_mustNotDependOnForbiddenModules() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.ghatana.yappc");

        noClasses()
                .that().resideInAnyPackage("com.ghatana.yappc.infrastructure..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.ghatana.yappc.scaffold..",
                        "com.ghatana.yappc.refactorer..",
                        "com.ghatana.yappc.lifecycle..",
                        "com.ghatana.yappc.aep..")
                .because("Infrastructure should be independent of domain-specific concerns")
                .check(imported);
    }

    @Test
    @DisplayName("CM-6: AEP adapter module must not depend on scaffold or refactorer")
    void aepAdapter_mustNotDependOnForbiddenModules() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.ghatana.yappc");

        noClasses()
                .that().resideInAnyPackage("com.ghatana.yappc.aep..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.ghatana.yappc.scaffold..",
                        "com.ghatana.yappc.refactorer..")
                .because("AEP adapter should be independent of scaffolding and refactoring concerns")
                .allowEmptyShould(true)
                .check(imported);
    }

    @Test
    @DisplayName("CM-7: No circular dependencies between core modules")
    void noCircularDependenciesBetweenCoreModules() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.ghatana.yappc");

        // Prevent agents from depending on lifecycle and lifecycle depending on agents
        noClasses()
                .that().resideInAnyPackage("com.ghatana.yappc.agents..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.ghatana.yappc.lifecycle..")
                .because("Agents and lifecycle should have clear separation of concerns")
                .allowEmptyShould(true)
                .check(imported);

        noClasses()
                .that().resideInAnyPackage("com.ghatana.yappc.lifecycle..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.ghatana.yappc.agents..")
                .because("Lifecycle and agents should have clear separation of concerns")
                .allowEmptyShould(true)
                .check(imported);
    }

    @Test
    @DisplayName("CM-8: Core modules should not depend on implementation details of other core modules")
    void coreModules_mustNotDependOnImplementationDetails() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.ghatana.yappc");

        noClasses()
                .that().resideInAnyPackage(
                        "com.ghatana.yappc.agents..",
                        "com.ghatana.yappc.scaffold..",
                        "com.ghatana.yappc.refactorer..",
                        "com.ghatana.yappc.lifecycle..",
                        "com.ghatana.yappc.infrastructure..",
                        "com.ghatana.yappc.aep..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..impl..", "..internal..", "..private..")
                .because("Core modules should not depend on implementation details of other modules")
                .check(imported);
    }
}
