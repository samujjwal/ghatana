/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link ClickHouseTimeSeriesConnector}.
 *
 * <p>Verifies CRUD and query operations against a real ClickHouse instance
 * managed by Testcontainers. Each test runs inside the ActiveJ event loop
 * via {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)}. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Integration tests for ClickHouseTimeSeriesConnector
 * @doc.layer product
 * @doc.pattern Test, Integration
 */
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@DisplayName("ClickHouseTimeSeriesConnector Integration Tests")
class ClickHouseTimeSeriesConnectorTest extends EventloopTestBase {

    @SuppressWarnings("resource")
    @Container
    static final ClickHouseContainer CLICK_HOUSE = new ClickHouseContainer( // GH-90000
            DockerImageName.parse("clickhouse/clickhouse-server:24.3-alpine"))
            .withStartupTimeout(Duration.ofMinutes(2)); // GH-90000

    private static final String TENANT_ID = "tenant-ch-test";
    private static final UUID   COLLECTION_ID = UUID.randomUUID(); // GH-90000

    private ClickHouseTimeSeriesConnector connector;

    @BeforeEach
    void setUpConnector() { // GH-90000
        connector = new ClickHouseTimeSeriesConnector( // GH-90000
                CLICK_HOUSE.getHost(), // GH-90000
                CLICK_HOUSE.getMappedPort(8123), // GH-90000
                CLICK_HOUSE.getUsername(), // GH-90000
                CLICK_HOUSE.getPassword(), // GH-90000
                new SimpleMetricsCollector(new SimpleMeterRegistry())); // GH-90000
    }

    @AfterEach
    void tearDownConnector() { // GH-90000
        // Connector is stateless beyond the ClickHouseNode — no close needed
        connector = null;
    }

    // =========================================================================
    //  Metadata
    // =========================================================================

    @Test
    @DisplayName("getMetadata should report TIMESERIES backend type")
    void getMetadataShouldReportCorrectBackendType() { // GH-90000
        StorageConnector.ConnectorMetadata meta = connector.getMetadata(); // GH-90000

        assertThat(meta.backendType()).isEqualTo(StorageBackendType.TIMESERIES); // GH-90000
        assertThat(meta.supportsTimeSeries()).isTrue(); // GH-90000
        assertThat(meta.supportsFullText()).isFalse(); // GH-90000
    }

    // =========================================================================
    //  Health check
    // =========================================================================

    @Test
    @DisplayName("healthCheck should complete without error when ClickHouse is up")
    void healthCheckShouldSucceed() { // GH-90000
        runPromise(() -> connector.healthCheck()); // GH-90000
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
        void shouldCreateAndReadEntity() { // GH-90000
            // GIVEN
            Entity entity = Entity.builder() // GH-90000
                    .tenantId(TENANT_ID) // GH-90000
                    .collectionName("timeseries-events")
                    .data(Map.of("metric", "cpu_usage", "value", 72.5, "host", "web-01")) // GH-90000
                    .build(); // GH-90000

            // WHEN create
            Entity saved = runPromise(() -> connector.create(entity)); // GH-90000

            // THEN save returns the entity with ID
            assertThat(saved).isNotNull(); // GH-90000
            assertThat(saved.getId()).isNotNull(); // GH-90000

            // WHEN read
            Optional<Entity> found = runPromise(() -> // GH-90000
                    connector.read(COLLECTION_ID, TENANT_ID, saved.getId())); // GH-90000

            // THEN
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().getData()).containsEntry("metric", "cpu_usage"); // GH-90000
        }

        @Test
        @DisplayName("should return empty Optional when entity does not exist")
        void shouldReturnEmptyForMissingEntity() { // GH-90000
            Optional<Entity> found = runPromise(() -> // GH-90000
                    connector.read(COLLECTION_ID, TENANT_ID, UUID.randomUUID())); // GH-90000

            assertThat(found).isEmpty(); // GH-90000
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
        void shouldUpdateEntity() { // GH-90000
            // GIVEN
            Entity original = Entity.builder() // GH-90000
                    .tenantId(TENANT_ID) // GH-90000
                    .collectionName("update-test")
                    .data(Map.of("status", "pending")) // GH-90000
                    .build(); // GH-90000
            Entity saved = runPromise(() -> connector.create(original)); // GH-90000

            Entity updated = Entity.builder() // GH-90000
                    .id(saved.getId()) // GH-90000
                    .tenantId(TENANT_ID) // GH-90000
                    .collectionName("update-test")
                    .data(Map.of("status", "completed")) // GH-90000
                    .build(); // GH-90000

            // WHEN
            Entity result = runPromise(() -> connector.update(updated)); // GH-90000

            // THEN
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getData()).containsEntry("status", "completed"); // GH-90000
        }

        @Test
        @DisplayName("should delete an entity by ID")
        void shouldDeleteEntity() { // GH-90000
            // GIVEN
            Entity entity = Entity.builder() // GH-90000
                    .tenantId(TENANT_ID) // GH-90000
                    .collectionName("delete-test")
                    .data(Map.of("tag", "ephemeral")) // GH-90000
                    .build(); // GH-90000
            Entity saved = runPromise(() -> connector.create(entity)); // GH-90000

            // WHEN
            runPromise(() -> connector.delete(COLLECTION_ID, TENANT_ID, saved.getId())); // GH-90000

            // THEN — entity should no longer be findable
            Optional<Entity> found = runPromise(() -> // GH-90000
                    connector.read(COLLECTION_ID, TENANT_ID, saved.getId())); // GH-90000
            assertThat(found).isEmpty(); // GH-90000
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
        void shouldCountEntities() { // GH-90000
            // GIVEN: insert 3 entities for a unique tenant
            String countTenant = "tenant-count-" + System.currentTimeMillis(); // GH-90000
            for (int i = 0; i < 3; i++) { // GH-90000
                final int seq = i;
                Entity e = Entity.builder() // GH-90000
                        .tenantId(countTenant) // GH-90000
                        .collectionName("metrics")
                        .data(Map.of("seq", seq)) // GH-90000
                        .build(); // GH-90000
                runPromise(() -> connector.create(e)); // GH-90000
            }

            // WHEN
            Long count = runPromise(() -> connector.count(COLLECTION_ID, countTenant, null)); // GH-90000

            // THEN
            assertThat(count).isGreaterThanOrEqualTo(3L); // GH-90000
        }

        @Test
        @DisplayName("scan should return paged results")
        void shouldScanWithPagination() { // GH-90000
            // GIVEN: insert 5 entities
            String scanTenant = "tenant-scan-" + System.currentTimeMillis(); // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                final int seq = i;
                Entity e = Entity.builder() // GH-90000
                        .tenantId(scanTenant) // GH-90000
                        .collectionName("scan-test")
                        .data(Map.of("seq", seq)) // GH-90000
                        .build(); // GH-90000
                runPromise(() -> connector.create(e)); // GH-90000
            }

            // WHEN
            List<Entity> page = runPromise(() -> connector.scan(COLLECTION_ID, scanTenant, null, 3, 0)); // GH-90000

            // THEN
            assertThat(page).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("count should reject tenant identifiers with SQL injection payloads")
        void shouldRejectInjectedTenantIdentifier() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    connector.count(COLLECTION_ID, "tenant' OR 1=1 --", null))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("tenantId contains illegal characters");
        }
    }

    // =========================================================================
    //  Query
    // =========================================================================

    @Test
    @DisplayName("query should return entities within time window")
    void shouldQueryWithinTimeWindow() { // GH-90000
        // GIVEN: insert an entity
        Entity entity = Entity.builder() // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName("time-window-test")
                .data(Map.of("event", "startup")) // GH-90000
                .build(); // GH-90000
        runPromise(() -> connector.create(entity)); // GH-90000

        // WHEN: query covers the current time
        QuerySpec spec = QuerySpec.builder() // GH-90000
                .timeWindow( // GH-90000
                        java.time.Instant.now().minusSeconds(60), // GH-90000
                        java.time.Instant.now().plusSeconds(60)) // GH-90000
                .limit(10) // GH-90000
                .build(); // GH-90000
        StorageConnector.QueryResult result = runPromise(() -> // GH-90000
                connector.query(COLLECTION_ID, TENANT_ID, spec)); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.entities()).isNotEmpty(); // GH-90000
    }
}
