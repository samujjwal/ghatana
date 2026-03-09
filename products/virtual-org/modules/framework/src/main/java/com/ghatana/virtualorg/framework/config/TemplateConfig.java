package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Template configuration for reusable agent/department definitions.
 *
 * <p><b>Purpose</b><br>
 * Templates enable "DRY" configuration by defining common patterns
 * that can be instantiated with specific parameters. For example,
 * a "SeniorEngineer" template defines capabilities and authority,
 * while individual agent configs just reference the template and
 * provide name/department.
 *
 * <p><b>Usage in YAML</b><br>
 * <pre>{@code
 * # templates/senior-engineer.yaml
 * apiVersion: virtualorg.ghatana.com/v1
 * kind: Template
 * metadata:
 *   name: senior-engineer
 * spec:
 *   targetKind: Agent
 *   defaults:
 *     capabilities:
 *       primary: [code-review, architecture, mentoring]
 *     authority:
 *       decisions: [approve-pr, approve-design]
 *     escalation:
 *       path: [tech-lead, architect]
 *
 * # agents/alice.yaml
 * apiVersion: virtualorg.ghatana.com/v1
 * kind: Agent
 * metadata:
 *   name: alice
 * spec:
 *   template: senior-engineer  # Inherits all template defaults
 *   displayName: Alice Johnson
 *   overrides:
 *     capabilities:
 *       primary: [+security]  # Adds to inherited capabilities
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Template configuration for reusable patterns
 * @doc.layer platform
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplateConfig(
        @JsonProperty("apiVersion")
        String apiVersion,

        @JsonProperty("kind")
        String kind,

        @JsonProperty("metadata")
        ConfigMetadata metadata,

        @JsonProperty("spec")
        TemplateSpec spec
) {

    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "Template".equals(kind)
                && metadata != null
                && spec != null;
    }

    public String getName() {
        return metadata != null ? metadata.name() : null;
    }

    /**
     * Template specification.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TemplateSpec(
            @JsonProperty("targetKind")
            String targetKind,

            @JsonProperty("description")
            String description,

            @JsonProperty("defaults")
            Map<String, Object> defaults,

            @JsonProperty("parameters")
            List<TemplateParameter> parameters,

            @JsonProperty("validators")
            List<String> validators
    ) {
        /**
         * Checks if this template is for agents.
         */
        public boolean isAgentTemplate() {
            return "Agent".equals(targetKind);
        }

        /**
         * Checks if this template is for departments.
         */
        public boolean isDepartmentTemplate() {
            return "Department".equals(targetKind);
        }
    }

    /**
     * Template parameter definition.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TemplateParameter(
            @JsonProperty("name")
            String name,

            @JsonProperty("type")
            String type,

            @JsonProperty("required")
            boolean required,

            @JsonProperty("default")
            Object defaultValue,

            @JsonProperty("description")
            String description
    ) {}
}
