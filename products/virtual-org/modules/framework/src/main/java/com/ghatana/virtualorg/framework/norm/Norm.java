package com.ghatana.virtualorg.framework.norm;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Represents a norm (rule) that governs agent behavior.
 *
 * <p><b>Purpose</b><br>
 * Norms are the "laws" of the organization. They define what agents
 * MUST do (Obligations), CAN do (Permissions), and MUST NOT do (Prohibitions).
 * This is based on Normative Multi-Agent Systems (NorMAS) research.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Norm respondToP1 = Norm.obligation("respond-p1")
 *     .description("Respond to P1 incidents within 15 minutes")
 *     .condition("event.priority == 'P1'")
 *     .action("acknowledge")
 *     .deadline(Duration.ofMinutes(15))
 *     .penalty(0.5)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Organizational norm definition
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record Norm(
        String id,
        NormType type,
        String description,
        String condition,
        String action,
        Optional<Duration> deadline,
        Optional<String> targetRole,
        double penaltyWeight,
        boolean active,
        Instant createdAt
) {

    /**
     * Compact constructor with validation.
     */
    public Norm {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Norm ID must not be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Norm type must not be null");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Norm action must not be null or blank");
        }
        deadline = deadline != null ? deadline : Optional.empty();
        targetRole = targetRole != null ? targetRole : Optional.empty();
        if (penaltyWeight < 0 || penaltyWeight > 1) {
            throw new IllegalArgumentException("Penalty weight must be between 0 and 1");
        }
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Creates an obligation norm builder.
     */
    public static Builder obligation(String id) {
        return new Builder(id, NormType.OBLIGATION);
    }

    /**
     * Creates a prohibition norm builder.
     */
    public static Builder prohibition(String id) {
        return new Builder(id, NormType.PROHIBITION);
    }

    /**
     * Creates a permission norm builder.
     */
    public static Builder permission(String id) {
        return new Builder(id, NormType.PERMISSION);
    }

    /**
     * Checks if this norm is an obligation.
     */
    public boolean isObligation() {
        return type == NormType.OBLIGATION;
    }

    /**
     * Checks if this norm is a prohibition.
     */
    public boolean isProhibition() {
        return type == NormType.PROHIBITION;
    }

    /**
     * Checks if this norm is a permission.
     */
    public boolean isPermission() {
        return type == NormType.PERMISSION;
    }

    /**
     * Builder for Norm.
     */
    public static class Builder {
        private final String id;
        private final NormType type;
        private String description = "";
        private String condition = "true";
        private String action;
        private Duration deadline;
        private String targetRole;
        private double penaltyWeight = 0.5;
        private boolean active = true;

        private Builder(String id, NormType type) {
            this.id = id;
            this.type = type;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder condition(String condition) {
            this.condition = condition;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder deadline(Duration deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder targetRole(String role) {
            this.targetRole = role;
            return this;
        }

        public Builder penalty(double weight) {
            this.penaltyWeight = weight;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Norm build() {
            return new Norm(
                    id,
                    type,
                    description,
                    condition,
                    action,
                    Optional.ofNullable(deadline),
                    Optional.ofNullable(targetRole),
                    penaltyWeight,
                    active,
                    Instant.now()
            );
        }
    }
}
