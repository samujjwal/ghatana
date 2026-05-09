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

    /** Context plane classes only. */
    private static JavaClasses CONTEXT_PLANE_CLASSES;

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

        CONTEXT_PLANE_CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ghatana.datacloud.context");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. No plane imports AEP server internals
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No Data Cloud plane imports AEP server internals (DC-BND-001)")
    class NoActionPlaneInternals {

        @Test
        @DisplayName("Data plane must not import AEP server internals")
        void dataPlaneHasNoAepServerDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(
                            "com.ghatana.datacloud.entity..",
                            "com.ghatana.datacloud.record..")
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
            rule.check(DATA_PLANE_CLASSES);
        }

        @Test
        @DisplayName("Event plane must not import AEP server internals")
        void eventPlaneHasNoAepServerDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(
                            "com.ghatana.datacloud.event..",
                            "com.ghatana.datacloud.platform.event..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.aep.server..",
                            "com.ghatana.aep.bootstrap..",
                            "com.ghatana.aep.di..")
                    .allowEmptyShould(true)
                    .because(
                            "The event plane must not couple directly to AEP server internals. "
                            + "Event streaming integration must go through the shared SPI or public Action Plane API.");
            rule.check(EVENT_PLANE_CLASSES);
        }

        @Test
        @DisplayName("Context plane must not import AEP server internals")
        void contextPlaneHasNoAepServerDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud.context..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.aep.server..",
                            "com.ghatana.aep.bootstrap..",
                            "com.ghatana.aep.di..")
                    .allowEmptyShould(true)
                    .because(
                            "The context plane must not couple directly to AEP server internals. "
                            + "Context layer integration must go through the shared SPI or public Action Plane API.");
            rule.check(CONTEXT_PLANE_CLASSES);
        }

        @Test
        @DisplayName("Governance plane must not import AEP server internals")
        void governancePlaneHasNoAepServerDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud.governance..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.aep.server..",
                            "com.ghatana.aep.bootstrap..",
                            "com.ghatana.aep.di..")
                    .allowEmptyShould(true)
                    .because(
                            "The governance plane must not couple directly to AEP server internals. "
                            + "Governance integration must go through the shared SPI or public Action Plane API.");
            rule.check(GOVERNANCE_PLANE_CLASSES);
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

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Domain planes must not import H2 / storage implementation classes
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Domain planes must not import storage implementation internals (DC-BND-001)")
    class NoStorageImplementationDependency {

        /** Classes in domain planes (entity, event, governance) that must not reach into storage impl. */
        private static JavaClasses domainPlanesClasses() {
            return new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(
                            "com.ghatana.datacloud.entity",
                            "com.ghatana.datacloud.record",
                            "com.ghatana.datacloud.event",
                            "com.ghatana.datacloud.platform.event",
                            "com.ghatana.datacloud.governance",
                            "com.ghatana.datacloud.config");
        }

        @Test
        @DisplayName("Domain planes must not import H2 storage implementation classes")
        void domainPlanesMustNotImportH2StorageImpl() {
            ArchRule rule = noClasses()
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.ghatana.datacloud.storage..")
                    .because(
                            "H2 storage implementation classes (H2SovereignEntityStore, H2SovereignEventLogStore, etc.) "
                            + "are delivery infrastructure. Domain planes must interact with stores only through "
                            + "the shared SPI (com.ghatana.datacloud.spi.*). "
                            + "Only the launcher and runtime-composition modules may wire storage implementations.");
            rule.check(domainPlanesClasses());
        }

        @Test
        @DisplayName("Data plane must not import Event plane implementation")
        void dataPlaneHasNoEventPlaneImplDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(
                            "com.ghatana.datacloud.entity..",
                            "com.ghatana.datacloud.record..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.datacloud.event.impl..",
                            "com.ghatana.datacloud.event.store..")
                    .because(
                            "The data/entity plane must not depend on event plane implementation details. "
                            + "Event streaming is a separate responsibility. "
                            + "Cross-plane integration must go through the shared SPI contract.");
            rule.check(DATA_PLANE_CLASSES);
        }

        @Test
        @DisplayName("Event plane must not import entity storage implementation")
        void eventPlaneHasNoEntityStorageImplDependency() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(
                            "com.ghatana.datacloud.event..",
                            "com.ghatana.datacloud.platform.event..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.ghatana.datacloud.storage..")
                    .because(
                            "The event plane must not reach into entity storage implementation details. "
                            + "Cross-plane data access must go through the shared SPI contract.");
            rule.check(EVENT_PLANE_CLASSES);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Shared SPI is the canonical cross-plane contract (DC-BND-001)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Shared SPI is the cross-plane integration contract (DC-BND-001)")
    class SpiContractEnforcement {

        @Test
        @DisplayName("Runtime-composition must implement SPI — not bypass it")
        void runtimeCompositionMustOnlyImplementSpi() {
            JavaClasses compositionClasses = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.ghatana.datacloud.storage");
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ghatana.datacloud.storage..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ghatana.datacloud.launcher..",
                            "com.ghatana.datacloud.bootstrap..",
                            "com.ghatana.aep.server..",
                            "com.ghatana.aep.bootstrap..")
                    .because(
                            "Storage implementation classes in runtime-composition must not couple to the launcher "
                            + "or any other product's server internals. "
                            + "They should only depend on platform modules, the shared SPI, and standard libraries.");
            rule.check(compositionClasses);
        }
    }
}
