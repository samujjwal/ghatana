/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of a YAPPC policy definition loaded from
 * {@code config/policies/*.yaml}.
 *
 * <h2>YAML Format</h2>
 * <pre>{@code
 * policies:
 *   - id: "phase_advance_policy"
 *     version: "1.0"
 *     description: "Governs valid lifecycle phase transitions"
 *     rules:
 *       - id: "require_test_coverage"
 *         description: "Test coverage ≥ 80% before DESIGN → PLANNING"
 *         condition:
 *           type: METRIC_THRESHOLD
 *           metric: test_coverage
 *           operator: GTE
 *           value: 80
 *         applies_to:
 *           from_phase: DESIGN
 *           to_phase: PLANNING
 *         action: BLOCK
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Immutable YAPPC policy definition (id, version, rules) parsed from YAML
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PolicyDefinition {

    private final String       id;
    private final String       version;
    private final String       description;
    private final List<Rule>   rules;
    private final boolean      enabled;

    // Jackson no-arg constructor
    @SuppressWarnings("unused")
    private PolicyDefinition() {
        this.id          = null;
        this.version     = "1.0";
        this.description = null;
        this.rules       = List.of();
        this.enabled     = true;
    }

    // All-args constructor (used by builder-style test factories)
    public PolicyDefinition(String id, String version, String description,
                             List<Rule> rules, boolean enabled) {
        this.id          = Objects.requireNonNull(id, "id");
        this.version     = version == null ? "1.0" : version;
        this.description = description;
        this.rules       = rules == null ? List.of() : List.copyOf(rules);
        this.enabled     = enabled;
    }

    @JsonProperty("id")
    public String getId() { return id; }

    @JsonProperty("version")
    public String getVersion() { return version; }

    @JsonProperty("description")
    public String getDescription() { return description; }

    @JsonProperty("rules")
    public List<Rule> getRules() { return rules; }

    @JsonProperty("enabled")
    public boolean isEnabled() { return enabled; }

    @Override
    public String toString() {
        return "PolicyDefinition{id='" + id + "', version='" + version
                + "', rules=" + rules.size() + ", enabled=" + enabled + '}';
    }

    // ─── Nested: Rule ─────────────────────────────────────────────────────────

    /**
     * A single evaluation rule within a {@link PolicyDefinition}.
     *
     * @doc.type class
     * @doc.purpose Single policy rule with condition, scope, and action
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Rule {

        private String             id;
        private String             description;
        private Map<String, Object> condition;
        private AppliesTo          appliesTo;
        private String             action;    // e.g. BLOCK, WARN, ALLOW
        private boolean            enabled = true;

        // Jackson no-arg constructor
        public Rule() {}

        @JsonProperty("id")
        public String getId() { return id; }

        @JsonProperty("id")
        public void setId(String id) { this.id = id; }

        @JsonProperty("description")
        public String getDescription() { return description; }

        @JsonProperty("description")
        public void setDescription(String description) { this.description = description; }

        @JsonProperty("condition")
        public Map<String, Object> getCondition() { return condition; }

        @JsonProperty("condition")
        public void setCondition(Map<String, Object> condition) { this.condition = condition; }

        @JsonProperty("applies_to")
        public AppliesTo getAppliesTo() { return appliesTo; }

        @JsonProperty("applies_to")
        public void setAppliesTo(AppliesTo appliesTo) { this.appliesTo = appliesTo; }

        @JsonProperty("action")
        public String getAction() { return action; }

        @JsonProperty("action")
        public void setAction(String action) { this.action = action; }

        @JsonProperty("enabled")
        public boolean isEnabled() { return enabled; }

        @JsonProperty("enabled")
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        @Override
        public String toString() {
            return "Rule{id='" + id + "', action='" + action + "'}";
        }
    }

    // ─── Nested: AppliesTo ────────────────────────────────────────────────────

    /**
     * Scope specifier for a {@link Rule}: which phase transitions it covers.
     *
     * @doc.type class
     * @doc.purpose Phase-transition scope for a policy rule
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class AppliesTo {

        private String fromPhase;
        private String toPhase;

        public AppliesTo() {}

        @JsonProperty("from_phase")
        public String getFromPhase() { return fromPhase; }

        @JsonProperty("from_phase")
        public void setFromPhase(String fromPhase) { this.fromPhase = fromPhase; }

        @JsonProperty("to_phase")
        public String getToPhase() { return toPhase; }

        @JsonProperty("to_phase")
        public void setToPhase(String toPhase) { this.toPhase = toPhase; }
    }
}
