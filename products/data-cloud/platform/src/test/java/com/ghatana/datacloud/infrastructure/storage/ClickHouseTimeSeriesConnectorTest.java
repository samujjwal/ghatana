/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.storage;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageBackendType;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ClickHouseTimeSeriesConnector}.
 *
 * <p>Verifies CRUD and query operations against a real ClickHouse instance
 * managed by Testcontainers. Each test runs inside the ActiveJ event loop
 * via {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for ClickHouseTimeSeriesConnector
 * @doc.layer product
 * @doc.pattern Test, Integration
 */
@Testcontainers
@DisplayName("ClickHouseTimeSeriesConnector Integration Tests")
class ClickHouseTimeSeriesConnectorTest extends EventloopTestBase {

    @Container
    static final ClickHouseContainer CLICK_HOUSE = new ClickHouseContainer(
            DockerImageName.parse("clickhouse/clickhouse-server:24.3-alpine"))
            .withStartupTimeout(Duration.ofMinutes(2));

    private static final String TENANT_ID = "tenant-ch-test";
    private static final UUID   COLLECTION_ID = UUID.randomUUID();

    private ClickHouseTimeSeriesConnector connector;

    @BeforeEach
    void setUpConnector() {
        connector = new ClickHouseTimeSeriesConnector(
                CLICK_HOUSE.getHost(),
                CLICK_HOUSE.getMappedPort(8123),
                CLICK_HOUSE.getUsername(),
                CLICK_HOUSE.getPassword(),
                new SimpleMetricsCollector(new SimpleMeterRegistry()));
    }

    @AfterEach
    void tearDownConnector() {
        // Connector is stateless beyond the ClickHouseNode — no close needed
        connector = null;
    }

    // =========================================================================
    //  Metadata
    // =========================================================================

    @Test
    @DisplayName("getMetadata should report TIMESERIES backend type")
    void getMetadataShouldReportCorrectBackendType() {
        StorageConnector.ConnectorMetadata meta = connector.getMetadata();

        assertThat(meta.backendType()).isEqualTo(StorageBackendType.TIMESERIES);
        assertThat(meta.supportsTimeSeries()).isTrue();
        assertThat(meta.supportsFullText()).isFalse();
    }

    // =========================================================================
    //  Health check
    // =========================================================================

    @Test
    @DisplayName("healthCheck should complete without error when ClickHouse is up")
    void healthCheckShouldSucceed() {
        runPromise(() -> connector.healthCheck());
        // No assertion needed — runPromise would throw if the promise fails
    }

    // =========================================================================
    //  Create / Read
    // =========================================================================

    @Nested
    @DisplayName("Create and Read")
    class CreateAndRead {

        @Test
        @DisplayName("should persist an entity and read it back by ID")
        void shouldCreateAndReadEntity() {
            // GIVEN
            Entity entity = Entity.builder()
                    .tenantId(TENANT_ID)
                    .collectionName("timeseries-events")
                    .data(Map.of("metric", "cpu_usage", "value", 72.5, "host", "web-01"))
                    .build();

            // WHEN create
            Entity saved = runPromise(() -> connector.create(entity));

            // THEN save returns the entity with ID
            assertThat(saved).isNotNull();
            assertThat(saved.getId()).isNotNull();

            // WHEN read
            Optional<Entity> found = runPromise(() ->
                    connector.read(COLLECTION_ID, TENANT_ID, saved.getId()));

            // THEN
            assertThat(found).isPresent();
            assertThat(found.get().getData()).containsEntry("metric", "cpu_usage");
        }

        @Test
        @DisplayName("should return empty Optional when entity does not exist")
        void shouldReturnEmptyForMissingEntity() {
            Optional<Entity> found = runPromise(() ->
                    connector.read(COLLECTION_ID, TENANT_ID, UUID.randomUUID()));

            assertThat(found).isEmpty();
        }
    }

    // =========================================================================
    //  Update / Delete
    // =========================================================================

    @Nested
    @DisplayName("Update and Delete")
    class UpdateAndDelete {

        @Test
        @DisplayName("should overwrite entity on update")
        void shouldUpdateEntity() {
            // GIVEN
            Entity original = Entity.builder()
                    .tenantId(TENANT_ID)
                    .collectionName("update-test")
                    .data(Map.of("status", "pending"))
                    .build();
            Entity saved = runPromise(() -> connector.create(original));

            Entity updated = Entity.builder()
                    .id(saved.getId())
                    .tenantId(TENANT_ID)
                    .collectionName("update-test")
                    .data(Map.of("status", "completed"))
                    .build();

            // WHEN
            Entity result = runPromise(() -> connector.update(updated));

            // THEN
            assertThat(result).isNotNull();
            assertThat(result.getData()).containsEntry("status", "completed");
        }

        @Test
        @DisplayName("should delete an entity by ID")
        void shouldDeleteEntity() {
            // GIVEN
            Entity entity = Entity.builder()
                    .tenantId(TENANT_ID)
                    .collectionName("delete-test")
                    .data(Map.of("tag", "ephemeral"))
                    .build();
            Entity saved = runPromise(() -> connector.create(entity));

            // WHEN
            runPromise(() -> connector.delete(COLLECTION_ID, TENANT_ID, saved.getId()));

            // THEN — entity should no longer be findable
            Optional<Entity> found = runPromise(() ->
                    connector.read(COLLECTION_ID, TENANT_ID, saved.getId()));
            assertThat(found).isEmpty();
        }
    }

    // =========================================================================
    //  Count / Scan
    // =========================================================================

    @Nested
    @DisplayName("Count and Scan")
    class CountAndScan {

        @Test
        @DisplayName("count should return number of tenant entities")
        void shouldCountEntities() {
            // GIVEN: insert 3 entities for a unique tenant
            String countTenant = "tenant-count-" + System.currentTimeMillis();
            for (int i = 0; i < 3; i++) {
                final int seq = i;
                Entity e = Entity.builder()
                        .tenantId(countTenant)
                        .collectionName("metrics")
                        .data(Map.of("seq", seq))
                        .build();
                runPromise(() -> connector.create(e));
            }

            // WHEN
            Long count = runPromise(() -> connector.count(COLLECTION_ID, countTenant, null));

            // THEN
            assertThat(count).isGreaterThanOrEqualTo(3L);
        }

        @Test
        @DisplayName("scan should return paged results")
        void shouldScanWithPagination() {
            // GIVEN: insert 5 entities
            String scanTenant = "tenant-scan-" + System.currentTimeMillis();
            for (int i = 0; i < 5; i++) {
                final int seq = i;
                Entity e = Entity.builder()
                        .tenantId(scanTenant)
                        .collectionName("scan-test")
                        .data(Map.of("seq", seq))
                        .build();
                runPromise(() -> connector.create(e));
            }

            // WHEN
            List<Entity> page = runPromise(() -> connector.scan(COLLECTION_ID, scanTenant, null, 3, 0));

            // THEN
            assertThat(page).hasSize(3);
        }
    }

    // =========================================================================
    //  Query
    // =========================================================================

    @Test
    @DisplayName("query should return entities within time window")
    void shouldQueryWithinTimeWindow() {
        // GIVEN: insert an entity
        Entity entity = Entity.builder()
                .tenantId(TENANT_ID)
                .collectionName("time-window-test")
                .data(Map.of("event", "startup"))
                .build();
        runPromise(() -> connector.create(entity));

        // WHEN: query covers the current time
        QuerySpec spec = QuerySpec.builder()
                .timeWindow(
                        java.time.Instant.now().minusSeconds(60),
                        java.time.Instant.now().plusSeconds(60))
                .limit(10)
                .build();
        StorageConnector.QueryResult result = runPromise(() ->
                connector.query(COLLECTION_ID, TENANT_ID, spec));

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.entities()).isNotEmpty();
    }
}
