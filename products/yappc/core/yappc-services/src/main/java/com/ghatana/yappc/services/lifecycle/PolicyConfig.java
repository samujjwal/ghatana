/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Policy Configuration Model
 */
package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * YAML deserialization model for lifecycle policy definitions.
 *
 * <p>Maps to {@code config/policies/lifecycle-policies.yaml} and drives
 * {@link YappcPolicyEngine} evaluation logic.
 *
 * @doc.type class
 * @doc.purpose YAML model for lifecycle policy configuration
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyConfig {

    private List<Policy> policies;

    public List<Policy> getPolicies() {
        return policies != null ? policies : List.of();
    }

    public void setPolicies(List<Policy> policies) {
        this.policies = policies;
    }

    // =====================================================================
    // Policy
    // =====================================================================

    /**
     * A named policy containing an ordered list of evaluation rules.
     *
     * @doc.type class
     * @doc.purpose Represents a single governance policy with versioned rules
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Policy {

        private String id;
        private String version;
        private String description;
        private List<Rule> rules;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<Rule> getRules() { return rules != null ? rules : List.of(); }
        public void setRules(List<Rule> rules) { this.rules = rules; }
    }

    // =====================================================================
    // Rule
    // =====================================================================

    /**
     * A single evaluation rule inside a policy.
     *
     * @doc.type class
     * @doc.purpose Represents one condition–action rule within a policy
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rule {

        private String id;
        private String description;
        private Condition condition;

        @JsonProperty("applies_to")
        private AppliesTo appliesTo;

        private String action;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Condition getCondition() { return condition; }
        public void setCondition(Condition condition) { this.condition = condition; }

        public AppliesTo getAppliesTo() { return appliesTo; }
        public void setAppliesTo(AppliesTo appliesTo) { this.appliesTo = appliesTo; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }

    // =====================================================================
    // Condition
    // =====================================================================

    /**
     * The testable condition that determines whether a rule evaluates to a block.
     * Supported types: {@code METRIC_THRESHOLD}, {@code ARTIFACT_PRESENT}, {@code CONFIDENCE_SCORE}.
     *
     * @doc.type class
     * @doc.purpose Condition descriptor for policy rule evaluation
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {

        private String type;
        private String metric;
        private String operator;
        private double value;

        @JsonProperty("artifact_id")
        private String artifactId;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getMetric() { return metric; }
        public void setMetric(String metric) { this.metric = metric; }

        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }

        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }

        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    }

    // =====================================================================
    // AppliesTo
    // =====================================================================

    /**
     * Scope filter that restricts a rule to specific phase transitions.
     * A null field means "match any value".
     *
     * @doc.type class
     * @doc.purpose Scope filter for phase-transition rule applicability
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppliesTo {

        @JsonProperty("from_phase")
        private String fromPhase;

        @JsonProperty("to_phase")
        private String toPhase;

        public String getFromPhase() { return fromPhase; }
        public void setFromPhase(String fromPhase) { this.fromPhase = fromPhase; }

        public String getToPhase() { return toPhase; }
        public void setToPhase(String toPhase) { this.toPhase = toPhase; }
    }
}
