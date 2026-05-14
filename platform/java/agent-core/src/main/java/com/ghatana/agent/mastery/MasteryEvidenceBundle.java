/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A bundle of evidence items supporting a mastery state transition or promotion.
 *
 * <p>Evidence bundles group related evidence items together to provide a comprehensive
 * audit trail and support structured promotion workflows. Bundles are tenant-scoped
 * for governance and isolation.
 *
 * <p>Bundle-level metadata includes:
 * <ul>
 *   <li>Bundle ID and tenant for isolation</li>
 *   <li>Target mastery item and skill for context</li>
 *   <li>Bundle type (e.g., PROMOTION, TRANSITION, EVALUATION)</li>
 *   <li>Aggregate weight computed from contained evidence</li>
 *   <li>Bundle status (PENDING, APPROVED, REJECTED, APPLIED)</li>
 *   <li>Timestamps for lifecycle tracking</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Bundle of evidence items for mastery transitions
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryEvidenceBundle(
        @NotNull String bundleId,
        @NotNull String tenantId,
        @NotNull MasteryEvidenceBundleType type,
        @NotNull String targetMasteryId,
        @NotNull String targetSkillId,
        @NotNull String targetAgentId,
        @NotNull List<MasteryEvidence> evidenceItems,
        double aggregateWeight,
        @NotNull MasteryEvidenceBundleStatus status,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        @NotNull String createdBy,
        @NotNull Map<String, String> metadata
) {
    public MasteryEvidenceBundle {
        Objects.requireNonNull(bundleId, "bundleId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(targetMasteryId, "targetMasteryId must not be null");
        Objects.requireNonNull(targetSkillId, "targetSkillId must not be null");
        Objects.requireNonNull(targetAgentId, "targetAgentId must not be null");
        Objects.requireNonNull(evidenceItems, "evidenceItems must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        
        if (evidenceItems.isEmpty()) {
            throw new IllegalArgumentException("evidenceItems must not be empty");
        }
        if (aggregateWeight < 0.0 || aggregateWeight > 1.0) {
            throw new IllegalArgumentException("aggregateWeight must be between 0.0 and 1.0");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        
        // Ensure all evidence items belong to the same tenant
        for (MasteryEvidence evidence : evidenceItems) {
            if (!evidence.tenantId().equals(tenantId)) {
                throw new IllegalArgumentException("All evidence items must belong to the same tenant");
            }
        }
        
        evidenceItems = List.copyOf(evidenceItems);
        metadata = Map.copyOf(metadata);
    }

    /**
     * Computes aggregate weight from evidence items using weighted average.
     *
     * @param evidenceItems list of evidence items
     * @return aggregate weight between 0.0 and 1.0
     */
    @NotNull
    private static double computeAggregateWeight(@NotNull List<MasteryEvidence> evidenceItems) {
        if (evidenceItems.isEmpty()) {
            return 0.0;
        }
        
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        
        for (MasteryEvidence evidence : evidenceItems) {
            double weight = evidence.weight();
            weightedSum += weight * weight; // Square weight to emphasize high-confidence evidence
            totalWeight += weight;
        }
        
        return totalWeight > 0.0 ? weightedSum / totalWeight : 0.0;
    }

    /**
     * Creates a new evidence bundle with generated ID and current timestamp.
     *
     * @param tenantId tenant identifier
     * @param type bundle type
     * @param targetMasteryId target mastery item ID
     * @param targetSkillId target skill ID
     * @param targetAgentId target agent ID
     * @param evidenceItems list of evidence items
     * @param createdBy creator of the bundle
     * @return new mastery evidence bundle
     */
    @NotNull
    public static MasteryEvidenceBundle create(
            @NotNull String tenantId,
            @NotNull MasteryEvidenceBundleType type,
            @NotNull String targetMasteryId,
            @NotNull String targetSkillId,
            @NotNull String targetAgentId,
            @NotNull List<MasteryEvidence> evidenceItems,
            @NotNull String createdBy
    ) {
        Instant now = Instant.now();
        double aggregateWeight = computeAggregateWeight(evidenceItems);
        
        return new MasteryEvidenceBundle(
                java.util.UUID.randomUUID().toString(),
                tenantId,
                type,
                targetMasteryId,
                targetSkillId,
                targetAgentId,
                evidenceItems,
                aggregateWeight,
                MasteryEvidenceBundleStatus.PENDING,
                now,
                now,
                createdBy,
                Map.of()
        );
    }

    /**
     * Creates a new evidence bundle with custom metadata.
     *
     * @param tenantId tenant identifier
     * @param type bundle type
     * @param targetMasteryId target mastery item ID
     * @param targetSkillId target skill ID
     * @param targetAgentId target agent ID
     * @param evidenceItems list of evidence items
     * @param createdBy creator of the bundle
     * @param metadata additional metadata
     * @return new mastery evidence bundle
     */
    @NotNull
    public static MasteryEvidenceBundle create(
            @NotNull String tenantId,
            @NotNull MasteryEvidenceBundleType type,
            @NotNull String targetMasteryId,
            @NotNull String targetSkillId,
            @NotNull String targetAgentId,
            @NotNull List<MasteryEvidence> evidenceItems,
            @NotNull String createdBy,
            @NotNull Map<String, String> metadata
    ) {
        Instant now = Instant.now();
        double aggregateWeight = computeAggregateWeight(evidenceItems);
        
        return new MasteryEvidenceBundle(
                java.util.UUID.randomUUID().toString(),
                tenantId,
                type,
                targetMasteryId,
                targetSkillId,
                targetAgentId,
                evidenceItems,
                aggregateWeight,
                MasteryEvidenceBundleStatus.PENDING,
                now,
                now,
                createdBy,
                metadata
        );
    }

    /**
     * Updates the bundle status.
     *
     * @param newStatus new status
     * @return updated bundle
     */
    @NotNull
    public MasteryEvidenceBundle withStatus(@NotNull MasteryEvidenceBundleStatus newStatus) {
        return new MasteryEvidenceBundle(
                bundleId,
                tenantId,
                type,
                targetMasteryId,
                targetSkillId,
                targetAgentId,
                evidenceItems,
                aggregateWeight,
                newStatus,
                createdAt,
                Instant.now(),
                createdBy,
                metadata
        );
    }

    /**
     * Adds metadata to the bundle.
     *
     * @param key metadata key
     * @param value metadata value
     * @return updated bundle
     */
    @NotNull
    public MasteryEvidenceBundle withMetadata(@NotNull String key, @NotNull String value) {
        Map<String, String> newMetadata = new java.util.HashMap<>(metadata);
        newMetadata.put(key, value);
        return new MasteryEvidenceBundle(
                bundleId,
                tenantId,
                type,
                targetMasteryId,
                targetSkillId,
                targetAgentId,
                evidenceItems,
                aggregateWeight,
                status,
                createdAt,
                Instant.now(),
                createdBy,
                newMetadata
        );
    }

    /**
     * Returns true if the bundle is ready for application (approved status and sufficient weight).
     *
     * @param minWeight minimum required aggregate weight
     * @return true if bundle is ready for application
     */
    public boolean isReadyForApplication(double minWeight) {
        return status == MasteryEvidenceBundleStatus.APPROVED && aggregateWeight >= minWeight;
    }
}
