/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contract for autonomy surfaces: agent policies, AI model governance,
 * learning loops, certification hooks.
 *
 * <p>An autonomy contract declares the agent capabilities a module provides,
 * the model governance rules it follows, and the policy/learning loop
 * integration points it exposes. Aligns with the GAA framework's agent
 * lifecycle (PERCEIVE → REASON → ACT → CAPTURE → REFLECT).</p>
 *
 * @doc.type class
 * @doc.purpose Autonomy contract for agent and AI governance declarations
 * @doc.layer core
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle perceive
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class AutonomyContract extends KernelContract {

    /**
     * Classification of agent capability tiers.
     */
    public enum AgentTier {
        /** Reflex-only: pattern matching, no LLM. */
        REFLEX,
        /** Deliberative: LLM-backed reasoning with policy guardrails. */
        DELIBERATIVE,
        /** Fully autonomous: self-learning with human-in-the-loop review. */
        AUTONOMOUS
    }

    /**
     * Declares an agent capability this module provides.
     */
    public record AgentCapabilityDeclaration(String capabilityId, AgentTier tier,
                                             double minimumConfidence,
                                             boolean requiresHumanReview) {
        public AgentCapabilityDeclaration {
            Objects.requireNonNull(capabilityId, "capabilityId required");
            Objects.requireNonNull(tier, "tier required");
            if (minimumConfidence < 0.0 || minimumConfidence > 1.0) {
                throw new IllegalArgumentException(
                    "minimumConfidence must be in [0.0, 1.0]: " + minimumConfidence);
            }
        }
    }

    /**
     * Declares a model governance rule.
     */
    public record ModelGovernanceRule(String ruleId, String description,
                                     String validationHook) {
        public ModelGovernanceRule {
            Objects.requireNonNull(ruleId, "ruleId required");
            Objects.requireNonNull(description, "description required");
        }
    }

    private final List<AgentCapabilityDeclaration> agentCapabilities;
    private final List<ModelGovernanceRule> governanceRules;

    private AutonomyContract(Builder builder) {
        super(builder.contractId, builder.name, builder.version,
              ContractFamily.AUTONOMY, builder.metadata);
        this.agentCapabilities = builder.agentCapabilities != null
            ? List.copyOf(builder.agentCapabilities) : List.of();
        this.governanceRules = builder.governanceRules != null
            ? List.copyOf(builder.governanceRules) : List.of();
        validate();
    }

    public List<AgentCapabilityDeclaration> getAgentCapabilities() { return agentCapabilities; }
    public List<ModelGovernanceRule> getGovernanceRules() { return governanceRules; }

    @Override
    protected void validate() {
        super.validate();
        for (AgentCapabilityDeclaration cap : agentCapabilities) {
            if (cap.tier() == AgentTier.AUTONOMOUS && !cap.requiresHumanReview()) {
                throw new IllegalArgumentException(
                    "AUTONOMOUS agents MUST require human review: " + cap.capabilityId());
            }
        }
    }

    /**
     * Creates a new builder for {@link AutonomyContract}.
     */
    public static Builder builder(String contractId, String name, String version) {
        return new Builder(contractId, name, version);
    }

    /**
     * Fluent builder for {@link AutonomyContract}.
     */
    public static final class Builder {
        private final String contractId;
        private final String name;
        private final String version;
        private Map<String, String> metadata = Map.of();
        private List<AgentCapabilityDeclaration> agentCapabilities = List.of();
        private List<ModelGovernanceRule> governanceRules = List.of();

        private Builder(String contractId, String name, String version) {
            this.contractId = contractId;
            this.name = name;
            this.version = version;
        }

        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder agentCapabilities(List<AgentCapabilityDeclaration> caps) { this.agentCapabilities = caps; return this; }
        public Builder governanceRules(List<ModelGovernanceRule> rules) { this.governanceRules = rules; return this; }

        public AutonomyContract build() { return new AutonomyContract(this); }
    }
}
