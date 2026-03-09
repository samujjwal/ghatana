package com.ghatana.agent.registry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Data Transfer Object for agent registration requests.
 * Used for validating and transferring agent registration data between layers.
 */
public class AgentRegistrationDto {
    private String name;
    private String description;
    private String version;
    private String implementationType;
    private String implementationUri;
    private String tenantId;
    private boolean deprecated;
    private String inputSchema;
    private String outputSchema;
    private String configSchema;
    private List<String> tags;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getImplementationType() {
        return implementationType;
    }

    public void setImplementationType(String implementationType) {
        this.implementationType = implementationType;
    }

    @JsonProperty("implementation_uri")
    public String getImplementationUri() {
        return implementationUri;
    }

    public void setImplementationUri(String implementationUri) {
        this.implementationUri = implementationUri;
    }

    @JsonProperty("tenant_id")
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    @JsonProperty("input_schema")
    public String getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = inputSchema;
    }

    @JsonProperty("output_schema")
    public String getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(String outputSchema) {
        this.outputSchema = outputSchema;
    }

    @JsonProperty("config_schema")
    public String getConfigSchema() {
        return configSchema;
    }

    public void setConfigSchema(String configSchema) {
        this.configSchema = configSchema;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    // Note: protobuf conversion removed during contract alignment.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentRegistrationDto that = (AgentRegistrationDto) o;
        return deprecated == that.deprecated &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(version, that.version) &&
                Objects.equals(implementationType, that.implementationType) &&
                Objects.equals(implementationUri, that.implementationUri) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(inputSchema, that.inputSchema) &&
                Objects.equals(outputSchema, that.outputSchema) &&
                Objects.equals(configSchema, that.configSchema) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, version, implementationType, implementationUri, 
                          tenantId, deprecated, inputSchema, outputSchema, configSchema, tags);
    }

    @Override
    public String toString() {
        return "AgentRegistrationDto{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", implementationType='" + implementationType + '\'' +
                ", implementationUri='" + implementationUri + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", deprecated=" + deprecated +
                '}';
    }

    /**
     * Builder for creating AgentRegistrationDto instances.
     */
    public static class Builder {
        private final AgentRegistrationDto dto = new AgentRegistrationDto();

        public Builder name(String name) {
            dto.name = name;
            return this;
        }

        public Builder description(String description) {
            dto.description = description;
            return this;
        }

        public Builder version(String version) {
            dto.version = version;
            return this;
        }

        public Builder implementationType(String implementationType) {
            dto.implementationType = implementationType;
            return this;
        }

        public Builder implementationUri(String implementationUri) {
            dto.implementationUri = implementationUri;
            return this;
        }

        public Builder tenantId(String tenantId) {
            dto.tenantId = tenantId;
            return this;
        }

        public Builder deprecated(boolean deprecated) {
            dto.deprecated = deprecated;
            return this;
        }

        public Builder inputSchema(String inputSchema) {
            dto.inputSchema = inputSchema;
            return this;
        }

        public Builder outputSchema(String outputSchema) {
            dto.outputSchema = outputSchema;
            return this;
        }

        public Builder configSchema(String configSchema) {
            dto.configSchema = configSchema;
            return this;
        }

        public Builder tags(List<String> tags) {
            dto.tags = tags;
            return this;
        }

        public AgentRegistrationDto build() {
            // Validate required fields
            if (dto.name == null || dto.name.trim().isEmpty()) {
                throw new IllegalArgumentException("Agent name is required");
            }
            if (dto.version == null || dto.version.trim().isEmpty()) {
                throw new IllegalArgumentException("Agent version is required");
            }
            return dto;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // Proto conversion removed during contract alignment.
    
    /**
     * Validates that all required fields are present and valid.
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (this.name == null || this.name.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent name is required");
        }
        if (this.version == null || this.version.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent version is required");
        }
    }
}
