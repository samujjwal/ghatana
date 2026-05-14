/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.framework.memory.Fact;
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
 * Adapter that wraps a Fact to implement the MemoryItem interface.
 *
 * @doc.type class
 * @doc.purpose Adapter for Fact to MemoryItem
 * @doc.layer agent-core
 * @doc.pattern Adapter
 */
public final class FactMemoryItemAdapter implements MemoryItem {

    private final Fact fact;
    private final String tenantId;
    private final String skillId;

    public FactMemoryItemAdapter(@NotNull Fact fact, @NotNull String tenantId, @Nullable String skillId) {
        this.fact = Objects.requireNonNull(fact, "fact must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.skillId = skillId;
    }

    @Override
    @NotNull
    public String getId() {
        return fact.getId() != null ? fact.getId() : "fact-" + fact.getSubject() + "-" + fact.getPredicate();
    }

    @Override
    @NotNull
    public MemoryItemType getType() {
        return MemoryItemType.FACT;
    }

    @Override
    @Nullable
    public String getSkillId() {
        return skillId;
    }

    @Override
    @NotNull
    public Instant getCreatedAt() {
        return fact.getLearnedAt();
    }

    @Override
    @NotNull
    public Instant getUpdatedAt() {
        return fact.getLearnedAt();
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
                .source(fact.getSource() != null ? fact.getSource() : "consolidation")
                .confidenceSource(Provenance.ConfidenceSource.CONSOLIDATION)
                .agentId(fact.getAgentId())
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
        return Validity.builder()
                .confidence(Math.max(0.0, Math.min(1.0, fact.getConfidence())))
                .lastVerified(fact.getLearnedAt())
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
        labels.put("subject", fact.getSubject());
        labels.put("predicate", fact.getPredicate());
        labels.put("object", fact.getObject());
        labels.put("confidence", String.valueOf(fact.getConfidence()));
        if (fact.getSource() != null) {
            labels.put("source", fact.getSource());
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
    public Fact getFact() {
        return fact;
    }
}
