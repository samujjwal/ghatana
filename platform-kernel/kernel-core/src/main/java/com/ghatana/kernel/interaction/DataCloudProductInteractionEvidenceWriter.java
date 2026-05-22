package com.ghatana.kernel.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Data Cloud-backed product interaction evidence writer for production Kernel runs.
 *
 * <p>This implementation writes interaction evidence to Data Cloud, providing durable,
 * queryable storage for audit trails and compliance requirements. It uses the Data Cloud
 * provider contract to persist evidence without coupling the Kernel to specific Data Cloud
 * internals.</p>
 *
 * @doc.type class
 * @doc.purpose Persist canonical product interaction evidence records to Data Cloud
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class DataCloudProductInteractionEvidenceWriter implements ProductInteractionEvidenceWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final DataCloudEvidenceClient dataCloudClient;
    private final Executor executor;

    public DataCloudProductInteractionEvidenceWriter(DataCloudEvidenceClient dataCloudClient, Executor executor) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<Void> write(ProductInteractionRequest<?> request, ProductInteractionOutcome<?> outcome) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        return Promise.ofBlocking(executor, () -> {
            writeBlocking(request, outcome);
            return null;
        });
    }

    private void writeBlocking(ProductInteractionRequest<?> request, ProductInteractionOutcome<?> outcome) {
        Map<String, Object> evidenceRecord = toEvidenceRecord(request, outcome);
        String evidenceId = evidenceId(request);
        
        try {
            dataCloudClient.writeEvidence(
                request.tenantId(),
                request.workspaceId(),
                "interaction-evidence",
                evidenceId,
                evidenceRecord
            );
        } catch (Exception error) {
            throw new RuntimeException(
                String.format("Failed to write interaction evidence to Data Cloud for tenant=%s workspace=%s evidenceId=%s",
                    request.tenantId(), request.workspaceId(), evidenceId), error);
        }
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
        record.put("capturedAt", Instant.now().toString());
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
                nullSafe(request.workspaceId()),
                nullSafe(request.runId()));
        return "interaction-evidence-" + sha256(material).substring(0, 24);
    }

    private static String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 digest is unavailable", error);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "missing" : value;
    }
}
