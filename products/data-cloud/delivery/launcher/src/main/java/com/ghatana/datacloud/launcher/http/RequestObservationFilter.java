package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.application.observability.TraceExportService;
import com.ghatana.datacloud.entity.observability.Span;
import com.ghatana.datacloud.entity.observability.SpanStatus;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.security.RequestContextResolver;
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
    private static final String DEFAULT_TENANT_ID = "default";

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
            String method = request.getMethod().name();
            String path = request.getPath();
            String resolvedTenantId = httpSupport.peekTenantId(request);
            String tenantId = resolvedTenantId;
            if (tenantId == null && !httpSupport.isStrictTenantResolution() && usesDefaultTenant(path)) {
                tenantId = DEFAULT_TENANT_ID;
            }
            final String effectiveTenantId = tenantId;
            TraceParent traceParent = parseTraceParent(request.getHeader(HttpHeaders.of("traceparent")));
            String traceId = traceParent != null ? traceParent.traceId() : newTraceId();
            String requestSpanId = newSpanId();
            boolean sampled = traceParent != null ? traceParent.sampled() : shouldSample(requestId, samplingRate);

            RequestMetadataAttachment metadata = new RequestMetadataAttachment(
                requestId,
                effectiveTenantId,
                traceId,
                requestSpanId,
                traceParent != null ? traceParent.parentSpanId() : null,
                method,
                path,
                sampled);
            request.attach(RequestMetadataAttachment.class, metadata);
            RequestTraceSupport.setCurrent(new RequestTraceSupport.TraceHeaders(
                metadata.requestId(),
                metadata.traceId(),
                metadata.requestSpanId(),
                metadata.parentSpanId(),
                metadata.sampled()));

            if (httpSupport.isStrictTenantResolution() && requiresTenant(path)) {
                RequestContextResolver.ResolutionResult resolution =
                    httpSupport.resolveRequestContextWithError(request);
                if (!resolution.isSuccess()) {
                    HttpResponse response = httpSupport.errorResponse(
                        resolution.errorCode(),
                        resolution.errorMessage(),
                        requestId);
                    RequestTraceSupport.clearCurrent();
                    return Promise.of(response);
                }
            }

            RequestContext context = effectiveTenantId == null || effectiveTenantId.isBlank()
                    ? RequestContext.bindRequest(requestId, traceId, method, path)
                    : RequestContext.bind(requestId, effectiveTenantId, traceId, method, path);

            log.info("[DC-OBS] request started method={} path={} requestId={} tenantId={} traceId={}",
                    method,
                    path,
                    requestId,
                    effectiveTenantId == null ? "unknown" : effectiveTenantId,
                    traceId);

            Promise<HttpResponse> responsePromise;
            try {
                responsePromise = delegate.serve(request);
            } catch (Exception exception) {
                context.close();
                RequestTraceSupport.clearCurrent();
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
                            effectiveTenantId == null ? "unknown" : effectiveTenantId,
                            traceId,
                            statusCode,
                            durationNanos / 1_000_000L);
                } else {
                    log.warn("[DC-OBS] request failed method={} path={} requestId={} tenantId={} traceId={} latencyMs={} error={}",
                            method,
                            path,
                            requestId,
                            effectiveTenantId == null ? "unknown" : effectiveTenantId,
                            traceId,
                            durationNanos / 1_000_000L,
                            error.getMessage(),
                            error);
                }
                recordBusinessMetrics(metadata, statusCode, durationNanos / 1_000_000L);
                exportRequestSpan(metadata, statusCode, durationNanos, error);
                context.close();
                RequestTraceSupport.clearCurrent();
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

    private static boolean usesDefaultTenant(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/v1/entities")
            || path.startsWith("/api/v1/events")
            || path.startsWith("/api/v1/analytics")
            || path.startsWith("/api/v1/brain")
            || path.startsWith("/api/v1/memory")
            || path.startsWith("/api/v1/learning")
            || path.equals("/api/v1/pipelines/draft")
            || path.matches("^/api/v1/pipelines/[^/]+/optimise-hint$")
            || path.startsWith("/api/v1/voice");
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

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String newSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
