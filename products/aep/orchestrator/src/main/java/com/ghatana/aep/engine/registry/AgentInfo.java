package com.ghatana.aep.engine.registry;

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

    public AgentInfo(String id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = "ACTIVE";
    }

    public AgentInfo(String id, String name, String type, String status) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
    }

    @Override
    public String toString() {
        return "AgentInfo{" + "id='"
                + id + '\'' + ", name='"
                + name + '\'' + ", type='"
                + type + '\'' + ", status='"
                + status + '\'' + '}';
    }
}
