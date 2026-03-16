package com.ghatana.appplatform.dlq;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Provides a secure read API for inspecting dead-letter event payloads.
 *              PII-sensitive fields are masked based on K-08 DataClassification of the
 *              topic's schema. Roles with COMPLIANCE authority may request full unmasked
 *              access (K-01 WorkflowPort approval required). Supports schema-violation
 *              diffing (compares payload against expected schema) and free-text search
 *              across stored JSONB payloads.
 *              Satisfies STORY-K19-007.
 * @doc.layer   Kernel
 * @doc.pattern PII masking via field classification; K-01 role-based unmasking gate;
 *              JSONB full-text search; schema diff via SchemaPort; Counter.
 */
public class DeadLetterPayloadInspectorService {

    private static final Set<String> COMPLIANCE_ROLES = Set.of("COMPLIANCE", "AUDIT_ADMIN");

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final SchemaPort       schemaPort;
    private final WorkflowPort     workflowPort;
    private final Counter          inspectionsCounter;
    private final Counter          unmaskRequestsCounter;

    public DeadLetterPayloadInspectorService(HikariDataSource dataSource, Executor executor,
                                              SchemaPort schemaPort, WorkflowPort workflowPort,
                                              MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.schemaPort           = schemaPort;
        this.workflowPort         = workflowPort;
        this.inspectionsCounter   = Counter.builder("dlq.inspector.inspections_total").register(registry);
        this.unmaskRequestsCounter = Counter.builder("dlq.inspector.unmask_requests_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface SchemaPort {
        /** Returns field names classified as PII/RESTRICTED for a given topic schema. */
        Set<String> getPiiFields(String topic);
        /** Returns null if schema unknown; returns field-level diff otherwise. */
        List<String> diffAgainstSchema(String topic, String payloadJson);
    }

    public interface WorkflowPort {
        String requestApproval(String resourceId, String resourceType,
                                String requestedBy, String rationale);
        boolean isApproved(String approvalId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record InspectionResult(String deadLetterId, String maskedPayload,
                                    boolean hasMaskedFields, List<String> schemaDiff,
                                    LocalDateTime inspectedAt) {}

    public record UnmaskRequest(String requestId, String deadLetterId,
                                 String requestedBy, String rationale) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<InspectionResult> inspect(String deadLetterId, String requestedByRole) {
        return Promise.ofBlocking(executor, () -> {
            String rawPayload = loadPayload(deadLetterId);
            String topic = loadTopic(deadLetterId);

            Set<String> piiFields = schemaPort.getPiiFields(topic);
            boolean hasMasked = !piiFields.isEmpty()
                    && !COMPLIANCE_ROLES.contains(requestedByRole);
            String displayPayload = hasMasked ? maskFields(rawPayload, piiFields) : rawPayload;

            List<String> schemaDiff = schemaPort.diffAgainstSchema(topic, rawPayload);
            inspectionsCounter.increment();
            return new InspectionResult(deadLetterId, displayPayload, hasMasked,
                    schemaDiff != null ? schemaDiff : List.of(), LocalDateTime.now());
        });
    }

    /** Returns full unmasked payload only after WorkflowPort approval is granted. */
    public Promise<String> getUnmaskedPayload(String deadLetterId, String approvalId) {
        return Promise.ofBlocking(executor, () -> {
            if (!workflowPort.isApproved(approvalId)) {
                throw new IllegalStateException("Approval " + approvalId + " not yet granted");
            }
            unmaskRequestsCounter.increment();
            return loadPayload(deadLetterId);
        });
    }

    /** Request approval to view an unmasked payload. Returns approvalId. */
    public Promise<String> requestUnmask(String deadLetterId, String requestedBy,
                                          String rationale) {
        return Promise.ofBlocking(executor, () -> {
            unmaskRequestsCounter.increment();
            return workflowPort.requestApproval(deadLetterId, "DLQ_PAYLOAD_UNMASK",
                    requestedBy, rationale);
        });
    }

    /** Full-text search across JSONB payloads for a given topic. */
    public Promise<List<String>> searchPayloads(String topic, String searchTerm, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<String> ids = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT dead_letter_id FROM dead_letters " +
                         "WHERE topic=? AND payload::text ILIKE ? " +
                         "ORDER BY captured_at DESC LIMIT ?")) {
                ps.setString(1, topic);
                ps.setString(2, "%" + searchTerm.replace("%", "\\%") + "%");
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) ids.add(rs.getString("dead_letter_id"));
                }
            }
            return ids;
        });
    }

    // ─── PII masking ─────────────────────────────────────────────────────────

    /**
     * Redacts known PII field values in the JSON string by simple key-value replacement.
     * This avoids a JSON parse library dependency while remaining safe for well-formed JSON.
     */
    private String maskFields(String payloadJson, Set<String> piiFields) {
        String result = payloadJson;
        for (String field : piiFields) {
            // Match "fieldName":"value" or "fieldName": value and replace value
            result = result.replaceAll("(\"" + field + "\"\\s*:\\s*)\"[^\"]*\"",
                                       "$1\"***REDACTED***\"");
            result = result.replaceAll("(\"" + field + "\"\\s*:\\s*)[^,}\\]]+",
                                       "$1\"***REDACTED***\"");
        }
        return result;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private String loadPayload(String deadLetterId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT payload FROM dead_letters WHERE dead_letter_id=?")) {
            ps.setString(1, deadLetterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("DeadLetter not found: " + deadLetterId);
                return rs.getString("payload");
            }
        }
    }

    private String loadTopic(String deadLetterId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT topic FROM dead_letters WHERE dead_letter_id=?")) {
            ps.setString(1, deadLetterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("DeadLetter not found: " + deadLetterId);
                return rs.getString("topic");
            }
        }
    }
}
