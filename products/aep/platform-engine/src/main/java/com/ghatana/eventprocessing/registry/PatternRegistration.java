package com.ghatana.eventprocessing.registry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a registered pattern specification with versioning and metadata.
 *
 * <p>
 * <b>Purpose</b><br>
 * Immutable record of pattern registration including specification, tenant
 * context, versioning, schema information, and optional agent/consumer hints.
 * Forms the domain model for pattern lifecycle tracking within the registry.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * PatternRegistration registration = PatternRegistration.builder()
 *     .patternId(UUID.randomUUID())
 *     .tenantId("tenant-123")
 *     .specification("SEQ(login.failed[2], transaction.decline)")
 *     .schemaVersion("1.0.0")
 *     .createdBy("user@example.com")
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Immutable pattern registration domain model with versioning
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class PatternRegistration {

    private final UUID patternId;
    private final String tenantId;
    private final String specification;
    private final String schemaVersion;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String agentHint;
    private final String consumerHint;
    private final List<String> tags;
    private final boolean active;

    private PatternRegistration(
            UUID patternId,
            String tenantId,
            String specification,
            String schemaVersion,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            String agentHint,
            String consumerHint,
            List<String> tags,
            boolean active) {
        this.patternId = patternId;
        this.tenantId = tenantId;
        this.specification = specification;
        this.schemaVersion = schemaVersion;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.agentHint = agentHint;
        this.consumerHint = consumerHint;
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.active = active;
    }

    /**
     * Creates a new builder for constructing PatternRegistration instances.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public UUID getPatternId() {
        return patternId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSpecification() {
        return specification;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getAgentHint() {
        return agentHint;
    }

    public String getConsumerHint() {
        return consumerHint;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Validates that all required fields are non-null and non-empty.
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return patternId != null
                && tenantId != null
                && !tenantId.isBlank()
                && specification != null
                && !specification.isBlank()
                && schemaVersion != null
                && !schemaVersion.isBlank()
                && createdBy != null
                && !createdBy.isBlank()
                && createdAt != null;
    }

    /**
     * Builder for constructing PatternRegistration instances with fluent API.
     *
     * <p>
     * All required fields must be set via builder methods before calling
     * build().
     */
    public static class Builder {

        private UUID patternId;
        private String tenantId;
        private String specification;
        private String schemaVersion;
        private String createdBy;
        private Instant createdAt;
        private Instant updatedAt;
        private String agentHint;
        private String consumerHint;
        private List<String> tags = List.of();
        private boolean active = true;

        public Builder patternId(UUID patternId) {
            this.patternId = patternId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder specification(String specification) {
            this.specification = specification;
            return this;
        }

        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder agentHint(String agentHint) {
            this.agentHint = agentHint;
            return this;
        }

        public Builder consumerHint(String consumerHint) {
            this.consumerHint = consumerHint;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        /**
         * Builds PatternRegistration instance.
         *
         * @return configured PatternRegistration
         */
        public PatternRegistration build() {
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            if (updatedAt == null) {
                updatedAt = createdAt;
            }
            return new PatternRegistration(
                    patternId,
                    tenantId,
                    specification,
                    schemaVersion,
                    createdBy,
                    createdAt,
                    updatedAt,
                    agentHint,
                    consumerHint,
                    tags,
                    active);
        }
    }

    @Override
    public String toString() {
        return "PatternRegistration{"
                + "patternId="
                + patternId
                + ", tenantId='"
                + tenantId
                + '\''
                + ", specification='"
                + specification
                + '\''
                + ", schemaVersion='"
                + schemaVersion
                + '\''
                + ", active="
                + active
                + '}';
    }
}
