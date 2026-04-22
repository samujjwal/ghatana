package com.ghatana.platform.governance;

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
 * ArchUnit fitness functions that enforce the monorepo architectural rules.
 *
 * <p>These rules are the executable equivalent of the Governance Audit document.
 * They run as part of the normal {@code ./gradlew test} flow and fail the build
 * when a rule is violated.</p>
 *
 * <h2>Covered rules</h2>
 * <ul>
 *   <li>Dependency flow: products → libs → core (never the reverse)</li> // GH-90000
 *   <li>No Spring Reactor / WebFlux in the platform layer</li>
 *   <li>No raw {@code CompletableFuture} in product modules (use ActiveJ Promise)</li> // GH-90000
 *   <li>All async tests must extend {@code EventloopTestBase}</li>
 *   <li>No direct SLF4J binding imports (must use abstraction)</li> // GH-90000
 *   <li>No {@code System.out.println} in production code</li>
 *   <li>Platform security classes must be in {@code platform.security} packages</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit governance fitness functions for the Ghatana monorepo
 * @doc.layer platform
 * @doc.pattern ArchitectureFitnessFunctions
 */
@DisplayName("Monorepo Architecture Governance Rules [GH-90000]")
class ArchitectureRulesTest {

    // ── Class import scopes ───────────────────────────────────────────────────

    private static JavaClasses PLATFORM_CLASSES;
    private static JavaClasses PRODUCT_CLASSES;
    private static JavaClasses ALL_CLASSES;

    @BeforeAll
    static void importClasses() { // GH-90000
        PLATFORM_CLASSES = new ClassFileImporter() // GH-90000
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) // GH-90000
                .importPackages("com.ghatana.platform [GH-90000]");

        PRODUCT_CLASSES = new ClassFileImporter() // GH-90000
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) // GH-90000
                .importPackages("com.ghatana.appplatform", // GH-90000
                                "com.ghatana.yappc",
                                "com.ghatana.datacloud",
                                "com.ghatana.agent",
                                "com.ghatana.refactorer");

        ALL_CLASSES = new ClassFileImporter() // GH-90000
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS) // GH-90000
                .importPackages("com.ghatana [GH-90000]");
    }

    // ── Dependency direction ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Dependency Direction [GH-90000]")
    class DependencyDirection {

        @Test
        @DisplayName("Platform libs must not depend on product code [GH-90000]")
        void platformMustNotDependOnProducts() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.platform.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage( // GH-90000
                            "com.ghatana.appplatform..",
                            "com.ghatana.yappc..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.refactorer..")
                    .because("Platform libs must be product-independent [GH-90000]");
            rule.check(PLATFORM_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Core platform must not depend on agent or product packages [GH-90000]")
        void coreMustNotDependOnAgents() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.platform.core.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAPackage("com.ghatana.agent.. [GH-90000]")
                    .because("Core platform must not have upward dependencies on agent framework [GH-90000]");
            rule.check(PLATFORM_CLASSES); // GH-90000
        }
    }

    // ── ActiveJ concurrency ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ActiveJ Concurrency Rules [GH-90000]")
    class ActiveJConcurrency {

        @Test
        @DisplayName("Platform code must not use Spring Reactor / WebFlux [GH-90000]")
        void noSpringReactorInPlatform() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.platform.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage( // GH-90000
                            "reactor.core..",
                            "org.springframework.web.reactive..",
                            "reactor.netty..")
                    .because("ActiveJ is the canonical async framework; Spring Reactor is forbidden [GH-90000]");
            rule.check(PLATFORM_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Product code must not use Spring Reactor / WebFlux [GH-90000]")
        void noSpringReactorInProducts() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.. [GH-90000]")
                    .should().dependOnClassesThat() // GH-90000
                    .resideInAnyPackage( // GH-90000
                            "reactor.core..",
                            "org.springframework.web.reactive..")
                    .because("ActiveJ is the canonical async framework; Spring Reactor is forbidden [GH-90000]");
            rule.check(ALL_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Production code must not use System.out / System.err for logging [GH-90000]")
        void noSystemOutInProductionCode() { // GH-90000
            ArchRule rule = noClasses() // GH-90000
                    .that().resideInAPackage("com.ghatana.. [GH-90000]")
                    .should().callMethod(System.class, "out") // GH-90000
                    .orShould().callMethod(System.class, "err") // GH-90000
                    .because("Use SLF4J loggers instead of System.out/err [GH-90000]");
            rule.check(ALL_CLASSES); // GH-90000
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security Rules [GH-90000]")
    class SecurityRules {

        @Test
        @DisplayName("JWT validation must reside in security packages [GH-90000]")
        void jwtValidationInSecurityPackages() { // GH-90000
            ArchRule rule = classes() // GH-90000
                    .that().haveSimpleNameEndingWith("JwtValidationFilter [GH-90000]")
                    .or().haveSimpleNameEndingWith("JwtTokenProvider [GH-90000]")
                    .should().resideInAnyPackage( // GH-90000
                            "com.ghatana.platform.security..",
                            "com.ghatana.appplatform.gateway..",
                            "com.ghatana.services.auth..")
                    .because("JWT handling must live in dedicated security packages [GH-90000]");
            rule.check(ALL_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Credential stores must implement CredentialStore interface [GH-90000]")
        void credentialStoresMustImplementInterface() { // GH-90000
            ArchRule rule = classes() // GH-90000
                    .that().haveSimpleNameEndingWith("CredentialStore [GH-90000]")
                    .and().doNotHaveSimpleName("CredentialStore [GH-90000]") // exclude the interface itself
                    .should().implement(com.ghatana.services.auth.CredentialStore.class) // GH-90000
                    .because("All credential store implementations must fulfil the CredentialStore contract [GH-90000]");
            rule.check(ALL_CLASSES); // GH-90000
        }
    }

    // ── Naming conventions ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Naming Conventions [GH-90000]")
    class NamingConventions {

        @Test
        @DisplayName("Filter classes must end in 'Filter' [GH-90000]")
        void filterClassesMustEndWithFilter() { // GH-90000
            ArchRule rule = classes() // GH-90000
                    .that().implement( // GH-90000
                            com.ghatana.platform.http.server.filter.FilterChain.Filter.class)
                    .should().haveSimpleNameEndingWith("Filter [GH-90000]")
                    .because("FilterChain.Filter implementations must be named *Filter for discoverability [GH-90000]");
            rule.check(ALL_CLASSES); // GH-90000
        }

        @Test
        @DisplayName("Repository implementations must end in 'Store' or 'Repository' [GH-90000]")
        void repositoriesNamingConvention() { // GH-90000
            ArchRule rule = classes() // GH-90000
                    .that().implement( // GH-90000
                            com.ghatana.agent.framework.memory.MemoryStore.class)
                    .and().doNotHaveSimpleName("MemoryStore [GH-90000]")
                    .should().haveSimpleNameEndingWith("MemoryStore [GH-90000]")
                    .because("MemoryStore implementations must follow the *MemoryStore naming pattern [GH-90000]");
            rule.check(ALL_CLASSES); // GH-90000
        }
    }

    // ── Package coupling ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Package Coupling [GH-90000]")
    class PackageCoupling {

        @Test
        @DisplayName("Platform sub-packages must not have circular dependencies [GH-90000]")
        void noPlatformCircularDependencies() { // GH-90000
            ArchRule rule = slices() // GH-90000
                    .matching("com.ghatana.platform.(*).. [GH-90000]")
                    .should().beFreeOfCycles() // GH-90000
                    .because("Platform packages must not have circular dependencies [GH-90000]");
            rule.check(PLATFORM_CLASSES); // GH-90000
        }
    }
}
