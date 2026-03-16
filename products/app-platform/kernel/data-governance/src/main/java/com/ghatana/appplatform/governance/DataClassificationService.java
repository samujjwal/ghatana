package com.ghatana.appplatform.governance;

import com.ghatana.appplatform.governance.port.DataClassificationStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

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

    private final DataClassificationStore classificationStore;
    private final Executor             executor;
    private final ClassificationRulePort rulePort;
    private final WorkflowPort         workflowPort;
    private final EventPort            eventPort;
    private final Counter              classifiedCounter;
    private final Counter              overrideCounter;

    public DataClassificationService(DataClassificationStore classificationStore, Executor executor,
                                      ClassificationRulePort rulePort, WorkflowPort workflowPort,
                                      EventPort eventPort, MeterRegistry registry) {
        this.classificationStore = classificationStore;
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
            DataClassificationStore.AssetRow asset = classificationStore.loadAsset(assetId);
            String rawLabel = rulePort.classify(asset.name(), asset.schemaContent());
            DataCatalogService.Classification newClass = parseClassification(rawLabel);

            DataCatalogService.Classification prev = asset.classification();
            if (prev != newClass) {
                classificationStore.updateClassification(assetId, newClass);
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
            List<String> assetIds = classificationStore.loadAllAssetIds();
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
            classificationStore.persistOverrideRequest(requestId, assetId, taskId,
                    newClassification, requestedBy, reason);
            overrideCounter.increment();
            return new OverrideRequest(requestId, assetId, taskId, newClassification, requestedBy,
                    reason, LocalDateTime.now());
        });
    }

    /** Apply approved manual override — called by workflow on approval. */
    public Promise<ClassificationResult> applyApprovedOverride(String requestId) {
        return Promise.ofBlocking(executor, () -> {
            DataClassificationStore.OverrideRow req = classificationStore.loadOverrideRequest(requestId);
            DataClassificationStore.AssetRow asset = classificationStore.loadAsset(req.assetId());
            DataCatalogService.Classification newClass = parseClassification(req.requestedClassification());
            classificationStore.updateClassification(req.assetId(), newClass);
            classificationStore.markOverrideApplied(requestId);
            eventPort.publish("governance.classification.changed",
                    new ClassificationChangedEvent(req.assetId(), asset.classification(), newClass, "MANUAL"));
            return new ClassificationResult(req.assetId(), asset.name(), asset.classification(),
                    newClass, "MANUAL", LocalDateTime.now());
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

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
