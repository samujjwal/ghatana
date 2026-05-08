/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.arch;

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
 * ArchUnit fitness functions that enforce Data Cloud inter-plane boundary rules.
 *
 * <p>The Data Cloud is decomposed into five semantic planes, each with a clear
 * responsibility boundary:
 * <ul>
 *   <li><b>Data plane</b>: entity records, storage specs, type system
 *       ({@code com.ghatana.datacloud.entity..}, {@code com.ghatana.datacloud.record..})</li>
 *   <li><b>Event plane</b>: event streaming, append, replay
 *       ({@code com.ghatana.datacloud.event..}, {@code com.ghatana.datacloud.platform.event..})</li>
 *   <li><b>Governance plane</b>: retention, policy, quota, consent
 *       ({@code com.ghatana.datacloud.governance..}, {@code com.ghatana.datacloud.config..})</li>
 *   <li><b>Intelligence plane</b>: analytics, feature ingestion
 *       ({@code com.ghatana.services.featurestore..})</li>
 *   <li><b>Action plane</b>: AEP event processor — separate product boundary
 *       ({@code com.ghatana.aep..})</li>
 * </ul>
 *
 * <h2>Rules enforced</h2>
 * <ul>
 *   <li>Data/Event/Governance/Intelligence planes must not import AEP server internals.</li>
 *   <li>Data/Event/Governance planes must not import Launcher internals.</li>
 *   <li>Governance plane must not import Data plane persistence internals.</li>
 *   <li>No plane may use Spring Reactor or WebFlux (ActiveJ is the async model).</li>
 * </ul>
 *
 * <h2>Allowed cross-plane integration</h2>
 * <ul>
 *   <li>{@code com.ghatana.datacloud.spi..} — shared SPI (canonical cross-plane API)</li>
 *   <li>{@code com.ghatana.datacloud} (root package only) — top-level domain types</li>
 *   <li>{@code com.ghatana.platform..} — platform modules</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose ArchUnit inter-plane boundary fitness functions for Data Cloud
 * @doc.layer product
 * @doc.pattern ArchitectureFitnessFunctions
 */
@DisplayName("Data Cloud Inter-Plane Boundary Tests")
class DataCloudPlaneBoundaryTest {

    /** All Data Cloud production classes across planes (tests excluded). */
    private static JavaClasses DATA_CLOUD_CLASSES;

    /** Data plane classes only. */
    private static JavaClasses DATA_PLANE_CLASSES;

    /** Event plane classes only. */
    private static JavaClasses EVENT_PLANE_CLASSES;

    /** Governance plane classes only. */
    private static JavaClasses GOVERNANCE_PLANE_CLASSES;

    @BeforeAll
    static void importClasses() {
        DATA_CLOUD_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(
                        "com.ghatana.datacloud",
                        "com.ghatana.services.featurestore");

        DATA_PLANE_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(
                        "com.ghatana.datacloud.entity",
                        "com.ghatana.datacloud.record");

        EVENT_PLANE_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(
                        "com.ghatana.datacloud.event",
                        "com.ghatana.datacloud.platform.event");

        GOVERNANCE_PLANE_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.datacloud.governance");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. No plane imports AEP server internals
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No Data Cloud plane imports AEP server internals")
    class NoActionPlaneInternals {

        @Test
        @DisplayName("Data plane must not import AEP server internals")
        void dataPlaneHasNoAepServerDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.aep.server..",
                            "com.ghatana.aep.bootstrap..",
                            "com.ghatana.aep.di..")
                    .because(
                            "Data Cloud planes must integrate with AEP exclusively through the "
                            + "public Action Plane API contract or the shared SPI. "
                            + "Importing AEP server internals creates a hard coupling that "
                            + "prevents independent product boundaries. "
                            + "Use com.ghatana.aep.client.* or the Action Plane contract API instead.");
            rule.check(DATA_CLOUD_CLASSES);
        }

        @Test
        @DisplayName("Intelligence plane must not import AEP server internals")
        void intelligencePlaneHasNoAepServerDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.services.featurestore..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.aep.server..",
                            "com.ghatana.aep.bootstrap..",
                            "com.ghatana.aep.di..")
                    .allowEmptyShould(true)
                    .because(
                            "The intelligence plane (feature store, analytics) must not couple directly "
                            + "to AEP server internals. Use platform-level integration contracts.");
            rule.check(DATA_CLOUD_CLASSES);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Domain planes must not import Launcher internals
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Domain planes must not import Launcher internals")
    class NoLauncherDependency {

        @Test
        @DisplayName("Data plane must not import Data Cloud Launcher internals")
        void dataPlaneHasNoLauncherDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud.entity..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.datacloud.launcher..",
                            "com.ghatana.datacloud.bootstrap..")
                    .because(
                            "The entity/data plane is a pure domain layer. "
                            + "It must not depend on delivery infrastructure (launcher, bootstrap). "
                            + "Dependency flows inward: launcher → planes, never planes → launcher.");
            rule.check(DATA_PLANE_CLASSES);
        }

        @Test
        @DisplayName("Event plane must not import Data Cloud Launcher internals")
        void eventPlaneHasNoLauncherDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud.event..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.datacloud.launcher..",
                            "com.ghatana.datacloud.bootstrap..")
                    .because(
                            "The event plane is a pure domain layer and must not depend on launcher internals. "
                            + "Event routing and handler wiring belongs in the launcher, "
                            + "not in the event domain itself.");
            rule.check(EVENT_PLANE_CLASSES);
        }

        @Test
        @DisplayName("Governance plane must not import Data Cloud Launcher internals")
        void governancePlaneHasNoLauncherDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud.governance..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.datacloud.launcher..",
                            "com.ghatana.datacloud.bootstrap..")
                    .because(
                            "Governance policy is a domain concern and must not depend on the delivery launcher. "
                            + "Policy decisions should be expressible without knowing the deployment topology.");
            rule.check(GOVERNANCE_PLANE_CLASSES);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Async model discipline across all planes
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Async discipline — ActiveJ only across all Data Cloud planes")
    class AsyncDiscipline {

        @Test
        @DisplayName("No Data Cloud class uses Spring Reactor or WebFlux")
        void noSpringReactorAcrossDataCloud() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "reactor.core..",
                            "org.springframework.web.reactive..")
                    .because(
                            "All Data Cloud components use ActiveJ Promises for async operations. "
                            + "Spring Reactor and WebFlux are incompatible with the ActiveJ event loop.");
            rule.check(DATA_CLOUD_CLASSES);
        }

        @Test
        @DisplayName("No Data Cloud class uses CompletableFuture in production code")
        void noCompletableFutureAcrossDataCloud() {
            // Exclusions for third-party SPI adapters that mandate CompletableFuture:
            //   - com.ghatana.datacloud.plugins.trino: Trino ConnectorSplitSource.getNextBatch()
            //     returns CompletableFuture<ConnectorSplitBatch> per the Trino SPI contract.
            //   - com.ghatana.datacloud.infrastructure.storage: ClickHouse Java client's
            //     execute() method returns CompletableFuture per the ClickHouse client library SPI.
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud..")
                    .and().haveNameNotMatching("com\\.ghatana\\.datacloud\\.plugins\\.trino\\..*")
                    .and().haveNameNotMatching("com\\.ghatana\\.datacloud\\.infrastructure\\.storage\\..*")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.concurrent.CompletableFuture")
                    .because(
                            "Use ActiveJ Promise for all async operations across Data Cloud planes. "
                            + "CompletableFuture is not compatible with the single-threaded ActiveJ event loop "
                            + "and introduces concurrency bugs when mixed with event-loop scheduling.");
            rule.check(DATA_CLOUD_CLASSES);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Governance plane must not reach into Data plane persistence
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Governance plane must not import Data plane persistence internals")
    class GovernancePlaneBoundary {

        @Test
        @DisplayName("Governance plane must not import Data plane record implementation")
        void governancePlaneHasNoRecordImplDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud.governance..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.ghatana.datacloud.record.impl..")
                    .because(
                            "The governance plane must interact with data records through the shared SPI "
                            + "(com.ghatana.datacloud.spi.*), not through implementation details "
                            + "in the data plane's record.impl subpackage. "
                            + "This preserves the governance plane's independence from storage implementation choices.");
            rule.check(GOVERNANCE_PLANE_CLASSES);
        }
    }
}
