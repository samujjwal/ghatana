package com.ghatana.datacloud.api;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("Data Cloud Platform API Architecture")
class DataCloudPlatformApiArchitectureTest {

    private static JavaClasses PLATFORM_API_CLASSES;

    @BeforeAll
    static void importClasses() { 
        PLATFORM_API_CLASSES = new ClassFileImporter() 
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) 
                .importPackages( 
                        "com.ghatana.datacloud.api",
                        "com.ghatana.datacloud.application",
                        "com.ghatana.datacloud.attention",
                        "com.ghatana.datacloud.client",
                        "com.ghatana.datacloud.memory",
                        "com.ghatana.datacloud.workspace");
    }

    @Test
    @DisplayName("platform-api must not depend on platform-launcher packages")
    void platformApiMustNotDependOnPlatformLauncher() { 
        ArchRule rule = noClasses() 
                .that().resideInAnyPackage( 
                        "com.ghatana.datacloud.api..",
                        "com.ghatana.datacloud.application..",
                        "com.ghatana.datacloud.attention..",
                        "com.ghatana.datacloud.client..",
                        "com.ghatana.datacloud.memory..",
                        "com.ghatana.datacloud.workspace..")
                .should().dependOnClassesThat() 
                .resideInAnyPackage("com.ghatana.datacloud.launcher..", "com.ghatana.datacloud.infrastructure..") 
                .because("The extracted API module must stay reusable and independent of runtime bootstrap code.");
        rule.check(PLATFORM_API_CLASSES); 
    }

    @Test
    @DisplayName("platform-api must not depend on AEP or orchestrator packages")
    void platformApiMustNotDependOnAepPackages() { 
        ArchRule rule = noClasses() 
                .that().resideInAnyPackage( 
                        "com.ghatana.datacloud.api..",
                        "com.ghatana.datacloud.application..",
                        "com.ghatana.datacloud.attention..",
                        "com.ghatana.datacloud.client..",
                        "com.ghatana.datacloud.memory..",
                        "com.ghatana.datacloud.workspace..")
                .should().dependOnClassesThat() 
                .resideInAnyPackage("com.ghatana.aep..", "com.ghatana.orchestrator..") 
                .because("Data Cloud API contracts are foundational and must remain product-independent.");
        rule.check(PLATFORM_API_CLASSES); 
    }
}
