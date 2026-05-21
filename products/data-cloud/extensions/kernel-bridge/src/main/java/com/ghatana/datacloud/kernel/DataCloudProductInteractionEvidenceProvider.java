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
        DataCloudProviderException validationError = validateInteractionEvidence(request);
        if (validationError != null) {
            return Promise.ofException(validationError);
        }

        String persistedAt = Instant.now().toString();
        Map<String, Object> evidenceRecord = new HashMap<>();
        evidenceRecord.put("evidenceId", request.evidenceId());
        evidenceRecord.put("contractId", request.contractId());
        evidenceRecord.put("providerProductId", request.providerProductId());
        evidenceRecord.put("consumerProductId", request.consumerProductId());
        evidenceRecord.put("evidence", request.evidence() != null ? request.evidence() : Map.of());
        evidenceRecord.put("capturedAt", request.capturedAt().toString());
        evidenceRecord.put("tenantId", context().getTenantId());
        evidenceRecord.put("workspaceId", context().getWorkspaceId());
        evidenceRecord.put("projectId", context().getProjectId());
        evidenceRecord.put("correlationId", request.correlationId());
        evidenceRecord.put("persistedAt", persistedAt);
        return persistRecord(request.evidenceId(), evidenceRecord)
                .map($ -> new InteractionEvidencePersistResponse(true, request.evidenceId(), persistedAt));
    }

    private DataCloudProviderException validateInteractionEvidence(InteractionEvidencePersistRequest request) {
        if (request == null) {
            return invalidInteractionEvidence("request is required");
        }
        if (isBlank(request.evidenceId())) {
            return invalidInteractionEvidence("evidenceId is required");
        }
        if (isBlank(request.contractId())) {
            return invalidInteractionEvidence("contractId is required");
        }
        if (isBlank(request.providerProductId())) {
            return invalidInteractionEvidence("providerProductId is required");
        }
        if (isBlank(request.consumerProductId())) {
            return invalidInteractionEvidence("consumerProductId is required");
        }
        if (request.capturedAt() == null) {
            return invalidInteractionEvidence("capturedAt is required");
        }
        if (isBlank(request.correlationId())) {
            return invalidInteractionEvidence("correlationId is required");
        }
        return null;
    }

    private DataCloudProviderException invalidInteractionEvidence(String message) {
        return new DataCloudProviderException(
                "interaction-evidence",
                "persist-interaction-evidence",
                message,
                DataCloudProviderException.ReasonCode.SCHEMA);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
