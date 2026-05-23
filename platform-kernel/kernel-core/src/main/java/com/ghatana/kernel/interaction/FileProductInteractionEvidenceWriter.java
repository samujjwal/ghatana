package com.ghatana.kernel.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * File-backed product interaction evidence writer for local and bootstrap Kernel runs.
 *
 * @doc.type class
 * @doc.purpose Persist canonical product interaction evidence records as JSON files
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class FileProductInteractionEvidenceWriter implements ProductInteractionEvidenceWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final Path evidenceRoot;
    private final Executor executor;

    public FileProductInteractionEvidenceWriter(Path evidenceRoot, Executor executor) {
        this.evidenceRoot = Objects.requireNonNull(evidenceRoot, "evidenceRoot must not be null");
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

    public Path evidencePath(ProductInteractionRequest<?> request) {
        Objects.requireNonNull(request, "request must not be null");
        return evidenceRoot
                .resolve(safePathSegment(request.runId()))
                .resolve(evidenceId(request) + ".json");
    }

    /**
     * Reads an evidence record by evidence ID.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param runId the run ID
     * @param evidenceId the evidence ID
     * @return the evidence record if found, empty otherwise
     */
    public java.util.Optional<Map<String, Object>> readEvidence(
            String tenantId,
            String workspaceId,
            String runId,
            String evidenceId) {
        try {
            Path evidencePath = evidenceRoot
                    .resolve(safePathSegment(runId))
                    .resolve(evidenceId + ".json");
            if (!Files.exists(evidencePath)) {
                return java.util.Optional.empty();
            }
            byte[] content = Files.readAllBytes(evidencePath);
            @SuppressWarnings("unchecked")
            Map<String, Object> record = OBJECT_MAPPER.readValue(content, Map.class);
            return java.util.Optional.of(record);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to read evidence record", error);
        }
    }

    /**
     * Lists evidence records for a tenant/workspace/run.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param runId the run ID
     * @param limit maximum number of records to return
     * @return list of evidence record metadata (IDs and timestamps)
     */
    public java.util.List<Map<String, String>> listEvidence(
            String tenantId,
            String workspaceId,
            String runId,
            int limit) {
        try {
            Path runDirectory = evidenceRoot.resolve(safePathSegment(runId));
            if (!Files.exists(runDirectory) || !Files.isDirectory(runDirectory)) {
                return List.of();
            }
            java.util.List<Map<String, String>> metadata = new java.util.ArrayList<>();
            try (var stream = Files.list(runDirectory)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json"))
                        .limit(limit)
                        .forEach(path -> {
                            try {
                                byte[] content = Files.readAllBytes(path);
                                @SuppressWarnings("unchecked")
                                Map<String, Object> record = OBJECT_MAPPER.readValue(content, Map.class);
                                Map<String, String> meta = new LinkedHashMap<>();
                                meta.put("evidenceId", path.getFileName().toString().replace(".json", ""));
                                Object capturedAt = record.get("capturedAt");
                                meta.put("capturedAt", capturedAt instanceof String ? (String) capturedAt : "");
                                metadata.add(meta);
                            } catch (IOException error) {
                                // Skip files that cannot be read
                            }
                        });
            }
            return metadata;
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to list evidence records", error);
        }
    }

    /**
     * Validates evidence freshness by checking if the evidence is within the allowed age.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param runId the run ID
     * @param evidenceId the evidence ID
     * @param maxAge the maximum allowed age for the evidence
     * @return true if evidence exists and is fresh enough, false otherwise
     */
    public boolean isEvidenceFresh(
            String tenantId,
            String workspaceId,
            String runId,
            String evidenceId,
            java.time.Duration maxAge) {
        java.util.Optional<Map<String, Object>> evidence = readEvidence(tenantId, workspaceId, runId, evidenceId);
        if (evidence.isEmpty()) {
            return false;
        }
        Object timestamp = evidence.get().get("capturedAt");
        if (timestamp instanceof String) {
            try {
                Instant capturedAt = Instant.parse((String) timestamp);
                return java.time.Duration.between(capturedAt, Instant.now()).compareTo(maxAge) <= 0;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Validates that evidence has valid source references.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param runId the run ID
     * @param evidenceId the evidence ID
     * @return true if evidence exists and has valid source references, false otherwise
     */
    public boolean hasValidSourceRefs(
            String tenantId,
            String workspaceId,
            String runId,
            String evidenceId) {
        java.util.Optional<Map<String, Object>> evidence = readEvidence(tenantId, workspaceId, runId, evidenceId);
        if (evidence.isEmpty()) {
            return false;
        }
        Object sourceRefs = evidence.get().get("sourceRefs");
        if (sourceRefs instanceof List) {
            @SuppressWarnings("unchecked")
            List<?> refs = (List<?>) sourceRefs;
            return !refs.isEmpty() && refs.stream().allMatch(ref -> ref instanceof String && !((String) ref).isBlank());
        }
        return false;
    }

    private void writeBlocking(ProductInteractionRequest<?> request, ProductInteractionOutcome<?> outcome) {
        Path target = evidencePath(request);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            byte[] payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(toEvidenceRecord(request, outcome));
            Files.write(temp, payload);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            cleanupTemp(temp);
            throw new UncheckedIOException("Failed to write product interaction evidence record to " + target, error);
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 digest is unavailable", error);
        }
    }

    private static String safePathSegment(String value) {
        return nullSafe(value).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String nullSafe(String value) {
        return value == null ? "missing" : value;
    }

    private static void cleanupTemp(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // The original write failure is the actionable error surfaced to the broker.
        }
    }
}
