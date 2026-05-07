package com.ghatana.aep.engine.registry;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent metadata for registry discovery and execution.
 *
 * @doc.type class
 * @doc.purpose Agent metadata and discovery information
 * @doc.layer product
 * @doc.pattern Data
 */
public class AgentInfo {
    public String id;
    public String name;
    public String type;
    public String status;
    public String product;
    public String version;
    public String description;
    public List<String> capabilities;
    public Map<String, Object> config;
    public String registeredAt;

    public AgentInfo(String id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = "ACTIVE";
        this.product = "";
        this.version = "1.0.0";
        this.description = "";
        this.capabilities = List.of();
        this.config = Map.of();
        this.registeredAt = java.time.Instant.now().toString();
    }

    public AgentInfo(String id, String name, String type, String status) {
        this(id, name, type);
        this.status = status;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public String status() {
        return status;
    }

    public String product() {
        return product != null ? product : "";
    }

    public String version() {
        return version != null ? version : "1.0.0";
    }

    public String description() {
        return description != null ? description : "";
    }

    public List<String> capabilities() {
        return capabilities != null ? capabilities : List.of();
    }

    public Map<String, Object> config() {
        return config != null ? config : Map.of();
    }

    public String registeredAt() {
        return registeredAt != null ? registeredAt : "";
    }

    @Override
    public String toString() {
        return "AgentInfo{id='" + id + "', name='" + name + "', type='" + type + "', status='" + status + "'}";
    }

    /** Returns a new builder for constructing {@link AgentInfo} instances. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link AgentInfo}.
     *
     * @doc.type class
     * @doc.purpose Builder for AgentInfo
     * @doc.layer product
     * @doc.pattern Builder
     */
    public static final class Builder {
        private String id;
        private String name;
        private String type;
        private String status = "ACTIVE";
        private String product = "";
        private String version = "1.0.0";
        private String description = "";
        private List<String> capabilities = List.of();
        private Map<String, Object> config = Map.of();
        private String registeredAt = java.time.Instant.now().toString();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder product(String product) {
            this.product = product;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder capabilities(List<String> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder capabilities(Set<String> capabilities) {
            this.capabilities = List.copyOf(capabilities);
            return this;
        }

        public Builder config(Map<String, Object> config) {
            this.config = config;
            return this;
        }

        public Builder registeredAt(String registeredAt) {
            this.registeredAt = registeredAt;
            return this;
        }

        /** Builds and returns the {@link AgentInfo} instance. */
        public AgentInfo build() {
            AgentInfo info = new AgentInfo(id, name, type);
            info.status = this.status;
            info.product = this.product;
            info.version = this.version;
            info.description = this.description;
            info.capabilities = this.capabilities;
            info.config = this.config;
            info.registeredAt = this.registeredAt;
            return info;
        }
    }
}
