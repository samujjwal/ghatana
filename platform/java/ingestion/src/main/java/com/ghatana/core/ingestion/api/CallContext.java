package com.ghatana.core.ingestion.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.domain.auth.TenantId;

import java.util.Objects;

/**
 * Immutable call context combining tenant, authentication, and distributed tracing for ingestion operations.
 *
 * <p><b>Purpose</b><br>
 * Provides request-scoped context propagated through all ingestion pipeline stages (validation,
 * authorization, enrichment, persistence). Ensures tenant isolation, principal authentication,
 * and distributed tracing correlation. Every ingestion operation MUST have a CallContext.
 *
 * <p><b>Architecture Role</b><br>
 * Core context value object used throughout ingestion pipeline for security, multi-tenancy,
 * and observability. Part of {@code core/ingestion/api} for platform-wide ingestion context.
 * Composes canonical types from {@code core/types}, {@code core/governance}, and {@code core/ingestion}.
 *
 * <p><b>Call Context Features</b><br>
 * <ul>
 *   <li>Tenant isolation: {@link TenantId} for multi-tenant data segregation</li>
 *   <li>Authentication: {@link Principal} for user/service identity and permissions</li>
 *   <li>Distributed tracing: {@link TracingContext} for request correlation (W3C Trace Context)</li>
 * @doc.type record
 * @doc.purpose Immutable request-scoped context for ingestion pipeline with tenant and tracing
 * @doc.layer core
 * @doc.pattern Value Object, Context Object
 *
 *   <li>Validation: Enforces tenant consistency (principal tenantId must match CallContext tenantId)</li>
 *   <li>Immutability: Java record (immutable value object)</li>
 *   <li>Non-null: All fields required (fails fast on null)</li>
 * </ul>
 *
 * <p><b>Usage Examples</b><br>
 *
 * <p><b>Example 1: Basic Call Context Creation</b>
 * <pre>{@code
 * // Create tenant and principal
 * TenantId tenantId = TenantId.of("tenant-123");
 * Principal principal = Principal.user("user-456", tenantId);
 *
 * // Create call context with auto-generated tracing
 * CallContext ctx = CallContext.of(tenantId, principal);
 *
 * // Use in ingestion
 * ingestionService.ingestOne(event, ctx);
 * }</pre>
 *
 * <p><b>Example 2: Service Account Context</b>
 * <pre>{@code
 * TenantId tenantId = TenantId.of("tenant-production");
 * Principal serviceAccount = Principal.serviceAccount("kafka-connector", tenantId)
 *     .withEventTypeAccess("user.*", "payment.*");
 *
 * CallContext ctx = CallContext.of(tenantId, serviceAccount);
 *
 * // Service account has limited event type access
 * ingestionService.ingestOne(userEvent, ctx);  // Allowed
 * }</pre>
 *
 * <p><b>Example 3: Distributed Tracing Propagation</b>
 * <pre>{@code
 * // Extract tracing from HTTP headers (W3C Trace Context)
 * String traceId = request.getHeader("traceparent").split("-")[1];
 * String parentSpanId = request.getHeader("traceparent").split("-")[2];
 *
 * // Create child span for this operation
 * String spanId = UUID.randomUUID().toString();
 * TracingContext tracing = new TracingContext(traceId, spanId, Optional.of(parentSpanId));
 *
 * // Create context with explicit tracing
 * CallContext ctx = new CallContext(tenantId, principal, tracing);
 *
 * // Tracing context propagates through entire ingestion pipeline
 * ingestionService.ingestOne(event, ctx);
 * // → Validation (span: validation-xxx, parent: spanId)
 * // → Authorization (span: authz-xxx, parent: spanId)
 * // → EventCloud append (span: append-xxx, parent: spanId)
 * }</pre>
 *
 * <p><b>Example 4: Multi-Tenant Isolation (Validation)</b>
 * <pre>{@code
 * TenantId tenant1 = TenantId.of("tenant-1");
 * TenantId tenant2 = TenantId.of("tenant-2");
 *
 * // Principal for tenant-2
 * Principal principal = Principal.user("user-123", tenant2);
 *
 * // ❌ WRONG - Tenant mismatch throws exception
 * try {
 *     CallContext invalidCtx = new CallContext(tenant1, principal, tracing);
 * } catch (IllegalArgumentException e) {
 *     // "Principal tenantId mismatch: expected tenant-1, got tenant-2"
 * }
 *
 * // ✅ CORRECT - Tenant matches
 * CallContext validCtx = new CallContext(tenant2, principal, tracing);
 * }</pre>
 *
 * <p><b>Example 5: API Gateway Integration</b>
 * <pre>{@code
 * @PostMapping("/events")
 * public ResponseEntity<AppendResult> ingestEvent(
 *     @RequestHeader("X-Tenant-Id") String tenantIdStr,
 *     @RequestHeader("Authorization") String authHeader,
 *     @RequestBody IngestEvent event
 * ) {
 *     // Extract tenant from header
 *     TenantId tenantId = TenantId.of(tenantIdStr);
 *
 *     // Authenticate and get principal
 *     Principal principal = authService.authenticate(authHeader, tenantId);
 *
 *     // Create call context
 *     CallContext ctx = CallContext.of(tenantId, principal);
 *
 *     // Ingest event
 *     AppendResult result = ingestionService.ingestOne(event, ctx).get();
 *     return ResponseEntity.ok(result);
 * }
 * }</pre>
 *
 * <p><b>Example 6: Batch Ingestion Context</b>
 * <pre>{@code
 * // Single context for entire batch (same tenant + principal)
 * CallContext ctx = CallContext.of(tenantId, principal);
 *
 * // All events in batch must have same tenant as context
 * List<IngestEvent> batch = events.stream()
 *     .filter(e -> e.tenantId().equals(ctx.tenantId()))
 *     .collect(Collectors.toList());
 *
 * // Ingest batch with shared context
 * List<AppendResult> results = ingestionService.ingestBatch(batch, ctx).get();
 * }</pre>
 *
 * <p><b>Call Context Composition</b><br>
 * <ul>
 *   <li>TenantId: From {@code core/types} - canonical tenant identifier</li>
 *   <li>Principal: From {@code core/governance/security} - authenticated identity with permissions</li>
 *   <li>TracingContext: From {@code core/ingestion} - W3C Trace Context for distributed tracing</li>
 * </ul>
 *
 * <p><b>Validation Rules</b><br>
 * <ul>
 *   <li>All fields required (non-null)</li>
 *   <li>Principal tenantId MUST match CallContext tenantId (throws IllegalArgumentException)</li>
 *   <li>TracingContext traceId/spanId must be valid UUIDs or hex strings</li>
 * </ul>
 *
 * <p><b>Factory Methods</b><br>
 * <ul>
 *   <li>{@code CallContext.of(tenantId, principal)}: Auto-generates new trace (root span)</li>
 *   <li>{@code new CallContext(tenantId, principal, tracing)}: Explicit tracing (propagate from upstream)</li>
 * </ul>
 *
 * <p><b>Best Practices</b><br>
 * <ul>
 *   <li>Use factory method: {@code CallContext.of()} for most cases (auto-generates tracing)</li>
 *   <li>Propagate tracing: Extract from HTTP headers (traceparent) for distributed tracing</li>
 *   <li>Tenant isolation: Always validate tenant match before ingestion</li>
 *   <li>Service accounts: Use Principal.serviceAccount() for system integrations</li>
 *   <li>Single context per request: Don't reuse CallContext across different requests</li>
 *   <li>Log context: Include tenantId and traceId in log MDC for correlation</li>
 * </ul>
 *
 * <p><b>Anti-Patterns (Avoid)</b><br>
 * <ul>
 *   <li>❌ Null fields: All fields required (fails fast)</li>
 *   <li>❌ Tenant mismatch: Principal tenantId must equal CallContext tenantId</li>
 *   <li>❌ Reuse across requests: Create new CallContext per request</li>
 *   <li>❌ Missing tracing: Always propagate or generate tracing context</li>
 *   <li>❌ Manual construction: Use factory method unless propagating tracing</li>
 * </ul>
 *
 * <p><b>Integration with Observability</b><br>
 * <pre>{@code
 * // Add tracing context to MDC for log correlation
 * MDC.put("tenantId", ctx.tenantId().toString());
 * MDC.put("traceId", ctx.tracingContext().traceId());
 * MDC.put("spanId", ctx.tracingContext().spanId());
 *
 * logger.info("Ingesting event");
 * // Log: [tenantId=tenant-123] [traceId=abc-123] [spanId=def-456] Ingesting event
 *
 * // Emit metrics with tenant tag
 * metrics.incrementCounter("events.ingested", "tenant", ctx.tenantId().toString());
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record. Safe for concurrent use and thread-local storage.
 *
 * @param tenantId Tenant identifier for multi-tenant isolation (required, non-null)
 * @param principal Authenticated user/service identity with permissions (required, non-null)
 * @param tracingContext Distributed tracing context for request correlation (required, non-null)
 * @see TenantId
 * @see Principal
 * @see TracingContext
 * @see IngestionService
 * @doc.type record
 * @doc.purpose Call context for tenant isolation, authentication, and tracing
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record CallContext(
    TenantId tenantId,
    Principal principal,
    TracingContext tracingContext
) {
    public CallContext {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(principal, "principal required");
        Objects.requireNonNull(tracingContext, "tracingContext required");

        // Validation: principal's tenantId must match
        if (!principal.getTenantId().equals(tenantId.toString())) {
            throw new IllegalArgumentException(
                "Principal tenantId mismatch: expected " + tenantId + ", got " + principal.getTenantId()
            );
        }
    }

    /**
     * Create CallContext with default tracing context.
     */
    public static CallContext of(TenantId tenantId, Principal principal) {
        String traceId = java.util.UUID.randomUUID().toString();
        String spanId = java.util.UUID.randomUUID().toString();
        return new CallContext(tenantId, principal, TracingContext.newTrace(traceId, spanId));
    }
}
