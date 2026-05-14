/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.framework.memory.Episode;
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
 * Adapter that wraps an Episode to implement the MemoryItem interface.
 *
 * @doc.type class
 * @doc.purpose Adapter for Episode to MemoryItem
 * @doc.layer agent-core
 * @doc.pattern Adapter
 */
public final class EpisodeMemoryItemAdapter implements MemoryItem {

    private final Episode episode;
    private final String tenantId;
    private final String skillId;

    public EpisodeMemoryItemAdapter(@NotNull Episode episode, @NotNull String tenantId, @Nullable String skillId) {
        this.episode = Objects.requireNonNull(episode, "episode must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.skillId = skillId;
    }

    @Override
    @NotNull
    public String getId() {
        return episode.getId() != null ? episode.getId() : "ep-" + episode.getTurnId();
    }

    @Override
    @NotNull
    public MemoryItemType getType() {
        return MemoryItemType.EPISODE;
    }

    @Override
    @Nullable
    public String getSkillId() {
        return skillId;
    }

    @Override
    @NotNull
    public Instant getCreatedAt() {
        return episode.getTimestamp();
    }

    @Override
    @NotNull
    public Instant getUpdatedAt() {
        return episode.getTimestamp();
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
                .source("agent-runtime")
                .confidenceSource(Provenance.ConfidenceSource.LLM_INFERENCE)
                .agentId(episode.getAgentId())
                .build();
    }

    @Override
    @Nullable
    public float[] getEmbedding() {
        if (episode.getEmbedding() != null) {
            // Parse embedding string if available
            String[] parts = episode.getEmbedding().split(",");
            float[] embedding = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                embedding[i] = Float.parseFloat(parts[i].trim());
            }
            return embedding;
        }
        return null;
    }

    @Override
    @NotNull
    public Validity getValidity() {
        double confidence = episode.getReward() != null ? episode.getReward() : 1.0;
        return Validity.builder()
                .confidence(Math.max(0.0, Math.min(1.0, confidence)))
                .lastVerified(episode.getTimestamp())
                .decayRate(0.01)
                .status(ValidityStatus.ACTIVE)
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
        labels.put("agentId", episode.getAgentId());
        labels.put("turnId", episode.getTurnId());
        if (episode.getTags() != null && !episode.getTags().isEmpty()) {
            labels.put("tags", String.join(",", episode.getTags()));
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
    public Episode getEpisode() {
        return episode;
    }
}
