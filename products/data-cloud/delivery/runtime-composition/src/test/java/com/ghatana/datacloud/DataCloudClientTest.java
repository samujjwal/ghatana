package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloudClient.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
@DisplayName("DataCloud Client Tests")
class DataCloudClientTest extends EventloopTestBase {

    private static final String TENANT = "test-tenant";
    private static final String USERS = "users";
    private static final String ITEMS = "items";

    private DataCloudClient client;

    @BeforeEach
    void setUp() { 
        client = DataCloud.forTesting(); 
    }

    @Nested
    @DisplayName("Entity Operations")
    class EntityOperations {

        @Test
        @DisplayName("should save and retrieve an entity")
        void shouldSaveAndRetrieve() { 
            Entity saved = runPromise(() -> client.save(TENANT, USERS, 
                    Map.<String, Object>of("id", "ent-1", "name", "Alice", "role", "admin"))); 
            assertThat(saved.id()).isEqualTo("ent-1");

            Optional<Entity> found = runPromise(() -> client.findById(TENANT, USERS, "ent-1")); 
            assertThat(found).isPresent(); 
            assertThat(found.get().collection()).isEqualTo(USERS); 
            assertThat(found.get().data()).containsEntry("name", "Alice"); 
        }

        @Test
        @DisplayName("should return empty for non-existent entity")
        void shouldReturnEmptyForMissing() { 
            Optional<Entity> found = runPromise(() -> client.findById(TENANT, USERS, "non-existent")); 
            assertThat(found).isEmpty(); 
        }

        @Test
        @DisplayName("should update an existing entity")
        void shouldUpdateEntity() { 
            runPromise(() -> client.save(TENANT, "config", 
                    Map.<String, Object>of("id", "ent-upd", "version", "1.0"))); 

            runPromise(() -> client.save(TENANT, "config", 
                    Map.<String, Object>of("id", "ent-upd", "version", "2.0"))); 

            Optional<Entity> found = runPromise(() -> client.findById(TENANT, "config", "ent-upd")); 
            assertThat(found).isPresent(); 
            assertThat(found.get().data()).containsEntry("version", "2.0"); 
        }

        @Test
        @DisplayName("should delete an entity")
        void shouldDeleteEntity() { 
            runPromise(() -> client.save(TENANT, "temp", 
                    Map.<String, Object>of("id", "ent-del"))); 

            runPromise(() -> client.delete(TENANT, "temp", "ent-del")); 

            Optional<Entity> found = runPromise(() -> client.findById(TENANT, "temp", "ent-del")); 
            assertThat(found).isEmpty(); 
        }

        @Test
        @DisplayName("should query entities by collection")
        void shouldQueryByCollection() { 
            runPromise(() -> client.save(TENANT, USERS, 
                    Map.<String, Object>of("id", "u1", "name", "A"))); 
            runPromise(() -> client.save(TENANT, USERS, 
                    Map.<String, Object>of("id", "u2", "name", "B"))); 
            runPromise(() -> client.save(TENANT, "orders", 
                    Map.<String, Object>of("id", "o1", "total", 100))); 

            List<Entity> users = runPromise(() -> client.query(TENANT, USERS, Query.all())); 
            assertThat(users).hasSize(2); 
            assertThat(users).allMatch(e -> e.collection().equals(USERS)); 
        }

        @Test
        @DisplayName("should handle batch save of multiple entities")
        void shouldBatchSave() { 
            for (int i = 0; i < 50; i++) { 
                int idx = i;
                runPromise(() -> client.save(TENANT, ITEMS, 
                        Map.<String, Object>of("id", "batch-" + idx, "index", idx))); 
            }

            List<Entity> items = runPromise(() -> client.query(TENANT, ITEMS, Query.all())); 
            assertThat(items).hasSize(50); 
        }
    }

    @Nested
    @DisplayName("Event Operations")
    class EventOperations {

        @Test
        @DisplayName("should append and query events")
        void shouldAppendAndQueryEvents() { 
            Event event = Event.of("user.created", Map.of("userId", "u1", "name", "Alice")); 

            runPromise(() -> client.appendEvent(TENANT, event)); 

            List<Event> events = runPromise( 
                    () -> client.queryEvents(TENANT, EventQuery.byType("user.created")));
            assertThat(events).isNotEmpty(); 
            assertThat(events.get(0).type()).isEqualTo("user.created");
        }

        @Test
        @DisplayName("should append multiple events and query by type")
        void shouldQueryByType() { 
            for (int i = 0; i < 10; i++) { 
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT, 
                        Event.of("sensor.reading", Map.of("value", (Object) (idx * 10))))); 
            }

            List<Event> events = runPromise( 
                    () -> client.queryEvents(TENANT, EventQuery.byType("sensor.reading")));
            assertThat(events).isNotEmpty(); 
        }

        @Test
        @DisplayName("should apply fromOffset for type-filtered event queries")
        void shouldApplyFromOffsetForTypeQueries() {
            runPromise(() -> client.appendEvent(TENANT, Event.of("sensor.reading", Map.of("value", 1))));
            runPromise(() -> client.appendEvent(TENANT, Event.of("other.event", Map.of("value", 2))));
            runPromise(() -> client.appendEvent(TENANT, Event.of("sensor.reading", Map.of("value", 3))));
            runPromise(() -> client.appendEvent(TENANT, Event.of("sensor.reading", Map.of("value", 4))));

            EventQuery query = new EventQuery(
                List.of("sensor.reading"),
                null,
                null,
                Offset.of(2),
                10);

            List<Event> events = runPromise(() -> client.queryEvents(TENANT, query));

            assertThat(events).hasSize(2);
            assertThat(events).allMatch(event -> event.type().equals("sensor.reading"));
            assertThat(events)
                .extracting(event -> event.payload().get("value"))
                .containsExactly(3, 4);
        }

        @Test
        @DisplayName("should apply fromOffset for all-event queries")
        void shouldApplyFromOffsetForAllQueries() {
            runPromise(() -> client.appendEvent(TENANT, Event.of("event.one", Map.of("idx", 1))));
            runPromise(() -> client.appendEvent(TENANT, Event.of("event.two", Map.of("idx", 2))));
            runPromise(() -> client.appendEvent(TENANT, Event.of("event.three", Map.of("idx", 3))));

            EventQuery query = new EventQuery(List.of(), null, null, Offset.of(1), 2);
            List<Event> events = runPromise(() -> client.queryEvents(TENANT, query));

            assertThat(events).hasSize(2);
            assertThat(events)
                .extracting(event -> event.payload().get("idx"))
                .containsExactly(2, 3);
        }

        @Test
        @DisplayName("should apply multi-type time-range query with store-level type pushdown")
        void shouldApplyMultiTypeTimeRangeWithOffset() {
            Instant base = Instant.parse("2026-01-01T00:00:00Z");

            runPromise(() -> client.appendEvent(TENANT, Event.builder()
                .type("type.a")
                .source("test")
                .timestamp(base)
                .payload(Map.of("idx", 0))
                .build()));
            runPromise(() -> client.appendEvent(TENANT, Event.builder()
                .type("type.b")
                .source("test")
                .timestamp(base.plusSeconds(1))
                .payload(Map.of("idx", 1))
                .build()));
            runPromise(() -> client.appendEvent(TENANT, Event.builder()
                .type("type.c")
                .source("test")
                .timestamp(base.plusSeconds(2))
                .payload(Map.of("idx", 2))
                .build()));
            runPromise(() -> client.appendEvent(TENANT, Event.builder()
                .type("type.a")
                .source("test")
                .timestamp(base.plusSeconds(3))
                .payload(Map.of("idx", 3))
                .build()));
            runPromise(() -> client.appendEvent(TENANT, Event.builder()
                .type("type.b")
                .source("test")
                .timestamp(base.plusSeconds(4))
                .payload(Map.of("idx", 4))
                .build()));

            EventQuery query = new EventQuery(
                List.of("type.a", "type.b"),
                base.plusSeconds(1),
                base.plusSeconds(4),
                Offset.of(1),
                10);

            List<Event> events = runPromise(() -> client.queryEvents(TENANT, query));

            assertThat(events).hasSize(2);
            assertThat(events)
                .extracting(Event::type)
                .containsExactly("type.b", "type.a");
            assertThat(events)
                .extracting(event -> event.payload().get("idx"))
                .containsExactly(1, 3);
        }

        @Test
        @DisplayName("should support event tailing")
        void shouldSupportTailing() { 
            runPromise(() -> client.appendEvent(TENANT, 
                    Event.of("log.entry", Map.of("msg", "first")))); 

            AtomicReference<Event> received = new AtomicReference<>(); 

            Subscription subscription = client.tailEvents( 
                    TENANT,
                    new TailRequest(Offset.zero(), List.of("log.entry")),
                    received::set);

            assertThat(subscription).isNotNull(); 
            subscription.cancel(); 
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should support close and re-creation")
        void shouldSupportCloseAndRecreate() { 
            runPromise(() -> client.save(TENANT, "test", 
                    Map.<String, Object>of("id", "e1"))); 
            client.close(); 

            DataCloudClient newClient = DataCloud.forTesting(); 
            // New client should start fresh (in-memory) 
            Optional<Entity> found = runPromise(() -> newClient.findById(TENANT, "test", "e1")); 
            assertThat(found).isEmpty(); 
            newClient.close(); 
        }
    }
}
