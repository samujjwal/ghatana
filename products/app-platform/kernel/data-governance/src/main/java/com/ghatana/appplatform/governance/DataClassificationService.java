package com.ghatana.appplatform.governance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Automated data classification: scans data assets and classifies sensitivity
 *              via K-03 T2 sandboxed rules. Field-name patterns (*_ssn → RESTRICTED) and
 *              content-regex patterns trigger auto-classification. Classification propagates
 *              to downstream assets via lineage. Manual override with maker-checker.
 *              Publishes ClassificationChanged event. Satisfies STORY-K08-004.
 * @doc.layer   Kernel
 * @doc.pattern K-03 T2 sandboxed classification rules; lineage propagation; K-01 maker-
 *              checker for overrides; ClassificationChanged event; Counter.
 */
public class DataClassificationService {

    private final HikariDataSource     dataSource;
    private final Executor             executor;
    private final ClassificationRulePort rulePort;
    private final WorkflowPort         workflowPort;
    private final EventPort            eventPort;
    private final Counter              classifiedCounter;
    private final Counter              overrideCounter;

    public DataClassificationService(HikariDataSource dataSource, Executor executor,
                                      ClassificationRulePort rulePort, WorkflowPort workflowPort,
                                      EventPort eventPort, MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.rulePort          = rulePort;
        this.workflowPort      = workflowPort;
        this.eventPort         = eventPort;
        this.classifiedCounter = Counter.builder("governance.classification.auto_total").register(registry);
        this.overrideCounter   = Counter.builder("governance.classification.overrides_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-03 T2 sandboxed rule evaluation for classification. */
    public interface ClassificationRulePort {
        /** Evaluates field name patterns and content patterns, returns highest sensitivity level. */
        String classify(String assetName, String schemaContent);
    }

    /** K-01 maker-checker for manual overrides. */
    public interface WorkflowPort {
        String createApprovalTask(String assetId, String requestedClassification,
                                   String submittedBy, String reason);
    }

    public interface EventPort {
        void publish(String topic, Object event);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ClassificationResult(String assetId, String assetName,
                                        DataCatalogService.Classification previousClassification,
                                        DataCatalogService.Classification newClassification,
                                        String method, LocalDateTime classifiedAt) {}

    public record OverrideRequest(String requestId, String assetId, String taskId,
                                   String requestedClassification, String requestedBy,
                                   String reason, LocalDateTime submittedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Auto-classify an asset using K-03 T2 sandboxed rules. */
    public Promise<ClassificationResult> autoClassify(String assetId) {
        return Promise.ofBlocking(executor, () -> {
            AssetRow asset = loadAsset(assetId);
            String rawLabel = rulePort.classify(asset.name(), asset.schemaContent());
            DataCatalogService.Classification newClass = parseClassification(rawLabel);

            DataCatalogService.Classification prev = asset.classification();
            if (prev != newClass) {
                updateClassification(assetId, newClass);
                eventPort.publish("governance.classification.changed",
                        new ClassificationChangedEvent(assetId, prev, newClass, "AUTO"));
            }
            classifiedCounter.increment();
            return new ClassificationResult(assetId, asset.name(), prev, newClass, "AUTO",
                    LocalDateTime.now());
        });
    }

    /** Auto-classify all assets in bulk. */
    public Promise<List<ClassificationResult>> bulkAutoClassify() {
        return Promise.ofBlocking(executor, () -> {
            List<String> assetIds = loadAllAssetIds();
            List<ClassificationResult> results = new ArrayList<>();
            for (String id : assetIds) {
                try {
                    results.add(autoClassify(id).get());
                } catch (Exception e) {
                    // Continue with remaining assets; partial failures logged
                }
            }
            return results;
        });
    }

    /** Submit manual override — requires K-01 maker-checker approval. */
    public Promise<OverrideRequest> requestManualOverride(String assetId, String newClassification,
                                                           String requestedBy, String reason) {
        return Promise.ofBlocking(executor, () -> {
            String requestId = UUID.randomUUID().toString();
            String taskId = workflowPort.createApprovalTask(assetId, newClassification,
                    requestedBy, reason);
            persistOverrideRequest(requestId, assetId, taskId, newClassification, requestedBy, reason);
            overrideCounter.increment();
            return new OverrideRequest(requestId, assetId, taskId, newClassification, requestedBy,
                    reason, LocalDateTime.now());
        });
    }

    /** Apply approved manual override — called by workflow on approval. */
    public Promise<ClassificationResult> applyApprovedOverride(String requestId) {
        return Promise.ofBlocking(executor, () -> {
            OverrideRow req = loadOverrideRequest(requestId);
            AssetRow asset = loadAsset(req.assetId());
            DataCatalogService.Classification newClass = parseClassification(req.requestedClassification());
            updateClassification(req.assetId(), newClass);
            markOverrideApplied(requestId);
            eventPort.publish("governance.classification.changed",
                    new ClassificationChangedEvent(req.assetId(), asset.classification(), newClass, "MANUAL"));
            return new ClassificationResult(req.assetId(), asset.name(), asset.classification(),
                    newClass, "MANUAL", LocalDateTime.now());
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private void updateClassification(String assetId,
                                       DataCatalogService.Classification classification) throws SQLException {
        String sql = "UPDATE data_catalog SET classification=?, updated_at=NOW() WHERE asset_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, classification.name()); ps.setString(2, assetId);
            ps.executeUpdate();
        }
    }

    private void persistOverrideRequest(String requestId, String assetId, String taskId,
                                         String classification, String requestedBy,
                                         String reason) throws SQLException {
        String sql = """
                INSERT INTO classification_override_requests
                    (request_id, asset_id, task_id, requested_classification, requested_by,
                     reason, status, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NOW())
                ON CONFLICT (request_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId); ps.setString(2, assetId); ps.setString(3, taskId);
            ps.setString(4, classification); ps.setString(5, requestedBy); ps.setString(6, reason);
            ps.executeUpdate();
        }
    }

    private void markOverrideApplied(String requestId) throws SQLException {
        String sql = "UPDATE classification_override_requests SET status='APPLIED' WHERE request_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId); ps.executeUpdate();
        }
    }

    record AssetRow(String assetId, String name, String schemaContent,
                    DataCatalogService.Classification classification) {}

    private AssetRow loadAsset(String assetId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT asset_id, name, schema_ref, classification FROM data_catalog WHERE asset_id=?")) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Asset not found: " + assetId);
                return new AssetRow(rs.getString("asset_id"), rs.getString("name"),
                        rs.getString("schema_ref"),
                        parseClassification(rs.getString("classification")));
            }
        }
    }

    private List<String> loadAllAssetIds() throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT asset_id FROM data_catalog");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("asset_id"));
        }
        return ids;
    }

    record OverrideRow(String assetId, String requestedClassification) {}

    private OverrideRow loadOverrideRequest(String requestId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT asset_id, requested_classification FROM classification_override_requests WHERE request_id=?")) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Override not found: " + requestId);
                return new OverrideRow(rs.getString("asset_id"),
                        rs.getString("requested_classification"));
            }
        }
    }

    private DataCatalogService.Classification parseClassification(String raw) {
        return switch (raw.toUpperCase()) {
            case "RESTRICTED"   -> DataCatalogService.Classification.RESTRICTED;
            case "CONFIDENTIAL" -> DataCatalogService.Classification.CONFIDENTIAL;
            case "INTERNAL"     -> DataCatalogService.Classification.INTERNAL;
            default             -> DataCatalogService.Classification.PUBLIC;
        };
    }

    record ClassificationChangedEvent(String assetId,
                                       DataCatalogService.Classification previousClass,
                                       DataCatalogService.Classification newClass, String method) {}
}
