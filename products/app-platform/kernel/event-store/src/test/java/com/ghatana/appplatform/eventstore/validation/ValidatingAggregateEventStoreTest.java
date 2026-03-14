package com.ghatana.appplatform.eventstore.validation;

import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import com.ghatana.appplatform.eventstore.domain.CompatibilityType;
import com.ghatana.appplatform.eventstore.domain.EventSchemaVersion;
import com.ghatana.appplatform.eventstore.domain.SchemaStatus;
import com.ghatana.appplatform.eventstore.domain.SchemaValidationError;
import com.ghatana.appplatform.eventstore.port.AggregateEventStore;
import com.ghatana.appplatform.eventstore.port.EventSchemaRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ValidatingAggregateEventStore}.
 *
 * <p>Uses stub implementations to isolate the decorator logic from JDBC and network.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the validating event store decorator
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ValidatingAggregateEventStore — Unit Tests")
class ValidatingAggregateEventStoreTest extends EventloopTestBase {

    private static final String EVENT_TYPE = "com.ghatana.order.OrderPlaced";

    private static final String VALID_SCHEMA = """
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "amount": { "type": "number" }
          },
          "required": ["amount"]
        }
        """;

    private static final EventSchemaVersion ACTIVE_SCHEMA = EventSchemaVersion.builder()
        .eventType(EVENT_TYPE)
        .version(1)
        .jsonSchema(VALID_SCHEMA)
        .status(SchemaStatus.ACTIVE)
        .compatType(CompatibilityType.BACKWARD)
        .build();

    /** Delegate store that records the last appended event. */
    private static class RecordingStore implements AggregateEventStore {
        AggregateEventRecord lastAppended;

        @Override
        public Promise<AggregateEventRecord> appendEvent(
                UUID aggregateId, String aggregateType, String eventType,
                Map<String, Object> data, Map<String, Object> metadata) {

            lastAppended = AggregateEventRecord.builder()
                .eventId(UUID.randomUUID())
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .sequenceNumber(0L)
                .data(data)
                .metadata(metadata)
                .build();
            return Promise.of(lastAppended);
        }

        @Override
        public Promise<List<AggregateEventRecord>> getEventsByAggregate(
                UUID aggregateId, long from, Long to) {
            return Promise.of(List.of());
        }
    }

    /** Registry stub returning a fixed optional schema. */
    private record StubRegistry(Optional<EventSchemaVersion> schema) implements EventSchemaRegistry {

        @Override
        public Promise<Void> registerSchema(EventSchemaVersion s)       { return Promise.of(null); }
        @Override
        public Promise<Void> activateSchema(String et, int v)           { return Promise.of(null); }
        @Override
        public Promise<Optional<EventSchemaVersion>> getActiveSchema(String et) {
            return Promise.of(schema);
        }
        @Override
        public Promise<Optional<EventSchemaVersion>> getSchema(String et, int v) {
            return Promise.of(schema);
        }
        @Override
        public Promise<List<EventSchemaVersion>> listVersions(String et) {
            return Promise.of(List.of());
        }
    }

    private RecordingStore delegate;

    @BeforeEach
    void setUp() {
        delegate = new RecordingStore();
    }

    @Test
    @DisplayName("validData_activeSchema_eventAppended — valid payload passes through to delegate")
    void validDataActiveSchemaEventAppended() {
        var store = new ValidatingAggregateEventStore(
            delegate, new StubRegistry(Optional.of(ACTIVE_SCHEMA)));

        UUID aggId = UUID.randomUUID();
        AggregateEventRecord result = runPromise(() -> store.appendEvent(
            aggId, "Order", EVENT_TYPE,
            Map.of("amount", 100.0),
            Map.of()));

        assertThat(result).isNotNull();
        assertThat(delegate.lastAppended).isNotNull();
        assertThat(delegate.lastAppended.eventType()).isEqualTo(EVENT_TYPE);
    }

    @Test
    @DisplayName("invalidData_activeSchema_schemaValidationErrorThrown — invalid payload is rejected")
    void invalidDataActiveSchemaSchemaValidationErrorThrown() {
        var store = new ValidatingAggregateEventStore(
            delegate, new StubRegistry(Optional.of(ACTIVE_SCHEMA)));

        assertThatThrownBy(() ->
            runPromise(() -> store.appendEvent(
                UUID.randomUUID(), "Order", EVENT_TYPE,
                Map.of("wrongField", "not-a-number"),   // missing required "amount"
                Map.of())))
            .isInstanceOf(SchemaValidationError.class)
            .hasMessageContaining(EVENT_TYPE);

        assertThat(delegate.lastAppended).isNull();
    }

    @Test
    @DisplayName("noActiveSchema_unknownType_eventAllowedThrough — unknown types pass without schema")
    void noActiveSchemaUnknownTypeEventAllowedThrough() {
        var store = new ValidatingAggregateEventStore(
            delegate, new StubRegistry(Optional.empty()));

        UUID aggId = UUID.randomUUID();
        AggregateEventRecord result = runPromise(() -> store.appendEvent(
            aggId, "Unknown", "com.ghatana.unknown.SomeEvent",
            Map.of("key", "value"),
            Map.of()));

        assertThat(result).isNotNull();
        assertThat(delegate.lastAppended).isNotNull();
    }

    @Test
    @DisplayName("getEventsByAggregate_delegatesDirectly — reads bypass schema validation")
    void getEventsByAggregateDelegatesDirectly() {
        var store = new ValidatingAggregateEventStore(
            delegate, new StubRegistry(Optional.of(ACTIVE_SCHEMA)));

        List<AggregateEventRecord> events = runPromise(() ->
            store.getEventsByAggregate(UUID.randomUUID(), 0L, null));

        assertThat(events).isEmpty();
    }
}
