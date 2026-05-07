package com.ghatana.digitalmarketing.domain.localization;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Time zone configuration for a locale/region.
 *
 * @doc.type class
 * @doc.purpose Time zone configuration with locale-specific handling (P3-005)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class TimeZoneConfig {

    private final String regionCode;
    private final String timeZoneId;
    private final String displayName;
    private final String utcOffset;
    private final boolean observesDST;

    private TimeZoneConfig(Builder builder) {
        this.regionCode = Objects.requireNonNull(builder.regionCode, "regionCode must not be null");
        this.timeZoneId = Objects.requireNonNull(builder.timeZoneId, "timeZoneId must not be null");
        this.displayName = builder.displayName != null ? builder.displayName : ZoneId.of(timeZoneId).getId();
        this.utcOffset = builder.utcOffset;
        this.observesDST = builder.observesDST;
    }

    public String getRegionCode() { return regionCode; }
    public String getTimeZoneId() { return timeZoneId; }
    public String getDisplayName() { return displayName; }
    public String getUtcOffset() { return utcOffset; }
    public boolean observesDST() { return observesDST; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeZoneConfig)) return false;
        return regionCode.equals(((TimeZoneConfig) o).regionCode) && timeZoneId.equals(((TimeZoneConfig) o).timeZoneId);
    }

    @Override
    public int hashCode() { return regionCode.hashCode() ^ timeZoneId.hashCode(); }

    @Override
    public String toString() {
        return "TimeZoneConfig{regionCode='" + regionCode + "', timeZoneId='" + timeZoneId + "'}";
    }

    public Builder toBuilder() {
        return new Builder()
            .regionCode(regionCode)
            .timeZoneId(timeZoneId)
            .displayName(displayName)
            .utcOffset(utcOffset)
            .observesDST(observesDST);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String regionCode;
        private String timeZoneId;
        private String displayName;
        private String utcOffset;
        private boolean observesDST;

        public Builder regionCode(String v) { this.regionCode = v; return this; }
        public Builder timeZoneId(String v) { this.timeZoneId = v; return this; }
        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder utcOffset(String v) { this.utcOffset = v; return this; }
        public Builder observesDST(boolean v) { this.observesDST = v; return this; }

        public TimeZoneConfig build() {
            if (regionCode == null || regionCode.isBlank()) throw new IllegalArgumentException("regionCode must not be blank");
            if (timeZoneId == null || timeZoneId.isBlank()) throw new IllegalArgumentException("timeZoneId must not be blank");
            return new TimeZoneConfig(this);
        }
    }
}
