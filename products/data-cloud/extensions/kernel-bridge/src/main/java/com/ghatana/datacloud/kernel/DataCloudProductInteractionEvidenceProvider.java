package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Cloud-backed product interaction evidence provider.
 *
 * @doc.type class
 * @doc.purpose Persist product interaction outcomes and evidence references in platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudProductInteractionEvidenceProvider extends DataCloudKernelProviderSupport {

    /**
     * Typed interaction evidence persist request.
     *
     * @doc.type record
     * @doc.purpose Encapsulate product interaction evidence with tenant context
     * @doc.layer adapter
     * @doc.pattern Request
     */
    public record InteractionEvidencePersistRequest(
            String evidenceId,
            String contractId,
            String providerProductId,
            String consumerProductId,
            Map<String, Object> evidence,
            Instant capturedAt,
            String correlationId
    ) {}

    /**
     * Typed interaction evidence persist response.
     *
     * @doc.type record
     * @doc.purpose Encapsulate product interaction evidence persistence result
     * @doc.layer adapter
     * @doc.pattern Response
     */
    public record InteractionEvidencePersistResponse(
            boolean success,
            String evidenceId,
            String persistedAt
    ) {}

    public DataCloudProductInteractionEvidenceProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.interaction-evidence." + context.getTenantId(), "interaction-evidence");
    }

    public Promise<Void> persistInteractionEvidence(String evidenceId, Map<String, Object> evidence) {
        return persistRecord(evidenceId, evidence);
    }

    public Promise<InteractionEvidencePersistResponse> persistInteractionEvidenceTyped(
            InteractionEvidencePersistRequest request) {
        Map<String, Object> evidenceRecord = new HashMap<>();
        evidenceRecord.put("evidenceId", request.evidenceId());
        evidenceRecord.put("contractId", request.contractId());
        evidenceRecord.put("providerProductId", request.providerProductId());
        evidenceRecord.put("consumerProductId", request.consumerProductId());
        evidenceRecord.put("evidence", request.evidence());
        evidenceRecord.put("capturedAt", request.capturedAt().toString());
        evidenceRecord.put("tenantId", context().getTenantId());
        evidenceRecord.put("workspaceId", context().getWorkspaceId());
        evidenceRecord.put("projectId", context().getProjectId());
        evidenceRecord.put("correlationId", request.correlationId());
        evidenceRecord.put("persistedAt", Instant.now().toString());
        return persistRecord(request.evidenceId(), evidenceRecord)
                .map($ -> new InteractionEvidencePersistResponse(true, request.evidenceId(), Instant.now().toString()));
    }
}
