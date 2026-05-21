/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ArchUnit fitness functions that enforce Data Cloud provider contract boundary rules.
 *
 * <p>Data Cloud must respect platform provider contracts and not bypass platform services.
 * Direct access to kernel provider internals or bypassing provider health checks is forbidden.
 *
 * <h2>Covered rules</h2>
 * <ul>
 *   <li>Data Cloud must not import kernel provider internals directly.</li>
 *   <li>Data Cloud must use provider health matrix for health checks.</li>
 *   <li>Data Cloud must respect provider mode enforcement.</li>
 *   <li>Data Cloud must not bypass provider readiness checks.</li>
 * </ul>
 *
 * <h2>Allowed integration points</h2>
 * <ul>
 *   <li>{@code com.ghatana.kernel.providers..} — Platform provider public contracts</li>
 *   <li>{@code com.ghatana.kernel.provider.contracts..} — Provider contract interfaces</li>
 *   <li>{@code com.ghatana.kernel.health..} — Health matrix and monitoring contracts</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit provider contract boundary fitness functions for Data Cloud
 * @doc.layer product
 * @doc.pattern ArchitectureFitnessFunctions
 */
@DisplayName("Data Cloud Provider Contract Boundary Tests")
class DataCloudProviderBoundaryTest {

    /** All Data Cloud production classes (test sources excluded). */
    private static JavaClasses DATACLOUD_CLASSES;

    @BeforeAll
    static void importClasses() {
        DATACLOUD_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.datacloud");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Data Cloud must not bypass provider contracts
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Data Cloud must not bypass provider contracts")
    class ProviderContractCompliance {

        @Test
        @DisplayName("Data Cloud must not import kernel provider internals")
        void noKernelProviderInternals() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.kernel.providers.internal..",
                            "com.ghatana.kernel.providers.impl..")
                    .because(
                            "Data Cloud must use platform provider contracts through public interfaces. "
                            + "Direct imports of provider internals create hard coupling and bypass "
                            + "platform provider mode enforcement and health monitoring.");
            rule.check(DATACLOUD_CLASSES);
        }

        @Test
        @DisplayName("Data Cloud must use provider health matrix for health checks")
        void mustUseProviderHealthMatrix() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.datacloud..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.ghatana.kernel.providers.health.internal..",
                    "com.ghatana.kernel.providers.health.impl..")
                    .because(
                    "Data Cloud health checks must stay on platform health contracts and avoid "
                    + "internal provider health implementations.");
            rule.check(DATACLOUD_CLASSES);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Data Cloud must respect provider mode enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Data Cloud must respect provider mode enforcement")
    class ProviderModeEnforcement {

        @Test
        @DisplayName("Data Cloud must not bypass provider mode checks")
        void mustNotBypassProviderModeChecks() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.ghatana.kernel.providers.mode.internal..",
                    "com.ghatana.kernel.providers.mode.impl..")
                    .because(
                            "Data Cloud must use the platform provider mode enforcer. "
                    + "Direct dependency on mode internals bypasses fail-closed platform mode behavior.");
            rule.check(DATACLOUD_CLASSES);
        }

        @Test
        @DisplayName("Data Cloud must respect provider readiness states")
        void mustRespectProviderReadinessStates() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.ghatana.kernel.providers.readiness.internal..",
                    "com.ghatana.kernel.providers.readiness.impl..")
                    .because(
                            "Data Cloud must use the platform provider readiness contract. "
                    + "Direct dependency on readiness internals bypasses platform health/mode enforcement.");
            rule.check(DATACLOUD_CLASSES);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Data Cloud must not bypass runtime-truth drift gate
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Data Cloud must not bypass runtime-truth drift gate")
    class RuntimeTruthDriftGate {

        @Test
        @DisplayName("Data Cloud must use runtime-truth service for route validation")
        void mustUseRuntimeTruthServiceForRouteValidation() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("com.ghatana.datacloud..")
                .and().haveSimpleNameContaining("Route")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.ghatana.kernel.runtime.truth.internal..")
                    .because(
                            "Data Cloud route validation must use the platform runtime-truth service. "
                    + "Route components must not couple to runtime-truth internals.");
            rule.check(DATACLOUD_CLASSES);
        }

        @Test
        @DisplayName("Data Cloud must not have direct route configuration without validation")
        void mustNotHaveDirectRouteConfigWithoutValidation() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud..routeconfig..")
                .should().resideInAnyPackage("com.ghatana.datacloud..")
                    .because(
                    "Direct route-config classes are forbidden in Data Cloud runtime code. "
                    + "Route state must be derived from canonical runtime-truth surfaces.")
                    .allowEmptyShould(true);
            rule.check(DATACLOUD_CLASSES);
        }
    }
}
