package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.interaction.ProductInteractionEvidenceWriter;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Data Cloud-backed product interaction evidence writer for platform-mode Kernel runs.
 *
 * <p>Persists canonical product interaction evidence records to Data Cloud datasets
 * for long-term auditability and cross-product governance. This provider replaces
 * the file-backed writer in production Data Cloud deployments.</p>
 *
 * @doc.type class
 * @doc.purpose Persist product interaction evidence records in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudProductInteractionEvidenceWriter 
        extends DataCloudKernelProviderSupport 
        implements ProductInteractionEvidenceWriter {

    public DataCloudProductInteractionEvidenceWriter(
            DataCloudKernelAdapter adapter, 
            BridgeContext context) {
        super(adapter, context, "kernel.interaction-evidence." + context.getTenantId(), "interaction-evidence");
    }

    @Override
    public Promise<Void> write(ProductInteractionRequest<?> request, ProductInteractionOutcome<?> outcome) {
        String evidenceId = evidenceId(request);
        Map<String, Object> record = toEvidenceRecord(request, outcome);
        return persistRecord(evidenceId, record);
    }

    private Map<String, Object> toEvidenceRecord(
            ProductInteractionRequest<?> request,
            ProductInteractionOutcome<?> outcome) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("schemaVersion", "1.0.0");
        record.put("evidenceId", evidenceId(request));
        record.put("manifestType", "interaction-evidence");
        record.put("contractId", request.contractId());
        record.put("contractVersion", request.contractVersion());
        record.put("providerProductId", request.providerProductId());
        record.put("consumerProductId", request.consumerProductId());
        record.put("mode", "request-response");
        record.put("tenantId", request.tenantId());
        record.put("workspaceId", request.workspaceId());
        record.put("productUnitId", request.productUnitId());
        record.put("runId", request.runId());
        record.put("correlationId", request.correlationId());
        record.put("requestedAt", request.requestedAt().toString());
        record.put("completedAt", completedAt(outcome));
        record.put("status", statusValue(outcome.status()));
        if (outcome.reasonCode() != null && !outcome.reasonCode().isBlank()) {
            record.put("reasonCode", outcome.reasonCode());
        }
        record.put("policyDecision", policyDecision(outcome));
        record.put("evidenceRefs", List.copyOf(outcome.evidenceRefs()));
        record.put("provenanceRefs", List.copyOf(outcome.provenanceRefs()));
        record.put("provider", providerName());
        record.put("tenantId", context().getTenantId());
        record.put("recordId", evidenceId(request));
        record.put("updatedAt", Instant.now().toString());
        return record;
    }

    private static String completedAt(ProductInteractionOutcome<?> outcome) {
        Instant completedAt = outcome.completedAt();
        return completedAt == null ? Instant.now().toString() : completedAt.toString();
    }

    private static String statusValue(ProductInteractionStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    private static String policyDecision(ProductInteractionOutcome<?> outcome) {
        if (outcome.status() == ProductInteractionStatus.DENIED) {
            return "denied";
        }
        if (outcome.status() == ProductInteractionStatus.SUCCEEDED
                || outcome.status() == ProductInteractionStatus.ALLOWED) {
            return "allowed";
        }
        return "not-evaluated";
    }

    private static String evidenceId(ProductInteractionRequest<?> request) {
        String material = String.join(
                "|",
                nullSafe(request.contractId()),
                nullSafe(request.contractVersion()),
                nullSafe(request.interactionId()),
                nullSafe(request.tenantId()),
                nullSafe(request.workspaceId()));
        return "interaction-evidence-" + sha256(material).substring(0, 24);
    }

    private static String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 digest is unavailable", error);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "missing" : value;
    }
}
