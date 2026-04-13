package com.ghatana.datacloud.application.observability;

import com.ghatana.datacloud.entity.observability.Span;
import com.ghatana.datacloud.entity.observability.SpanStatus;
import com.ghatana.datacloud.entity.observability.TraceExporter;
import com.ghatana.platform.observability.trace.SpanData;
import com.ghatana.platform.observability.trace.TraceStorage;
import io.activej.promise.Promise;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts the data-cloud {@link TraceExporter} port to the platform
 * {@link TraceStorage} abstraction (B4).
 *
 * <p>Data-cloud spans are converted to {@link SpanData} records using field
 * alignment: {@code traceId}, {@code spanId}, {@code parentSpanId},
 * {@code operationName}, {@code startTime}, {@code endTime}, {@code status},
 * and {@code attributes} (mapped to {@code tags}).
 *
 * <p>Wired at bootstrap via
 * {@code DataCloudHttpLauncherBootstrap.buildTraceExporter(env)}.
 *
 * @doc.type class
 * @doc.purpose Bridges data-cloud TraceExporter to platform TraceStorage (B4)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class ClickHouseTraceExporter implements TraceExporter {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseTraceExporter.class);

    private static final String SERVICE_NAME = "data-cloud";

    private final TraceStorage storage;

    /**
     * @param storage platform ClickHouseTraceStorage (or any TraceStorage impl)
     */
    public ClickHouseTraceExporter(TraceStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public Promise<ExportResult> exportSpans(List<Span> spans) {
        if (spans == null || spans.isEmpty()) {
            return Promise.of(new ExportResult(true, 0, 0, List.of(), 0L));
        }

        long startMs = System.currentTimeMillis();
        List<SpanData> spanDataList = spans.stream()
                .map(this::toSpanData)
                .toList();

        return storage.storeSpans(spanDataList)
                .map(ignored -> {
                    long duration = System.currentTimeMillis() - startMs;
                    log.debug("Exported {} spans to ClickHouse in {}ms", spanDataList.size(), duration);
                    return new ExportResult(true, spanDataList.size(), 0, List.of(), duration);
                })
                .then(
                        result -> Promise.of(result),
                        e -> {
                            long duration = System.currentTimeMillis() - startMs;
                            log.warn("Failed to export {} spans to ClickHouse: {}", spans.size(), e.getMessage());
                            return Promise.of(new ExportResult(false, 0, spans.size(), List.of(e.getMessage()), duration));
                        }
                );
    }

    @Override
    public ExportConfig getConfig() {
        // ClickHouse URL would come from env; return a safe default config
        return new ExportConfig("clickhouse://internal", 500, 5000L, false, 3, 500L);
    }

    @Override
    public Promise<Boolean> isHealthy() {
        return storage.isHealthy();
    }

    // ── Conversion helpers ──────────────────────────────────────────────────

    private SpanData toSpanData(Span span) {
        long durationMs = span.getEndTime() != null
                ? Duration.between(span.getStartTime(), span.getEndTime()).toMillis()
                : 0L;

        return SpanData.builder()
                .spanId(span.getSpanId())
                .traceId(span.getTraceId())
                .parentSpanId(span.getParentSpanId())
                .name(span.getOperationName())
                .serviceName(SERVICE_NAME)
                .operationName(span.getOperationName())
                .startTime(span.getStartTime())
                .endTime(span.getEndTime() != null ? span.getEndTime() : span.getStartTime())
                .durationMs(durationMs)
                .status(toStatusString(span.getStatus()))
                .tags(span.getAttributes())
                .build();
    }

    private static String toStatusString(SpanStatus status) {
        return switch (status) {
            case OK -> "OK";
            case ERROR -> "ERROR";
            default -> "UNSET";
        };
    }
}
