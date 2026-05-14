/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.framework.memory.Policy;
import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.MemoryLink;
import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.Validity;
import com.ghatana.agent.memory.model.ValidityStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter that wraps a Policy to implement the MemoryItem interface.
 *
 * @doc.type class
 * @doc.purpose Adapter for Policy to MemoryItem
 * @doc.layer agent-core
 * @doc.pattern Adapter
 */
public final class PolicyMemoryItemAdapter implements MemoryItem {

    private final Policy policy;
    private final String tenantId;
    private final String skillId;

    public PolicyMemoryItemAdapter(@NotNull Policy policy, @NotNull String tenantId, @Nullable String skillId) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.skillId = skillId;
    }

    @Override
    @NotNull
    public String getId() {
        return policy.getId() != null ? policy.getId() : "policy-" + policy.getSituation().hashCode();
    }

    @Override
    @NotNull
    public MemoryItemType getType() {
        return MemoryItemType.PROCEDURE;
    }

    @Override
    @Nullable
    public String getSkillId() {
        return skillId;
    }

    @Override
    @NotNull
    public Instant getCreatedAt() {
        return policy.getLearnedAt();
    }

    @Override
    @NotNull
    public Instant getUpdatedAt() {
        return policy.getLastUsedAt() != null ? policy.getLastUsedAt() : policy.getLearnedAt();
    }

    @Override
    @Nullable
    public Instant getExpiresAt() {
        return null;
    }

    @Override
    @NotNull
    public Provenance getProvenance() {
        return Provenance.builder()
                .source("reflection")
                .confidenceSource(Provenance.ConfidenceSource.CONSOLIDATION)
                .agentId(policy.getAgentId())
                .build();
    }

    @Override
    @Nullable
    public float[] getEmbedding() {
        return null;
    }

    @Override
    @NotNull
    public Validity getValidity() {
        double confidence = Math.max(0.0, Math.min(1.0, policy.getConfidence()));
        return Validity.builder()
                .confidence(confidence)
                .lastVerified(policy.getLearnedAt())
                .decayRate(policy.requiresReview() ? 0.05 : 0.01)
                .status(policy.requiresReview() ? ValidityStatus.STALE : ValidityStatus.ACTIVE)
                .build();
    }

    @Override
    @NotNull
    public List<MemoryLink> getLinks() {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Map<String, String> getLabels() {
        Map<String, String> labels = new java.util.HashMap<>();
        labels.put("situation", policy.getSituation());
        labels.put("confidence", String.valueOf(policy.getConfidence()));
        labels.put("version", policy.getVersion());
        labels.put("useCount", String.valueOf(policy.getUseCount()));
        if (policy.getLearnedFromEpisodes() != null) {
            labels.put("learnedFrom", policy.getLearnedFromEpisodes());
        }
        return labels;
    }

    @Override
    @NotNull
    public String getTenantId() {
        return tenantId;
    }

    @Override
    @Nullable
    public String getSphereId() {
        return null;
    }

    @Override
    @NotNull
    public String getClassification() {
        return "INTERNAL";
    }

    @NotNull
    public Policy getPolicy() {
        return policy;
    }
}
