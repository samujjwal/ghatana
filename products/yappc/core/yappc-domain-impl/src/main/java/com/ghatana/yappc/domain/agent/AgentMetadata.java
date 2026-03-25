package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Metadata describing an AI agent's capabilities.
 *
 * @doc.type record
 * @doc.purpose Agent metadata and capabilities
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record AgentMetadata(
        @NotNull AgentName name,
        @NotNull String version,
        @NotNull String description,
        @NotNull List<String> capabilities,
        @NotNull List<String> supportedModels,
        long latencySLA,
        @Nullable Double costPerRequest
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AgentName name;
        private String version;
        private String description;
        private List<String> capabilities = List.of();
        private List<String> supportedModels = List.of();
        private long latencySLA;
        private Double costPerRequest;

        public Builder name(AgentName name) {
            this.name = name;
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

        public Builder supportedModels(List<String> supportedModels) {
            this.supportedModels = supportedModels;
            return this;
        }

        public Builder latencySLA(long latencySLA) {
            this.latencySLA = latencySLA;
            return this;
        }

        public Builder costPerRequest(Double costPerRequest) {
            this.costPerRequest = costPerRequest;
            return this;
        }

        public AgentMetadata build() {
            return new AgentMetadata(
                    name,
                    version,
                    description,
                    capabilities,
                    supportedModels,
                    latencySLA,
                    costPerRequest
            );
        }
    }
}
