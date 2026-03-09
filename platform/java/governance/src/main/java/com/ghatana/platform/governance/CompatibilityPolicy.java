package com.ghatana.platform.governance;

import java.util.Objects;

/**
 * Defines compatibility requirements between event producers and consumers.
 *
 * @doc.type class
 * @doc.purpose Compatibility requirements policy between event producers and consumers
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class CompatibilityPolicy {
    private final boolean strictSchemaEvolution;
    private final boolean allowBackwardCompatibleChanges;
    private final boolean allowForwardCompatibleChanges;

    private CompatibilityPolicy(Builder builder) {
        this.strictSchemaEvolution = builder.strictSchemaEvolution;
        this.allowBackwardCompatibleChanges = builder.allowBackwardCompatibleChanges;
        this.allowForwardCompatibleChanges = builder.allowForwardCompatibleChanges;
    }

    public boolean isStrictSchemaEvolution() {
        return strictSchemaEvolution;
    }

    public boolean isAllowBackwardCompatibleChanges() {
        return allowBackwardCompatibleChanges;
    }

    public boolean isAllowForwardCompatibleChanges() {
        return allowForwardCompatibleChanges;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CompatibilityPolicy strict() {
        return builder()
            .withStrictSchemaEvolution(true)
            .withAllowBackwardCompatibleChanges(false)
            .withAllowForwardCompatibleChanges(false)
            .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompatibilityPolicy that = (CompatibilityPolicy) o;
        return strictSchemaEvolution == that.strictSchemaEvolution &&
            allowBackwardCompatibleChanges == that.allowBackwardCompatibleChanges &&
            allowForwardCompatibleChanges == that.allowForwardCompatibleChanges;
    }

    @Override
    public int hashCode() {
        return Objects.hash(strictSchemaEvolution, allowBackwardCompatibleChanges, allowForwardCompatibleChanges);
    }

    public static final class Builder {
        private boolean strictSchemaEvolution;
        private boolean allowBackwardCompatibleChanges = true;
        private boolean allowForwardCompatibleChanges = true;

        private Builder() {
        }

        public Builder withStrictSchemaEvolution(boolean strictSchemaEvolution) {
            this.strictSchemaEvolution = strictSchemaEvolution;
            return this;
        }

        public Builder withAllowBackwardCompatibleChanges(boolean allowBackwardCompatibleChanges) {
            this.allowBackwardCompatibleChanges = allowBackwardCompatibleChanges;
            return this;
        }

        public Builder withAllowForwardCompatibleChanges(boolean allowForwardCompatibleChanges) {
            this.allowForwardCompatibleChanges = allowForwardCompatibleChanges;
            return this;
        }

        public CompatibilityPolicy build() {
            return new CompatibilityPolicy(this);
        }
    }
}
