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

/**
 * ArchUnit fitness functions that enforce AEP ↔ Data-Cloud cross-product boundary rules.
 *
 * <p>AEP must consume Data Cloud exclusively through the public SPI and client API.
 * Direct coupling to Data Cloud launcher internals (governance, infrastructure, DI)
 * or to any other product's internal packages is forbidden.
 *
 * <h2>Covered rules</h2>
 * <ul>
 *   <li>AEP server must not import Data Cloud launcher internals.</li>
 *   <li>AEP server must not import Data Cloud governance internals.</li>
 *   <li>AEP server must not import Data Cloud infrastructure internals.</li>
 *   <li>AEP server HTTP handlers must not import other product UI/handlers.</li>
 * </ul>
 *
 * <h2>Allowed integration points</h2>
 * <ul>
 *   <li>{@code com.ghatana.datacloud.spi..} — Data Cloud SPI (canonical public API)</li>
 *   <li>{@code com.ghatana.datacloud.DataCloud*} — top-level client façade</li>
 *   <li>{@code com.ghatana.datacloud.client..} — DataCloudClientFactory and client module</li>
 *   <li>{@code com.ghatana.datacloud.agent.registry..} — agent-registry module (explicit dep)</li>
 *   <li>{@code com.ghatana.datacloud.deployment..} — deployment config (platform-launcher)</li>
 *   <li>{@code com.ghatana.datacloud} (root package only) — top-level types</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit cross-product boundary fitness functions for AEP server
 * @doc.layer product
 * @doc.pattern ArchitectureFitnessFunctions
 */
@DisplayName("AEP Cross-Product Boundary Tests")
class AepCrossProductBoundaryTest {

    /** All AEP server production classes (test sources excluded). */
    private static JavaClasses AEP_SERVER_CLASSES;

    @BeforeAll
    static void importClasses() {
        AEP_SERVER_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("com.ghatana.aep");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. AEP must not import Data Cloud internal packages
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP must not import Data Cloud internal packages")
    class DataCloudInternalImports {

        @Test
        @DisplayName("AEP must not import Data Cloud launcher internals")
        void noDataCloudLauncherInternals() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.aep..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.datacloud.launcher..",
                            "com.ghatana.datacloud.di..",
                            "com.ghatana.datacloud.bootstrap..")
                    .because(
                            "AEP must access Data Cloud only through the public SPI and client API. "
                            + "Importing Data Cloud launcher internals creates a hard coupling that "
                            + "prevents independent deployment and violates the product boundary. "
                            + "Use com.ghatana.datacloud.spi.* or com.ghatana.datacloud.client.* instead.");
            rule.check(AEP_SERVER_CLASSES);
        }

        @Test
        @DisplayName("AEP must not import Data Cloud governance internals")
        void noDataCloudGovernanceInternals() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.aep..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ghatana.datacloud.governance..")
                    .because(
                            "Data Cloud governance internals (quota, approval, retention enforcement) "
                            + "are implementation details of the Data Cloud product. "
                            + "AEP must route governance concerns through the platform governance API "
                            + "or through the Data Cloud SPI, not by calling internal governance classes directly.");
            rule.check(AEP_SERVER_CLASSES);
        }

        @Test
        @DisplayName("AEP must not import Data Cloud infrastructure internals")
        void noDataCloudInfrastructureInternals() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.aep..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.datacloud.infrastructure..",
                            "com.ghatana.datacloud.storage..",
                            "com.ghatana.datacloud.persistence..")
                    .because(
                            "Data Cloud infrastructure (storage adapters, persistence) are internal. "
                            + "AEP must use the SPI or the client API, not storage-layer internals directly.");
            rule.check(AEP_SERVER_CLASSES);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. AEP server async discipline
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AEP server async discipline")
    class AsyncDiscipline {

        @Test
        @DisplayName("AEP server must not use Spring Reactor or WebFlux")
        void noSpringReactorOrWebFlux() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.aep..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("reactor.core..", "org.springframework.web.reactive..")
                    .because(
                            "AEP uses ActiveJ Promises for all asynchronous operations. "
                            + "Spring Reactor and WebFlux are incompatible with the ActiveJ event loop.");
            rule.check(AEP_SERVER_CLASSES);
        }

        @Test
        @DisplayName("AEP server must not use CompletableFuture in production code")
        void noCompletableFutureInProductionCode() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.aep..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.concurrent.CompletableFuture")
                    .because(
                            "Use ActiveJ Promise for all asynchronous operations. "
                            + "CompletableFuture is not compatible with the single-threaded ActiveJ event loop.");
            rule.check(AEP_SERVER_CLASSES);
        }
    }
}
