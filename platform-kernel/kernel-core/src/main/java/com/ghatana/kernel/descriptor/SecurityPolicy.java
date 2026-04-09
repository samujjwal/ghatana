package com.ghatana.kernel.descriptor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Defines security policies for kernel components.
 *
 * <p>Security policies specify authentication, authorization, encryption, and audit
 * requirements for kernel modules and plugins.</p>
 *
 * @doc.type class
 * @doc.purpose Security policy configuration for kernel component protection
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class SecurityPolicy {

    private final boolean authenticationRequired;
    private final boolean authorizationRequired;
    private final Set<String> requiredRoles;
    private final boolean encryptionAtRest;
    private final boolean encryptionInTransit;
    private final boolean auditEnabled;
    private final AuditLevel auditLevel;
    private final boolean multiTenantIsolation;
    private final boolean privileged;

    private SecurityPolicy(Builder builder) {
        this.authenticationRequired = builder.authenticationRequired;
        this.authorizationRequired = builder.authorizationRequired;
        this.requiredRoles = Collections.unmodifiableSet(new HashSet<>(builder.requiredRoles));
        this.encryptionAtRest = builder.encryptionAtRest;
        this.encryptionInTransit = builder.encryptionInTransit;
        this.auditEnabled = builder.auditEnabled;
        this.auditLevel = builder.auditLevel != null ? builder.auditLevel : AuditLevel.BASIC;
        this.multiTenantIsolation = builder.multiTenantIsolation;
        this.privileged = builder.privileged;
    }

    public static SecurityPolicy defaultPolicy() {
        return new Builder().build();
    }

    // Getters
    public boolean isAuthenticationRequired() { return authenticationRequired; }
    public boolean isAuthorizationRequired() { return authorizationRequired; }
    public Set<String> getRequiredRoles() { return requiredRoles; }
    public boolean isEncryptionAtRest() { return encryptionAtRest; }
    public boolean isEncryptionInTransit() { return encryptionInTransit; }
    public boolean isAuditEnabled() { return auditEnabled; }
    public AuditLevel getAuditLevel() { return auditLevel; }
    public boolean isMultiTenantIsolation() { return multiTenantIsolation; }
    public boolean isPrivileged() { return privileged; }

    // Builder
    public static class Builder {
        private boolean authenticationRequired = true;
        private boolean authorizationRequired = true;
        private Set<String> requiredRoles = new HashSet<>();
        private boolean encryptionAtRest = true;
        private boolean encryptionInTransit = true;
        private boolean auditEnabled = true;
        private AuditLevel auditLevel = AuditLevel.BASIC;
        private boolean multiTenantIsolation = true;
        private boolean privileged = false;

        public Builder withAuthenticationRequired(boolean required) {
            this.authenticationRequired = required;
            return this;
        }

        public Builder withAuthorizationRequired(boolean required) {
            this.authorizationRequired = required;
            return this;
        }

        public Builder withRequiredRole(String role) {
            this.requiredRoles.add(role);
            return this;
        }

        public Builder withEncryptionAtRest(boolean enabled) {
            this.encryptionAtRest = enabled;
            return this;
        }

        public Builder withEncryptionInTransit(boolean enabled) {
            this.encryptionInTransit = enabled;
            return this;
        }

        public Builder withAuditEnabled(boolean enabled) {
            this.auditEnabled = enabled;
            return this;
        }

        public Builder withAuditLevel(AuditLevel level) {
            this.auditLevel = level;
            return this;
        }

        public Builder withMultiTenantIsolation(boolean enabled) {
            this.multiTenantIsolation = enabled;
            return this;
        }

        public Builder withPrivileged(boolean privileged) {
            this.privileged = privileged;
            return this;
        }

        public SecurityPolicy build() {
            return new SecurityPolicy(this);
        }
    }

    public enum AuditLevel {
        NONE,
        BASIC,
        DETAILED,
        FULL
    }
}
