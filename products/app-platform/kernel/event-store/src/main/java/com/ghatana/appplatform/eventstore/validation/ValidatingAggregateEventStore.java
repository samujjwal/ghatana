package com.ghatana.appplatform.eventstore.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import com.ghatana.appplatform.eventstore.domain.SchemaValidationError;
import com.ghatana.appplatform.eventstore.port.AggregateEventStore;
import com.ghatana.appplatform.eventstore.port.EventSchemaRegistry;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Decorator that validates event payloads against the active JSON schema before delegating
 * to the wrapped {@link AggregateEventStore}.
 *
 * <p>Behaviour table:
 * <ul>
 *   <li>Active schema found and data is valid → delegate to wrapped store.</li>
 *   <li>Active schema found and data is invalid → promise fails with
 *       {@link SchemaValidationError}; the event is NOT persisted.</li>
 *   <li>No active schema for the event type → allow through (unknown types are permitted
 *       by default to support incremental schema adoption).</li>
 * </ul>
 *
 * <p>All schema lookups are async (via {@link EventSchemaRegistry}) and compose cleanly
 * in the ActiveJ eventloop through promise chaining.
 *
 * @doc.type class
 * @doc.purpose Decorator that validates event data against the active JSON schema
 * @doc.layer product
 * @doc.pattern Decorator
 */
public final class ValidatingAggregateEventStore implements AggregateEventStore {

    private static final Logger log = LoggerFactory.getLogger(ValidatingAggregateEventStore.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    private final AggregateEventStore delegate;
    private final EventSchemaRegistry schemaRegistry;

    public ValidatingAggregateEventStore(
            AggregateEventStore delegate,
            EventSchemaRegistry schemaRegistry) {
        this.delegate       = delegate;
        this.schemaRegistry = schemaRegistry;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates {@code data} against the active schema for {@code eventType} before
     * delegating. If no active schema exists the event is passed through unchanged.
     */
    @Override
    public Promise<AggregateEventRecord> appendEvent(
            UUID aggregateId,
            String aggregateType,
            String eventType,
            Map<String, Object> data,
            Map<String, Object> metadata) {

        return schemaRegistry.getActiveSchema(eventType)
            .then(optSchema -> {
                if (optSchema.isEmpty()) {
                    log.debug("No active schema for eventType={}; allowing event through.", eventType);
                    return delegate.appendEvent(aggregateId, aggregateType, eventType, data, metadata);
                }

                var schema = optSchema.get();
                try {
                    Set<ValidationMessage> errors = validate(schema.jsonSchema(), data);
                    if (!errors.isEmpty()) {
                        String message = errors.stream()
                            .map(ValidationMessage::getMessage)
                            .collect(Collectors.joining("; "));
                        log.warn("Schema validation failed eventType={} version={}: {}",
                            eventType, schema.version(), message);
                        return Promise.ofException(
                            new SchemaValidationError(eventType, schema.version(), message));
                    }
                } catch (Exception e) {
                    log.error("Error evaluating schema for eventType={}: {}", eventType, e.getMessage());
                    return Promise.ofException(
                        new SchemaValidationError(eventType, schema.version(),
                            "Schema evaluation error: " + e.getMessage()));
                }

                return delegate.appendEvent(aggregateId, aggregateType, eventType, data, metadata);
            });
    }

    /** {@inheritDoc} — delegates read-path directly; no schema validation needed. */
    @Override
    public Promise<List<AggregateEventRecord>> getEventsByAggregate(
            UUID aggregateId,
            long fromSequence,
            Long toSequence) {
        return delegate.getEventsByAggregate(aggregateId, fromSequence, toSequence);
    }

    private Set<ValidationMessage> validate(String jsonSchema, Map<String, Object> data) {
        var schema  = SCHEMA_FACTORY.getSchema(jsonSchema);
        var dataNode = MAPPER.valueToTree(data);
        return schema.validate(dataNode);
    }
}
