package com.ghatana.yappc.infrastructure.audit;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.infrastructure.audit.AuditLogger.AuditEvent;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import com.ghatana.yappc.infrastructure.observability.tracing.TracingContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Data-Cloud backed implementation of AuditLogger.
 *
 * <p><b>Purpose</b><br>
 * Persists audit events to Data-Cloud for durability, queryability, and
 * cross-cutting security analysis. Events are scoped to tenant for isolation.
 *
 * <p><b>Features</b><br>
 * - Multi-tenant event isolation<br>
 * - Correlation ID propagation<br>
 * - Structured logging fallback<br>
 * - Metrics for observability<br>
 *
 * @doc.type class
 * @doc.purpose Data-Cloud audit logging implementation
 * @doc.layer infrastructure
 * @doc.pattern Repository, Adapter
 */
public class DataCloudAuditLogger implements AuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger(DataCloudAuditLogger.class);
    private static final String AUDIT_COLLECTION = "audit_events";

    private final DataCloudClient client;
    private final YappcEntityMapper mapper;
    private final MetricsCollector metrics;

    public DataCloudAuditLogger(
            DataCloudClient client,
            YappcEntityMapper mapper,
            MetricsCollector metrics) {
        this.client = client;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @Override
    public Promise<Void> log(AuditEvent event) {
        String tenantId = resolveTenantId(event);

        // Add correlation ID if present
        Map<String, Object> enrichedMetadata = new HashMap<>(event.metadata());
        TracingContext.getCorrelationId().ifPresent(id -> {
            enrichedMetadata.put("correlationId", id);
        });

        // Build event data
        Map<String, Object> eventData = Map.of(
            "id", event.id().toString(),
            "timestamp", event.timestamp().toString(),
            "type", event.type().toString(),
            "action", event.action(),
            "userId", event.userId(),
            "tenantId", tenantId,
            "status", event.status().toString(),
            "resourceType", event.resourceType(),
            "resourceId", event.resourceId(),
            "metadata", enrichedMetadata
        );

        LOG.debug("Audit event: {} - {} - {}", event.type(), event.action(), event.status());

        return client.save(tenantId, AUDIT_COLLECTION, eventData)
            .whenResult(v -> {
                metrics.incrementCounter("audit.event.logged",
                    "type", event.type().toString(),
                    "status", event.status().toString());
            })
            .whenException(e -> {
                LOG.error("Failed to persist audit event to Data-Cloud: {}", event.id(), e);
                metrics.incrementCounter("audit.event.failed",
                    "type", event.type().toString());
                // Still log to structured logging as fallback
                logToStructuredLogging(event, enrichedMetadata);
            })
            .toVoid();
    }

    private String resolveTenantId(AuditEvent event) {
        // Use event tenant if set, otherwise current context, otherwise "system"
        if (event.tenantId() != null && !event.tenantId().isBlank()) {
            return event.tenantId();
        }
        String contextTenant = TenantContext.getCurrentTenantId();
        if (contextTenant != null && !contextTenant.isBlank()) {
            return contextTenant;
        }
        return "system";
    }

    private void logToStructuredLogging(AuditEvent event, Map<String, Object> metadata) {
        LOG.info("AUDIT_EVENT type={} action={} user={} tenant={} status={} id={}",
            event.type(),
            event.action(),
            event.userId(),
            event.tenantId(),
            event.status(),
            event.id());
    }
}
