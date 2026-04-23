package com.ghatana.datacloud.plugins;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@DisplayName("Data Cloud Platform Plugins Architecture")
class DataCloudPlatformPluginsArchitectureTest {

    private static JavaClasses PLUGIN_CLASSES;

    @BeforeAll
    static void importClasses() { // GH-90000
        PLUGIN_CLASSES = new ClassFileImporter() // GH-90000
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) // GH-90000
                .importPackages("com.ghatana.datacloud.plugins");
    }

    @Test
    @DisplayName("platform plugins must not depend on AEP or orchestrator packages")
    void pluginsMustNotDependOnAepPackages() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.datacloud.plugins..")
                .should().dependOnClassesThat() // GH-90000
                .resideInAnyPackage("com.ghatana.aep..", "com.ghatana.orchestrator..") // GH-90000
                .because("Data Cloud plugins are foundational capabilities and must remain product-independent.");
        rule.check(PLUGIN_CLASSES); // GH-90000
    }

    @Test
    @DisplayName("platform plugins must not depend on launcher packages")
    void pluginsMustNotDependOnLauncherPackages() { // GH-90000
        ArchRule rule = noClasses() // GH-90000
                .that().resideInAPackage("com.ghatana.datacloud.plugins..")
                .should().dependOnClassesThat() // GH-90000
                .resideInAnyPackage("com.ghatana.datacloud.launcher..")
                .because("Extracted plugin implementations should not drift back into launcher-owned transport code.");
        rule.check(PLUGIN_CLASSES); // GH-90000
    }
}
