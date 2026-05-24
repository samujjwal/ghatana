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
    private static JavaClasses aepClasses;

    @BeforeAll
    static void importClasses() {
        dataCloudClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                "com.ghatana.datacloud",
                "com.ghatana.services");
        aepClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                "com.ghatana.aep",
                "com.ghatana.core.operator.agent");
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
                "com.ghatana.datacloud.entity..",
                "com.ghatana.datacloud.record..",
                "com.ghatana.datacloud.event..",
                "com.ghatana.datacloud.platform.event..",
                "com.ghatana.datacloud.context..",
                "com.ghatana.datacloud.governance..",
                "com.ghatana.datacloud.analytics..",
                "com.ghatana.datacloud.config..",
                "com.ghatana.datacloud.application.policy..",
                "com.ghatana.datacloud.infrastructure.policy..",
                "com.ghatana.datacloud.pattern..",
                "com.ghatana.datacloud.reflex..",
                "com.ghatana.datacloud.storage..",
                "com.ghatana.datacloud.plugins..",
                "com.ghatana.services.featurestore..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.ghatana.aep..",
                "com.ghatana.aep.runtime..",
                "com.ghatana.aep.orchestration..",
                "com.ghatana.orchestrator..",
                "com.ghatana.pattern..",
                "com.ghatana.core.operator..",
                "com.ghatana.pipeline.registry..");

        rule.check(dataCloudClasses);
    }

    @Test
    @DisplayName("AEP EventCloud SPI does not depend on Data-Cloud implementations")
    void aepEventCloudSpiMustNotDependOnDataCloudImplementations() {
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage("com.ghatana.aep.event.spi..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.ghatana.datacloud..",
                "com.ghatana.services..");

        rule.check(aepClasses);
    }

    @Test
    @DisplayName("AEP PatternSpec contracts do not depend on Data-Cloud implementations")
    void aepPatternSpecContractsMustNotDependOnDataCloudImplementations() {
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage(
                "com.ghatana.aep.pattern.spec..",
                "com.ghatana.aep.pattern.runtime..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.ghatana.datacloud..",
                "com.ghatana.services..");

        rule.check(aepClasses);
    }
}
