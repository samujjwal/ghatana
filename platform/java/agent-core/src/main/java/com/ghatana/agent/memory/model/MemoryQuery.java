/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Query for retrieving memory items from the memory store.
 *
 * @doc.type class
 * @doc.purpose Query for retrieving memory items
 * @doc.layer agent-core
 * @doc.pattern Query
 */
public record MemoryQuery(
    @NotNull String agentId,
    @Nullable String tenantId,
    @Nullable String situation,
    @Nullable Set<String> skillTags,
    @Nullable Map<String, Object> metadata,
    @Nullable Integer limit,
    boolean requireFreshness,
    @Nullable List<?> items
) {
    public MemoryQuery {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Agent ID cannot be null or blank");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentId;
        private String tenantId;
        private String situation;
        private Set<String> skillTags;
        private Map<String, Object> metadata;
        private Integer limit;
        private boolean requireFreshness = false;
        private List<?> items;

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder situation(String situation) {
            this.situation = situation;
            return this;
        }

        public Builder skillTags(Set<String> skillTags) {
            this.skillTags = skillTags;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder requireFreshness(boolean requireFreshness) {
            this.requireFreshness = requireFreshness;
            return this;
        }

        public Builder items(List<?> items) {
            this.items = items;
            return this;
        }

        public MemoryQuery build() {
            return new MemoryQuery(agentId, tenantId, situation, skillTags, metadata, limit, requireFreshness, items);
        }
    }
}
