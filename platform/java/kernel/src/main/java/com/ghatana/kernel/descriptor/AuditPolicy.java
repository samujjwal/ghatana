package com.ghatana.kernel.descriptor;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Defines audit policies for kernel components.
 *
 * <p>Audit policies control what events are audited, retention policies, and where
 * audit logs are stored.</p>
 *
 * @doc.type class
 * @doc.purpose Audit policy configuration for kernel component activity logging
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class AuditPolicy {
    
    private final boolean enabled;
    private final AuditLevel level;
    private final Duration retentionPeriod;
    private final boolean logSuccessEvents;
    private final boolean logFailureEvents;
    private final boolean logAccessEvents;
    private final boolean logConfigChanges;
    private final Map<String, String> storageConfig;

    private AuditPolicy(Builder builder) {
        this.enabled = builder.enabled;
        this.level = builder.level != null ? builder.level : AuditLevel.BASIC;
        this.retentionPeriod = builder.retentionPeriod != null ? builder.retentionPeriod : Duration.ofDays(90);
        this.logSuccessEvents = builder.logSuccessEvents;
        this.logFailureEvents = builder.logFailureEvents;
        this.logAccessEvents = builder.logAccessEvents;
        this.logConfigChanges = builder.logConfigChanges;
        this.storageConfig = Collections.unmodifiableMap(new HashMap<>(builder.storageConfig));
    }

    public static AuditPolicy defaultPolicy() {
        return new Builder().build();
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public AuditLevel getLevel() { return level; }
    public Duration getRetentionPeriod() { return retentionPeriod; }
    public boolean isLogSuccessEvents() { return logSuccessEvents; }
    public boolean isLogFailureEvents() { return logFailureEvents; }
    public boolean isLogAccessEvents() { return logAccessEvents; }
    public boolean isLogConfigChanges() { return logConfigChanges; }
    public Map<String, String> getStorageConfig() { return storageConfig; }

    // Builder
    public static class Builder {
        private boolean enabled = true;
        private AuditLevel level = AuditLevel.BASIC;
        private Duration retentionPeriod = Duration.ofDays(90);
        private boolean logSuccessEvents = true;
        private boolean logFailureEvents = true;
        private boolean logAccessEvents = true;
        private boolean logConfigChanges = true;
        private Map<String, String> storageConfig = new HashMap<>();

        public Builder withEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder withLevel(AuditLevel level) {
            this.level = level;
            return this;
        }

        public Builder withRetentionPeriod(Duration period) {
            this.retentionPeriod = period;
            return this;
        }

        public Builder withLogSuccessEvents(boolean log) {
            this.logSuccessEvents = log;
            return this;
        }

        public Builder withLogFailureEvents(boolean log) {
            this.logFailureEvents = log;
            return this;
        }

        public Builder withLogAccessEvents(boolean log) {
            this.logAccessEvents = log;
            return this;
        }

        public Builder withLogConfigChanges(boolean log) {
            this.logConfigChanges = log;
            return this;
        }

        public Builder withStorageConfig(String key, String value) {
            this.storageConfig.put(key, value);
            return this;
        }

        public AuditPolicy build() {
            return new AuditPolicy(this);
        }
    }

    public enum AuditLevel {
        NONE,
        BASIC,
        DETAILED,
        FULL
    }
}
