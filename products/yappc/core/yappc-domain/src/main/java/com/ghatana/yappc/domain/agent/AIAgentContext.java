package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Extended agent context for AI operations.
 * <p>
 * Includes user preferences, permissions, and AI-specific configuration.
 *
 * @doc.type record
 * @doc.purpose AI-specific agent execution context
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record AIAgentContext(
        @NotNull String userId,
        @NotNull String workspaceId,
        @NotNull String requestId,
        @NotNull String tenantId,
        @NotNull String organizationId,
        @NotNull Set<String> permissions,
        @Nullable UserAIPreferences preferences,
        long timeout,
        @NotNull Map<String, Object> metadata
) {

    /**
     * Default timeout in milliseconds (30 seconds).
     */
    public static final long DEFAULT_TIMEOUT = 30_000L;

    /**
     * Builder for AIAgentContext.
     */
    public static Builder builder() {
        return new Builder();
    }

    public @NotNull String getTenantId() {
        return tenantId;
    }

    public @NotNull String getOrganizationId() {
        return organizationId;
    }

    public @Nullable String getConfig(@NotNull String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    public @NotNull Object getMemory() {
        return metadata.getOrDefault("memory", Map.of());
    }

    public @NotNull Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Builder for constructing AIAgentContext instances.
     */
    public static final class Builder {
        private String userId;
        private String workspaceId;
        private String requestId;
        private String tenantId;
        private String organizationId;
        private Set<String> permissions = Set.of();
        private UserAIPreferences preferences;
        private long timeout = DEFAULT_TIMEOUT;
        private Map<String, Object> metadata = Map.of();

        private Builder() {}

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder permissions(Set<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder preferences(UserAIPreferences preferences) {
            this.preferences = preferences;
            return this;
        }

        public Builder timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public AIAgentContext build() {
            return new AIAgentContext(
                    userId,
                    workspaceId,
                    requestId,
                    tenantId,
                    organizationId,
                    permissions,
                    preferences,
                    timeout,
                    metadata
            );
        }
    }
}
