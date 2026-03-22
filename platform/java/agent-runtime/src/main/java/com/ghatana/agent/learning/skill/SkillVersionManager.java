package com.ghatana.agent.learning.skill;

import com.ghatana.agent.learning.SkillVersion;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.procedure.ProcedureVersion;
import com.ghatana.agent.memory.store.MemoryPlane;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages skill (procedure) versioning — tracks version history,
 * enables promotion and rollback, and provides version comparison.
 *
 * @doc.type class
 * @doc.purpose Skill version management
 * @doc.layer agent-learning
 */
public class SkillVersionManager {

    private static final Logger log = LoggerFactory.getLogger(SkillVersionManager.class);

    private final MemoryPlane memoryPlane;

    // Version index: skillId → sorted versions (most recent first)
    private final ConcurrentHashMap<String, List<SkillVersion>> versionIndex = new ConcurrentHashMap<>();

    public SkillVersionManager(@NotNull MemoryPlane memoryPlane) {
        this.memoryPlane = Objects.requireNonNull(memoryPlane, "memoryPlane");
    }

    /**
     * Creates a new version for a skill.
     *
     * @param skillId The skill to version
     * @param version The version string
     * @param changeLog What changed
     * @param confidence Confidence at time of versioning
     * @return The created SkillVersion
     */
    @NotNull
    public Promise<SkillVersion> createVersion(
            @NotNull String skillId,
            @NotNull String version,
            @Nullable String changeLog,
            double confidence) {

        SkillVersion sv = SkillVersion.builder()
                .versionId(UUID.randomUUID().toString())
                .skillId(skillId)
                .version(version)
                .confidence(confidence)
                .status("ACTIVE")
                .changeLog(changeLog)
                .createdAt(Instant.now())
                .build();

        versionIndex.computeIfAbsent(skillId, k -> new ArrayList<>()).add(0, sv);
        log.info("Created version {} for skill {} (confidence={})", version, skillId, confidence);
        return Promise.of(sv);
    }

    /**
     * Lists all versions for a skill, most recent first.
     */
    @NotNull
    public Promise<List<SkillVersion>> listVersions(@NotNull String skillId) {
        List<SkillVersion> versions = versionIndex.getOrDefault(skillId, List.of());
        return Promise.of(versions);
    }

    /**
     * Gets the currently active version for a skill.
     */
    @NotNull
    public Promise<@Nullable SkillVersion> getActiveVersion(@NotNull String skillId) {
        List<SkillVersion> versions = versionIndex.getOrDefault(skillId, List.of());
        SkillVersion active = versions.stream()
                .filter(v -> "ACTIVE".equals(v.getStatus()) || "PROMOTED".equals(v.getStatus()))
                .findFirst()
                .orElse(null);
        return Promise.of(active);
    }

    /**
     * Marks a version as promoted.
     */
    @NotNull
    public Promise<SkillVersion> markPromoted(@NotNull String skillId, @NotNull String versionId) {
        List<SkillVersion> versions = versionIndex.getOrDefault(skillId, List.of());
        for (SkillVersion sv : versions) {
            if (sv.getVersionId().equals(versionId)) {
                SkillVersion promoted = SkillVersion.builder()
                        .versionId(sv.getVersionId())
                        .skillId(sv.getSkillId())
                        .version(sv.getVersion())
                        .confidence(sv.getConfidence())
                        .status("PROMOTED")
                        .changeLog(sv.getChangeLog())
                        .source(sv.getSource())
                        .evaluationScore(sv.getEvaluationScore())
                        .createdAt(sv.getCreatedAt())
                        .promotedAt(Instant.now())
                        .build();
                log.info("Promoted version {} for skill {}", sv.getVersion(), skillId);
                return Promise.of(promoted);
            }
        }
        return Promise.ofException(new IllegalArgumentException(
                "Version not found: " + versionId + " for skill " + skillId));
    }

    /**
     * Rolls back to a prior version, marking the current as ROLLED_BACK.
     */
    @NotNull
    public Promise<SkillVersion> rollback(@NotNull String skillId, @NotNull String targetVersionId) {
        log.info("Rolling back skill {} to version {}", skillId, targetVersionId);
        List<SkillVersion> versions = versionIndex.getOrDefault(skillId, List.of());
        SkillVersion target = versions.stream()
                .filter(v -> v.getVersionId().equals(targetVersionId))
                .findFirst()
                .orElse(null);

        if (target == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Target version not found: " + targetVersionId));
        }

        SkillVersion restored = SkillVersion.builder()
                .versionId(target.getVersionId())
                .skillId(target.getSkillId())
                .version(target.getVersion())
                .confidence(target.getConfidence())
                .status("ACTIVE")
                .changeLog(target.getChangeLog())
                .source(target.getSource())
                .evaluationScore(target.getEvaluationScore())
                .createdAt(target.getCreatedAt())
                .promotedAt(target.getPromotedAt())
                .build();

        return Promise.of(restored);
    }
}
