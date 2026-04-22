/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.storage;

import com.ghatana.aep.server.storage.DataLifecycleManager.SweepReport;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataLifecycleManager}.
 *
 * <p>The {@link StorageTierManager} is mocked to return synchronous promises,
 * so no Eventloop is required. The lifecycle manager's sweep logic, collection
 * registration, and archive operation are tested in isolation.
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataLifecycleManager — sweep logic and collection lifecycle
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataLifecycleManager [GH-90000]")
class DataLifecycleManagerTest {

    private static final String TENANT = "tenant-lifecycle";
    private static final String COLL_1 = "aep_patterns";
    private static final String COLL_2 = "aep_pipelines";

    @Mock
    private StorageTierManager tierManager;

    private DataLifecycleManager mgr;

    @BeforeEach
    void setUp() { // GH-90000
        mgr = new DataLifecycleManager( // GH-90000
                tierManager,
                Duration.ofHours(1), // GH-90000
                Duration.ofDays(7), // GH-90000
                Duration.ofDays(90), // GH-90000
                new SimpleMeterRegistry() // GH-90000
        );
    }

    // =========================================================================
    // Collection registration
    // =========================================================================

    @Nested
    @DisplayName("Collection registration [GH-90000]")
    class RegistrationTests {

        @Test
        @DisplayName("registerCollection: adds new collection to inventory [GH-90000]")
        void registerCollection_addsEntry() { // GH-90000
            assertThat(mgr.registeredCollectionCount()).isZero(); // GH-90000
            mgr.registerCollection(TENANT, COLL_1); // GH-90000
            assertThat(mgr.registeredCollectionCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("registerCollection: duplicate registration is idempotent [GH-90000]")
        void registerCollection_duplicateIsIdempotent() { // GH-90000
            mgr.registerCollection(TENANT, COLL_1); // GH-90000
            mgr.registerCollection(TENANT, COLL_1); // GH-90000
            assertThat(mgr.registeredCollectionCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("registerCollection: registers multiple distinct collections [GH-90000]")
        void registerCollection_multipleCollections() { // GH-90000
            mgr.registerCollection(TENANT, COLL_1); // GH-90000
            mgr.registerCollection(TENANT, COLL_2); // GH-90000
            mgr.registerCollection("other-tenant", COLL_1); // GH-90000
            assertThat(mgr.registeredCollectionCount()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("deregisterCollection: removes collection from inventory [GH-90000]")
        void deregisterCollection_removesEntry() { // GH-90000
            mgr.registerCollection(TENANT, COLL_1); // GH-90000
            mgr.deregisterCollection(TENANT, COLL_1); // GH-90000
            assertThat(mgr.registeredCollectionCount()).isZero(); // GH-90000
        }
    }

    // =========================================================================
    // runLifecycleSweep
    // =========================================================================

    @Nested
    @DisplayName("runLifecycleSweep() [GH-90000]")
    class SweepTests {

        @BeforeEach
        void stubTierManager() { // GH-90000
            lenient().when(tierManager.demoteCoolToCold(anyString(), anyString(), any(Instant.class))) // GH-90000
                    .thenReturn(Promise.of(3)); // GH-90000
            lenient().when(tierManager.demoteWarmToCool(anyString(), anyString(), any(Instant.class))) // GH-90000
                    .thenReturn(Promise.of(2)); // GH-90000
            lenient().when(tierManager.demoteIdleEntities(anyString(), anyString(), any(Instant.class))) // GH-90000
                    .thenReturn(Promise.of(5)); // GH-90000
        }

        @Test
        @DisplayName("runLifecycleSweep: returns skipped=true when no collections registered [GH-90000]")
        void sweep_noCollections_returnsSkipped() { // GH-90000
            // mgr has no registered collections
            SweepReport report = mgr.runLifecycleSweep().getResult(); // GH-90000

            assertThat(report.totalDemotions()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("runLifecycleSweep: processes all registered collections exactly once [GH-90000]")
        void sweep_oneCollection_callsTierManagerEachMethod() { // GH-90000
            mgr.registerCollection(TENANT, COLL_1); // GH-90000

            SweepReport report = mgr.runLifecycleSweep().getResult(); // GH-90000

            assertThat(report.skipped()).isFalse(); // GH-90000
            // COOL→COLD + WARM→COOL + HOT→WARM per collection = 3+2+5 = 10
            assertThat(report.totalDemotions()).isEqualTo(10); // GH-90000
            assertThat(report.coolToCold()).isEqualTo(3); // GH-90000
            assertThat(report.warmToCool()).isEqualTo(2); // GH-90000
            assertThat(report.hotToWarm()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("runLifecycleSweep: aggregates demotions across multiple collections [GH-90000]")
        void sweep_multipleCollections_aggregatesResults() { // GH-90000
            mgr.registerCollection(TENANT, COLL_1); // GH-90000
            mgr.registerCollection(TENANT, COLL_2); // GH-90000

            SweepReport report = mgr.runLifecycleSweep().getResult(); // GH-90000

            // 2 collections × (3+2+5) = 20 total // GH-90000
            assertThat(report.totalDemotions()).isEqualTo(20); // GH-90000
        }

        @Test
        @DisplayName("runLifecycleSweep: cascade order is COOL→COLD first, HOT→WARM last [GH-90000]")
        void sweep_invocationsInCorrectCascadeOrder() { // GH-90000
            mgr.registerCollection(TENANT, COLL_1); // GH-90000

            mgr.runLifecycleSweep().getResult(); // GH-90000

            var inOrder = inOrder(tierManager); // GH-90000
            inOrder.verify(tierManager).demoteCoolToCold(eq(TENANT), eq(COLL_1), any()); // GH-90000
            inOrder.verify(tierManager).demoteWarmToCool(eq(TENANT), eq(COLL_1), any()); // GH-90000
            inOrder.verify(tierManager).demoteIdleEntities(eq(TENANT), eq(COLL_1), any()); // GH-90000
        }

        @Test
        @DisplayName("runLifecycleSweep: collection failure is absorbed; other collections continue [GH-90000]")
        void sweep_oneCollectionFails_othersComplete() { // GH-90000
            when(tierManager.demoteCoolToCold(eq(TENANT), eq(COLL_1), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("COLL_1 failed [GH-90000]")));
            when(tierManager.demoteCoolToCold(eq(TENANT), eq(COLL_2), any())) // GH-90000
                    .thenReturn(Promise.of(3)); // GH-90000

            mgr.registerCollection(TENANT, COLL_1); // GH-90000
            mgr.registerCollection(TENANT, COLL_2); // GH-90000

            // Should complete without throwing
            SweepReport report = mgr.runLifecycleSweep().getResult(); // GH-90000

            assertThat(report.skipped()).isFalse(); // GH-90000
            // COLL_1 contributed 0 due to error; COLL_2 contributed 3+2+5 = 10
            assertThat(report.totalDemotions()).isEqualTo(10); // GH-90000
        }
    }

    // =========================================================================
    // archiveCollection
    // =========================================================================

    @Nested
    @DisplayName("archiveCollection() [GH-90000]")
    class ArchiveTests {

        @Test
        @DisplayName("archiveCollection: runs HOT→WARM, WARM→COOL, COOL→COLD in sequence [GH-90000]")
        void archiveCollection_runsFullCascade() { // GH-90000
            when(tierManager.demoteIdleEntities(anyString(), anyString(), any(Instant.class))) // GH-90000
                    .thenReturn(Promise.of(4)); // GH-90000
            when(tierManager.demoteWarmToCool(anyString(), anyString(), any(Instant.class))) // GH-90000
                    .thenReturn(Promise.of(2)); // GH-90000
            when(tierManager.demoteCoolToCold(anyString(), anyString(), any(Instant.class))) // GH-90000
                    .thenReturn(Promise.of(1)); // GH-90000

            int total = mgr.archiveCollection(TENANT, COLL_1).getResult(); // GH-90000

            assertThat(total).isEqualTo(7); // 4+2+1 // GH-90000
        }

        @Test
        @DisplayName("archiveCollection: calls all three tier manager methods [GH-90000]")
        void archiveCollection_invokesAllThreeMethods() { // GH-90000
            when(tierManager.demoteIdleEntities(anyString(), anyString(), any())).thenReturn(Promise.of(0)); // GH-90000
            when(tierManager.demoteWarmToCool(anyString(), anyString(), any())).thenReturn(Promise.of(0)); // GH-90000
            when(tierManager.demoteCoolToCold(anyString(), anyString(), any())).thenReturn(Promise.of(0)); // GH-90000

            mgr.archiveCollection(TENANT, COLL_1).getResult(); // GH-90000

            verify(tierManager).demoteIdleEntities(eq(TENANT), eq(COLL_1), any()); // GH-90000
            verify(tierManager).demoteWarmToCool(eq(TENANT), eq(COLL_1), any()); // GH-90000
            verify(tierManager).demoteCoolToCold(eq(TENANT), eq(COLL_1), any()); // GH-90000
        }
    }

    // =========================================================================
    // SweepReport
    // =========================================================================

    @Test
    @DisplayName("SweepReport.totalDemotions: sums all three demotion counts [GH-90000]")
    void sweepReport_totalDemotions_sumsAll() { // GH-90000
        SweepReport r = new SweepReport(5, 3, 2, Duration.ZERO, false); // GH-90000
        assertThat(r.totalDemotions()).isEqualTo(10); // GH-90000
    }

    @Test
    @DisplayName("SweepReport.skipped(): returns true when constructed as skipped [GH-90000]")
    void sweepReport_skipped_returnsTrue() { // GH-90000
        SweepReport r = new SweepReport(0, 0, 0, Duration.ZERO, true); // GH-90000
        assertThat(r.skipped()).isTrue(); // GH-90000
    }
}
