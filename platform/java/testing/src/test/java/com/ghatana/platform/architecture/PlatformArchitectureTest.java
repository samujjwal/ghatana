/*
 * Copyright (c) 2026 Ghatana.
 * All rights reserved.
 */
package com.ghatana.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Platform-wide ArchUnit rules enforcing Ghatana architectural standards.
 * 
 * <p>These rules enforce:
 * <ul>
 *   <li>No circular dependencies between modules</li>
 *   <li>libs cannot depend on products</li>
 *   <li>ActiveJ Promise required (no CompletableFuture)</li>
 *   <li>SLF4J required (no Log4j2)</li>
 *   <li>Proper layer dependencies</li>
 * </ul>
 *
 * <p><b>NOTE:</b> Some rules overlap with {@code ArchitectureGuardrailsTest} in the
 * core module (platform-product isolation, cross-product isolation, CompletableFuture ban,
 * async test rules). Changes to overlapping rules MUST be synchronized between both classes.
 *
 * @see com.ghatana.platform.architecture.ArchitectureGuardrailsTest
 * @doc.type class
 * @doc.purpose Platform-wide architectural constraint enforcement
 * @doc.layer platform
 * @doc.pattern ArchitecturalTest
 */
@DisplayName("Platform Architecture Rules")
public class PlatformArchitectureTest {

    private static JavaClasses allClasses;
    private static JavaClasses libsClasses;
    private static JavaClasses productsClasses;

    @BeforeAll
    static void importClasses() {
        allClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ghatana");
        
        libsClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                "com.ghatana.core",
                "com.ghatana.ai",
                "com.ghatana.observability",
                "com.ghatana.security",
                "com.ghatana.plugin"
            );
        
        productsClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                "com.ghatana.datacloud",
                "com.ghatana.eventprocessing",
                "com.ghatana.yappc",
                "com.ghatana.guardian"
            );
    }

    @Nested
    @DisplayName("Dependency Rules")
    class DependencyRules {

        @Test
        @DisplayName("No circular dependencies between slices")
        void noCircularDependencies() {
            ArchRule rule = SlicesRuleDefinition.slices()
                .matching("com.ghatana.(*)..")
                .should().beFreeOfCycles()
                .as("No circular dependencies between top-level packages (informational)");
            
            rule.allowEmptyShould(true).check(allClasses);
        }

        // ── CRIT-001: Core ↔ Domain Circular Dependency Prevention ──────────
        // See: SHARED_MODULES_AUDIT_REPORT.md FINDING-001 / CRIT-001
        // The domain module (com.ghatana.platform.domain.**, com.ghatana.platform.schema.**)
        // must NOT import from core service-layer implementation packages.
        // Domain is allowed to use core value types (com.ghatana.platform.types.**)
        // and utilities (com.ghatana.platform.core.util.**) but NOT core service implementations.

        @Test
        @DisplayName("Domain must not import core service-layer operators (CRIT-001)")
        void domainMustNotImportCoreOperators() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                    "com.ghatana.platform.domain..",
                    "com.ghatana.platform.schema.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                    "com.ghatana.core.operator..",
                    "com.ghatana.core.service..",
                    "com.ghatana.core.pipeline.."
                )
                .as("Domain/Schema modules must not import core service-layer operator or pipeline classes " +
                    "(CRIT-001: resolves core↔domain circular dependency)");

            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("Platform domain module must not import core state implementations (CRIT-001)")
        void platformDomainMustNotImportCoreStateImpl() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                    "com.ghatana.platform.domain..",
                    "com.ghatana.platform.schema.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                    "com.ghatana.core.state.."
                )
                .as("Platform domain classes must not depend on core state implementations " +
                    "(CRIT-001: prevents layering violation that causes build instability)");

            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("Core module must not import domain pipeline specs (CRIT-001)")
        void coreMustNotImportDomainPipelineSpecs() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                    "com.ghatana.platform.core..",
                    "com.ghatana.platform.types.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                    "com.ghatana.platform.domain.pipeline..",
                    "com.ghatana.platform.domain.auth..",
                    "com.ghatana.platform.schema.."
                )
                .as("Core/types modules must not depend on domain-layer pipeline or schema classes " +
                    "(CRIT-001: enforces strict downward dependency flow core→domain is wrong direction)");

            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("Libs cannot depend on products")
        void libsCannotDependOnProducts() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                    "com.ghatana.core..",
                    "com.ghatana.ai..",
                    "com.ghatana.observability..",
                    "com.ghatana.security..",
                    "com.ghatana.plugin.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                    "com.ghatana.datacloud..",
                    "com.ghatana.eventprocessing..",
                    "com.ghatana.yappc..",
                    "com.ghatana.guardian.."
                )
                .as("Library modules should not depend on product modules (informational)");
            
            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("SPI modules only contain interfaces")
        void spiModulesOnlyContainInterfaces() {
            ArchRule rule = classes()
                .that().resideInAnyPackage("..spi..")
                .should().beInterfaces()
                .orShould().beRecords()
                .orShould().beEnums()
                .as("SPI packages should only contain interfaces, records, and enums");
            
            // Note: This is informational, violations logged but not failed
            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("AEP must not depend on DataCloud product code")
        void aepMustNotDependOnDataCloud() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                    "com.ghatana.aep..",
                    "com.ghatana.pipeline..",
                    "com.ghatana.agent..",
                    "com.ghatana.eventprocessing.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                    "com.ghatana.datacloud.."
                )
                .as("AEP product must not depend on DataCloud product code directly (informational)");

            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("DataCloud must not depend on AEP product code")
        void dataCloudMustNotDependOnAep() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                    "com.ghatana.datacloud.."
                )
                .should().dependOnClassesThat().resideInAnyPackage(
                    "com.ghatana.aep..",
                    "com.ghatana.pipeline..",
                    "com.ghatana.agent..",
                    "com.ghatana.eventprocessing.."
                )
                .as("DataCloud product must not depend on AEP product code directly (informational)");

            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("Products must not use platform namespace for product code")
        void productsMustNotUsePlatformNamespace() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                    "com.ghatana.platform.event..",
                    "com.ghatana.platform.health..",
                    "com.ghatana.platform.database..",
                    "com.ghatana.platform.config..",
                    "com.ghatana.platform.service.."
                )
                .should().resideOutsideOfPackages(
                    "com.ghatana.platform.."
                )
                .as("Product-specific code should not live under com.ghatana.platform namespace (informational)");

            rule.allowEmptyShould(true).check(productsClasses);
        }
    }

    @Nested
    @DisplayName("Concurrency Rules")
    class ConcurrencyRules {

        @Test
        @DisplayName("No CompletableFuture - use ActiveJ Promise")
        void noCompletableFuture() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.ghatana..")
                .and().resideOutsideOfPackages(
                    "..infrastructure..",
                    "..adapters..",
                    "..bridge..",
                    "..async..",
                    "..promise..",
                    "..launcher..",
                    "..runtime..",
                    "..testing.."
                )
                .should().dependOnClassesThat()
                .areAssignableTo(CompletableFuture.class)
                .because("ActiveJ Promise must be used instead of CompletableFuture. " +
                    "Infrastructure/adapter layers may use CompletableFuture for external library integration. " +
                    "See copilot-instructions.md for async standards.");
            
            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("Async methods should return Promise")
        void asyncMethodsReturnPromise() {
            ArchRule rule = methods()
                .that().haveNameMatching(".*Async")
                .and().areDeclaredInClassesThat().resideOutsideOfPackages(
                    "..testing..",
                    "..test.."
                )
                .should().haveRawReturnType("io.activej.promise.Promise")
                .as("Methods ending with 'Async' should return ActiveJ Promise");
            
            rule.allowEmptyShould(true).check(allClasses);
        }

        // ── HIGH-004: Promise pattern enforcement ────────────────────────────
        // See: SHARED_MODULES_AUDIT_REPORT.md FINDING-005 / HIGH-004
        // Blocking I/O must use Promise.ofBlocking(executor, ...).
        // Promise.of() / Promise.ofException() are only for already-computed values.

        @Test
        @DisplayName("Service/Store classes must not call Thread.sleep (HIGH-004)")
        void serviceAndStoreClassesMustNotCallThreadSleep() {
            ArchRule rule = noClasses()
                .that().haveNameMatching(".*(Service|Store|Repository|Gateway)")
                .and().areNotInterfaces()
                .and().resideOutsideOfPackages(
                    "..testing..",
                    "..test..",
                    "..bridge.."
                )
                .should().callMethodWhere(
                    com.tngtech.archunit.base.DescribedPredicate.describe(
                        "Thread.sleep() which blocks the ActiveJ event loop (use Promise.ofBlocking instead)",
                        call -> call.getTarget().getOwner().getName().equals("java.lang.Thread")
                            && call.getTarget().getName().equals("sleep")
                    )
                )
                .because("Calling Thread.sleep() on the event-loop thread stalls all concurrent requests. " +
                    "Wrap any blocking delay in Promise.ofBlocking(executor, ...). " +
                    "(HIGH-004 async pattern enforcement)");

            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("Non-test classes must not use CompletableFuture.get() which blocks (HIGH-004)")
        void mustNotCallCompletableFutureGet() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.ghatana..")
                .and().resideOutsideOfPackages(
                    "..testing..",
                    "..test..",
                    "..bridge..",
                    "..adapters..",
                    "..launcher.."
                )
                .should().callMethodWhere(
                    com.tngtech.archunit.base.DescribedPredicate.describe(
                        "CompletableFuture.get() which blocks the event loop",
                        call -> call.getTarget().getOwner().getName().equals("java.util.concurrent.CompletableFuture")
                            && call.getTarget().getName().equals("get")
                    )
                )
                .because("CompletableFuture.get() blocks the calling thread. " +
                    "Use Promise.ofBlocking() for async work. (HIGH-004)");

            rule.allowEmptyShould(true).check(allClasses);
        }
    }

    @Nested
    @DisplayName("Logging Rules")
    class LoggingRules {

        @Test
        @DisplayName("No Log4j2 annotation - use @Slf4j")
        void noLog4j2Annotation() {
            ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.ghatana..")
                .should().beAnnotatedWith("lombok.extern.log4j.Log4j2")
                .because("Use @Slf4j instead of @Log4j2. See copilot-instructions.md.");
            
            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("Services should have logging")
        void servicesShouldHaveLogging() {
            ArchRule rule = classes()
                .that().haveNameMatching(".*Service")
                .and().areNotInterfaces()
                .should().beAnnotatedWith("lombok.extern.slf4j.Slf4j")
                .orShould().haveOnlyFinalFields()
                .as("Service classes should use @Slf4j for logging");
            
            rule.allowEmptyShould(true).check(allClasses);
        }
    }

    @Nested
    @DisplayName("Layered Architecture Rules")
    class LayeredArchitectureRules {

        @Test
        @DisplayName("Domain layer is independent")
        void domainLayerIsIndependent() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "..infrastructure..",
                    "..adapters..",
                    "..controller..",
                    "..repository.."
                )
                .as("Domain layer should not depend on infrastructure concerns");
            
            rule.allowEmptyShould(true).check(allClasses);
        }

        @Test
        @DisplayName("Hexagonal architecture layers")
        void hexagonalLayers() {
            ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Domain").definedBy("..domain..")
                .layer("Application").definedBy("..application..")
                .layer("Adapters").definedBy("..adapters..")
                .layer("Infrastructure").definedBy("..infrastructure..")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapters", "Infrastructure")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Infrastructure")
                .as("Hexagonal architecture layers should be respected");
            
            rule.allowEmptyShould(true).check(allClasses);
        }
    }

    @Nested
    @DisplayName("Plugin Architecture Rules")
    class PluginArchitectureRules {

        @Test
        @DisplayName("Plugins implement Plugin interface")
        void pluginsImplementPluginInterface() {
            ArchRule rule = classes()
                .that().haveNameMatching(".*Plugin")
                .and().areNotInterfaces()
                .should().implement("com.ghatana.datacloud.spi.Plugin")
                .orShould().implement("com.ghatana.datacloud.event.spi.Plugin")
                .as("Plugin classes should implement the Plugin interface");
            
            rule.allowEmptyShould(true).check(productsClasses);
        }

        @Test
        @DisplayName("Plugin providers follow naming convention")
        void pluginProviderNaming() {
            ArchRule rule = classes()
                .that().implement("com.ghatana.datacloud.spi.PluginProvider")
                .should().haveNameMatching(".*PluginProvider")
                .as("PluginProvider implementations should end with 'PluginProvider'");
            
            rule.allowEmptyShould(true).check(allClasses);
        }
    }

    @Nested
    @DisplayName("Test Architecture Rules")
    class TestArchitectureRules {

        @Test
        @DisplayName("Async tests extend EventloopTestBase")
        void asyncTestsExtendEventloopTestBase() {
            JavaClasses testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.ghatana");
            
            ArchRule rule = classes()
                .that().haveNameMatching(".*AsyncTest")
                .or().haveNameMatching(".*PromiseTest")
                .should().beAssignableTo("com.ghatana.test.EventloopTestBase")
                .as("Async tests must extend EventloopTestBase");
            
            rule.allowEmptyShould(true).check(testClasses);
        }
    }
}
