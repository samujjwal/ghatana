package com.ghatana.yappc.agents.code;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit boundary tests for delivery-specialists module.
 *
 * @doc.type class
 * @doc.purpose Enforce module boundary rules for delivery-specialists
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Delivery Specialists Boundary Tests [GH-90000]")
class DeliverySpecialistsBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter() // GH-90000
            .importPackages("com.ghatana.yappc.agents.code [GH-90000]");

    @Test
    @DisplayName("delivery specialists should not depend on code specialists directly [GH-90000]")
    void deliverySpecialistsShouldNotDependOnCodeSpecialistInternals() { // GH-90000
        // The module is allowed to share the same package for build reasons,
        // but should not introduce circular cross-specialist dependencies.
        ArchRule rule = noClasses() // GH-90000
                .that().haveNameMatching(".*ReleaseOrchestrator.*|.*BudgetGate.*|.*SbomGenerator.* [GH-90000]")
                .should().dependOnClassesThat().haveNameMatching(".*UxDirector.*|.*ApiHandlerGenerator.*|.*ReplayDebugger.* [GH-90000]")
                .because("Delivery specialists must not depend on code-generation specialists [GH-90000]")
                .allowEmptyShould(true); // GH-90000
        rule.check(CLASSES); // GH-90000
    }

    @Test
    @DisplayName("delivery specialists should not depend on scaffold [GH-90000]")
    void shouldNotDependOnScaffold() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("..agents.. [GH-90000]")
                .should().dependOnClassesThat().resideInAPackage("..scaffold.. [GH-90000]")
                .because("Delivery specialists must not depend on scaffold modules [GH-90000]")
                .allowEmptyShould(true); // GH-90000
        rule.check(CLASSES); // GH-90000
    }

    @Test
    @DisplayName("delivery specialists should not depend on refactorer [GH-90000]")
    void shouldNotDependOnRefactorer() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("..agents.. [GH-90000]")
                .should().dependOnClassesThat().resideInAPackage("..refactorer.. [GH-90000]")
                .because("Delivery specialists must not depend on refactorer modules [GH-90000]")
                .allowEmptyShould(true); // GH-90000
        rule.check(CLASSES); // GH-90000
    }
}
