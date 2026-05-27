package com.ghatana.yappc.services.intent;

import com.ghatana.yappc.api.PlatformEvidence;
import com.ghatana.yappc.common.ServiceObservability;
import com.ghatana.yappc.domain.intent.IntentAnalysis;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Writes intent capture and analysis evidence through the platform evidence adapter
 * @doc.layer service
 * @doc.pattern Adapter
 */
public final class PlatformIntentEvidenceService implements IntentEvidenceService {

    private static final String LIFECYCLE_PHASE = "INTENT";
    private static final String EVIDENCE_TYPE = "INTENT_EVIDENCE";

    private final PlatformIntegrationClient platformIntegrationClient;

    /**
     * Creates a platform-backed intent evidence service.
     *
     * @param platformIntegrationClient platform evidence adapter
     */
    public PlatformIntentEvidenceService(@NotNull PlatformIntegrationClient platformIntegrationClient) {
        this.platformIntegrationClient = platformIntegrationClient;
    }

    @Override
    public Promise<String> recordCapture(@NotNull IntentInput input, @NotNull IntentSpec spec) {
        String evidenceId = "intent-capture-" + spec.id() + "-" + UUID.randomUUID();
        PlatformEvidence evidence = evidence(
                evidenceId,
                input.tenantId(),
                input.workspaceId(),
                input.projectId(),
                input.userId(),
                spec.id(),
                "intent.capture",
                "INTENT phase evidence: captured intent " + spec.id() + " for project " + input.projectId(),
                Map.of(
                        "operation", "intent.capture",
                        "intentId", spec.id(),
                        "productName", spec.productName()));
        return store(evidence);
    }

    @Override
    public Promise<String> recordAnalysis(
            @NotNull IntentSpec spec,
            @NotNull IntentAnalysis analysis,
            @NotNull Map<String, Object> groundingMetadata) {
        String evidenceId = "intent-analysis-" + spec.id() + "-" + UUID.randomUUID();
        String tenantId = spec.tenantId();
        String workspaceId = spec.metadata().get("workspaceId");
        String projectId = spec.metadata().get("projectId");
        PlatformEvidence evidence = evidence(
                evidenceId,
                tenantId,
                workspaceId,
                projectId,
                spec.metadata().get("userId"),
                spec.id(),
                "intent.analyze",
                "INTENT phase evidence: analyzed intent " + spec.id() + " with feasibility " + analysis.feasible(),
                mergeAttributes(Map.of(
                                "operation", "intent.analyze",
                                "intentId", spec.id(),
                                "feasible", String.valueOf(analysis.feasible())),
                        ServiceObservability.redactSensitiveFields(groundingMetadata)));
        return store(evidence);
    }

    private Promise<String> store(PlatformEvidence evidence) {
        boolean stored = platformIntegrationClient.storeEvidence(evidence);
        if (!stored) {
            return Promise.ofException(new IllegalStateException(
                    "Intent evidence adapter rejected evidence " + evidence.evidenceId()));
        }
        return Promise.of(evidence.evidenceId());
    }

    private static PlatformEvidence evidence(
            String evidenceId,
            String tenantId,
            String workspaceId,
            String projectId,
            String userId,
            String intentId,
            String operation,
            String content,
            Map<String, Object> attributes
    ) {
        requireText(tenantId, "tenantId");
        requireText(workspaceId, "workspaceId");
        requireText(projectId, "projectId");
        Instant now = Instant.now();
        return new PlatformEvidence(
                evidenceId,
                operation + ":" + intentId,
                projectId,
                workspaceId,
                tenantId,
                new PlatformEvidence.EvidenceRecord(
                        EVIDENCE_TYPE,
                        "text/plain",
                        content,
                        new PlatformEvidence.EvidenceSource(
                                "YAPPC_INTENT_SERVICE",
                                operation,
                                "v1",
                                evidenceId,
                                now),
                        List.of(new PlatformEvidence.EvidenceReference(
                                intentId,
                                "intent",
                                "yappc_intents/" + projectId + "/" + intentId,
                                "")),
                        attributes),
                new PlatformEvidence.EvidenceMetadata(
                        evidenceId,
                        userId == null || userId.isBlank() ? "system" : userId,
                        LIFECYCLE_PHASE,
                        Set.of("intent", operation, "phase-evidence"),
                        Map.of(
                                "phase", "intent",
                                "projectId", projectId,
                                "workspaceId", workspaceId,
                                "tenantId", tenantId,
                                "intentId", intentId)),
                now,
                now);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static Map<String, Object> mergeAttributes(
            Map<String, Object> base,
            Map<String, Object> additional
    ) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>(base);
        if (additional != null) {
            merged.putAll(additional);
        }
        return Map.copyOf(merged);
    }
}
