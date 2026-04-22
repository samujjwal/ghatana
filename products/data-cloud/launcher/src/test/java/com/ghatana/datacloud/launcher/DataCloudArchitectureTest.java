/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit fitness functions that enforce Data-Cloud launcher module boundary rules.
 *
 * <p>These rules are executable, build-time guardrails that prevent regressions in
 * the architectural boundaries defined in {@code MONOREPO_ARCHITECTURE.md} and the
 * Data-Cloud governance contract.
 *
 * <h2>Covered rules</h2>
 * <ul>
 *   <li>Launcher layer must not import platform internals directly.</li>
 *   <li>HTTP handlers must not depend on each other (fan-in / fan-out forbidden).</li> // GH-90000
 *   <li>No Spring Reactor / WebFlux in launcher production code.</li>
 *   <li>No raw {@code CompletableFuture} in launcher production code.</li>
 *   <li>No {@code System.out.println} in launcher production code (use SLF4J).</li> // GH-90000
 *   <li>No cyclic dependencies between launcher packages.</li>
 *   <li>Security filter must reside in the {@code http} package (not leaked to handlers).</li> // GH-90000
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit boundary fitness functions for the Data-Cloud launcher module
 * @doc.layer product
 * @doc.pattern ArchitectureFitnessFunctions
 */
@DisplayName("Data-Cloud Architecture Boundary Tests [GH-90000]")
class DataCloudArchitectureTest {

    // ── Class import scopes ───────────────────────────────────────────────────

    /** All launcher production classes (excluding test sources). */ // GH-90000
    private static JavaClasses LAUNCHER_CLASSES;

    /** Only the launcher module's handler classes. */
    private static JavaClasses HANDLER_CLASSES;

    @BeforeAll
    static void importClasses() { // GH-90000
        LAUNCHER_CLASSES = new ClassFileImporter() // GH-90000
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) // GH-90000
            .importPackages("com.ghatana.datacloud.launcher [GH-90000]");

        HANDLER_CLASSES = new ClassFileImporter() // GH-90000
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) // GH-90000
                .importPackages("com.ghatana.datacloud.launcher.http.handlers [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Forbidden technology imports
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Forbidden technology imports [GH-90000]")
    class ForbiddenImports {

        @Test
        @DisplayName("Root launcher must delegate transport and infra wiring to bootstrap classes [GH-90000]")
        void rootLauncherMustNotComposeTransportOrInfrastructureDirectly() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().haveSimpleName("DataCloudLauncher [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage( // GH-90000
                            "com.ghatana.datacloud.di..",
                            "com.ghatana.datacloud.infrastructure..",
                            "com.ghatana.aiplatform..",
                            "com.ghatana.datacloud.launcher.http..",
                            "com.ghatana.datacloud.launcher.grpc..",
                            "javax.sql..",
                            "com.zaxxer.hikari..")
                    .because( // GH-90000
                        "The root launcher should stay thin and delegate transport/resource composition "
                        + "to dedicated bootstrap classes instead of wiring product internals directly.");
            rule.check(LAUNCHER_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Data-Cloud must not use Spring Reactor / WebFlux [GH-90000]")
        void noSpringReactorOrWebFlux() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.datacloud.launcher.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage("reactor.core..", "org.springframework.web.reactive..") // GH-90000
                    .because( // GH-90000
                        "The Data-Cloud product must use ActiveJ Promises only. "
                        + "Spring Reactor / WebFlux is incompatible with the ActiveJ event loop.");
            rule.check(LAUNCHER_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Data-Cloud production code must not use CompletableFuture [GH-90000]")
        void noCompletableFutureInProductionCode() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.datacloud.launcher.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .haveFullyQualifiedName("java.util.concurrent.CompletableFuture [GH-90000]")
                    .because( // GH-90000
                        "Use ActiveJ Promise for all asynchronous operations. "
                        + "CompletableFuture is not compatible with the single-threaded ActiveJ event loop.");
            rule.check(LAUNCHER_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Data-Cloud launcher must not depend on AEP product packages [GH-90000]")
        void noDirectAepDependencies() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.datacloud.launcher.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage("com.ghatana.aep..", "com.ghatana.orchestrator..") // GH-90000
                    .because( // GH-90000
                        "Data-Cloud is the backbone layer. Product-specific AEP concerns must stay "
                        + "outside the Data-Cloud launcher boundary.");
            rule.check(LAUNCHER_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Data-Cloud must not use System.out or System.err for logging [GH-90000]")
        void noSystemOutOrSystemErr() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.datacloud.launcher.. [GH-90000]")
                    .should().accessField(System.class, "out") // GH-90000
                    .orShould().accessField(System.class, "err") // GH-90000
                    .because("Use SLF4J Logger for all logging — System.out/err bypasses structured logging. [GH-90000]");
                rule.check(LAUNCHER_CLASSES); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Handler isolation – handlers must not import each other
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Handler isolation [GH-90000]")
    class HandlerIsolation {

        @Test
        @DisplayName("HTTP handlers must not depend on other handlers in the same layer [GH-90000]")
        void handlersMustNotDependOnEachOther() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.datacloud.launcher.http.handlers [GH-90000]")
                    .and().haveSimpleNameEndingWith("Handler [GH-90000]")
                    .and().doNotHaveSimpleName("HttpHandlerSupport [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .haveSimpleNameEndingWith("Handler [GH-90000]")
                    .because( // GH-90000
                        "Each handler must be independently composable. "
                        + "Cross-handler dependencies create tight coupling and cyclic dependencies.")
                    .allowEmptyShould(true); // GH-90000
            rule.check(HANDLER_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Handlers must reside in the handlers sub-package [GH-90000]")
        void handlersMustResideInHandlersPackage() { // GH-90000
            ArchRule rule = classes() // GH-90000
                    .that().haveSimpleNameEndingWith("Handler [GH-90000]")
                    .and().resideInAPackage("com.ghatana.datacloud.launcher.. [GH-90000]")
                    .should().resideInAPackage("com.ghatana.datacloud.launcher.http.handlers [GH-90000]")
                    .because("All HTTP handlers must be in the handlers sub-package for discoverability. [GH-90000]");
                rule.check(LAUNCHER_CLASSES); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Security filter placement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security filter placement [GH-90000]")
    class SecurityFilterPlacement {

        @Test
        @DisplayName("DataCloudSecurityFilter must reside in the http layer, not handlers [GH-90000]")
        void securityFilterMustNotResideInHandlersPackage() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().haveSimpleNameContaining("SecurityFilter [GH-90000]")
                    .and().resideInAPackage("com.ghatana.datacloud.launcher.. [GH-90000]")
                    .should().resideInAPackage("com.ghatana.datacloud.launcher.http.handlers.. [GH-90000]")
                    .because( // GH-90000
                        "Security filters are middleware, not handlers. "
                        + "They must remain in the http layer to be applied before route dispatch.");
            rule.check(LAUNCHER_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Handlers must not directly import DataCloudSecurityFilter [GH-90000]")
        void handlersMustNotImportSecurityFilter() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.datacloud.launcher.http.handlers [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .haveSimpleName("DataCloudSecurityFilter [GH-90000]")
                    .because( // GH-90000
                        "Handlers must not be aware of the security filter. "
                        + "Security wrapping is the responsibility of the server composition layer.");
            rule.check(HANDLER_CLASSES); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. No cyclic package dependencies
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No cyclic package dependencies [GH-90000]")
    class NoCyclicDependencies {

        @Test
        @DisplayName("Data-Cloud packages must be acyclic (no circular package dependencies) [GH-90000]")
        void dataCloudPackagesMustBeAcyclic() { // GH-90000
            ArchRule rule = slices() // GH-90000
                    .matching("com.ghatana.datacloud.launcher.(*).. [GH-90000]")
                    .should().beFreeOfCycles() // GH-90000
                    .because( // GH-90000
                        "Cyclic module dependencies prevent independent deployment "
                        + "and create tight coupling that violates the layered architecture.");
            rule.check(LAUNCHER_CLASSES); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Naming conventions
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Naming conventions [GH-90000]")
    class NamingConventions {

        @Test
        @DisplayName("Classes ending with 'Test' must reside in test sources [GH-90000]")
        void testClassesMustBeInTestSources() { // GH-90000
            // This test uses only production classes — verifies no *Test class leaked to main
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.datacloud.launcher.. [GH-90000]")
                    .and().haveSimpleNameEndingWith("Test [GH-90000]")
                    .should().bePublic() // GH-90000
                    .because( // GH-90000
                        "Test classes must not appear in production source sets. "
                        + "Any Test-suffixed class in main/ is a test leaked to production.")
                    .allowEmptyShould(true); // GH-90000
            rule.check(LAUNCHER_CLASSES); // GH-90000
        }
    }
}
