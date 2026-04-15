package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.application.observability.TraceExportService;
import com.ghatana.datacloud.entity.observability.Span;
import com.ghatana.datacloud.entity.observability.SpanStatus;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared helper for exporting handler and downstream child spans.
 *
 * @doc.type class
 * @doc.purpose Creates and exports request-child spans for launcher handlers
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class TraceSpanSupport {

    private static final Logger log = LoggerFactory.getLogger(TraceSpanSupport.class);
    private static final TraceSpanSupport DISABLED = new TraceSpanSupport(null);

    private final TraceExportService traceExportService;

    public TraceSpanSupport(TraceExportService traceExportService) {
        this.traceExportService = traceExportService;
    }

    public static TraceSpanSupport disabled() {
        return DISABLED;
    }

    public String requestSpanId(HttpRequest request) {
        RequestMetadataAttachment metadata = request.getAttachment(RequestMetadataAttachment.class);
        return metadata != null ? metadata.requestSpanId() : null;
    }

    public TraceSpanScope startSpan(HttpRequest request,
                                    String tenantId,
                                    String operationName,
                                    String parentSpanId,
                                    Map<String, Object> attributes) {
        RequestMetadataAttachment metadata = request.getAttachment(RequestMetadataAttachment.class);
        if (traceExportService == null || metadata == null || !metadata.sampled()) {
            return TraceSpanScope.disabled();
        }

        String resolvedTenantId = tenantId != null && !tenantId.isBlank()
                ? tenantId
                : metadata.tenantId() != null && !metadata.tenantId().isBlank()
                    ? metadata.tenantId()
                    : "unknown";

        LinkedHashMap<String, Object> spanAttributes = new LinkedHashMap<>();
        spanAttributes.put("request.id", metadata.requestId());
        spanAttributes.put("tenant.id", resolvedTenantId);
        spanAttributes.put("http.method", metadata.method());
        spanAttributes.put("http.path", metadata.path());
        mergeAttributes(spanAttributes, attributes);

        return new TraceSpanScope(
                true,
                resolvedTenantId,
                metadata.traceId(),
                UUID.randomUUID().toString(),
                normalize(parentSpanId),
                operationName,
                Instant.now(),
                spanAttributes);
    }

    public <T> Promise<T> trace(HttpRequest request,
                                String tenantId,
                                String operationName,
                                String parentSpanId,
                                Map<String, Object> attributes,
                                Supplier<Promise<T>> work) {
        TraceSpanScope spanScope = startSpan(request, tenantId, operationName, parentSpanId, attributes);
        Promise<T> promise;
        try {
            promise = work.get();
        } catch (RuntimeException error) {
            finish(spanScope, null, error);
            throw error;
        }
        return promise.whenComplete((result, error) -> finish(spanScope, result, error));
    }

    public void finish(TraceSpanScope spanScope, Object result, Throwable error) {
        if (!spanScope.enabled()) {
            return;
        }

        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>(spanScope.attributes());
        Integer statusCode = null;
        if (result instanceof HttpResponse response) {
            statusCode = response.getCode();
            attributes.put("http.status_code", statusCode);
        }
        if (error != null) {
            attributes.put("error.type", error.getClass().getSimpleName());
            if (error.getMessage() != null && !error.getMessage().isBlank()) {
                attributes.put("error.message", error.getMessage());
            }
        }

        SpanStatus status = error == null && (statusCode == null || statusCode < 500)
                ? SpanStatus.OK
                : SpanStatus.ERROR;

        Span.Builder builder = Span.builder()
                .traceId(spanScope.traceId())
                .spanId(spanScope.spanId())
                .operationName(spanScope.operationName())
                .tenantId(spanScope.tenantId())
                .startTime(spanScope.startTime())
                .endTime(Instant.now())
                .status(status);
        if (spanScope.parentSpanId() != null) {
            builder.parentSpanId(spanScope.parentSpanId());
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            builder.attribute(entry.getKey(), entry.getValue());
        }

        traceExportService.exportSpans(spanScope.tenantId(), List.of(builder.build())).whenException(exception ->
                log.debug("child span export failed for operation={}: {}",
                        spanScope.operationName(),
                        exception.getMessage()));
    }

    private static void mergeAttributes(Map<String, Object> target, Map<String, Object> attributes) {
        if (attributes == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() != null) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static final class TraceSpanScope {
        private static final TraceSpanScope DISABLED = new TraceSpanScope(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of());

        private final boolean enabled;
        private final String tenantId;
        private final String traceId;
        private final String spanId;
        private final String parentSpanId;
        private final String operationName;
        private final Instant startTime;
        private final Map<String, Object> attributes;

        private TraceSpanScope(boolean enabled,
                               String tenantId,
                               String traceId,
                               String spanId,
                               String parentSpanId,
                               String operationName,
                               Instant startTime,
                               Map<String, Object> attributes) {
            this.enabled = enabled;
            this.tenantId = tenantId;
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.operationName = operationName;
            this.startTime = startTime;
            this.attributes = attributes;
        }

        public static TraceSpanScope disabled() {
            return DISABLED;
        }

        public boolean enabled() {
            return enabled;
        }

        public String tenantId() {
            return tenantId;
        }

        public String traceId() {
            return traceId;
        }

        public String spanId() {
            return spanId;
        }

        public String parentSpanId() {
            return parentSpanId;
        }

        public String operationName() {
            return operationName;
        }

        public Instant startTime() {
            return startTime;
        }

        public Map<String, Object> attributes() {
            return attributes;
        }
    }
}