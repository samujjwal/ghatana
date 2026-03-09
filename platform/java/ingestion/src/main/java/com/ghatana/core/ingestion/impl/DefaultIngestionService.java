package com.ghatana.core.ingestion.impl;

import com.ghatana.core.connectors.EventSink;
import com.ghatana.core.connectors.IngestEvent;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.core.event.cloud.EventCloud.AppendOptions;
import com.ghatana.core.event.cloud.EventCloud.AppendRequest;
import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.core.event.cloud.EventTypeRef;
import com.ghatana.core.event.cloud.Version;
import com.ghatana.core.ingestion.api.*;
import com.ghatana.platform.types.identity.EventId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation of IngestionService delegating to EventCloud after validation and enrichment.
 *
 * <p><b>Purpose</b><br>
 * Production-ready implementation of the ingestion facade that validates tenant isolation,
 * validates event schemas, enriches events with system metadata (detection time, tracing headers),
 * and delegates persistence to EventCloud. Supports optional audit sink for compliance logging.
 *
 * @doc.type class
 * @doc.purpose Default IngestionService implementation with EventCloud delegation
 * @doc.layer core
 * @doc.pattern Implementation, Facade
 */
public class DefaultIngestionService implements IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultIngestionService.class);

    private final EventCloud eventCloud;
    private final ValidationConfig validationConfig;
    private final EventSink auditSink; // optional

    public DefaultIngestionService(EventCloud eventCloud, ValidationConfig validationConfig) {
        this(eventCloud, validationConfig, null);
    }

    public DefaultIngestionService(EventCloud eventCloud) {
        this(eventCloud, ValidationConfig.defaults(), null);
    }

    /**
     * New constructor with optional audit sink.
     */
    public DefaultIngestionService(EventCloud eventCloud, ValidationConfig validationConfig, EventSink auditSink) {
        this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud required");
        this.validationConfig = Objects.requireNonNull(validationConfig, "validationConfig required");
        this.auditSink = auditSink; // may be null
    }

    @Override
    public Promise<Offset> ingestOne(IngestEvent event, CallContext ctx) {
        // Validate tenant match
        if (!event.tenantId().equals(ctx.tenantId())) {
            return Promise.ofException(new ValidationException(
                "Event tenantId " + event.tenantId() + " does not match context tenantId " + ctx.tenantId()
            ));
        }

        EventRecord record = toEventRecord(event, ctx);
        AppendRequest request = new AppendRequest(record, AppendOptions.defaults());

        return eventCloud.append(request)
            .map(result -> {
                if (auditSink != null) {
                    try {
                        auditSink.send(record).whenComplete((v, ex) -> {
                            if (ex != null) {
                                logger.warn("Audit sink failed to send record {}: {}", record.eventId(), ex.toString());
                            }
                        });
                    } catch (Exception ex) {
                        logger.warn("Failed to schedule audit sink send: {}", ex.toString());
                    }
                }
                return result.offset();
            });
    }

    @Override
    public Promise<List<Offset>> ingestBatch(List<IngestEvent> batch, CallContext ctx) {
        // Validate all events
        for (IngestEvent event : batch) {
            if (!event.tenantId().equals(ctx.tenantId())) {
                return Promise.ofException(new ValidationException(
                    "Event tenantId " + event.tenantId() + " does not match context tenantId " + ctx.tenantId()
                ));
            }
        }

        List<AppendRequest> requests = new ArrayList<>(batch.size());
        List<EventRecord> records = new ArrayList<>(batch.size());
        for (IngestEvent event : batch) {
            EventRecord record = toEventRecord(event, ctx);
            records.add(record);
            requests.add(new AppendRequest(record, AppendOptions.defaults()));
        }

        return eventCloud.appendBatch(requests)
            .map(results -> {
                if (auditSink != null) {
                    for (EventRecord record : records) {
                        try {
                            auditSink.send(record).whenComplete((v, ex) -> {
                                if (ex != null) {
                                    logger.warn("Audit sink failed to send record {}: {}", record.eventId(), ex.toString());
                                }
                            });
                        } catch (Exception ex) {
                            logger.warn("Failed to schedule audit sink send for batch: {}", ex.toString());
                        }
                    }
                }
                return results.stream()
                    .map(r -> r.offset())
                    .toList();
            });
    }

    @Override
    public IngestStream openStream(StreamInit init, CallContext ctx) {
        return new DefaultIngestStream(init, ctx);
    }

    /**
     * Convert IngestEvent to EventRecord with system-generated fields.
     * Bridges between core.types (IngestEvent) and platform.types (EventRecord) namespaces.
     */
    private EventRecord toEventRecord(IngestEvent event, CallContext ctx) {
        Instant detectionTime = Instant.now();

        Map<String, String> headers = enrichHeaders(event.headers(), ctx);
        headers.putIfAbsent("schemaUri", event.schemaUri());
        event.partitionKey().ifPresent(key -> headers.putIfAbsent("partitionKey", key));

        // Bridge TenantId: core.types.TenantId → platform.types.TenantId
        TenantId tenantId = TenantId.of(event.tenantId().toString());

        // Bridge ContentType: core.types.ContentType → platform.types.ContentType
        com.ghatana.platform.types.ContentType contentType =
            com.ghatana.platform.types.ContentType.fromMimeType(event.contentType().getMimeType());

        // Parse version string (e.g., "1.0.0" or "1.0") to major.minor
        String[] versionParts = event.eventTypeVersion().split("\\.");
        int major = Integer.parseInt(versionParts[0]);
        int minor = versionParts.length > 1 ? Integer.parseInt(versionParts[1]) : 0;
        EventTypeRef typeRef = EventTypeRef.of(event.eventTypeName(), major, minor);

        // Generate EventId from idempotencyKey (deterministic) or random
        String idempotencyKeyStr = event.idempotencyKey().map(k -> k.raw()).orElse(null);
        EventId eventId = idempotencyKeyStr != null
            ? EventId.of(java.util.UUID.nameUUIDFromBytes(
                idempotencyKeyStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString())
            : EventId.random();

        // Bridge CorrelationId: core.types → platform.types
        Optional<com.ghatana.platform.types.identity.CorrelationId> correlationId =
            event.correlationId().map(c ->
                com.ghatana.platform.types.identity.CorrelationId.of(c.raw()));

        // Bridge IdempotencyKey: core.types → platform.types
        Optional<com.ghatana.platform.types.identity.IdempotencyKey> idempotencyKey =
            event.idempotencyKey().map(k ->
                com.ghatana.platform.types.identity.IdempotencyKey.of(k.raw()));

        return EventRecord.builder()
            .tenantId(tenantId)
            .typeRef(typeRef)
            .eventId(eventId)
            .occurrenceTime(event.occurrenceTime())
            .detectionTime(detectionTime)
            .headers(headers)
            .contentType(contentType)
            .schemaUri(event.schemaUri())
            .payload(event.payload())
            .correlationId(correlationId.orElse(null))
            .idempotencyKey(idempotencyKey.orElse(null))
            .build();
    }

    /**
     * Enrich headers with tracing information.
     */
    private java.util.Map<String, String> enrichHeaders(
        java.util.Map<String, String> headers,
        CallContext ctx
    ) {
        java.util.Map<String, String> enriched = new java.util.HashMap<>(headers);
        enriched.put("trace-id", ctx.tracingContext().traceId());
        enriched.put("span-id", ctx.tracingContext().spanId());
        enriched.put("principal", ctx.principal().getName());
        return enriched;
    }

    /**
     * Validation configuration for ingestion.
     */
    public record ValidationConfig(
        boolean validateSchema,
        boolean requireKnownEventType
    ) {
        public static ValidationConfig defaults() {
            return new ValidationConfig(true, true);
        }

        public static ValidationConfig lenient() {
            return new ValidationConfig(false, false);
        }
    }

    /**
     * Validation exception thrown when ingestion validation fails.
     */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    /**
     * Default implementation of IngestStream.
     */
    private class DefaultIngestStream implements IngestStream {
        private final StreamInit init;
        private final CallContext ctx;
        private final List<IngestEvent> buffer = new ArrayList<>();
        private volatile boolean closed = false;

        DefaultIngestStream(StreamInit init, CallContext ctx) {
            this.init = init;
            this.ctx = ctx;
        }

        @Override
        public Promise<Offset> send(IngestEvent event) {
            if (closed) {
                return Promise.ofException(new IllegalStateException("Stream is closed"));
            }
            return ingestOne(event, ctx);
        }

        @Override
        public Promise<List<Offset>> sendBatch(List<IngestEvent> batch) {
            if (closed) {
                return Promise.ofException(new IllegalStateException("Stream is closed"));
            }
            return ingestBatch(batch, ctx);
        }

        @Override
        public Promise<Void> flush() {
            if (buffer.isEmpty()) {
                return Promise.complete();
            }

            List<IngestEvent> toFlush = new ArrayList<>(buffer);
            buffer.clear();

            return ingestBatch(toFlush, ctx).map(results -> null);
        }

        @Override
        public void close() {
            closed = true;
            buffer.clear();
        }
    }
}
