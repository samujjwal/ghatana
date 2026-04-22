/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.sdk;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * SDK contract validation tests — DC-003.
 *
 * <p>These tests cover the input-validation, boundary, and error-handling behaviour
 * that the generated SDK test files address with placeholder TODOs. The scenarios
 * here are the authoritative unit-level regression suite for any Data-Cloud SDK
 * consumer.
 *
 * <p>All async assertions use {@link EventloopTestBase#runPromise} in accordance
 * with the repo convention — never call {@code Promise.getResult()} directly. // GH-90000
 *
 * @doc.type class
 * @doc.purpose SDK contract validation and input-boundary tests (DC-003) // GH-90000
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("Data-Cloud SDK — Contract Validation (DC-003) [GH-90000]")
class DataCloudSdkValidationTest extends EventloopTestBase {

    private static final String TENANT = "sdk-test-tenant";
    private static final String COLLECTION = "sdk-items";

    private DataCloudClient client;

    @BeforeEach
    void setUp() { // GH-90000
        client = DataCloud.forTesting(); // GH-90000
    }

    // =========================================================================
    // Entity input validation
    // =========================================================================

    @Nested
    @DisplayName("Entity — input boundaries [GH-90000]")
    class EntityInputBoundaries {

        @Test
        @DisplayName("save with minimal required fields succeeds [GH-90000]")
        void saveWithMinimalFields() { // GH-90000
            Entity entity = runPromise(() -> client.save( // GH-90000
                    TENANT, COLLECTION,
                    Map.<String, Object>of("id", "min-1"))); // GH-90000

            assertThat(entity).isNotNull(); // GH-90000
            assertThat(entity.id()).isEqualTo("min-1 [GH-90000]");
            assertThat(entity.collection()).isEqualTo(COLLECTION); // GH-90000
        }

        @Test
        @DisplayName("save with rich nested payload is preserved faithfully [GH-90000]")
        void saveWithNestedPayloadPreservesStructure() { // GH-90000
            Map<String, Object> data = Map.of( // GH-90000
                    "id", "rich-1",
                    "tags", List.of("alpha", "beta"), // GH-90000
                    "meta", Map.of("version", 3, "flags", Map.of("active", true))); // GH-90000

            Entity entity = runPromise(() -> client.save(TENANT, COLLECTION, data)); // GH-90000

            assertThat(entity.data()).containsKey("tags [GH-90000]");
            assertThat(entity.data()).containsKey("meta [GH-90000]");
        }

        @Test
        @DisplayName("findById returns empty for unknown id [GH-90000]")
        void findByIdReturnEmptyForMissing() { // GH-90000
            Optional<Entity> result = runPromise( // GH-90000
                    () -> client.findById(TENANT, COLLECTION, "does-not-exist")); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("overwrite an entity retains the latest data [GH-90000]")
        void overwriteEntityRetainsLatestData() { // GH-90000
            runPromise(() -> client.save(TENANT, COLLECTION, // GH-90000
                    Map.<String, Object>of("id", "ow-1", "v", "first"))); // GH-90000

            runPromise(() -> client.save(TENANT, COLLECTION, // GH-90000
                    Map.<String, Object>of("id", "ow-1", "v", "second"))); // GH-90000

            Optional<Entity> found = runPromise(() -> client.findById(TENANT, COLLECTION, "ow-1")); // GH-90000
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().data()).containsEntry("v", "second"); // GH-90000
        }

        @Test
        @DisplayName("delete removes the entity and subsequent find returns empty [GH-90000]")
        void deleteRemovesEntity() { // GH-90000
            runPromise(() -> client.save(TENANT, COLLECTION, // GH-90000
                    Map.<String, Object>of("id", "del-1", "status", "alive"))); // GH-90000

            runPromise(() -> client.delete(TENANT, COLLECTION, "del-1")); // GH-90000

            Optional<Entity> found = runPromise(() -> client.findById(TENANT, COLLECTION, "del-1")); // GH-90000
            assertThat(found).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("delete of non-existent entity does not throw [GH-90000]")
        void deleteNonExistentIsIdempotent() { // GH-90000
            assertThatCode(() -> runPromise(() -> client.delete(TENANT, COLLECTION, "ghost-id"))) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("query returns only entities in the requested collection [GH-90000]")
        void queryIsScopedToCollection() { // GH-90000
            runPromise(() -> client.save(TENANT, COLLECTION, // GH-90000
                    Map.<String, Object>of("id", "qc-1", "x", "1"))); // GH-90000
            runPromise(() -> client.save(TENANT, "other-collection", // GH-90000
                    Map.<String, Object>of("id", "qc-2", "x", "2"))); // GH-90000

            List<Entity> results = runPromise(() -> client.query(TENANT, COLLECTION, Query.all())); // GH-90000

            assertThat(results).isNotEmpty(); // GH-90000
            assertThat(results).allMatch(e -> COLLECTION.equals(e.collection())); // GH-90000
        }

        @Test
        @DisplayName("entity created time is set and not null [GH-90000]")
        void entityCreatedTimeIsSet() { // GH-90000
            Entity entity = runPromise(() -> client.save(TENANT, COLLECTION, // GH-90000
                    Map.<String, Object>of("id", "time-1"))); // GH-90000

            assertThat(entity.createdAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("large batch of entities is stored and queryable in full [GH-90000]")
        void largeBatchIsStoredAndQueryable() { // GH-90000
            int batchSize = 100;
            for (int i = 0; i < batchSize; i++) { // GH-90000
                int idx = i;
                runPromise(() -> client.save(TENANT, "batch-coll", // GH-90000
                        Map.<String, Object>of("id", "b-" + idx, "seq", idx))); // GH-90000
            }

            List<Entity> results = runPromise(() -> client.query(TENANT, "batch-coll", Query.all())); // GH-90000
            assertThat(results).hasSize(batchSize); // GH-90000
        }
    }

    // =========================================================================
    // Tenant isolation
    // =========================================================================

    @Nested
    @DisplayName("Entity — tenant isolation (SDK boundary) [GH-90000]")
    class EntityTenantIsolation {

        @Test
        @DisplayName("entity saved by tenant-A is invisible to tenant-B [GH-90000]")
        void tenantAEntityHiddenFromTenantB() { // GH-90000
            runPromise(() -> client.save("tenant-alpha", COLLECTION, // GH-90000
                    Map.<String, Object>of("id", "iso-1", "owner", "alpha"))); // GH-90000

            Optional<Entity> fromB = runPromise( // GH-90000
                    () -> client.findById("tenant-beta", COLLECTION, "iso-1")); // GH-90000

            assertThat(fromB).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("query by tenant-B returns no entities saved by tenant-A [GH-90000]")
        void queryIsScopedToTenant() { // GH-90000
            runPromise(() -> client.save("tenant-alpha", COLLECTION, // GH-90000
                    Map.<String, Object>of("id", "iso-q1"))); // GH-90000

            List<Entity> results = runPromise( // GH-90000
                    () -> client.query("tenant-beta", COLLECTION, Query.all())); // GH-90000

            assertThat(results).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // Event operations
    // =========================================================================

    @Nested
    @DisplayName("Event — input boundaries and ordering [GH-90000]")
    class EventInputBoundaries {

        @Test
        @DisplayName("appended event is retrievable via queryEvents [GH-90000]")
        void appendedEventIsRetrievable() { // GH-90000
            runPromise(() -> client.appendEvent(TENANT, // GH-90000
                    DataCloudClient.Event.of("item.created", // GH-90000
                            Map.of("itemId", "item-sdk-1", "status", "NEW")))); // GH-90000

            List<DataCloudClient.Event> events = runPromise( // GH-90000
                    () -> client.queryEvents(TENANT, // GH-90000
                            DataCloudClient.EventQuery.byType("item.created [GH-90000]")));

            assertThat(events).isNotEmpty(); // GH-90000
            assertThat(events.get(0).type()).isEqualTo("item.created [GH-90000]");
        }

        @Test
        @DisplayName("multiple events share the same type and are all returned [GH-90000]")
        void multipleEventsOfSameTypeAreAllReturned() { // GH-90000
            int count = 5;
            for (int i = 0; i < count; i++) { // GH-90000
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT, // GH-90000
                        DataCloudClient.Event.of("metric.reported", // GH-90000
                                Map.of("index", (Object) idx)))); // GH-90000
            }

            List<DataCloudClient.Event> events = runPromise( // GH-90000
                    () -> client.queryEvents(TENANT, // GH-90000
                            DataCloudClient.EventQuery.byType("metric.reported [GH-90000]")));

            assertThat(events).hasSize(count); // GH-90000
        }

        @Test
        @DisplayName("events from other tenants are not returned [GH-90000]")
        void eventsAreTenantScoped() { // GH-90000
            runPromise(() -> client.appendEvent("other-tenant", // GH-90000
                    DataCloudClient.Event.of("secret.event", // GH-90000
                            Map.of("payload", "hidden")))); // GH-90000

            List<DataCloudClient.Event> events = runPromise( // GH-90000
                    () -> client.queryEvents(TENANT, // GH-90000
                            DataCloudClient.EventQuery.byType("secret.event [GH-90000]")));

            assertThat(events).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // Client lifecycle
    // =========================================================================

    @Nested
    @DisplayName("Client lifecycle [GH-90000]")
    class ClientLifecycle {

        @Test
        @DisplayName("two independent in-memory clients do not share state [GH-90000]")
        void twoClientsShareNoState() { // GH-90000
            DataCloudClient clientA = DataCloud.forTesting(); // GH-90000
            DataCloudClient clientB = DataCloud.forTesting(); // GH-90000

            runPromise(() -> clientA.save(TENANT, COLLECTION, // GH-90000
                    Map.<String, Object>of("id", "lc-1"))); // GH-90000

            Optional<Entity> fromB = runPromise( // GH-90000
                    () -> clientB.findById(TENANT, COLLECTION, "lc-1")); // GH-90000

            assertThat(fromB).isEmpty(); // GH-90000

            clientA.close(); // GH-90000
            clientB.close(); // GH-90000
        }

        @Test
        @DisplayName("client may be closed without exception [GH-90000]")
        void closeIsIdempotent() { // GH-90000
            DataCloudClient c = DataCloud.forTesting(); // GH-90000
            assertThatCode(c::close).doesNotThrowAnyException(); // GH-90000
        }
    }
}
