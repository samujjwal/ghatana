package com.ghatana.phr.kernel.evidence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * File-backed PHR evidence outbox.
 *
 * <p>The outbox intentionally persists every regulated event before attempting
 * delivery to Data Cloud so a transient adapter outage cannot silently discard
 * lifecycle, audit, or consent proof.</p>
 */
public final class FileBackedPhrEvidenceOutbox implements PhrEvidenceOutbox {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final Path pendingDir;
    private final Path deliveredDir;
    private final Path deadLetterDir;

    public FileBackedPhrEvidenceOutbox(Path rootDir) {
        this(new ObjectMapper().findAndRegisterModules(), rootDir);
    }

    FileBackedPhrEvidenceOutbox(ObjectMapper objectMapper, Path rootDir) {
        this.objectMapper = objectMapper;
        this.pendingDir = rootDir.resolve("pending");
        this.deliveredDir = rootDir.resolve("delivered");
        this.deadLetterDir = rootDir.resolve("dead-letter");
        ensureDirectory(pendingDir);
        ensureDirectory(deliveredDir);
        ensureDirectory(deadLetterDir);
    }

    @Override
    public synchronized PhrEvidenceOutboxEntry enqueue(
            String datasetId,
            String eventId,
            byte[] body,
            Map<String, String> metadata) {
        Instant now = Instant.now();
        String outboxId = sanitize(datasetId) + "--" + sanitize(eventId) + "--" + UUID.randomUUID();
        PhrEvidenceOutboxEntry entry = PhrEvidenceOutboxEntry.pending(outboxId, datasetId, eventId, body, metadata, now);
        write(pendingDir.resolve(outboxId + ".json"), entry);
        return entry;
    }

    @Override
    public synchronized List<PhrEvidenceOutboxEntry> pending(int limit) {
        try (Stream<Path> stream = Files.list(pendingDir)) {
            return stream
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .limit(limit)
                .map(this::read)
                .toList();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read PHR evidence outbox", error);
        }
    }

    @Override
    public synchronized void markDelivered(PhrEvidenceOutboxEntry entry) {
        PhrEvidenceOutboxEntry delivered = entry.delivered(Instant.now());
        move(entry, deliveredDir, delivered);
    }

    @Override
    public synchronized void markFailed(PhrEvidenceOutboxEntry entry, Throwable error, int maxAttempts) {
        String message = error == null ? "unknown evidence delivery failure" : error.getMessage();
        PhrEvidenceOutboxEntry failed = entry.failed(message, maxAttempts, Instant.now());
        Path destination = failed.status() == PhrEvidenceOutboxEntry.Status.DEAD_LETTER ? deadLetterDir : pendingDir;
        move(entry, destination, failed);
    }

    @Override
    public synchronized long pendingCount() {
        return count(pendingDir);
    }

    @Override
    public synchronized long deadLetterCount() {
        return count(deadLetterDir);
    }

    private void move(PhrEvidenceOutboxEntry original, Path destinationDir, PhrEvidenceOutboxEntry replacement) {
        Path source = pendingDir.resolve(original.outboxId() + ".json");
        Path target = destinationDir.resolve(original.outboxId() + ".json");
        write(target, replacement);
        try {
            Files.deleteIfExists(source);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to delete delivered PHR evidence outbox entry", error);
        }
    }

    private void write(Path path, PhrEvidenceOutboxEntry entry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outboxId", entry.outboxId());
        payload.put("datasetId", entry.datasetId());
        payload.put("eventId", entry.eventId());
        payload.put("bodyBase64", Base64.getEncoder().encodeToString(entry.body()));
        payload.put("metadata", entry.metadata());
        payload.put("attempts", entry.attempts());
        payload.put("status", entry.status().name());
        payload.put("lastError", entry.lastError());
        payload.put("createdAt", entry.createdAt().toString());
        payload.put("updatedAt", entry.updatedAt().toString());
        try {
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            String json = objectMapper.writeValueAsString(payload);
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to persist PHR evidence outbox entry", error);
        }
    }

    @SuppressWarnings("unchecked")
    private PhrEvidenceOutboxEntry read(Path path) {
        try {
            Map<String, Object> payload = objectMapper.readValue(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE);
            Map<String, String> metadata = payload.get("metadata") instanceof Map<?, ?> raw
                    ? raw.entrySet().stream().collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue())),
                        LinkedHashMap::putAll)
                    : Map.of();
            return new PhrEvidenceOutboxEntry(
                String.valueOf(payload.get("outboxId")),
                String.valueOf(payload.get("datasetId")),
                String.valueOf(payload.get("eventId")),
                Base64.getDecoder().decode(String.valueOf(payload.get("bodyBase64"))),
                metadata,
                ((Number) payload.getOrDefault("attempts", 0)).intValue(),
                PhrEvidenceOutboxEntry.Status.valueOf(String.valueOf(payload.get("status"))),
                String.valueOf(payload.getOrDefault("lastError", "")),
                Instant.parse(String.valueOf(payload.get("createdAt"))),
                Instant.parse(String.valueOf(payload.get("updatedAt")))
            );
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read PHR evidence outbox entry", error);
        }
    }

    private long count(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json")).count();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to count PHR evidence outbox entries", error);
        }
    }

    private static void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to create PHR evidence outbox directory: " + dir, error);
        }
    }

    private static String sanitize(String value) {
        String safe = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe.isBlank() ? "unknown" : safe;
    }
}
