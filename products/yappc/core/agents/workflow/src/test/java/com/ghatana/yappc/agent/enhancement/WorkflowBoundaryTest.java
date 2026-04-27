package com.ghatana.yappc.agent.enhancement;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit boundary tests for the agents workflow module.
 *
 * @doc.type class
 * @doc.purpose Enforce that workflow steps do not violate YAPPC module boundaries
 * @doc.layer product
 * @doc.pattern ArchUnit
 */
class WorkflowBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .importPackages("com.ghatana.yappc.agent.enhancement",
                        "com.ghatana.yappc.agent.ops",
                        "com.ghatana.yappc.agent.implementation");

    @Test
    void workflowShouldNotDependOnScaffold() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ghatana.yappc.agent..")
            .should().dependOnClassesThat().resideInAPackage("..scaffold..")
            .because("Workflow/step modules must not depend on scaffold — they are different concerns")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void workflowShouldNotDependOnRefactorer() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ghatana.yappc.agent..")
            .should().dependOnClassesThat().resideInAPackage("..refactorer..")
            .because("Workflow/step modules must not depend on refactorer")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }

    @Test
    void workflowShouldNotDependOnFrontendLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ghatana.yappc.agent..")
            .should().dependOnClassesThat().resideInAPackage("..frontend..")
            .because("Agent workflow must not depend on frontend code")
            .allowEmptyShould(true);

        rule.check(CLASSES);
    }
}
