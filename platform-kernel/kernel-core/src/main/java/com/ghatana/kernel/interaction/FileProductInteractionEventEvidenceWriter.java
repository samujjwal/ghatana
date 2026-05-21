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
 * File-backed product interaction event evidence writer for local and bootstrap Kernel runs.
 *
 * @doc.type class
 * @doc.purpose Persist canonical product interaction event evidence records as JSON files
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class FileProductInteractionEventEvidenceWriter implements ProductInteractionEventEvidenceWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final Path evidenceRoot;
    private final Executor executor;

    public FileProductInteractionEventEvidenceWriter(Path evidenceRoot, Executor executor) {
        this.evidenceRoot = Objects.requireNonNull(evidenceRoot, "evidenceRoot must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<Void> write(ProductInteractionEventEnvelope<?> envelope, ProductInteractionEventOutcome outcome) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        return Promise.ofBlocking(executor, () -> {
            writeBlocking(envelope, outcome);
            return null;
        });
    }

    public Path evidencePath(ProductInteractionEventEnvelope<?> envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        return evidenceRoot
                .resolve(safePathSegment(envelope.runId()))
                .resolve(evidenceId(envelope) + ".json");
    }

    private void writeBlocking(ProductInteractionEventEnvelope<?> envelope, ProductInteractionEventOutcome outcome) {
        Path target = evidencePath(envelope);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            byte[] payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(toEvidenceRecord(envelope, outcome));
            Files.write(temp, payload);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            cleanupTemp(temp);
            throw new UncheckedIOException(
                    "Failed to write product interaction event evidence record to " + target,
                    error);
        }
    }

    private Map<String, Object> toEvidenceRecord(
            ProductInteractionEventEnvelope<?> envelope,
            ProductInteractionEventOutcome outcome) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("schemaVersion", "1.0.0");
        record.put("evidenceId", evidenceId(envelope));
        record.put("manifestType", "interaction-event-evidence");
        record.put("contractId", envelope.contractId());
        record.put("contractVersion", envelope.contractVersion());
        record.put("providerProductId", envelope.providerProductId());
        record.put("consumerProductIds", List.copyOf(envelope.consumerProductIds()));
        record.put("mode", "event-publish");
        record.put("topic", envelope.topic());
        record.put("tenantId", envelope.tenantId());
        record.put("workspaceId", envelope.workspaceId());
        record.put("productUnitId", envelope.productUnitId());
        record.put("runId", envelope.runId());
        record.put("correlationId", envelope.correlationId());
        record.put("requestedAt", publishedAt(envelope));
        record.put("publishedAt", publishedAt(envelope));
        record.put("completedAt", completedAt(outcome));
        record.put("status", statusValue(outcome.status()));
        if (outcome.reasonCode() != null && !outcome.reasonCode().isBlank()) {
            record.put("reasonCode", outcome.reasonCode());
        }
        record.put("policyDecision", policyDecision(outcome));
        record.put("evidenceRefs", List.copyOf(outcome.evidenceRefs()));
        record.put("deliveredSubscriberIds", List.copyOf(outcome.deliveredSubscriberIds()));
        return record;
    }

    private static String publishedAt(ProductInteractionEventEnvelope<?> envelope) {
        Instant publishedAt = envelope.publishedAt();
        return publishedAt == null ? Instant.now().toString() : publishedAt.toString();
    }

    private static String completedAt(ProductInteractionEventOutcome outcome) {
        Instant completedAt = outcome.completedAt();
        return completedAt == null ? Instant.now().toString() : completedAt.toString();
    }

    private static String statusValue(ProductInteractionStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    private static String policyDecision(ProductInteractionEventOutcome outcome) {
        if (outcome.status() == ProductInteractionStatus.BLOCKED
                || outcome.status() == ProductInteractionStatus.DENIED) {
            return "denied";
        }
        if (outcome.status() == ProductInteractionStatus.SUCCEEDED
                || outcome.status() == ProductInteractionStatus.ALLOWED) {
            return "allowed";
        }
        return "not-evaluated";
    }

    private static String evidenceId(ProductInteractionEventEnvelope<?> envelope) {
        String material = String.join(
                "|",
                nullSafe(envelope.contractId()),
                nullSafe(envelope.contractVersion()),
                nullSafe(envelope.eventId()),
                nullSafe(envelope.tenantId()),
                nullSafe(envelope.workspaceId()),
                nullSafe(envelope.topic()));
        return "interaction-event-evidence-" + sha256(material).substring(0, 24);
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
