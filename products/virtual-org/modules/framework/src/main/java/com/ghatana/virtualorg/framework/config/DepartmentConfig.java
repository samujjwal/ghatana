package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Department configuration POJO.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a department configuration loaded from YAML files. Maps to the
 * Virtual-Org department configuration schema.
 *
 * @doc.type record
 * @doc.purpose Department configuration data model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DepartmentConfig(
        @JsonProperty("apiVersion")
        String apiVersion,
        @JsonProperty("kind")
        String kind,
        @JsonProperty("metadata")
        ConfigMetadata metadata,
        @JsonProperty("spec")
        DepartmentSpec spec
        ) {

    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "Department".equals(kind)
                && metadata != null
                && spec != null;
    }

    // Accessor methods to expose nested spec properties
    public String getName() {
        return metadata != null ? metadata.name() : null;
    }

    public String getDisplayName() {
        return spec != null ? spec.displayName() : null;
    }

    public String getType() {
        return spec != null ? spec.type() : null;
    }

    public String getDescription() {
        return spec != null ? spec.description() : null;
    }

    public List<ConfigReference> getAgents() {
        return spec != null ? spec.agents() : List.of();
    }

    public List<KpiDefinition> getKpis() {
        return spec != null ? spec.kpis() : List.of();
    }
}

/**
 * Department specification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DepartmentSpec(
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("type")
        String type,
        @JsonProperty("description")
        String description,
        @JsonProperty("customTypeDefinition")
        CustomTypeDefinition customTypeDefinition,
        @JsonProperty("hierarchy")
        HierarchyConfig hierarchy,
        @JsonProperty("settings")
        DepartmentSettings settings,
        @JsonProperty("agents")
        List<ConfigReference> agents,
        @JsonProperty("workflows")
        List<WorkflowConfig> workflows,
        @JsonProperty("kpis")
        List<KpiDefinition> kpis,
        @JsonProperty("taskTypes")
        List<TaskTypeDefinition> taskTypes
        ) {

}

/**
 * Custom department type definition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CustomTypeDefinition(
        @JsonProperty("code")
        String code,
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("description")
        String description,
        @JsonProperty("capabilities")
        List<String> capabilities
        ) {

}

/**
 * Department hierarchy configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record HierarchyConfig(
        @JsonProperty("parent")
        String parent,
        @JsonProperty("children")
        List<String> children,
        @JsonProperty("reportingTo")
        String reportingTo
        ) {

}

/**
 * Department-specific settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DepartmentSettings(
        @JsonProperty("maxAgents")
        Integer maxAgents,
        @JsonProperty("autoScaleAgents")
        Boolean autoScaleAgents,
        @JsonProperty("taskAssignment")
        TaskAssignmentConfig taskAssignment
        ) {

    public Integer maxAgents() {
        return maxAgents != null ? maxAgents : 50;
    }

    public Boolean autoScaleAgents() {
        return autoScaleAgents != null ? autoScaleAgents : false;
    }
}

/**
 * Task assignment strategy configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskAssignmentConfig(
        @JsonProperty("strategy")
        String strategy,
        @JsonProperty("customStrategyClass")
        String customStrategyClass
        ) {

    public String strategy() {
        return strategy != null ? strategy : "round-robin";
    }
}
