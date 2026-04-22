package com.ghatana.datacloud.api;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("Data Cloud Platform API Architecture [GH-90000]")
class DataCloudPlatformApiArchitectureTest {

    private static JavaClasses PLATFORM_API_CLASSES;

    @BeforeAll
    static void importClasses() { // GH-90000
        PLATFORM_API_CLASSES = new ClassFileImporter() // GH-90000
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) // GH-90000
                .importPackages( // GH-90000
                        "com.ghatana.datacloud.api",
                        "com.ghatana.datacloud.application",
                        "com.ghatana.datacloud.attention",
                        "com.ghatana.datacloud.client",
                        "com.ghatana.datacloud.memory",
                        "com.ghatana.datacloud.workspace");
    }

    @Test
    @DisplayName("platform-api must not depend on platform-launcher packages [GH-90000]")
    void platformApiMustNotDependOnPlatformLauncher() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
                .that().resideInAnyPackage( // GH-90000
                        "com.ghatana.datacloud.api..",
                        "com.ghatana.datacloud.application..",
                        "com.ghatana.datacloud.attention..",
                        "com.ghatana.datacloud.client..",
                        "com.ghatana.datacloud.memory..",
                        "com.ghatana.datacloud.workspace..")
                .should().dependOnClassesThat() // GH-90000
                .resideInAnyPackage("com.ghatana.datacloud.launcher..", "com.ghatana.datacloud.infrastructure..") // GH-90000
                .because("The extracted API module must stay reusable and independent of runtime bootstrap code. [GH-90000]");
        rule.check(PLATFORM_API_CLASSES); // GH-90000
    }

    @Test
    @DisplayName("platform-api must not depend on AEP or orchestrator packages [GH-90000]")
    void platformApiMustNotDependOnAepPackages() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
                .that().resideInAnyPackage( // GH-90000
                        "com.ghatana.datacloud.api..",
                        "com.ghatana.datacloud.application..",
                        "com.ghatana.datacloud.attention..",
                        "com.ghatana.datacloud.client..",
                        "com.ghatana.datacloud.memory..",
                        "com.ghatana.datacloud.workspace..")
                .should().dependOnClassesThat() // GH-90000
                .resideInAnyPackage("com.ghatana.aep..", "com.ghatana.orchestrator..") // GH-90000
                .because("Data Cloud API contracts are foundational and must remain product-independent. [GH-90000]");
        rule.check(PLATFORM_API_CLASSES); // GH-90000
    }
}
