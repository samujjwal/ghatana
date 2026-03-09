/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-21
 * DEPENDS_ON: platform:java:governance
 */
package com.ghatana.platform.governance;

import java.time.Duration;
import java.util.Objects;

/**
 * Defines the retention policy for events, specifying how long they should be retained
 * and any conditions for their deletion or archival.
 *
 * @doc.type class
 * @doc.purpose Retention policy configuration for event lifecycle management
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class RetentionPolicy {
    private final Duration retentionPeriod;
    private final boolean archiveBeforeDeletion;
    private final String archiveLocation;
    private final boolean immutable;
    private final boolean allowExtension;
    private final String legalHoldId;

    private RetentionPolicy(Builder builder) {
        this.retentionPeriod = builder.retentionPeriod;
        this.archiveBeforeDeletion = builder.archiveBeforeDeletion;
        this.archiveLocation = builder.archiveLocation;
        this.immutable = builder.immutable;
        this.allowExtension = builder.allowExtension;
        this.legalHoldId = builder.legalHoldId;
    }

    public Duration getRetentionPeriod() {
        return retentionPeriod;
    }

    public boolean isArchiveBeforeDeletion() {
        return archiveBeforeDeletion;
    }

    public String getArchiveLocation() {
        return archiveLocation;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public boolean isAllowExtension() {
        return allowExtension;
    }

    public String getLegalHoldId() {
        return legalHoldId;
    }

    public boolean isRetainIndefinitely() {
        return retentionPeriod == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RetentionPolicy that = (RetentionPolicy) o;
        return archiveBeforeDeletion == that.archiveBeforeDeletion &&
            immutable == that.immutable &&
            allowExtension == that.allowExtension &&
            Objects.equals(retentionPeriod, that.retentionPeriod) &&
            Objects.equals(archiveLocation, that.archiveLocation) &&
            Objects.equals(legalHoldId, that.legalHoldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(retentionPeriod, archiveBeforeDeletion, archiveLocation,
            immutable, allowExtension, legalHoldId);
    }

    @Override
    public String toString() {
        return "RetentionPolicy{" +
            "retentionPeriod=" + retentionPeriod +
            ", archiveBeforeDeletion=" + archiveBeforeDeletion +
            ", archiveLocation='" + archiveLocation + '\'' +
            ", immutable=" + immutable +
            ", allowExtension=" + allowExtension +
            ", legalHoldId='" + legalHoldId + '\'' +
            '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RetentionPolicy defaults() {
        return builder()
            .withRetentionPeriod(Duration.ofDays(365 * 7))
            .withArchiveBeforeDeletion(true)
            .withArchiveLocation("archive/default")
            .withAllowExtension(true)
            .build();
    }

    public static RetentionPolicy retainIndefinitely() {
        return builder()
            .withRetentionPeriod(null)
            .withArchiveBeforeDeletion(true)
            .withArchiveLocation("archive/indefinite")
            .withImmutable(true)
            .withAllowExtension(false)
            .build();
    }

    public static final class Builder {
        private Duration retentionPeriod = Duration.ofDays(365);
        private boolean archiveBeforeDeletion;
        private String archiveLocation;
        private boolean immutable;
        private boolean allowExtension = true;
        private String legalHoldId;

        private Builder() {
        }

        public Builder withRetentionPeriod(Duration retentionPeriod) {
            this.retentionPeriod = retentionPeriod;
            return this;
        }

        public Builder withArchiveBeforeDeletion(boolean archiveBeforeDeletion) {
            this.archiveBeforeDeletion = archiveBeforeDeletion;
            return this;
        }

        public Builder withArchiveLocation(String archiveLocation) {
            this.archiveLocation = archiveLocation;
            return this;
        }

        public Builder withImmutable(boolean immutable) {
            this.immutable = immutable;
            return this;
        }

        public Builder withAllowExtension(boolean allowExtension) {
            this.allowExtension = allowExtension;
            return this;
        }

        public Builder withLegalHoldId(String legalHoldId) {
            this.legalHoldId = legalHoldId;
            return this;
        }

        public RetentionPolicy build() {
            if (archiveBeforeDeletion && (archiveLocation == null || archiveLocation.trim().isEmpty())) {
                throw new IllegalStateException("Archive location must be specified when archiveBeforeDeletion is true");
            }
            if (legalHoldId != null && !legalHoldId.trim().isEmpty()) {
                this.immutable = true;
                this.retentionPeriod = null;
                this.allowExtension = false;
            }
            return new RetentionPolicy(this);
        }
    }
}
