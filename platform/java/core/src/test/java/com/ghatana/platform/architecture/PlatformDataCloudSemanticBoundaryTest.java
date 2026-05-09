/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * ArchUnit fitness functions enforcing that platform shared modules contain no
 * Data Cloud (or any other product) specific semantics.
 *
 * <h2>Context</h2>
 * <p>Platform modules must remain genuinely reusable across all Ghatana products.
 * Any method, constant, or annotation that embeds product-specific nomenclature
 * (e.g., "datacloud", "yappc", "aep") inside a {@code com.ghatana.platform.*}
 * class is a boundary violation under
 * <a href="../../../../../docs/audits/end-to-end-data-cloud-todo-list.md">DC-P1-297</a>
 * and the monorepo architecture contract.
 *
 * <h2>Historical Violations Remediated</h2>
 * <ul>
 *   <li>{@code platform/java/observability/BusinessMetrics.java}: contained
 *       {@code recordEventPublished}, {@code recordEventConsumed}, and
 *       {@code recordEntityPersisted} methods that emitted
 *       {@code business.datacloud.*} counters.  These methods have been removed
 *       and their functionality migrated to
 *       {@code com.ghatana.datacloud.launcher.http.DataCloudBusinessMetrics}
 *       (commit: DC-P1-297 remediation).
 *   </li>
 *   <li>{@code platform/java/cache/CacheInvalidationEventPublisher.java}:
 *       comment reference to {@code DataCloudQueryCacheService} — comment-only
 *       violation with no class dependency, documented and verified harmless.
 *   </li>
 *   <li>{@code platform/java/agent-core/InMemoryAgentRegistry.java}:
 *       JavaDoc reference to {@code DataCloudAgentRegistry} — doc-only violation,
 *       no compile-time dependency on product classes.
 *   </li>
 *   <li>{@code platform/java/core/src/main/java/com/ghatana/platform/core/feature/Feature.java}:
 *       contained {@code DATA_CLOUD_KNOWLEDGE_GRAPH}, {@code AEP_ADVANCED_PATTERNS},
 *       {@code AEP_MACHINE_LEARNING}, and {@code YAPPC_SCAFFOLDING} enum constants.
 *       These product-specific feature flags have been removed (DC-BND-003 remediation).
 *       Product-specific features must be defined in the product layer.
 *   </li>
 * </ul>
 *
 * <h2>Rules enforced</h2>
 * <ol>
 *   <li>Platform packages must not import from any product package.</li>
 *   <li>Platform observability classes must not depend on product metric constants.</li>
 *   <li>Platform agent-core classes must not depend on product agent registry classes.</li>
 *   <li>Platform cache classes must not depend on product cache service classes.</li>
 *   <li>Platform feature flags must not contain product-specific constants.</li>
 * </ol>
 *
 * <h2>TypeScript Platform Modules</h2>
 * <p>TypeScript platform modules are audited separately via ESLint rules
 * in {@code @ghatana/eslint-plugin} (rule: {@code no-platform-to-product-imports}).
 * Doc-only references in JSDoc examples are acceptable and documented.</p>
 *
 * @see ArchitectureGuardrailsTest broader platform guardrails
 * @doc.type class
 * @doc.purpose Platform/product boundary enforcement — Data Cloud semantic audit (DC-P1-297)
 * @doc.layer platform
 * @doc.pattern ArchitecturalTest
 */
@DisplayName("Platform Shared Modules – Data Cloud Semantic Boundary (DC-P1-297)")
class PlatformDataCloudSemanticBoundaryTest {

    /** All platform production classes, scoped to platform packages only. */
    private static JavaClasses PLATFORM_CLASSES;

    /** Platform observability module classes. */
    private static JavaClasses OBSERVABILITY_CLASSES;

    /** Platform agent-core module classes. */
    private static JavaClasses AGENT_CORE_CLASSES;

    /** Platform cache module classes. */
    private static JavaClasses CACHE_CLASSES;

    @BeforeAll
    static void importClasses() {
        PLATFORM_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.platform");

        OBSERVABILITY_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.platform.observability");

        AGENT_CORE_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.agent");

        CACHE_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.platform.cache");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Platform → Product import isolation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Platform must not import from any product namespace")
    class PlatformProductImportIsolation {

        @Test
        @DisplayName("platform.observability must not import com.ghatana.datacloud")
        void observabilityMustNotImportDataCloud() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform.observability..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ghatana.datacloud..")
                    .because("platform/java/observability must not depend on Data Cloud product classes. "
                            + "Product-specific metrics belong in products/data-cloud/delivery/launcher "
                            + "(DataCloudBusinessMetrics). See DC-P1-297.");
            rule.check(OBSERVABILITY_CLASSES);
        }

        @Test
        @DisplayName("platform.agent.registry must not import com.ghatana.datacloud")
        void agentCoreMustNotImportDataCloud() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.agent..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ghatana.datacloud..")
                    .because("platform/java/agent-core must not depend on Data Cloud product classes. "
                            + "Product-specific agent registry extensions belong in the product layer. "
                            + "See DC-P1-297.");
            rule.check(AGENT_CORE_CLASSES);
        }

        @Test
        @DisplayName("platform.cache must not import com.ghatana.datacloud")
        void cacheMustNotImportDataCloud() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform.cache..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ghatana.datacloud..")
                    .because("platform/java/cache must not depend on Data Cloud product classes. "
                            + "Product-specific cache invalidation services belong in the product layer. "
                            + "See DC-P1-297.");
            rule.check(CACHE_CLASSES);
        }

        @Test
        @DisplayName("platform must not import com.ghatana.aep")
        void platformMustNotImportAep() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ghatana.aep..")
                    .because("platform modules must not import from AEP product namespace. "
                            + "Platform code must remain product-agnostic. See DC-P1-297.");
            rule.check(PLATFORM_CLASSES);
        }

        @Test
        @DisplayName("platform must not import com.ghatana.yappc")
        void platformMustNotImportYappc() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.platform..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ghatana.yappc..")
                    .because("platform modules must not import from YAPPC product namespace. "
                            + "Platform code must remain product-agnostic. See DC-P1-297.");
            rule.check(PLATFORM_CLASSES);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. platform.observability must not import product data-cloud SPI contracts
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Observability module boundary")
    class ObservabilityBoundary {

        @Test
        @DisplayName("BusinessMetrics must not depend on product SPI contracts")
        void businessMetricsMustNotImportProductSpi() {
            ArchRule rule = noClasses()
                    .that().haveSimpleName("BusinessMetrics")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.datacloud..",
                            "com.ghatana.aep..",
                            "com.ghatana.yappc..")
                    .because("BusinessMetrics is a platform-level utility and must not embed any "
                            + "product-specific class dependencies. "
                            + "Product-specific counters must live in product-scoped metrics classes "
                            + "(e.g., DataCloudBusinessMetrics). See DC-P1-297.");
            rule.check(OBSERVABILITY_CLASSES);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Agent-core boundary
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Agent-core module boundary")
    class AgentCoreBoundary {

        @Test
        @DisplayName("InMemoryAgentRegistry must not import product agent registry classes")
        void inMemoryRegistryMustNotImportProductRegistries() {
            ArchRule rule = noClasses()
                    .that().haveSimpleName("InMemoryAgentRegistry")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.datacloud..",
                            "com.ghatana.aep..")
                    .because("InMemoryAgentRegistry is a platform primitive and must not depend on any "
                            + "product-specific agent registry implementations. "
                            + "Products extend the platform registry via the SPI. See DC-P1-297.");
            rule.check(AGENT_CORE_CLASSES);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Feature enum boundary (DC-BND-003)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Feature enum boundary")
    class FeatureEnumBoundary {

        @Test
        @DisplayName("Feature enum must not contain product-specific constants")
        void featureEnumMustNotContainProductSpecificConstants() throws ClassNotFoundException {
            Class<?> featureEnumClass = Class.forName("com.ghatana.platform.core.feature.Feature");
            Object[] constants = featureEnumClass.getEnumConstants();
            if (constants == null) {
                return;
            }

            for (Object constant : constants) {
                String name = ((Enum<?>) constant).name();
                assertFalse(
                    name.matches(".*(AEP|DATA_CLOUD|YAPPC|FLASHIT|TUTORPUTOR|VIRTUAL_ORG|SOFTWARE_ORG).*"),
                    "Feature enum in platform module must not contain product-specific constants. "
                        + "Product-specific features must be defined in the product layer. "
                        + "See DC-BND-003 remediation. Found: " + name
                );
            }
        }
    }
}
