package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Root organization configuration POJO.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents the complete organization configuration loaded from YAML files.
 * Maps to the Virtual-Org configuration schema.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * OrganizationConfig config = ConfigParser.parse(
 *     Path.of("config/organization.yaml"),
 *     OrganizationConfig.class
 * );
 *
 * ConfigurableOrganization org = new ConfigurableOrganization(
 *     eventloop,
 *     tenantId,
 *     resolvedConfig,
 *     new AgentRegistry()
 * );
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Organization configuration data model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrganizationConfig(
        @JsonProperty("apiVersion")
        String apiVersion,
        @JsonProperty("kind")
        String kind,
        @JsonProperty("metadata")
        ConfigMetadata metadata,
        @JsonProperty("spec")
        OrganizationSpec spec
        ) {

    /**
     * Gets the organization display name.
     */
    public String getDisplayName() {
        return spec != null ? spec.displayName() : null;
    }

    /**
     * Gets the organization description.
     */
    public String getDescription() {
        return spec != null ? spec.description() : null;
    }

    /**
     * Gets the tenant namespace from metadata.
     */
    public String getNamespace() {
        return metadata != null ? metadata.namespace() : null;
    }

    /**
     * Validates that this is a valid Organization configuration.
     */
    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "Organization".equals(kind)
                && metadata != null
                && spec != null;
    }
}

/**
 * Organization specification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OrganizationSpec(
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("description")
        String description,
        @JsonProperty("structure")
        StructureConfig structure,
        @JsonProperty("settings")
        OrganizationSettings settings,
        @JsonProperty("departments")
        List<ConfigReference> departments,
        @JsonProperty("workflows")
        List<ConfigReference> workflows,
        @JsonProperty("actions")
        List<ConfigReference> actions,
        @JsonProperty("personas")
        List<ConfigReference> personas,
        @JsonProperty("interactions")
        List<ConfigReference> interactions
        ) {

}

/**
 * Organization structure configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record StructureConfig(
        @JsonProperty("type")
        String type,
        @JsonProperty("maxDepth")
        Integer maxDepth
        ) {

    public String type() {
        return type != null ? type : "hierarchical";
    }

    public Integer maxDepth() {
        return maxDepth != null ? maxDepth : 3;
    }
}

/**
 * Organization-wide settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OrganizationSettings(
        @JsonProperty("defaultTimezone")
        String defaultTimezone,
        @JsonProperty("workingHours")
        WorkingHoursConfig workingHours,
        @JsonProperty("events")
        EventsConfig events,
        @JsonProperty("kpis")
        KpisConfig kpis,
        @JsonProperty("hitl")
        HitlConfig hitl
        ) {

}

/**
 * Working hours configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record WorkingHoursConfig(
        @JsonProperty("start")
        String start,
        @JsonProperty("end")
        String end,
        @JsonProperty("days")
        List<String> days
        ) {

}

/**
 * Events configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EventsConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("publishTo")
        String publishTo
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String publishTo() {
        return publishTo != null ? publishTo : "aep";
    }
}

/**
 * KPIs configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record KpisConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("aggregationInterval")
        String aggregationInterval
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }
}

