package com.ghatana.refactorer.server.jobs;

import com.ghatana.refactorer.server.auth.TenantContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of a client job submission.
 *
 * @doc.type class
 * @doc.purpose Carry tenant context and desired run attributes into the orchestration layer.
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class JobSubmission {
    private final TenantContext tenantContext;
    private final boolean dryRun;
    private final Map<String, String> attributes;

    private JobSubmission(
            TenantContext tenantContext, boolean dryRun, Map<String, String> attributes) {
        this.tenantContext =
                Objects.requireNonNull(tenantContext, "tenantContext must not be null");
        this.dryRun = dryRun;
        this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    public TenantContext tenantContext() {
        return tenantContext;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public static Builder builder(TenantContext tenantContext) {
        return new Builder(tenantContext);
    }

    public static final class Builder {
        private final TenantContext tenantContext;
        private boolean dryRun;
        private final Map<String, String> attributes = new HashMap<>();

        private Builder(TenantContext tenantContext) {
            this.tenantContext =
                    Objects.requireNonNull(tenantContext, "tenantContext must not be null");
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            attributes.put("dryRun", Boolean.toString(dryRun));
            return this;
        }

        public Builder attribute(String key, String value) {
            if (value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        public Builder attributes(Map<String, String> extra) {
            extra.forEach(this::attribute);
            return this;
        }

        public JobSubmission build() {
            return new JobSubmission(tenantContext, dryRun, attributes);
        }
    }
}
