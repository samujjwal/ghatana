/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.launcher.storage;

import com.ghatana.aep.launcher.storage.DataLifecycleManager.SweepReport;
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
@ExtendWith(MockitoExtension.class)
@DisplayName("DataLifecycleManager")
class DataLifecycleManagerTest {

    private static final String TENANT = "tenant-lifecycle";
    private static final String COLL_1 = "aep_patterns";
    private static final String COLL_2 = "aep_pipelines";

    @Mock
    private StorageTierManager tierManager;

    private DataLifecycleManager mgr;

    @BeforeEach
    void setUp() {
        mgr = new DataLifecycleManager(
                tierManager,
                Duration.ofHours(1),
                Duration.ofDays(7),
                Duration.ofDays(90),
                new SimpleMeterRegistry()
        );
    }

    // =========================================================================
    // Collection registration
    // =========================================================================

    @Nested
    @DisplayName("Collection registration")
    class RegistrationTests {

        @Test
        @DisplayName("registerCollection: adds new collection to inventory")
        void registerCollection_addsEntry() {
            assertThat(mgr.registeredCollectionCount()).isZero();
            mgr.registerCollection(TENANT, COLL_1);
            assertThat(mgr.registeredCollectionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("registerCollection: duplicate registration is idempotent")
        void registerCollection_duplicateIsIdempotent() {
            mgr.registerCollection(TENANT, COLL_1);
            mgr.registerCollection(TENANT, COLL_1);
            assertThat(mgr.registeredCollectionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("registerCollection: registers multiple distinct collections")
        void registerCollection_multipleCollections() {
            mgr.registerCollection(TENANT, COLL_1);
            mgr.registerCollection(TENANT, COLL_2);
            mgr.registerCollection("other-tenant", COLL_1);
            assertThat(mgr.registeredCollectionCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("deregisterCollection: removes collection from inventory")
        void deregisterCollection_removesEntry() {
            mgr.registerCollection(TENANT, COLL_1);
            mgr.deregisterCollection(TENANT, COLL_1);
            assertThat(mgr.registeredCollectionCount()).isZero();
        }
    }

    // =========================================================================
    // runLifecycleSweep
    // =========================================================================

    @Nested
    @DisplayName("runLifecycleSweep()")
    class SweepTests {

        @BeforeEach
        void stubTierManager() {
            lenient().when(tierManager.demoteCoolToCold(anyString(), anyString(), any(Instant.class)))
                    .thenReturn(Promise.of(3));
            lenient().when(tierManager.demoteWarmToCool(anyString(), anyString(), any(Instant.class)))
                    .thenReturn(Promise.of(2));
            lenient().when(tierManager.demoteIdleEntities(anyString(), anyString(), any(Instant.class)))
                    .thenReturn(Promise.of(5));
        }

        @Test
        @DisplayName("runLifecycleSweep: returns skipped=true when no collections registered")
        void sweep_noCollections_returnsSkipped() {
            // mgr has no registered collections
            SweepReport report = mgr.runLifecycleSweep().getResult();

            assertThat(report.totalDemotions()).isZero();
        }

        @Test
        @DisplayName("runLifecycleSweep: processes all registered collections exactly once")
        void sweep_oneCollection_callsTierManagerEachMethod() {
            mgr.registerCollection(TENANT, COLL_1);

            SweepReport report = mgr.runLifecycleSweep().getResult();

            assertThat(report.skipped()).isFalse();
            // COOL→COLD + WARM→COOL + HOT→WARM per collection = 3+2+5 = 10
            assertThat(report.totalDemotions()).isEqualTo(10);
            assertThat(report.coolToCold()).isEqualTo(3);
            assertThat(report.warmToCool()).isEqualTo(2);
            assertThat(report.hotToWarm()).isEqualTo(5);
        }

        @Test
        @DisplayName("runLifecycleSweep: aggregates demotions across multiple collections")
        void sweep_multipleCollections_aggregatesResults() {
            mgr.registerCollection(TENANT, COLL_1);
            mgr.registerCollection(TENANT, COLL_2);

            SweepReport report = mgr.runLifecycleSweep().getResult();

            // 2 collections × (3+2+5) = 20 total
            assertThat(report.totalDemotions()).isEqualTo(20);
        }

        @Test
        @DisplayName("runLifecycleSweep: cascade order is COOL→COLD first, HOT→WARM last")
        void sweep_invocationsInCorrectCascadeOrder() {
            mgr.registerCollection(TENANT, COLL_1);

            mgr.runLifecycleSweep().getResult();

            var inOrder = inOrder(tierManager);
            inOrder.verify(tierManager).demoteCoolToCold(eq(TENANT), eq(COLL_1), any());
            inOrder.verify(tierManager).demoteWarmToCool(eq(TENANT), eq(COLL_1), any());
            inOrder.verify(tierManager).demoteIdleEntities(eq(TENANT), eq(COLL_1), any());
        }

        @Test
        @DisplayName("runLifecycleSweep: collection failure is absorbed; other collections continue")
        void sweep_oneCollectionFails_othersComplete() {
            when(tierManager.demoteCoolToCold(eq(TENANT), eq(COLL_1), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("COLL_1 failed")));
            when(tierManager.demoteCoolToCold(eq(TENANT), eq(COLL_2), any()))
                    .thenReturn(Promise.of(3));

            mgr.registerCollection(TENANT, COLL_1);
            mgr.registerCollection(TENANT, COLL_2);

            // Should complete without throwing
            SweepReport report = mgr.runLifecycleSweep().getResult();

            assertThat(report.skipped()).isFalse();
            // COLL_1 contributed 0 due to error; COLL_2 contributed 3+2+5 = 10
            assertThat(report.totalDemotions()).isEqualTo(10);
        }
    }

    // =========================================================================
    // archiveCollection
    // =========================================================================

    @Nested
    @DisplayName("archiveCollection()")
    class ArchiveTests {

        @Test
        @DisplayName("archiveCollection: runs HOT→WARM, WARM→COOL, COOL→COLD in sequence")
        void archiveCollection_runsFullCascade() {
            when(tierManager.demoteIdleEntities(anyString(), anyString(), any(Instant.class)))
                    .thenReturn(Promise.of(4));
            when(tierManager.demoteWarmToCool(anyString(), anyString(), any(Instant.class)))
                    .thenReturn(Promise.of(2));
            when(tierManager.demoteCoolToCold(anyString(), anyString(), any(Instant.class)))
                    .thenReturn(Promise.of(1));

            int total = mgr.archiveCollection(TENANT, COLL_1).getResult();

            assertThat(total).isEqualTo(7); // 4+2+1
        }

        @Test
        @DisplayName("archiveCollection: calls all three tier manager methods")
        void archiveCollection_invokesAllThreeMethods() {
            when(tierManager.demoteIdleEntities(anyString(), anyString(), any())).thenReturn(Promise.of(0));
            when(tierManager.demoteWarmToCool(anyString(), anyString(), any())).thenReturn(Promise.of(0));
            when(tierManager.demoteCoolToCold(anyString(), anyString(), any())).thenReturn(Promise.of(0));

            mgr.archiveCollection(TENANT, COLL_1).getResult();

            verify(tierManager).demoteIdleEntities(eq(TENANT), eq(COLL_1), any());
            verify(tierManager).demoteWarmToCool(eq(TENANT), eq(COLL_1), any());
            verify(tierManager).demoteCoolToCold(eq(TENANT), eq(COLL_1), any());
        }
    }

    // =========================================================================
    // SweepReport
    // =========================================================================

    @Test
    @DisplayName("SweepReport.totalDemotions: sums all three demotion counts")
    void sweepReport_totalDemotions_sumsAll() {
        SweepReport r = new SweepReport(5, 3, 2, Duration.ZERO, false);
        assertThat(r.totalDemotions()).isEqualTo(10);
    }

    @Test
    @DisplayName("SweepReport.skipped(): returns true when constructed as skipped")
    void sweepReport_skipped_returnsTrue() {
        SweepReport r = new SweepReport(0, 0, 0, Duration.ZERO, true);
        assertThat(r.skipped()).isTrue();
    }
}
