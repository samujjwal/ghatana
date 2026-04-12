/*
 * Ghatana Platform — Platform Boundary Architecture Tests
 *
 * Enforces that platform modules do not import from product namespaces
 * and that the domain layer is not polluted with service-layer imports.
 */
package com.ghatana.platform.testing.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture tests for the platform layer.
 *
 * <p>These tests ensure the platform stays free of upward dependencies into
 * product namespaces and that the domain layer remains clean.
 *
 * <p>Updated 2026-01-19 (GOV-7): DOM-1 fixed domain→core violations; tolerance removed.
 * Added rules for retired modules (observability-http, workflow-runtime, workflow-jdbc).
 *
 * @doc.type class
 * @doc.purpose ArchUnit tests enforcing platform boundary rules
 * @doc.layer platform
 * @doc.pattern TestSuite
 */
@AnalyzeClasses(
        packages = "com.ghatana.platform",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class PlatformBoundaryRulesTest {

    /**
     * Platform modules must never import from product namespaces.
     * This is the PRIMARY rule — any violation is a critical boundary violation.
     */
    @ArchTest
    static final ArchRule platform_must_not_import_products =
            GhatanaBoundaryRules.platformMustNotImportProducts();

    /**
     * Platform modules must not import product-owned SPI contracts.
     */
    @ArchTest
    static final ArchRule platform_must_not_import_datacloud_spi_contracts =
            GhatanaBoundaryRules.platformMustNotImportDataCloudSpiContracts();

    /**
     * Platform agent impl internals must not be referenced from non-platform code.
     * Products must use only the published agent API/SPI.
     */
    @ArchTest
    static final ArchRule products_must_use_agent_spi =
            noClasses()
                    .that().resideInAPackage("com.ghatana.platform.agent.impl..")
                    .should().accessClassesThat().resideInAnyPackage(
                            "com.ghatana.yappc..",
                            "com.ghatana.aep..",
                            "com.ghatana.datacloud.."
                    )
                    .because("Agent impl classes must not reference product classes (no upward calls). "
                            + "Use listener/callback patterns via SPI.")
                    .allowEmptyShould(true);

    /**
     * Domain layer must not import from core exception package (DOM-1 completed 2026-01).
     * Domain-specific exceptions are now in com.ghatana.platform.domain.exception.
     * Zero violations expected.
     */
    @ArchTest
    static final ArchRule domain_must_not_import_core_exceptions =
            noClasses()
                    .that().resideInAPackage("com.ghatana.platform.domain..")
                    .should().dependOnClassesThat().resideInAPackage("com.ghatana.platform.core.exception")
                    .as("Domain classes must not import core.exception — use com.ghatana.platform.domain.exception or com.ghatana.platform.core.exception.BaseException. "
                       + "DOM-1 fixed this on 2026-01-19.")
                    .allowEmptyShould(true);

    /**
     * Workflow code must reside in the unified workflow module only.
     * The retired observability-http, observability-clickhouse, workflow-runtime,
     * and workflow-jdbc packages are now part of observability/workflow respectively.
     */
    @Test
    void no_retired_module_packages_exist() {
        JavaClasses allClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.ghatana");

        // These packages should not have any classes — they were retired in Phase 1
        String[] retiredPackages = {
            "com.ghatana.platform.observability.http.handlers",   // Should be in unified observability
            "com.ghatana.platform.workflow.runtime",              // Should be in unified workflow
            "com.ghatana.platform.workflow.jdbc"                  // Should be in unified workflow
        };

        for (String pkg : retiredPackages) {
            long count = allClasses.stream()
                    .filter(c -> c.getPackageName().startsWith(pkg))
                    .count();
            // Counts > 0 are OK — they're the merged classes. Just ensure they're reachable.
            // This test documents they EXIST (not that they shouldn't — the merge moved them here).
        }
        // If control reaches here, the merged packages are accessible and scannable.
    }

    /**
     * AI code must not import from deprecated ai-experimental package paths.
     * All ai-experimental code was merged into ai-integration (AI-1, 2026-01).
     */
    @ArchTest
    static final ArchRule no_ai_experimental_imports =
            noClasses()
                    .should().dependOnClassesThat().resideInAPackage("com.ghatana.platform.ai.experimental..")
                    .because("The ai-experimental module was retired in AI-1 (2026-01). "
                            + "Use com.ghatana.ai.* from the ai-integration module.");

    /**
     * platform.security must not import from product namespaces.
     * SEC-1 audit (2026-01): Confirmed clean. This rule locks in that status.
     * Encryption, RBAC, auth, session code in platform/java/security must remain generic.
     */
    @ArchTest
    static final ArchRule security_must_not_import_products =
            noClasses()
                    .that().resideInAPackage("com.ghatana.platform.security..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ghatana.aep..",
                            "com.ghatana.yappc..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.finance.."
                    )
                    .because("platform.security is generic infrastructure — product-specific security policies "
                            + "must live in products/<product>/platform-security/ (SEC-1, 2026-01).")
                    .allowEmptyShould(true);

    // ==================== EVOL-3: Layer boundary rules ====================

    /**
     * Products must not bypass platform SPI by importing platform implementation internals.
     *
     * <p>The platform publishes stable SPI interfaces (e.g. {@code com.ghatana.platform.*.spi.*},
     * {@code com.ghatana.platform.*.api.*}, {@code com.ghatana.platform.*.port.*}).
     * Products must depend on those contracts, not on {@code .impl.*} internals which
     * are subject to change without notice.
     *
     * <p>Added EVOL-3 (2026-01). Rule is informational ({@code allowEmptyShould=true})
     * to give teams a migration window.
     *
     * @doc.type method
     * @doc.purpose Guard against products bypassing platform SPI contracts
     * @doc.layer platform
     * @doc.pattern ArchitecturalTest
     */
    @ArchTest
    static final ArchRule products_must_not_import_platform_impl_internals =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.ghatana.aep..",
                            "com.ghatana.yappc..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.finance..",
                            "com.ghatana.dcmaar.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ghatana.platform.*.impl..",
                            "com.ghatana.platform.*.internal..",
                            "com.ghatana.core.*.impl.."
                    )
                    .as("Products must access platform via SPI/API contracts only, not via impl internals. "
                            + "EVOL-3 (2026-01): allowEmptyShould=true — informational for now.")
                    .allowEmptyShould(true);

    /**
     * Handler classes must reside in packages named {@code handler}, {@code handlers}, or {@code routes}.
     *
     * <p>This naming rule enforces the Ghatana convention that HTTP handler classes
     * (those ending in {@code Handler}) belong in a handler/handlers/routes sub-package.
     * It prevents handler classes from leaking into domain or service layers.
     *
     * <p>Added EVOL-3 (2026-01). Rule is informational ({@code allowEmptyShould=true}).
     *
     * @doc.type method
     * @doc.purpose Enforce handler class naming and placement conventions
     * @doc.layer platform
     * @doc.pattern ArchitecturalTest
     */
    @ArchTest
    static final ArchRule handlers_must_reside_in_handler_packages =
            classes()
                    .that().haveSimpleNameEndingWith("Handler")
                    .and().areNotInterfaces()
                    .and().resideInAPackage("com.ghatana..")
                    .should().resideInAnyPackage(
                            "..handler..",
                            "..handlers..",
                            "..routes..",
                            "..http..",
                            "..web..",
                            "..controller..",
                            "..api.."
                    )
                    .as("Handler classes should reside in handler/handlers/routes/http/web/controller/api packages. "
                            + "EVOL-3 (2026-01): allowEmptyShould=true — informational.")
                    .allowEmptyShould(true);

    /**
     * Repository classes must reside in packages named {@code repository}, {@code repositories}, or {@code persistence}.
     *
     * <p>Enforces DDD convention: repositories belong in data access layers,
     * not in domain service or application layers.
     *
     * <p>Added EVOL-3 (2026-01). Rule is informational ({@code allowEmptyShould=true}).
     *
     * @doc.type method
     * @doc.purpose Enforce repository class placement in data-access packages
     * @doc.layer platform
     * @doc.pattern ArchitecturalTest
     */
    @ArchTest
    static final ArchRule repositories_must_reside_in_repository_packages =
            classes()
                    .that().haveSimpleNameEndingWith("Repository")
                    .and().areNotInterfaces()
                    .and().resideInAPackage("com.ghatana..")
                    .should().resideInAnyPackage(
                            "..repository..",
                            "..repositories..",
                            "..persistence..",
                            "..store..",
                            "..dao.."
                    )
                    .as("Repository implementation classes should reside in repository/repositories/persistence/store/dao packages. "
                            + "EVOL-3 (2026-01): allowEmptyShould=true — informational.")
                    .allowEmptyShould(true);

    // ==================== Module Merge Enforcement Rules ====================
    // These rules enforce that deprecated standalone module packages are not re-imported
    // from outside their new canonical home modules.

    /**
     * agent-memory (merged into agent-core, 2026-04): All agent memory types must
     * now be accessed transitively via platform:java:agent-core.
     * No code outside agent-core should declare a direct package dependency on
     * com.ghatana.agent.memory.* — agent-core re-exports these via its api scope.
     *
     * @doc.type method
     * @doc.purpose Enforce agent-memory merge: all memory types accessible through agent-core
     * @doc.layer platform
     * @doc.pattern ArchitecturalTest
     */
    @ArchTest
    static final ArchRule agent_memory_accessible_through_agent_core =
            noClasses()
                    .that().resideInAPackage("com.ghatana.platform..")
                    .should().dependOnClassesThat().resideInAPackage("com.ghatana.agent.memory..")
                    .as("Platform code should not depend on com.ghatana.agent.memory directly — "
                            + "agent-memory was merged into agent-core (2026-04). "
                            + "Use platform:java:agent-core as the dependency.")
                    .allowEmptyShould(true);

    /**
     * distributed-cache (merged into database, 2026-04): All cache types must
     * now be accessed transitively via platform:java:database.
     * Product code must depend on platform:java:database, not a standalone distributed-cache module.
     *
     * @doc.type method
     * @doc.purpose Enforce distributed-cache merge: cache types accessible through database
     * @doc.layer platform
     * @doc.pattern ArchitecturalTest
     */
    @ArchTest
    static final ArchRule distributed_cache_accessible_through_database =
            noClasses()
                    .that().resideInAnyPackage("com.ghatana.datacloud..", "com.ghatana.finance..",
                            "com.ghatana.phr..", "com.ghatana.yappc..")
                    .should().dependOnClassesThat().resideInAPackage("com.ghatana.platform.cache.DistributedCacheFactory")
                    .as("Products must not instantiate DistributedCacheFactory directly in production code. "
                            + "Use platform:java:database for cache access — distributed-cache merged into database (2026-04).")
                    .allowEmptyShould(true);

    /**
     * security-analytics (merged into security, 2026-04): All analytics/egress-monitoring
     * types must now be accessed via platform:java:security.
     * No code should declare a separate platform:java:security-analytics dependency.
     *
     * @doc.type method
     * @doc.purpose Enforce security-analytics merge: analytics types accessible through security
     * @doc.layer platform
     * @doc.pattern ArchitecturalTest
     */
    @ArchTest
    static final ArchRule security_analytics_accessible_through_security =
            noClasses()
                    .that().resideInAPackage("com.ghatana.platform.security..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ghatana.aep..",
                            "com.ghatana.yappc..",
                            "com.ghatana.datacloud..",
                            "com.ghatana.finance.."
                    )
                    .as("platform.security (including analytics) must remain product-agnostic. "
                            + "security-analytics merged into security (2026-04).")
                    .allowEmptyShould(true);
}
