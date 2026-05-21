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
