package com.ghatana.orchestrator.models;

import java.time.Instant;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Provides agent metadata entity functionality.
 * @doc.layer product
 * @doc.pattern Entity
 */
public class AgentMetadataEntity {
    public String id;
    public String className;
    public String version;
    public AgentCapabilities capabilities;
    public String status;
    public String description;
    public Instant registeredAt;
    public String tenantId = "default";

    // Simple capabilities holder
    public static class AgentCapabilities {
        public final String version;
        public final Set<String> capabilities;

        public AgentCapabilities(String version, Set<String> capabilities) {
            this.version = version;
            this.capabilities = capabilities;
        }
    }
}
