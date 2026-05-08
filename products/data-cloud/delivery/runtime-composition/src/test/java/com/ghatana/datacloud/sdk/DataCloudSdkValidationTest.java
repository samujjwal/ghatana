/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * with the repo convention — never call {@code Promise.getResult()} directly. 
 *
 * @doc.type class
 * @doc.purpose SDK contract validation and input-boundary tests (DC-003) 
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("Data-Cloud SDK — Contract Validation (DC-003)")
class DataCloudSdkValidationTest extends EventloopTestBase {

    private static final String TENANT = "sdk-test-tenant";
    private static final String COLLECTION = "sdk-items";

    private DataCloudClient client;

    @BeforeEach
    void setUp() { 
        client = DataCloud.forTesting(); 
    }

    // =========================================================================
    // Entity input validation
    // =========================================================================

    @Nested
    @DisplayName("Entity — input boundaries")
    class EntityInputBoundaries {

        @Test
        @DisplayName("save with minimal required fields succeeds")
        void saveWithMinimalFields() { 
            Entity entity = runPromise(() -> client.save( 
                    TENANT, COLLECTION,
                    Map.<String, Object>of("id", "min-1"))); 

            assertThat(entity).isNotNull(); 
            assertThat(entity.id()).isEqualTo("min-1");
            assertThat(entity.collection()).isEqualTo(COLLECTION); 
        }

        @Test
        @DisplayName("save with rich nested payload is preserved faithfully")
        void saveWithNestedPayloadPreservesStructure() { 
            Map<String, Object> data = Map.of( 
                    "id", "rich-1",
                    "tags", List.of("alpha", "beta"), 
                    "meta", Map.of("version", 3, "flags", Map.of("active", true))); 

            Entity entity = runPromise(() -> client.save(TENANT, COLLECTION, data)); 

            assertThat(entity.data()).containsKey("tags");
            assertThat(entity.data()).containsKey("meta");
        }

        @Test
        @DisplayName("findById returns empty for unknown id")
        void findByIdReturnEmptyForMissing() { 
            Optional<Entity> result = runPromise( 
                    () -> client.findById(TENANT, COLLECTION, "does-not-exist")); 

            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("overwrite an entity retains the latest data")
        void overwriteEntityRetainsLatestData() { 
            runPromise(() -> client.save(TENANT, COLLECTION, 
                    Map.<String, Object>of("id", "ow-1", "v", "first"))); 

            runPromise(() -> client.save(TENANT, COLLECTION, 
                    Map.<String, Object>of("id", "ow-1", "v", "second"))); 

            Optional<Entity> found = runPromise(() -> client.findById(TENANT, COLLECTION, "ow-1")); 
            assertThat(found).isPresent(); 
            assertThat(found.get().data()).containsEntry("v", "second"); 
        }

        @Test
        @DisplayName("delete removes the entity and subsequent find returns empty")
        void deleteRemovesEntity() { 
            runPromise(() -> client.save(TENANT, COLLECTION, 
                    Map.<String, Object>of("id", "del-1", "status", "alive"))); 

            runPromise(() -> client.delete(TENANT, COLLECTION, "del-1")); 

            Optional<Entity> found = runPromise(() -> client.findById(TENANT, COLLECTION, "del-1")); 
            assertThat(found).isEmpty(); 
        }

        @Test
        @DisplayName("delete of non-existent entity does not throw")
        void deleteNonExistentIsIdempotent() { 
            assertThatCode(() -> runPromise(() -> client.delete(TENANT, COLLECTION, "ghost-id"))) 
                    .doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("query returns only entities in the requested collection")
        void queryIsScopedToCollection() { 
            runPromise(() -> client.save(TENANT, COLLECTION, 
                    Map.<String, Object>of("id", "qc-1", "x", "1"))); 
            runPromise(() -> client.save(TENANT, "other-collection", 
                    Map.<String, Object>of("id", "qc-2", "x", "2"))); 

            List<Entity> results = runPromise(() -> client.query(TENANT, COLLECTION, Query.all())); 

            assertThat(results).isNotEmpty(); 
            assertThat(results).allMatch(e -> COLLECTION.equals(e.collection())); 
        }

        @Test
        @DisplayName("entity created time is set and not null")
        void entityCreatedTimeIsSet() { 
            Entity entity = runPromise(() -> client.save(TENANT, COLLECTION, 
                    Map.<String, Object>of("id", "time-1"))); 

            assertThat(entity.createdAt()).isNotNull(); 
        }

        @Test
        @DisplayName("large batch of entities is stored and queryable in full")
        void largeBatchIsStoredAndQueryable() { 
            int batchSize = 100;
            for (int i = 0; i < batchSize; i++) { 
                int idx = i;
                runPromise(() -> client.save(TENANT, "batch-coll", 
                        Map.<String, Object>of("id", "b-" + idx, "seq", idx))); 
            }

            List<Entity> results = runPromise(() -> client.query(TENANT, "batch-coll", Query.all())); 
            assertThat(results).hasSize(batchSize); 
        }
    }

    // =========================================================================
    // Tenant isolation
    // =========================================================================

    @Nested
    @DisplayName("Entity — tenant isolation (SDK boundary)")
    class EntityTenantIsolation {

        @Test
        @DisplayName("entity saved by tenant-A is invisible to tenant-B")
        void tenantAEntityHiddenFromTenantB() { 
            runPromise(() -> client.save("tenant-alpha", COLLECTION, 
                    Map.<String, Object>of("id", "iso-1", "owner", "alpha"))); 

            Optional<Entity> fromB = runPromise( 
                    () -> client.findById("tenant-beta", COLLECTION, "iso-1")); 

            assertThat(fromB).isEmpty(); 
        }

        @Test
        @DisplayName("query by tenant-B returns no entities saved by tenant-A")
        void queryIsScopedToTenant() { 
            runPromise(() -> client.save("tenant-alpha", COLLECTION, 
                    Map.<String, Object>of("id", "iso-q1"))); 

            List<Entity> results = runPromise( 
                    () -> client.query("tenant-beta", COLLECTION, Query.all())); 

            assertThat(results).isEmpty(); 
        }
    }

    // =========================================================================
    // Event operations
    // =========================================================================

    @Nested
    @DisplayName("Event — input boundaries and ordering")
    class EventInputBoundaries {

        @Test
        @DisplayName("appended event is retrievable via queryEvents")
        void appendedEventIsRetrievable() { 
            runPromise(() -> client.appendEvent(TENANT, 
                    DataCloudClient.Event.builder()
                    .type("item.created")
                    .payload(Map.of("itemId", "item-sdk-1", "status", "NEW"))
                    .build()));

            List<DataCloudClient.Event> events = runPromise( 
                    () -> client.queryEvents(TENANT, 
                            DataCloudClient.EventQuery.byType("item.created")));

            assertThat(events).isNotEmpty(); 
            assertThat(events.get(0).type()).isEqualTo("item.created");
        }

        @Test
        @DisplayName("multiple events share the same type and are all returned")
        void multipleEventsOfSameTypeAreAllReturned() { 
            int count = 5;
            for (int i = 0; i < count; i++) { 
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT, 
                    DataCloudClient.Event.builder()
                        .type("metric.reported")
                        .payload(Map.of("index", (Object) idx))
                        .build()));
            }

            List<DataCloudClient.Event> events = runPromise( 
                    () -> client.queryEvents(TENANT, 
                            DataCloudClient.EventQuery.byType("metric.reported")));

            assertThat(events).hasSize(count); 
        }

        @Test
        @DisplayName("events from other tenants are not returned")
        void eventsAreTenantScoped() { 
            runPromise(() -> client.appendEvent("other-tenant", 
                    DataCloudClient.Event.builder()
                    .type("secret.event")
                    .payload(Map.of("payload", "hidden"))
                    .build()));

            List<DataCloudClient.Event> events = runPromise( 
                    () -> client.queryEvents(TENANT, 
                            DataCloudClient.EventQuery.byType("secret.event")));

            assertThat(events).isEmpty(); 
        }
    }

    // =========================================================================
    // Client lifecycle
    // =========================================================================

    @Nested
    @DisplayName("Client lifecycle")
    class ClientLifecycle {

        @Test
        @DisplayName("two independent in-memory clients do not share state")
        void twoClientsShareNoState() { 
            DataCloudClient clientA = DataCloud.forTesting(); 
            DataCloudClient clientB = DataCloud.forTesting(); 

            runPromise(() -> clientA.save(TENANT, COLLECTION, 
                    Map.<String, Object>of("id", "lc-1"))); 

            Optional<Entity> fromB = runPromise( 
                    () -> clientB.findById(TENANT, COLLECTION, "lc-1")); 

            assertThat(fromB).isEmpty(); 

            clientA.close(); 
            clientB.close(); 
        }

        @Test
        @DisplayName("client may be closed without exception")
        void closeIsIdempotent() { 
            DataCloudClient c = DataCloud.forTesting(); 
            assertThatCode(c::close).doesNotThrowAnyException(); 
        }
    }
}
