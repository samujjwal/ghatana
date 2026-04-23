package com.ghatana.platform.testing.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * ArchUnit test suite for enforcing platform architecture rules.
 *
 * @doc.type class
 * @doc.purpose Enforces dependency direction and layering rules per audit
 * @doc.layer platform
 * @doc.pattern Architecture Test
 */
public class ArchitectureRulesTest {

    private static JavaClasses platformClasses;
    private static JavaClasses productClasses;

    @BeforeAll
    static void setup() { // GH-90000
        ClassFileImporter importer = new ClassFileImporter(); // GH-90000
        // Import packages - ArchUnit will return empty set if packages don't exist
        platformClasses = importer.importPackages("com.ghatana.platform");
        productClasses = importer.importPackages("com.ghatana.products");
    }

    @Test
    @DisplayName("No circular dependencies in platform modules")
    void noCircularDependencies() { // GH-90000
        SlicesRuleDefinition.slices() // GH-90000
                .matching("com.ghatana.platform.(*)..")
                .should().beFreeOfCycles() // GH-90000
                .check(platformClasses); // GH-90000
    }

    @Test
    @DisplayName("Products depend only on platform, not other products")
    void productDependencies() { // GH-90000
        ArchRule rule = ArchRuleDefinition.classes() // GH-90000
                .that().resideInAPackage("com.ghatana.products..")
                .should().onlyDependOnClassesThat() // GH-90000
                .resideInAnyPackage( // GH-90000
                        "com.ghatana.platform..",
                        "com.ghatana.products.*",
                        "java..",
                        "javax..",
                        "io.activej..",
                        "com.fasterxml.jackson..",
                        "org.slf4j.."
                )
                .allowEmptyShould(true); // GH-90000
        rule.check(productClasses); // GH-90000
    }

    @Test
    @DisplayName("Platform foundation has minimal dependencies")
    void platformFoundationIndependence() { // GH-90000
        ArchRule rule = ArchRuleDefinition.classes() // GH-90000
                .that().resideInAPackage("com.ghatana.platform.core..")
                .should().onlyDependOnClassesThat() // GH-90000
                .resideInAnyPackage( // GH-90000
                        "com.ghatana.platform.core..",
                        "com.ghatana.platform.validation..",
                        "java..",
                        "javax..",
                        "io.activej..",
                        "com.fasterxml.jackson..",
                        "org.slf4j..",
                        "org.jetbrains.annotations..",
                        "lombok.."
                );
        rule.check(platformClasses); // GH-90000
    }

    @Test
    @DisplayName("Plugins depend only on platform, used by products")
    void pluginDependencyDirection() { // GH-90000
        JavaClasses pluginClasses = new ClassFileImporter() // GH-90000
                .importPackages("com.ghatana.platform.plugins");

        ArchRule rule = ArchRuleDefinition.classes() // GH-90000
                .that().resideInAPackage("com.ghatana.platform.plugins..")
                .should().onlyDependOnClassesThat() // GH-90000
                .resideInAnyPackage( // GH-90000
                        "com.ghatana.platform..",
                        "com.ghatana.platform.plugins..",
                        "java..",
                        "javax.."
                )
                .allowEmptyShould(true); // GH-90000
        rule.check(pluginClasses); // GH-90000
    }

    @Test
    @DisplayName("Shared services depend on platform and kernel")
    void sharedServiceDependencies() { // GH-90000
        JavaClasses sharedClasses = new ClassFileImporter() // GH-90000
                .importPackages("com.ghatana.shared");

        ArchRule rule = ArchRuleDefinition.classes() // GH-90000
                .that().resideInAPackage("com.ghatana.shared..")
                .should().onlyDependOnClassesThat() // GH-90000
                .resideInAnyPackage( // GH-90000
                        "com.ghatana.platform..",
                        "com.ghatana.kernel..",
                        "com.ghatana.shared..",
                        "java..",
                        "javax.."
                )
                .allowEmptyShould(true); // GH-90000
        rule.check(sharedClasses); // GH-90000
    }
}
