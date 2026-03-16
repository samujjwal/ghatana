/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Backend Persistence
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a policy learned by an agent through successful high-confidence executions.
 *
 * <p>Corresponds to the {@code yappc.learned_policies} table. A learned policy captures
 * a generalizable action pattern extracted from episodic memory when the agent's
 * confidence score exceeds the learning threshold (≥ 0.9 by default).
 *
 * <h2>Table DDL</h2>
 * <pre>{@code
 * CREATE TABLE yappc.learned_policies (
 *   id          TEXT PRIMARY KEY,
 *   agent_id    TEXT            NOT NULL,
 *   name        TEXT,
 *   description TEXT,
 *   procedure   TEXT            NOT NULL,
 *   confidence  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
 *   source      TEXT,
 *   version     INTEGER         NOT NULL DEFAULT 1,
 *   tenant_id   TEXT            NOT NULL,
 *   created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
 *   updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
 * );
 * CREATE INDEX ON yappc.learned_policies (tenant_id, agent_id);
 * CREATE INDEX ON yappc.learned_policies (tenant_id, confidence);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Domain model for YAPPC agent-learned policies (procedural memory)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class LearnedPolicy {

    private String  id;
    private String  agentId;
    private String  name;
    private String  description;
    /** JSON-encoded procedure / action steps. */
    private String  procedure;
    private double  confidence;
    private String  source;
    private int     version;
    private String  tenantId;
    private Instant createdAt;
    private Instant updatedAt;

    public LearnedPolicy() {}

    private LearnedPolicy(Builder b) {
        this.id          = Objects.requireNonNull(b.id,          "id");
        this.agentId     = Objects.requireNonNull(b.agentId,     "agentId");
        this.name        = b.name;
        this.description = b.description;
        this.procedure   = Objects.requireNonNull(b.procedure,   "procedure");
        this.confidence  = b.confidence;
        this.source      = b.source;
        this.version     = b.version;
        this.tenantId    = Objects.requireNonNull(b.tenantId,    "tenantId");
        this.createdAt   = b.createdAt  == null ? Instant.now() : b.createdAt;
        this.updatedAt   = b.updatedAt  == null ? Instant.now() : b.updatedAt;
    }

    public static Builder builder() { return new Builder(); }

    // ─── Getters ──────────────────────────────────────────────────────────────
    public String  getId()          { return id; }
    public String  getAgentId()     { return agentId; }
    public String  getName()        { return name; }
    public String  getDescription() { return description; }
    public String  getProcedure()   { return procedure; }
    public double  getConfidence()  { return confidence; }
    public String  getSource()      { return source; }
    public int     getVersion()     { return version; }
    public String  getTenantId()    { return tenantId; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }

    // ─── Setters (for ORM-style mapping) ─────────────────────────────────────
    public void setId(String id)               { this.id = id; }
    public void setAgentId(String agentId)     { this.agentId = agentId; }
    public void setName(String name)           { this.name = name; }
    public void setDescription(String d)       { this.description = d; }
    public void setProcedure(String procedure) { this.procedure = procedure; }
    public void setConfidence(double confidence){ this.confidence = confidence; }
    public void setSource(String source)       { this.source = source; }
    public void setVersion(int version)        { this.version = version; }
    public void setTenantId(String tenantId)   { this.tenantId = tenantId; }
    public void setCreatedAt(Instant t)        { this.createdAt = t; }
    public void setUpdatedAt(Instant t)        { this.updatedAt = t; }

    @Override
    public String toString() {
        return "LearnedPolicy{id='" + id + "', agentId='" + agentId
                + "', confidence=" + confidence + ", version=" + version + '}';
    }

    // ─── Builder ──────────────────────────────────────────────────────────────
    public static final class Builder {
        private String  id;
        private String  agentId;
        private String  name;
        private String  description;
        private String  procedure;
        private double  confidence;
        private String  source;
        private int     version = 1;
        private String  tenantId;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String id)                  { this.id          = id;          return this; }
        public Builder agentId(String agentId)        { this.agentId     = agentId;     return this; }
        public Builder name(String name)              { this.name        = name;        return this; }
        public Builder description(String description){ this.description = description; return this; }
        public Builder procedure(String procedure)    { this.procedure   = procedure;   return this; }
        public Builder confidence(double confidence)  { this.confidence  = confidence;  return this; }
        public Builder source(String source)          { this.source      = source;      return this; }
        public Builder version(int version)           { this.version     = version;     return this; }
        public Builder tenantId(String tenantId)      { this.tenantId    = tenantId;    return this; }
        public Builder createdAt(Instant createdAt)   { this.createdAt   = createdAt;   return this; }
        public Builder updatedAt(Instant updatedAt)   { this.updatedAt   = updatedAt;   return this; }
        public LearnedPolicy build()                  { return new LearnedPolicy(this); }
    }
}
