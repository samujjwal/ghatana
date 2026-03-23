package com.ghatana.eventprocessing.observability;

import com.ghatana.platform.observability.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Observability handler for gRPC service operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Centralizes metrics collection, structured logging, and context management
 * for gRPC service endpoints (pattern RPC, pipeline RPC, metadata operations).
 * Provides request/response tracking with tenant isolation and distributed
 * tracing support for gRPC calls.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * GrpcServiceObservability obs = new GrpcServiceObservability(metricsCollector);
 * obs.recordRpcCallStart("tenant-123", "CreatePattern", "user-1");
 * try {
 *   // gRPC service logic
 *   obs.recordRpcCallSuccess("CreatePattern", 150);
 * } catch (Exception e) {
 *   obs.recordRpcCallError("CreatePattern", e);
 * } finally {
 *   obs.clearGrpcContext();
 * }
 * }</pre>
 *
 * <p>
 * <b>Metrics Emitted</b><br>
 * - aep.grpc.call.count (tags: tenant_id, rpc_method, status) - gRPC call count
 * - aep.grpc.call.errors (tags: tenant_id, rpc_method, error_type) - gRPC call
 * failures - aep.grpc.call.latency (tags: tenant_id, rpc_method, status) - RPC
 * processing time - aep.grpc.message.size.request (tags: tenant_id, rpc_method)
 * - Request message size (bytes) - aep.grpc.message.size.response (tags:
 * tenant_id, rpc_method, status) - Response message size (bytes) -
 * aep.grpc.stream.events (tags: tenant_id, rpc_method) - For streaming calls,
 * number of events processed
 *
 * <p>
 * <b>Logging</b><br>
 * Uses SLF4J with Log4j2 backend. MDC context includes: - tenantId: Tenant
 * identifier - grpcMethod: gRPC method name (e.g., "CreatePattern") -
 * grpcStatus: gRPC status code (OK, INVALID_ARGUMENT, NOT_FOUND, etc.) -
 * userId: User making request (optional) - requestId: Unique request identifier
 * for tracing - traceId: Distributed trace identifier
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe via MetricsCollector abstraction. MDC is thread-local.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * This is an observability adapter for gRPC layer. Consumed by
 * PipelineRegistryGrpcService and other gRPC services. Integrates with
 * core/observability abstractions and gRPC interceptors for context
 * propagation.
 *
 * @see MetricsCollector
 * @see PipelineRegistryGrpcService
 * @see ServerAuthInterceptor
 *
 * @doc.type class
 * @doc.purpose Centralized observability handler for gRPC service operations
 * @doc.layer product
 * @doc.pattern Observability Adapter
 */
@Slf4j
@RequiredArgsConstructor
public class GrpcServiceObservability {

    private final MetricsCollector metricsCollector;

    private static final String GRPC_LAYER = "grpc";

    // ==================== gRPC Call Lifecycle ====================
    /**
     * Records the start of a gRPC call.
     *
     * <p>
     * Sets MDC context for distributed tracing across async boundaries. Must be
     * paired with recordRpcCallSuccess() or recordRpcCallError().
     *
     * @param tenantId the tenant identifier
     * @param rpcMethod the gRPC method name (e.g., "CreatePattern")
     * @param userId the user making the call (optional, nullable)
     */
    public void recordRpcCallStart(String tenantId, String rpcMethod, String userId) {
        setGrpcContext(tenantId, rpcMethod, userId);
        log.debug(
                "gRPC call started: method={}, tenantId={}",
                rpcMethod, tenantId);
    }

    /**
     * Records successful gRPC call completion.
     *
     * @param tenantId the tenant identifier
     * @param rpcMethod the gRPC method name
     * @param durationMs the time taken to process call in milliseconds
     */
    public void recordRpcCallSuccess(String tenantId, String rpcMethod, long durationMs) {
        metricsCollector.incrementCounter(
                "aep.grpc.call.count",
                "tenant_id", tenantId,
                "rpc_method", rpcMethod,
                "status", "OK");
        metricsCollector.recordTimer(
                "aep.grpc.call.latency",
                durationMs,
                "tenant_id", tenantId,
                "rpc_method", rpcMethod,
                "status", "OK");

        log.info(
                "gRPC call succeeded: method={}, duration={}ms",
                rpcMethod, durationMs);
    }

    /**
     * Records failed gRPC call.
     *
     * @param tenantId the tenant identifier
     * @param rpcMethod the gRPC method name
     * @param error the exception that occurred
     */
    public void recordRpcCallError(String tenantId, String rpcMethod, Exception error) {
        String errorType = error.getClass().getSimpleName();
        metricsCollector.incrementCounter(
                "aep.grpc.call.errors",
                "tenant_id", tenantId,
                "rpc_method", rpcMethod,
                "error_type", errorType);

        log.warn(
                "gRPC call failed: method={}, error={}",
                rpcMethod, errorType, error);
    }

    /**
     * Records specific gRPC status code for failed call.
     *
     * @param tenantId the tenant identifier
     * @param rpcMethod the gRPC method name
     * @param statusCode the gRPC status code (INVALID_ARGUMENT, NOT_FOUND,
     * etc.)
     * @param errorMessage the error message from gRPC status
     */
    public void recordRpcCallStatus(
            String tenantId, String rpcMethod, String statusCode, String errorMessage) {
        metricsCollector.incrementCounter(
                "aep.grpc.call.count",
                "tenant_id", tenantId,
                "rpc_method", rpcMethod,
                "status", statusCode);

        log.warn(
                "gRPC call returned status {}: method={}, message={}",
                statusCode, rpcMethod, errorMessage);
    }

    // ==================== Message Size Tracking ====================
    /**
     * Records request message size.
     *
     * @param tenantId the tenant identifier
     * @param rpcMethod the gRPC method name
     * @param sizeBytes the request message size in bytes
     */
    public void recordRequestMessageSize(String tenantId, String rpcMethod, long sizeBytes) {
        metricsCollector.getMeterRegistry().gauge(
                "aep.grpc.message.size.request",
                io.micrometer.core.instrument.Tags.of(
                        "tenant_id", tenantId,
                        "rpc_method", rpcMethod),
                sizeBytes);
    }

    /**
     * Records response message size.
     *
     * @param tenantId the tenant identifier
     * @param rpcMethod the gRPC method name
     * @param sizeBytes the response message size in bytes
     */
    public void recordResponseMessageSize(
            String tenantId, String rpcMethod, long sizeBytes) {
        metricsCollector.getMeterRegistry().gauge(
                "aep.grpc.message.size.response",
                io.micrometer.core.instrument.Tags.of(
                        "tenant_id", tenantId,
                        "rpc_method", rpcMethod),
                sizeBytes);
    }

    // ==================== Streaming Operations ====================
    /**
     * Records streaming call event count.
     *
     * <p>
     * For server-streaming or client-streaming RPCs, records number of events
     * processed in the stream.
     *
     * @param tenantId the tenant identifier
     * @param rpcMethod the gRPC method name
     * @param eventCount the number of events/messages in stream
     */
    public void recordStreamingEventCount(String tenantId, String rpcMethod, long eventCount) {
        metricsCollector.getMeterRegistry().gauge(
                "aep.grpc.stream.events",
                io.micrometer.core.instrument.Tags.of(
                        "tenant_id", tenantId,
                        "rpc_method", rpcMethod),
                eventCount);

        log.info(
                "gRPC streaming call processed {} events for method={}",
                eventCount, rpcMethod);
    }

    /**
     * Records streaming call completion with summary stats.
     *
     * @param tenantId the tenant identifier
     * @param rpcMethod the gRPC method name
     * @param eventCount the total events processed
     * @param durationMs the total duration in milliseconds
     * @param errorCount the number of errors during streaming
     */
    public void recordStreamingCallComplete(
            String tenantId, String rpcMethod, long eventCount, long durationMs, int errorCount) {
        metricsCollector.recordTimer(
                "aep.grpc.stream.latency",
                durationMs,
                "tenant_id", tenantId,
                "rpc_method", rpcMethod);
        metricsCollector.incrementCounter(
                "aep.grpc.stream.events",
                "tenant_id", tenantId,
                "rpc_method", rpcMethod);

        if (errorCount > 0) {
            metricsCollector.incrementCounter(
                    "aep.grpc.stream.errors",
                    "tenant_id", tenantId,
                    "rpc_method", rpcMethod,
                    "error_count", String.valueOf(errorCount));
        }

        log.info(
                "gRPC streaming completed: method={}, events={}, duration={}ms, errors={}",
                rpcMethod, eventCount, durationMs, errorCount);
    }

    // ==================== Pattern-Specific RPCs ====================
    /**
     * Records pattern RPC operation (register, activate, deactivate).
     *
     * @param tenantId the tenant identifier
     * @param operation the operation type (register, activate, deactivate)
     * @param durationMs the operation time in milliseconds
     * @param statusCode the gRPC status
     */
    public void recordPatternRpcOperation(
            String tenantId, String operation, long durationMs, String statusCode) {
        metricsCollector.incrementCounter(
                "aep.grpc.pattern.operations",
                "tenant_id", tenantId,
                "operation", operation,
                "status", statusCode);
        metricsCollector.recordTimer(
                "aep.grpc.pattern.operation.latency",
                durationMs,
                "tenant_id", tenantId,
                "operation", operation);

        log.info(
                "Pattern RPC operation: operation={}, status={}, duration={}ms",
                operation, statusCode, durationMs);
    }

    // ==================== Pipeline-Specific RPCs ====================
    /**
     * Records pipeline RPC operation (create, update, deploy, undeploy).
     *
     * @param tenantId the tenant identifier
     * @param operation the operation type (create, update, deploy, undeploy)
     * @param durationMs the operation time in milliseconds
     * @param statusCode the gRPC status
     */
    public void recordPipelineRpcOperation(
            String tenantId, String operation, long durationMs, String statusCode) {
        metricsCollector.incrementCounter(
                "aep.grpc.pipeline.operations",
                "tenant_id", tenantId,
                "operation", operation,
                "status", statusCode);
        metricsCollector.recordTimer(
                "aep.grpc.pipeline.operation.latency",
                durationMs,
                "tenant_id", tenantId,
                "operation", operation);

        log.info(
                "Pipeline RPC operation: operation={}, status={}, duration={}ms",
                operation, statusCode, durationMs);
    }

    // ==================== Authentication & Authorization ====================
    /**
     * Records authentication check result for gRPC call.
     *
     * @param tenantId the tenant identifier
     * @param rpcMethod the gRPC method name
     * @param authenticated whether authentication passed
     * @param reason the reason if failed (optional)
     */
    public void recordAuthenticationResult(
            String tenantId, String rpcMethod, boolean authenticated, String reason) {
        if (!authenticated) {
            metricsCollector.incrementCounter(
                    "aep.grpc.auth.failures",
                    "tenant_id", tenantId,
                    "rpc_method", rpcMethod,
                    "reason", reason);
            log.warn(
                    "gRPC authentication failed: method={}, reason={}",
                    rpcMethod, reason);
        }
    }

    // ==================== MDC Context Management ====================
    /**
     * Sets gRPC context for MDC and distributed tracing.
     *
     * @param tenantId the tenant identifier
     * @param rpcMethod the gRPC method name
     * @param userId the user making call (optional)
     */
    private void setGrpcContext(String tenantId, String rpcMethod, String userId) {
        MDC.put("layer", GRPC_LAYER);
        MDC.put("tenantId", tenantId);
        MDC.put("grpcMethod", rpcMethod);
        if (userId != null && !userId.isEmpty()) {
            MDC.put("userId", userId);
        }
    }

    /**
     * Clears gRPC context from MDC. Must be called in finally block to prevent
     * context leakage.
     */
    public void clearGrpcContext() {
        MDC.remove("layer");
        MDC.remove("tenantId");
        MDC.remove("grpcMethod");
        MDC.remove("userId");
        MDC.remove("grpcStatus");
    }
}
