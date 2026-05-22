package com.ghatana.datacloud.integration;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * @doc.type class
 * @doc.purpose Enforce Data Cloud shared-library package boundaries with bytecode-level ArchUnit rules
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Shared Library Boundary Architecture")
@Tag("architecture")
class SharedLibraryBoundaryArchTest {

    private static JavaClasses dataCloudClasses;

    @BeforeAll
    static void importClasses() {
        dataCloudClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ghatana.datacloud");
    }

    @Test
    @DisplayName("Extensions remain independent from launcher transport internals")
    void extensionsMustNotDependOnLauncherInternals() {
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage(
                "com.ghatana.datacloud.agent..",
                "com.ghatana.datacloud.kernel..",
                "com.ghatana.datacloud.plugins..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.ghatana.datacloud.launcher..");

        rule.check(dataCloudClasses);
    }

    @Test
    @DisplayName("Foundational planes do not import Action Plane implementation packages")
    void foundationalPlanesMustNotDependOnActionPlaneImplementations() {
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage(
                "com.ghatana.datacloud.data..",
                "com.ghatana.datacloud.event..",
                "com.ghatana.datacloud.context..",
                "com.ghatana.datacloud.governance..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.ghatana.aep.runtime..",
                "com.ghatana.aep.orchestration..",
                "com.ghatana.orchestrator..");

        rule.check(dataCloudClasses);
    }
}
