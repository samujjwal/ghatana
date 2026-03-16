package com.ghatana.appplatform.dlq;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Payload transformation for dead-letter messages before replay. Supports
 *              five transformations: FIELD_RENAME (rename a JSON key), FIELD_REMOVE (drop
 *              a field), SCHEMA_VERSION_BUMP (update schema version field to target version),
 *              ENVELOPE_UNWRAP (extract nested payload to root), and CUSTOM_SCRIPT
 *              (apply a registered named transformation). Transformations are applied via
 *              TransformPort to keep JSON manipulation out of this service.
 *              Satisfies STORY-K19-008.
 * @doc.layer   Kernel
 * @doc.pattern Transformation type enum; TransformPort delegation; audit trail per
 *              transformation; transformApplied Counter; original payload preserved.
 */
public class DeadLetterPayloadTransformationService {

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final TransformPort     transformPort;
    private final AuditPort         auditPort;
    private final Counter           transformAppliedCounter;

    public DeadLetterPayloadTransformationService(HikariDataSource dataSource, Executor executor,
                                                   TransformPort transformPort,
                                                   AuditPort auditPort,
                                                   MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.transformPort           = transformPort;
        this.auditPort               = auditPort;
        this.transformAppliedCounter = Counter.builder("dlq.transform.applied_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Applies JSON transformations; implementation is JSON-library-specific. */
    public interface TransformPort {
        String fieldRename(String payload, String oldKey, String newKey);
        String fieldRemove(String payload, String key);
        String schemaVersionBump(String payload, String versionField, String targetVersion);
        String envelopeUnwrap(String payload, String wrapperKey);
        String applyCustomScript(String payload, String scriptName);
    }

    /** K-07 audit trail. */
    public interface AuditPort {
        void log(String action, String resourceType, String resourceId, Map<String, Object> details);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum TransformationType {
        FIELD_RENAME, FIELD_REMOVE, SCHEMA_VERSION_BUMP, ENVELOPE_UNWRAP, CUSTOM_SCRIPT
    }

    public record TransformRequest(
        TransformationType type,
        String param1, // e.g. old key / field name / wrapper key / script name
        String param2  // e.g. new key / target version (null where not needed)
    ) {}

    public record TransformResult(
        String deadLetterId, String originalPayload, String transformedPayload,
        List<TransformRequest> transformsApplied, Instant transformedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Apply a list of transformations sequentially to the dead-letter message payload.
     * The original payload is preserved as a backup in the database before overwriting.
     */
    public Promise<TransformResult> applyTransformations(String deadLetterId,
                                                          List<TransformRequest> transforms,
                                                          String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            String originalPayload = fetchPayload(deadLetterId);
            String workingPayload  = originalPayload;

            for (TransformRequest transform : transforms) {
                workingPayload = applyOne(workingPayload, transform);
                transformAppliedCounter.increment();
            }

            persistTransformedPayload(deadLetterId, originalPayload, workingPayload);

            auditPort.log("DLQ_PAYLOAD_TRANSFORMED", "DeadLetter", deadLetterId,
                Map.of("transformCount", transforms.size(), "operatorId", operatorId,
                        "transformTypes", transforms.stream().map(t -> t.type().name()).toList()));

            return new TransformResult(deadLetterId, originalPayload, workingPayload,
                transforms, Instant.now());
        });
    }

    /**
     * Preview transformations without persisting — for operator verification before committing.
     */
    public Promise<String> previewTransformations(String deadLetterId,
                                                   List<TransformRequest> transforms) {
        return Promise.ofBlocking(executor, () -> {
            String payload = fetchPayload(deadLetterId);
            for (TransformRequest transform : transforms) {
                payload = applyOne(payload, transform);
            }
            return payload;
        });
    }

    /**
     * Restore the original payload (undo all transformations) for a given dead-letter message.
     */
    public Promise<Void> restoreOriginalPayload(String deadLetterId, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE dead_letters SET payload = original_payload, " +
                     "payload_transformed = FALSE WHERE dead_letter_id = ? " +
                     "AND original_payload IS NOT NULL")) {
                ps.setString(1, deadLetterId);
                ps.executeUpdate();
            }
            auditPort.log("DLQ_PAYLOAD_RESTORED", "DeadLetter", deadLetterId,
                Map.of("operatorId", operatorId));
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private String applyOne(String payload, TransformRequest t) {
        return switch (t.type()) {
            case FIELD_RENAME        -> transformPort.fieldRename(payload, t.param1(), t.param2());
            case FIELD_REMOVE        -> transformPort.fieldRemove(payload, t.param1());
            case SCHEMA_VERSION_BUMP -> transformPort.schemaVersionBump(payload, t.param1(), t.param2());
            case ENVELOPE_UNWRAP     -> transformPort.envelopeUnwrap(payload, t.param1());
            case CUSTOM_SCRIPT       -> transformPort.applyCustomScript(payload, t.param1());
        };
    }

    private String fetchPayload(String deadLetterId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT payload FROM dead_letters WHERE dead_letter_id = ?")) {
            ps.setString(1, deadLetterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("DLQ message not found: " + deadLetterId);
                return rs.getString("payload");
            }
        }
    }

    private void persistTransformedPayload(String deadLetterId, String originalPayload,
                                            String transformedPayload) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dead_letters SET payload = ?, original_payload = COALESCE(original_payload, ?), " +
                 "payload_transformed = TRUE, transformed_at = NOW() WHERE dead_letter_id = ?")) {
            ps.setString(1, transformedPayload);
            ps.setString(2, originalPayload); // only set original_payload if not already set
            ps.setString(3, deadLetterId);
            ps.executeUpdate();
        }
    }
}
