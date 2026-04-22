package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloudClient.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for the DataCloud core client logic.
 * Tests the DefaultDataCloudClient via the factory API.
 *
 * @doc.type class
 * @doc.purpose DataCloud core client tests
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("DataCloud Client Tests [GH-90000]")
class DataCloudClientTest extends EventloopTestBase {

    private static final String TENANT = "test-tenant";
    private static final String USERS = "users";
    private static final String ITEMS = "items";

    private DataCloudClient client;

    @BeforeEach
    void setUp() { // GH-90000
        client = DataCloud.forTesting(); // GH-90000
    }

    @Nested
    @DisplayName("Entity Operations [GH-90000]")
    class EntityOperations {

        @Test
        @DisplayName("should save and retrieve an entity [GH-90000]")
        void shouldSaveAndRetrieve() { // GH-90000
            Entity saved = runPromise(() -> client.save(TENANT, USERS, // GH-90000
                    Map.<String, Object>of("id", "ent-1", "name", "Alice", "role", "admin"))); // GH-90000
            assertThat(saved.id()).isEqualTo("ent-1 [GH-90000]");

            Optional<Entity> found = runPromise(() -> client.findById(TENANT, USERS, "ent-1")); // GH-90000
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().collection()).isEqualTo(USERS); // GH-90000
            assertThat(found.get().data()).containsEntry("name", "Alice"); // GH-90000
        }

        @Test
        @DisplayName("should return empty for non-existent entity [GH-90000]")
        void shouldReturnEmptyForMissing() { // GH-90000
            Optional<Entity> found = runPromise(() -> client.findById(TENANT, USERS, "non-existent")); // GH-90000
            assertThat(found).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should update an existing entity [GH-90000]")
        void shouldUpdateEntity() { // GH-90000
            runPromise(() -> client.save(TENANT, "config", // GH-90000
                    Map.<String, Object>of("id", "ent-upd", "version", "1.0"))); // GH-90000

            runPromise(() -> client.save(TENANT, "config", // GH-90000
                    Map.<String, Object>of("id", "ent-upd", "version", "2.0"))); // GH-90000

            Optional<Entity> found = runPromise(() -> client.findById(TENANT, "config", "ent-upd")); // GH-90000
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().data()).containsEntry("version", "2.0"); // GH-90000
        }

        @Test
        @DisplayName("should delete an entity [GH-90000]")
        void shouldDeleteEntity() { // GH-90000
            runPromise(() -> client.save(TENANT, "temp", // GH-90000
                    Map.<String, Object>of("id", "ent-del"))); // GH-90000

            runPromise(() -> client.delete(TENANT, "temp", "ent-del")); // GH-90000

            Optional<Entity> found = runPromise(() -> client.findById(TENANT, "temp", "ent-del")); // GH-90000
            assertThat(found).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should query entities by collection [GH-90000]")
        void shouldQueryByCollection() { // GH-90000
            runPromise(() -> client.save(TENANT, USERS, // GH-90000
                    Map.<String, Object>of("id", "u1", "name", "A"))); // GH-90000
            runPromise(() -> client.save(TENANT, USERS, // GH-90000
                    Map.<String, Object>of("id", "u2", "name", "B"))); // GH-90000
            runPromise(() -> client.save(TENANT, "orders", // GH-90000
                    Map.<String, Object>of("id", "o1", "total", 100))); // GH-90000

            List<Entity> users = runPromise(() -> client.query(TENANT, USERS, Query.all())); // GH-90000
            assertThat(users).hasSize(2); // GH-90000
            assertThat(users).allMatch(e -> e.collection().equals(USERS)); // GH-90000
        }

        @Test
        @DisplayName("should handle batch save of multiple entities [GH-90000]")
        void shouldBatchSave() { // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                int idx = i;
                runPromise(() -> client.save(TENANT, ITEMS, // GH-90000
                        Map.<String, Object>of("id", "batch-" + idx, "index", idx))); // GH-90000
            }

            List<Entity> items = runPromise(() -> client.query(TENANT, ITEMS, Query.all())); // GH-90000
            assertThat(items).hasSize(50); // GH-90000
        }
    }

    @Nested
    @DisplayName("Event Operations [GH-90000]")
    class EventOperations {

        @Test
        @DisplayName("should append and query events [GH-90000]")
        void shouldAppendAndQueryEvents() { // GH-90000
            Event event = Event.of("user.created", Map.of("userId", "u1", "name", "Alice")); // GH-90000

            runPromise(() -> client.appendEvent(TENANT, event)); // GH-90000

            List<Event> events = runPromise( // GH-90000
                    () -> client.queryEvents(TENANT, EventQuery.byType("user.created [GH-90000]")));
            assertThat(events).isNotEmpty(); // GH-90000
            assertThat(events.get(0).type()).isEqualTo("user.created [GH-90000]");
        }

        @Test
        @DisplayName("should append multiple events and query by type [GH-90000]")
        void shouldQueryByType() { // GH-90000
            for (int i = 0; i < 10; i++) { // GH-90000
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT, // GH-90000
                        Event.of("sensor.reading", Map.of("value", (Object) (idx * 10))))); // GH-90000
            }

            List<Event> events = runPromise( // GH-90000
                    () -> client.queryEvents(TENANT, EventQuery.byType("sensor.reading [GH-90000]")));
            assertThat(events).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should support event tailing [GH-90000]")
        void shouldSupportTailing() { // GH-90000
            runPromise(() -> client.appendEvent(TENANT, // GH-90000
                    Event.of("log.entry", Map.of("msg", "first")))); // GH-90000

            AtomicReference<Event> received = new AtomicReference<>(); // GH-90000

            Subscription subscription = client.tailEvents( // GH-90000
                    TENANT,
                    new TailRequest(Offset.zero(), List.of("log.entry [GH-90000]")),
                    received::set);

            assertThat(subscription).isNotNull(); // GH-90000
            subscription.cancel(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Lifecycle [GH-90000]")
    class Lifecycle {

        @Test
        @DisplayName("should support close and re-creation [GH-90000]")
        void shouldSupportCloseAndRecreate() { // GH-90000
            runPromise(() -> client.save(TENANT, "test", // GH-90000
                    Map.<String, Object>of("id", "e1"))); // GH-90000
            client.close(); // GH-90000

            DataCloudClient newClient = DataCloud.forTesting(); // GH-90000
            // New client should start fresh (in-memory) // GH-90000
            Optional<Entity> found = runPromise(() -> newClient.findById(TENANT, "test", "e1")); // GH-90000
            assertThat(found).isEmpty(); // GH-90000
            newClient.close(); // GH-90000
        }
    }
}
