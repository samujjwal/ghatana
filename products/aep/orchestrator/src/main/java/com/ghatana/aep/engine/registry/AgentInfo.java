package com.ghatana.aep.engine.registry;

import java.util.List;
import java.util.Map;

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
}
