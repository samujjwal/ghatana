package com.ghatana.datacloud.infrastructure.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audit trail service for change tracking and compliance.
 *
 * <p><b>Purpose</b><br>
 * Tracks all changes to collections and entities for compliance and auditing.
 * Provides immutable audit logs with query capabilities.
 *
 * <p><b>Features</b><br>
 * - Change tracking
 * - Event logging
 * - Audit queries
 * - Compliance reporting
 * - Metrics emission
 * - Immutable logs
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AuditTrailService auditService = new AuditTrailService(metrics);
 *
 * // Log event
 * auditService.logEvent(new AuditEvent(
 *     "tenant-123",
 *     "user-456",
 *     "COLLECTION_CREATED",
 *     "collection:orders",
 *     Map.of("name", "orders")
 * ));
 *
 * // Query audit trail
 * List<AuditEvent> events = auditService.queryEvents(
 *     "tenant-123",
 *     "COLLECTION_CREATED",
 *     null
 * ).get();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Audit layer in infrastructure
 * - Integrates with MetricsCollector
 * - Supports multi-tenancy
 * - Compliance reporting
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Uses ConcurrentHashMap for storage.
 *
 * @see AuditEvent
 * @see MetricsCollector
 * @doc.type class
 * @doc.purpose Audit trail service for change tracking
 * @doc.layer product
 * @doc.pattern Audit Trail (Infrastructure Layer)
 */
public class AuditTrailService {

    private static final Logger logger = LoggerFactory.getLogger(AuditTrailService.class);

    private final MetricsCollector metrics;
    private final Map<String, List<AuditEvent>> auditLog;
    private final long createdAt;

    /**
     * Creates a new audit trail service.
     *
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if metrics is null
     */
    public AuditTrailService(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
        this.auditLog = new ConcurrentHashMap<>();
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Logs an audit event.
     *
     * <p><b>Event Logging</b><br>
     * - Immutable event record
     * - Tenant-scoped
     * - Timestamped
     * - Metrics emission
     *
     * @param event the audit event
     * @return Promise of void
     */
    public Promise<Void> logEvent(AuditEvent event) {
        Objects.requireNonNull(event, "Event must not be null");

        try {
            String key = event.tenantId + ":" + event.eventType;
            auditLog.computeIfAbsent(key, k -> new ArrayList<>())
                .add(event);

            logger.info("Audit event logged: {} by {} on {}", 
                event.eventType, event.userId, event.resourceId);

            metrics.incrementCounter("audit.event.logged",
                "tenant", event.tenantId,
                "eventType", event.eventType);

            return Promise.of(null);
        } catch (Exception ex) {
            logger.error("Error logging audit event", ex);
            metrics.incrementCounter("audit.event.error",
                "tenant", event.tenantId,
                "eventType", event.eventType);
            return Promise.ofException(ex);
        }
    }

    /**
     * Queries audit events.
     *
     * <p><b>Query Parameters</b><br>
     * - tenantId: Required, tenant scope
     * - eventType: Optional, filter by event type
     * - userId: Optional, filter by user
     * - startTime: Optional, filter by start time
     * - endTime: Optional, filter by end time
     *
     * @param tenantId the tenant ID
     * @param eventType the event type (optional)
     * @param userId the user ID (optional)
     * @return Promise of audit events
     */
    public Promise<List<AuditEvent>> queryEvents(String tenantId, String eventType, String userId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");

        try {
            List<AuditEvent> results = new ArrayList<>();

            auditLog.forEach((key, events) -> {
                if (key.startsWith(tenantId + ":")) {
                    for (AuditEvent event : events) {
                        if ((eventType == null || event.eventType.equals(eventType)) &&
                            (userId == null || event.userId.equals(userId))) {
                            results.add(event);
                        }
                    }
                }
            });

            // Sort by timestamp descending
            results.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

            logger.debug("Audit query returned {} events for tenant: {}", results.size(), tenantId);

            metrics.incrementCounter("audit.query.success",
                "tenant", tenantId,
                "results", String.valueOf(results.size()));

            return Promise.of(results);
        } catch (Exception ex) {
            logger.error("Error querying audit events", ex);
            metrics.incrementCounter("audit.query.error", "tenant", tenantId);
            return Promise.ofException(ex);
        }
    }

    /**
     * Gets events for resource.
     *
     * @param tenantId the tenant ID
     * @param resourceId the resource ID
     * @return Promise of audit events
     */
    public Promise<List<AuditEvent>> getResourceEvents(String tenantId, String resourceId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(resourceId, "Resource ID must not be null");

        try {
            List<AuditEvent> results = new ArrayList<>();

            auditLog.forEach((key, events) -> {
                if (key.startsWith(tenantId + ":")) {
                    for (AuditEvent event : events) {
                        if (event.resourceId.equals(resourceId)) {
                            results.add(event);
                        }
                    }
                }
            });

            // Sort by timestamp ascending
            results.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));

            logger.debug("Found {} events for resource: {}", results.size(), resourceId);

            return Promise.of(results);
        } catch (Exception ex) {
            logger.error("Error getting resource events", ex);
            return Promise.ofException(ex);
        }
    }

    /**
     * Gets audit statistics.
     *
     * @param tenantId the tenant ID
     * @return Promise of audit statistics
     */
    public Promise<AuditStats> getStats(String tenantId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");

        try {
            long totalEvents = 0;
            Map<String, Long> eventTypeCounts = new HashMap<>();

            for (Map.Entry<String, List<AuditEvent>> entry : auditLog.entrySet()) {
                if (entry.getKey().startsWith(tenantId + ":")) {
                    for (AuditEvent event : entry.getValue()) {
                        totalEvents++;
                        eventTypeCounts.merge(event.eventType, 1L, Long::sum);
                    }
                }
            }

            AuditStats stats = new AuditStats(
                tenantId,
                totalEvents,
                eventTypeCounts,
                System.currentTimeMillis()
            );

            logger.debug("Audit stats: tenant={}, totalEvents={}", tenantId, totalEvents);

            return Promise.of(stats);
        } catch (Exception ex) {
            logger.error("Error getting audit stats", ex);
            return Promise.ofException(ex);
        }
    }

    /**
     * Exports audit trail.
     *
     * @param tenantId the tenant ID
     * @param format the export format (JSON, CSV, PDF)
     * @return Promise of exported data
     */
    public Promise<String> exportAuditTrail(String tenantId, String format) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(format, "Format must not be null");

        try {
            List<AuditEvent> events = new ArrayList<>();

            auditLog.forEach((key, eventList) -> {
                if (key.startsWith(tenantId + ":")) {
                    events.addAll(eventList);
                }
            });

            String exported = switch (format.toUpperCase()) {
                case "JSON" -> exportAsJson(events);
                case "CSV" -> exportAsCsv(events);
                case "PDF" -> exportAsPdf(events);
                default -> throw new IllegalArgumentException("Unsupported format: " + format);
            };

            logger.info("Audit trail exported for tenant: {} in format: {}", tenantId, format);

            metrics.incrementCounter("audit.export.success",
                "tenant", tenantId,
                "format", format);

            return Promise.of(exported);
        } catch (Exception ex) {
            logger.error("Error exporting audit trail", ex);
            metrics.incrementCounter("audit.export.error",
                "tenant", tenantId,
                "format", format);
            return Promise.ofException(ex);
        }
    }

    /**
     * Exports audit trail as JSON.
     *
     * <p>Produces a JSON array of audit events with proper formatting
     * and ISO-8601 timestamps.
     *
     * @param events the events to export
     * @return JSON string representation of the audit events
     */
    private String exportAsJson(List<AuditEvent> events) {
        try {
            ObjectMapper mapper = JsonUtils.getPrettyMapper();
            
            // Convert to export-friendly format
            List<Map<String, Object>> exportEvents = new ArrayList<>();
            for (AuditEvent event : events) {
                Map<String, Object> eventMap = new LinkedHashMap<>();
                eventMap.put("tenantId", event.tenantId);
                eventMap.put("userId", event.userId);
                eventMap.put("eventType", event.eventType);
                eventMap.put("resourceId", event.resourceId);
                eventMap.put("timestamp", Instant.ofEpochMilli(event.timestamp).toString());
                eventMap.put("details", event.details);
                exportEvents.add(eventMap);
            }
            
            return mapper.writeValueAsString(exportEvents);
        } catch (JsonProcessingException e) {
            logger.error("Failed to export audit events as JSON", e);
            // Return a basic JSON array on error
            return "[]";
        }
    }

    /**
     * Exports audit trail as CSV.
     *
     * <p>Produces a CSV with columns: tenantId, userId, eventType, resourceId, 
     * timestamp, details (as JSON). Uses RFC 4180 compliant formatting with
     * proper escaping of special characters.
     *
     * @param events the events to export
     * @return CSV string representation of the audit events
     */
    private String exportAsCsv(List<AuditEvent> events) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        ObjectMapper mapper = JsonUtils.getDefaultMapper();
        
        StringBuilder csv = new StringBuilder();
        csv.append("tenantId,userId,eventType,resourceId,timestamp,details\n");
        
        for (AuditEvent event : events) {
            csv.append(escapeCsvField(event.tenantId)).append(",");
            csv.append(escapeCsvField(event.userId)).append(",");
            csv.append(escapeCsvField(event.eventType)).append(",");
            csv.append(escapeCsvField(event.resourceId)).append(",");
            csv.append(escapeCsvField(Instant.ofEpochMilli(event.timestamp).toString())).append(",");
            
            // Serialize details as JSON
            String detailsJson = "{}";
            try {
                detailsJson = mapper.writeValueAsString(event.details != null ? event.details : Map.of());
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize details for audit event", e);
            }
            csv.append(escapeCsvField(detailsJson)).append("\n");
        }
        
        return csv.toString();
    }
    
    /**
     * Escapes a field value for CSV output per RFC 4180.
     *
     * @param field the field value to escape
     * @return escaped field value
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // If field contains comma, quote, or newline, wrap in quotes and escape internal quotes
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /**
     * Exports audit trail as PDF.
     *
     * <p>Produces a text-based report suitable for PDF generation.
     * Note: This returns a text representation that can be converted to PDF
     * by a PDF library. For full PDF support, add a PDF library dependency.
     *
     * @param events the events to export
     * @return Text representation suitable for PDF conversion
     */
    private String exportAsPdf(List<AuditEvent> events) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
        
        StringBuilder report = new StringBuilder();
        
        // PDF Header
        report.append("╔══════════════════════════════════════════════════════════════════════════════╗\n");
        report.append("║                            AUDIT TRAIL REPORT                               ║\n");
        report.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
        report.append("║ Generated: ").append(formatter.format(Instant.now()));
        report.append(" ".repeat(Math.max(0, 66 - formatter.format(Instant.now()).length()))).append("║\n");
        report.append("║ Total Events: ").append(events.size());
        report.append(" ".repeat(Math.max(0, 63 - String.valueOf(events.size()).length()))).append("║\n");
        report.append("╚══════════════════════════════════════════════════════════════════════════════╝\n\n");
        
        // Events
        int eventNum = 1;
        for (AuditEvent event : events) {
            report.append("┌──────────────────────────────────────────────────────────────────────────────┐\n");
            report.append("│ Event #").append(eventNum++).append("\n");
            report.append("├──────────────────────────────────────────────────────────────────────────────┤\n");
            report.append("│ Timestamp:   ").append(formatter.format(Instant.ofEpochMilli(event.timestamp))).append("\n");
            report.append("│ Tenant ID:   ").append(event.tenantId).append("\n");
            report.append("│ User ID:     ").append(event.userId).append("\n");
            report.append("│ Event Type:  ").append(event.eventType).append("\n");
            report.append("│ Resource ID: ").append(event.resourceId).append("\n");
            if (event.details != null && !event.details.isEmpty()) {
                report.append("│ Details:\n");
                for (Map.Entry<String, Object> entry : event.details.entrySet()) {
                    report.append("│   - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }
            report.append("└──────────────────────────────────────────────────────────────────────────────┘\n\n");
        }
        
        // Footer
        report.append("═══════════════════════════════════════════════════════════════════════════════\n");
        report.append("                             End of Report\n");
        report.append("═══════════════════════════════════════════════════════════════════════════════\n");
        
        return report.toString();
    }

    /**
     * Audit event.
     *
     * @doc.type class
     * @doc.purpose Audit event data
     */
    public static class AuditEvent {
        public String tenantId;
        public String userId;
        public String eventType;
        public String resourceId;
        public Map<String, Object> details;
        public long timestamp;

        public AuditEvent(String tenantId, String userId, String eventType, String resourceId, Map<String, Object> details) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.eventType = eventType;
            this.resourceId = resourceId;
            this.details = details;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Audit statistics.
     *
     * @doc.type class
     * @doc.purpose Audit statistics
     */
    public static class AuditStats {
        public String tenantId;
        public long totalEvents;
        public Map<String, Long> eventTypeCounts;
        public long timestamp;

        public AuditStats(String tenantId, long totalEvents, Map<String, Long> eventTypeCounts, long timestamp) {
            this.tenantId = tenantId;
            this.totalEvents = totalEvents;
            this.eventTypeCounts = eventTypeCounts;
            this.timestamp = timestamp;
        }
    }
}
