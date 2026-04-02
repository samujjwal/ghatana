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
@DisplayName("Delivery Specialists Boundary Tests")
class DeliverySpecialistsBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .importPackages("com.ghatana.yappc.agents.code");

    @Test
    @DisplayName("delivery specialists should not depend on code specialists directly")
    void deliverySpecialistsShouldNotDependOnCodeSpecialistInternals() {
        // The module is allowed to share the same package for build reasons,
        // but should not introduce circular cross-specialist dependencies.
        ArchRule rule = noClasses()
                .that().haveNameMatching(".*ReleaseOrchestrator.*|.*BudgetGate.*|.*SbomGenerator.*")
                .should().dependOnClassesThat().haveNameMatching(".*UxDirector.*|.*ApiHandlerGenerator.*|.*ReplayDebugger.*")
                .because("Delivery specialists must not depend on code-generation specialists")
                .allowEmptyShould(true);
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("delivery specialists should not depend on scaffold")
    void shouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..agents..")
                .should().dependOnClassesThat().resideInAPackage("..scaffold..")
                .because("Delivery specialists must not depend on scaffold modules")
                .allowEmptyShould(true);
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("delivery specialists should not depend on refactorer")
    void shouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..agents..")
                .should().dependOnClassesThat().resideInAPackage("..refactorer..")
                .because("Delivery specialists must not depend on refactorer modules")
                .allowEmptyShould(true);
        rule.check(CLASSES);
    }
}
