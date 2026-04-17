package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.application.observability.TraceExportService;
import com.ghatana.datacloud.entity.observability.Span;
import com.ghatana.datacloud.entity.observability.SpanStatus;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.support.RequestContext;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outer HTTP observation wrapper for request metadata propagation and structured lifecycle logs.
 *
 * @doc.type class
 * @doc.purpose Propagates request metadata, binds MDC context, and exports request-level trace spans
 * @doc.layer product
 * @doc.pattern Middleware
 */
public final class RequestObservationFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestObservationFilter.class);
    private static final String API_PREFIX = "/api/v1/";

    private final HttpHandlerSupport httpSupport;
    private final DataCloudBusinessMetrics businessMetrics;
    private final TraceExportService traceExportService;
    private final double samplingRate;

    public RequestObservationFilter(HttpHandlerSupport httpSupport,
                                    DataCloudBusinessMetrics businessMetrics,
                                    TraceExportService traceExportService,
                                    double samplingRate) {
        this.httpSupport = httpSupport;
        this.businessMetrics = businessMetrics;
        this.traceExportService = traceExportService;
        if (samplingRate < 0.0 || samplingRate > 1.0) {
            throw new IllegalArgumentException("samplingRate must be between 0.0 and 1.0");
        }
        this.samplingRate = samplingRate;
    }

    public AsyncServlet apply(AsyncServlet delegate) {
        return request -> {
            long startNanos = System.nanoTime();
            String requestId = httpSupport.resolveCorrelationId(request);
            String tenantId = httpSupport.peekTenantId(request);
            TraceParent traceParent = parseTraceParent(request.getHeader(HttpHeaders.of("traceparent")));
            String traceId = traceParent != null ? traceParent.traceId() : requestId;
            String requestSpanId = UUID.randomUUID().toString();
            boolean sampled = traceParent != null ? traceParent.sampled() : shouldSample(requestId, samplingRate);
            String method = request.getMethod().name();
            String path = request.getPath();

            RequestMetadataAttachment metadata = new RequestMetadataAttachment(
                requestId,
                tenantId,
                traceId,
                requestSpanId,
                traceParent != null ? traceParent.parentSpanId() : null,
                method,
                path,
                sampled);
            request.attach(RequestMetadataAttachment.class, metadata);

            if (httpSupport.isStrictTenantResolution() && requiresTenant(path)) {
                if (!httpSupport.hasExplicitTenantCandidate(request)) {
                    return Promise.of(httpSupport.errorResponse(401,
                        "X-Tenant-Id header or tenantId parameter required",
                        requestId));
                }
                if (tenantId == null) {
                    return Promise.of(httpSupport.errorResponse(400,
                        "Invalid tenant identifier format",
                        requestId));
                }
            }

            RequestContext context = tenantId == null || tenantId.isBlank()
                    ? RequestContext.bindRequest(requestId, traceId, method, path)
                    : RequestContext.bind(requestId, tenantId, traceId, method, path);

            log.info("[DC-OBS] request started method={} path={} requestId={} tenantId={} traceId={}",
                    method,
                    path,
                    requestId,
                    tenantId == null ? "unknown" : tenantId,
                    traceId);

            Promise<HttpResponse> responsePromise;
            try {
                responsePromise = delegate.serve(request);
            } catch (Exception exception) {
                context.close();
                exportRequestSpan(metadata, 500, System.nanoTime() - startNanos, exception);
                return Promise.ofException(exception);
            }

            return responsePromise.whenComplete((response, error) -> {
                long durationNanos = System.nanoTime() - startNanos;
                int statusCode = response != null ? response.getCode() : 500;
                if (error == null) {
                    log.info("[DC-OBS] request completed method={} path={} requestId={} tenantId={} traceId={} status={} latencyMs={}",
                            method,
                            path,
                            requestId,
                            tenantId == null ? "unknown" : tenantId,
                            traceId,
                            statusCode,
                            durationNanos / 1_000_000L);
                } else {
                    log.warn("[DC-OBS] request failed method={} path={} requestId={} tenantId={} traceId={} latencyMs={} error={}",
                            method,
                            path,
                            requestId,
                            tenantId == null ? "unknown" : tenantId,
                            traceId,
                            durationNanos / 1_000_000L,
                            error.getMessage(),
                            error);
                }
                recordBusinessMetrics(metadata, statusCode, durationNanos / 1_000_000L);
                exportRequestSpan(metadata, statusCode, durationNanos, error);
                context.close();
            });
        };
    }

    private static boolean requiresTenant(String path) {
        if (path == null || !path.startsWith(API_PREFIX)) {
            return false;
        }
        return !path.equals("/api/v1/health")
            && !path.equals("/api/v1/health/detail")
            && !path.equals("/api/v1/health/deep")
            && !path.equals("/api/v1/ready")
            && !path.equals("/api/v1/live")
            && !path.equals("/api/v1/metrics")
            && !path.equals("/api/v1/info");
    }

    private void recordBusinessMetrics(RequestMetadataAttachment metadata, int statusCode, long durationMs) {
        String status = statusCode < 400 ? "success" : "error";
        String path = metadata.path();
        String method = metadata.method();

        if (path.startsWith("/api/v1/entities/")) {
            String collection = extractCollection(path);
            String operation = entityOperation(method, path);
            businessMetrics.recordEntityOperation(operation, collection, metadata.tenantId(), status, durationMs);
            return;
        }

        if ("POST".equals(method) && "/api/v1/events".equals(path)) {
            businessMetrics.recordEventAppend(metadata.tenantId(), status, durationMs, "append");
            return;
        }

        if (path.startsWith("/api/v1/governance/")) {
            businessMetrics.recordGovernanceOperation(governanceType(path), metadata.tenantId(), status, durationMs);
        }
    }

    private void exportRequestSpan(RequestMetadataAttachment metadata, int statusCode, long durationNanos, Throwable error) {
        if (traceExportService == null || !metadata.sampled()) {
            return;
        }

        String tenantId = metadata.tenantId() == null || metadata.tenantId().isBlank()
                ? "unknown"
                : metadata.tenantId();
        Span span = Span.builder()
                .traceId(metadata.traceId())
                .spanId(metadata.requestSpanId())
                .operationName(metadata.method().toLowerCase() + " " + metadata.path())
                .tenantId(tenantId)
                .startTime(Instant.now().minusNanos(durationNanos))
                .endTime(Instant.now())
                .status(error == null && statusCode < 500 ? SpanStatus.OK : SpanStatus.ERROR)
                .attribute("http.method", metadata.method())
                .attribute("http.path", metadata.path())
                .attribute("http.status_code", statusCode)
                .attribute("request.id", metadata.requestId())
                .attribute("tenant.id", tenantId)
                .attribute("trace.sampled", metadata.sampled())
                .build();

        if (metadata.parentSpanId() != null && !metadata.parentSpanId().isBlank()) {
            span = Span.builder()
                    .traceId(metadata.traceId())
                    .spanId(metadata.requestSpanId())
                    .parentSpanId(metadata.parentSpanId())
                    .operationName(metadata.method().toLowerCase() + " " + metadata.path())
                    .tenantId(tenantId)
                    .startTime(Instant.now().minusNanos(durationNanos))
                    .endTime(Instant.now())
                    .status(error == null && statusCode < 500 ? SpanStatus.OK : SpanStatus.ERROR)
                    .attribute("http.method", metadata.method())
                    .attribute("http.path", metadata.path())
                    .attribute("http.status_code", statusCode)
                    .attribute("request.id", metadata.requestId())
                    .attribute("tenant.id", tenantId)
                    .attribute("trace.sampled", metadata.sampled())
                    .build();
        }

        traceExportService.exportSpans(tenantId, List.of(span)).whenException(exception ->
                log.debug("request span export failed for requestId={}: {}", metadata.requestId(), exception.getMessage()));
    }

    private static boolean shouldSample(String seed, double rate) {
        if (rate <= 0.0) {
            return false;
        }
        if (rate >= 1.0) {
            return true;
        }
        long bucket = Integer.toUnsignedLong(seed.hashCode()) % 10_000L;
        long threshold = Math.round(rate * 10_000L);
        return bucket < threshold;
    }

    private static TraceParent parseTraceParent(String traceparentHeader) {
        if (traceparentHeader == null || traceparentHeader.isBlank()) {
            return null;
        }
        String[] parts = traceparentHeader.split("-");
        if (parts.length < 4 || parts[1].isBlank() || parts[2].isBlank()) {
            return null;
        }
        boolean sampled = false;
        try {
            sampled = (Integer.parseInt(parts[3], 16) & 0x01) == 0x01;
        } catch (NumberFormatException ignored) {
            sampled = false;
        }
        return new TraceParent(parts[1], parts[2], sampled);
    }

    private static String extractCollection(String path) {
        String[] parts = path.split("/");
        return parts.length > 4 ? parts[4] : "unknown";
    }

    private static String entityOperation(String method, String path) {
        if (path.endsWith("/batch")) {
            return "POST".equals(method) ? "batch_save" : "batch_delete";
        }
        if (path.endsWith("/search")) {
            return "search";
        }
        if (path.endsWith("/export")) {
            return "export";
        }
        if (path.endsWith("/anomalies")) {
            return "anomaly_detect";
        }
        if (path.endsWith("/validate") || path.endsWith("/validate/batch")) {
            return "validate";
        }
        if (path.endsWith("/history")) {
            return "history";
        }
        String[] parts = path.split("/");
        if (parts.length == 5) {
            return switch (method) {
                case "POST" -> "save";
                case "GET" -> "query";
                default -> method.toLowerCase();
            };
        }
        return switch (method) {
            case "GET" -> "get";
            case "DELETE" -> "delete";
            default -> method.toLowerCase();
        };
    }

    private static String governanceType(String path) {
        return path.substring("/api/v1/governance/".length()).replace('/', '_');
    }

    private record TraceParent(String traceId, String parentSpanId, boolean sampled) {
    }
}