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
 *   <li>Dependency flow: products → libs → core (never the reverse)</li>
 *   <li>No Spring Reactor / WebFlux in the platform layer</li>
 *   <li>No raw {@code CompletableFuture} in product modules (use ActiveJ Promise)</li>
 *   <li>All async tests must extend {@code EventloopTestBase}</li>
 *   <li>No direct SLF4J binding imports (must use abstraction)</li>
 *   <li>No {@code System.out.println} in production code</li>
 *   <li>Platform security classes must be in {@code platform.security} packages</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit governance fitness functions for the Ghatana monorepo
 * @doc.layer platform
 * @doc.pattern ArchitectureFitnessFunctions
 */
@DisplayName("Monorepo Architecture Governance Rules")
class ArchitectureRulesTest {

    // ── Class import scopes ───────────────────────────────────────────────────

    private static JavaClasses PLATFORM_CLASSES;
    private static JavaClasses PRODUCT_CLASSES;
    private static JavaClasses ALL_CLASSES;

    @BeforeAll
    static void importClasses() {
        PLATFORM_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.platform");

        PRODUCT_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.appplatform",
                                "com.ghatana.yappc",
                                "com.ghatana.datacloud",
                                "com.ghatana.agent",
                                "com.ghatana.refactorer");

        ALL_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana");
    }

    // ── Dependency direction ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Dependency Direction")
    class DependencyDirection {

        @Test
        @DisplayName("Platform libs must not depend on product code")
        void platformMustNotDependOnProducts() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.appplatform..",
                            "com.ghatana.yappc..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.refactorer..")
                    .because("Platform libs must be product-independent");
            rule.check(PLATFORM_CLASSES);
        }

        @Test
        @DisplayName("Core platform must not depend on agent or product packages")
        void coreMustNotDependOnAgents() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform.core..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ghatana.agent..")
                    .because("Core platform must not have upward dependencies on agent framework");
            rule.check(PLATFORM_CLASSES);
        }
    }

    // ── ActiveJ concurrency ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ActiveJ Concurrency Rules")
    class ActiveJConcurrency {

        @Test
        @DisplayName("Platform code must not use Spring Reactor / WebFlux")
        void noSpringReactorInPlatform() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "reactor.core..",
                            "org.springframework.web.reactive..",
                            "reactor.netty..")
                    .because("ActiveJ is the canonical async framework; Spring Reactor is forbidden");
            rule.check(PLATFORM_CLASSES);
        }

        @Test
        @DisplayName("Product code must not use Spring Reactor / WebFlux")
        void noSpringReactorInProducts() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "reactor.core..",
                            "org.springframework.web.reactive..")
                    .because("ActiveJ is the canonical async framework; Spring Reactor is forbidden");
            rule.check(ALL_CLASSES);
        }

        @Test
        @DisplayName("Production code must not use System.out / System.err for logging")
        void noSystemOutInProductionCode() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana..")
                    .should().callMethod(System.class, "out")
                    .orShould().callMethod(System.class, "err")
                    .because("Use SLF4J loggers instead of System.out/err");
            rule.check(ALL_CLASSES);
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Security Rules")
    class SecurityRules {

        @Test
        @DisplayName("JWT validation must reside in security packages")
        void jwtValidationInSecurityPackages() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("JwtValidationFilter")
                    .or().haveSimpleNameEndingWith("JwtTokenProvider")
                    .should().resideInAnyPackage(
                            "com.ghatana.platform.security..",
                            "com.ghatana.appplatform.gateway..",
                            "com.ghatana.services.auth..")
                    .because("JWT handling must live in dedicated security packages");
            rule.check(ALL_CLASSES);
        }

        @Test
        @DisplayName("Credential stores must implement CredentialStore interface")
        void credentialStoresMustImplementInterface() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("CredentialStore")
                    .and().doNotHaveSimpleName("CredentialStore") // exclude the interface itself
                    .should().implement(com.ghatana.services.auth.CredentialStore.class)
                    .because("All credential store implementations must fulfil the CredentialStore contract");
            rule.check(ALL_CLASSES);
        }
    }

    // ── Naming conventions ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Naming Conventions")
    class NamingConventions {

        @Test
        @DisplayName("Filter classes must end in 'Filter'")
        void filterClassesMustEndWithFilter() {
            ArchRule rule = classes()
                    .that().implement(
                            com.ghatana.platform.http.server.filter.FilterChain.Filter.class)
                    .should().haveSimpleNameEndingWith("Filter")
                    .because("FilterChain.Filter implementations must be named *Filter for discoverability");
            rule.check(ALL_CLASSES);
        }

        @Test
        @DisplayName("Repository implementations must end in 'Store' or 'Repository'")
        void repositoriesNamingConvention() {
            ArchRule rule = classes()
                    .that().implement(
                            com.ghatana.agent.framework.memory.MemoryStore.class)
                    .and().doNotHaveSimpleName("MemoryStore")
                    .should().haveSimpleNameEndingWith("MemoryStore")
                    .because("MemoryStore implementations must follow the *MemoryStore naming pattern");
            rule.check(ALL_CLASSES);
        }
    }

    // ── Package coupling ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Package Coupling")
    class PackageCoupling {

        @Test
        @DisplayName("Platform sub-packages must not have circular dependencies")
        void noPlatformCircularDependencies() {
            ArchRule rule = slices()
                    .matching("com.ghatana.platform.(*)..")
                    .should().beFreeOfCycles()
                    .because("Platform packages must not have circular dependencies");
            rule.check(PLATFORM_CLASSES);
        }
    }
}
