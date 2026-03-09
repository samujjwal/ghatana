/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.snapshots;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.yappc.core.ci.CIPipelineSpec;
import com.ghatana.yappc.core.ci.GeneratedCIPipeline;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages CI configuration snapshots for rollback and comparison.
 *
 * <p>Week 8 Day 39: Snapshot management for CI configurations.
 *
 * @doc.type class
 * @doc.purpose Manages CI configuration snapshots for rollback and comparison.
 * @doc.layer platform
 * @doc.pattern Manager
 */
public class CISnapshotManager {

    private static final Logger log = LoggerFactory.getLogger(CISnapshotManager.class);

    private final Path snapshotsDir;
    private final ObjectMapper objectMapper;

    public CISnapshotManager() {
        this.snapshotsDir = Paths.get(System.getProperty("user.home"), ".yappc", "ci-snapshots");
        this.objectMapper = JsonUtils.getDefaultMapper();

        try {
            Files.createDirectories(snapshotsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create snapshots directory", e);
        }
    }

    public CISnapshotManager(Path customSnapshotsDir) {
        this.snapshotsDir = customSnapshotsDir;
        this.objectMapper = JsonUtils.getDefaultMapper();

        try {
            Files.createDirectories(snapshotsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create snapshots directory", e);
        }
    }

    /**
 * Creates a new CI configuration snapshot. */
    public String createSnapshot(CIPipelineSpec spec, GeneratedCIPipeline pipeline) {
        String snapshotId = generateSnapshotId();

        CISnapshot snapshot =
                new CISnapshot(
                        snapshotId,
                        generateDescription(spec),
                        LocalDateTime.now(),
                        spec,
                        pipeline,
                        extractMetadata(spec, pipeline));

        try {
            Path snapshotFile = snapshotsDir.resolve(snapshotId + ".json");
            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(snapshotFile.toFile(), snapshot);

            // Update index
            updateIndex(snapshot);

            return snapshotId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create snapshot", e);
        }
    }

    /**
 * Retrieves a specific snapshot by ID. */
    public Optional<CISnapshot> getSnapshot(String snapshotId) {
        Path snapshotFile = snapshotsDir.resolve(snapshotId + ".json");

        if (!Files.exists(snapshotFile)) {
            return Optional.empty();
        }

        try {
            CISnapshot snapshot = objectMapper.readValue(snapshotFile.toFile(), CISnapshot.class);
            return Optional.of(snapshot);
        } catch (IOException e) {
            log.error("Warning: Failed to read snapshot {}: {}", snapshotId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
 * Lists all available snapshots sorted by creation time (newest first). */
    public List<CISnapshot> listSnapshots() {
        try {
            if (!Files.exists(snapshotsDir)) {
                return List.of();
            }

            return Files.list(snapshotsDir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(this::loadSnapshot)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted(Comparator.comparing(CISnapshot::createdAt).reversed())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Warning: Failed to list snapshots: {}", e.getMessage());
            return List.of();
        }
    }

    /**
 * Deletes a snapshot by ID. */
    public boolean deleteSnapshot(String snapshotId) {
        Path snapshotFile = snapshotsDir.resolve(snapshotId + ".json");

        try {
            boolean deleted = Files.deleteIfExists(snapshotFile);
            if (deleted) {
                removeFromIndex(snapshotId);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Warning: Failed to delete snapshot {}: {}", snapshotId, e.getMessage());
            return false;
        }
    }

    /**
 * Compares two snapshots and returns differences. */
    public CISnapshotComparison compareSnapshots(String snapshot1Id, String snapshot2Id) {
        Optional<CISnapshot> snap1 = getSnapshot(snapshot1Id);
        Optional<CISnapshot> snap2 = getSnapshot(snapshot2Id);

        if (snap1.isEmpty() || snap2.isEmpty()) {
            throw new IllegalArgumentException("One or both snapshots not found");
        }

        return new CISnapshotComparison(
                snap1.get(), snap2.get(), calculateDifferences(snap1.get(), snap2.get()));
    }

    /**
 * Cleans up old snapshots, keeping only the specified number. */
    public void cleanupSnapshots(int keepCount) {
        List<CISnapshot> snapshots = listSnapshots();

        if (snapshots.size() <= keepCount) {
            return;
        }

        List<CISnapshot> toDelete = snapshots.subList(keepCount, snapshots.size());

        for (CISnapshot snapshot : toDelete) {
            deleteSnapshot(snapshot.id());
        }
    }

    private String generateSnapshotId() {
        String timestamp =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String random = UUID.randomUUID().toString().substring(0, 8);
        return "ci-" + timestamp + "-" + random;
    }

    private String generateDescription(CIPipelineSpec spec) {
        return String.format("%s pipeline for %s", spec.platform().getDisplayName(), spec.name());
    }

    private Map<String, Object> extractMetadata(CIPipelineSpec spec, GeneratedCIPipeline pipeline) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("platform", spec.platform().getKey());
        metadata.put("stageCount", spec.stages().size());
        metadata.put("fileCount", pipeline.pipelineFiles().size());
        metadata.put("secretCount", pipeline.generatedSecrets().size());
        metadata.put("hasMatrix", spec.matrix() != null);
        metadata.put(
                "hasSecurity", spec.security() != null && spec.security().enableSecurityScanning());
        return metadata;
    }

    private void updateIndex(CISnapshot snapshot) {
        // Simple implementation - in production might use a proper index
        Path indexFile = snapshotsDir.resolve("index.json");

        try {
            List<SnapshotIndexEntry> index = loadIndex();

            SnapshotIndexEntry entry =
                    new SnapshotIndexEntry(
                            snapshot.id(),
                            snapshot.description(),
                            snapshot.createdAt(),
                            snapshot.metadata());

            index.removeIf(e -> e.id().equals(snapshot.id()));
            index.add(entry);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), index);
        } catch (IOException e) {
            log.error("Warning: Failed to update snapshot index: {}", e.getMessage());
        }
    }

    private void removeFromIndex(String snapshotId) {
        Path indexFile = snapshotsDir.resolve("index.json");

        try {
            List<SnapshotIndexEntry> index = loadIndex();
            index.removeIf(e -> e.id().equals(snapshotId));

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), index);
        } catch (IOException e) {
            log.error("Warning: Failed to update snapshot index: {}", e.getMessage());
        }
    }

    private List<SnapshotIndexEntry> loadIndex() {
        Path indexFile = snapshotsDir.resolve("index.json");

        if (!Files.exists(indexFile)) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    indexFile.toFile(),
                    objectMapper
                            .getTypeFactory()
                            .constructCollectionType(List.class, SnapshotIndexEntry.class));
        } catch (IOException e) {
            log.error("Warning: Failed to load snapshot index, creating new one: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Optional<CISnapshot> loadSnapshot(Path snapshotFile) {
        try {
            return Optional.of(objectMapper.readValue(snapshotFile.toFile(), CISnapshot.class));
        } catch (IOException e) {
            log.error("Warning: Failed to load snapshot {}: {}", snapshotFile.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    private List<String> calculateDifferences(CISnapshot snap1, CISnapshot snap2) {
        List<String> differences = new ArrayList<>();

        // Platform comparison
        if (!snap1.spec().platform().equals(snap2.spec().platform())) {
            differences.add(
                    String.format(
                            "Platform: %s → %s",
                            snap1.spec().platform().getDisplayName(),
                            snap2.spec().platform().getDisplayName()));
        }

        // Stage count comparison
        if (snap1.spec().stages().size() != snap2.spec().stages().size()) {
            differences.add(
                    String.format(
                            "Stages: %d → %d",
                            snap1.spec().stages().size(), snap2.spec().stages().size()));
        }

        // File count comparison
        if (snap1.generatedPipeline().pipelineFiles().size()
                != snap2.generatedPipeline().pipelineFiles().size()) {
            differences.add(
                    String.format(
                            "Generated files: %d → %d",
                            snap1.generatedPipeline().pipelineFiles().size(),
                            snap2.generatedPipeline().pipelineFiles().size()));
        }

        // Security configuration comparison
        boolean snap1Security =
                snap1.spec().security() != null && snap1.spec().security().enableSecurityScanning();
        boolean snap2Security =
                snap2.spec().security() != null && snap2.spec().security().enableSecurityScanning();

        if (snap1Security != snap2Security) {
            differences.add(
                    String.format(
                            "Security scanning: %s → %s",
                            snap1Security ? "enabled" : "disabled",
                            snap2Security ? "enabled" : "disabled"));
        }

        return differences;
    }

    /**
 * CI configuration snapshot record. */
    public record CISnapshot(
            String id,
            String description,
            LocalDateTime createdAt,
            CIPipelineSpec spec,
            GeneratedCIPipeline generatedPipeline,
            Map<String, Object> metadata) {}

    /**
 * Snapshot index entry for fast lookups. */
    public record SnapshotIndexEntry(
            String id, String description, LocalDateTime createdAt, Map<String, Object> metadata) {}

    /**
 * Result of comparing two snapshots. */
    public record CISnapshotComparison(
            CISnapshot snapshot1, CISnapshot snapshot2, List<String> differences) {}
}
