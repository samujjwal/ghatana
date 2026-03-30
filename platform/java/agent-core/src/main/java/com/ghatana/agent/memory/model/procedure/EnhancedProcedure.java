/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.model.procedure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A generalized procedure extracted from high-confidence agent episodes during
 * the REFLECT phase of an agent turn.
 *
 * <p>Used by {@code PolicyLearningService} to convert episodic memory into
 * reusable learned policies.
 *
 * @doc.type class
 * @doc.purpose Domain model for a learned multi-step procedure extracted from agent memory
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class EnhancedProcedure {

    private String id;
    private String agentId;
    private String situation;
    private String action;
    private double confidence;
    private int version;
    private Instant createdAt;
    private List<ProcedureStep> steps = new ArrayList<>();
    private Provenance provenance;

    /** No-arg constructor for serialization frameworks. */
    public EnhancedProcedure() {}

    // ─── Accessors ────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getSituation() { return situation; }
    public void setSituation(String situation) { this.situation = situation; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<ProcedureStep> getSteps() {
        return steps != null ? steps : new ArrayList<>();
    }
    public void setSteps(List<ProcedureStep> steps) { this.steps = steps; }

    public Provenance getProvenance() { return provenance; }
    public void setProvenance(Provenance provenance) { this.provenance = provenance; }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final EnhancedProcedure p = new EnhancedProcedure();

        public Builder id(String id) { p.id = id; return this; }
        public Builder agentId(String agentId) { p.agentId = agentId; return this; }
        public Builder situation(String situation) { p.situation = situation; return this; }
        public Builder action(String action) { p.action = action; return this; }
        public Builder confidence(double confidence) { p.confidence = confidence; return this; }
        public Builder version(int version) { p.version = version; return this; }
        public Builder createdAt(Instant createdAt) { p.createdAt = createdAt; return this; }
        public Builder steps(List<ProcedureStep> steps) { p.steps = steps; return this; }
        public Builder provenance(Provenance provenance) { p.provenance = provenance; return this; }
        public EnhancedProcedure build() { return p; }
    }

    // ─── Nested types ─────────────────────────────────────────────────────────

    /**
     * Provenance metadata describing how the procedure was derived.
     */
    public static class Provenance {
        private String source;
        private String episodeId;

        public Provenance() {}

        public Provenance(String source) { this.source = source; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getEpisodeId() { return episodeId; }
        public void setEpisodeId(String episodeId) { this.episodeId = episodeId; }
    }
}
