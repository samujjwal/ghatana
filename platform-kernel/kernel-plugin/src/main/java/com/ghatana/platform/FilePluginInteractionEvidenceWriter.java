package com.ghatana.platform.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * File-backed plugin interaction evidence writer for local and bootstrap Kernel runs.
 *
 * @doc.type class
 * @doc.purpose Persist canonical plugin interaction evidence records as JSON files
 * @doc.layer core
 * @doc.pattern Adapter
 */
public final class FilePluginInteractionEvidenceWriter implements PluginInteractionEvidenceWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final Path evidenceRoot;

    public FilePluginInteractionEvidenceWriter(Path evidenceRoot) {
        this.evidenceRoot = Objects.requireNonNull(evidenceRoot, "evidenceRoot must not be null");
    }

    @Override
    public void write(@NotNull PluginInteractionAuditRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        Path target = evidencePath(record);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            byte[] payload = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(toEvidenceRecord(record));
            Files.write(temp, payload);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            cleanupTemp(temp);
            throw new UncheckedIOException("Failed to write plugin interaction evidence record to " + target, error);
        }
    }

    public Path evidencePath(PluginInteractionAuditRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        return evidenceRoot
                .resolve(safePathSegment(record.tenantId()))
                .resolve(safePathSegment(record.workspaceId()))
                .resolve(evidenceId(record) + ".json");
    }

    private Map<String, Object> toEvidenceRecord(PluginInteractionAuditRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0.0");
        payload.put("evidenceId", evidenceId(record));
        payload.put("manifestType", "plugin-interaction-evidence");
        payload.put("interactionId", record.interactionId());
        payload.put("contractId", record.contractId());
        payload.put("contractSchemaVersion", record.schemaVersion());
        payload.put("callerPluginId", record.callerPluginId());
        if (record.targetPluginId() != null && !record.targetPluginId().isBlank()) {
            payload.put("targetPluginId", record.targetPluginId());
        }
        if (record.topic() != null && !record.topic().isBlank()) {
            payload.put("topic", record.topic());
        }
        if (record.tenantId() != null && !record.tenantId().isBlank()) {
            payload.put("tenantId", record.tenantId());
        }
        if (record.workspaceId() != null && !record.workspaceId().isBlank()) {
            payload.put("workspaceId", record.workspaceId());
        }
        if (record.lifecyclePhase() != null && !record.lifecyclePhase().isBlank()) {
            payload.put("lifecyclePhase", record.lifecyclePhase());
        }
        payload.put("correlationId", record.correlationId());
        payload.put("mode", record.topic() == null ? "request-response" : "event-publish");
        payload.put("outcome", record.outcome().toLowerCase(Locale.ROOT));
        payload.put("reasonCode", record.reasonCode());
        payload.put("observedAt", record.observedAt().toString());
        return payload;
    }

    private static String evidenceId(PluginInteractionAuditRecord record) {
        String material = String.join(
                "|",
                nullSafe(record.contractId()),
                nullSafe(record.interactionId()),
                nullSafe(record.callerPluginId()),
                nullSafe(record.targetPluginId()),
                nullSafe(record.topic()),
                nullSafe(record.tenantId()),
                nullSafe(record.workspaceId()),
                nullSafe(record.lifecyclePhase()),
                nullSafe(record.reasonCode()));
        return "plugin-interaction-evidence-" + sha256(material).substring(0, 24);
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
