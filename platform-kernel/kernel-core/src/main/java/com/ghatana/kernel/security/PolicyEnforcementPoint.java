package com.ghatana.kernel.security;

import io.activej.promise.Promise;

import java.util.Objects;

/**
 * Policy enforcement point for security policies.
 *
 * <p>Intercepts requests and enforces security policies before allowing
 * access to protected resources.</p>
 *
 * @doc.type class
 * @doc.purpose Policy enforcement for security decisions
 * @doc.layer core
 * @doc.pattern Interceptor
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class PolicyEnforcementPoint {

    private final KernelSecurityManager securityManager;
    private final PrivacyManager privacyManager;

    public PolicyEnforcementPoint(KernelSecurityManager securityManager, PrivacyManager privacyManager) {
        this.securityManager = Objects.requireNonNull(securityManager);
        this.privacyManager = Objects.requireNonNull(privacyManager);
    }

    /**
     * Enforces policy for a request.
     *
     * @param request the request to enforce policy on
     * @param context the security context
     * @return enforcement decision
     */
    public Promise<EnforcementDecision> enforce(Request request, SecurityContext context) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        // Check authentication
        if (!context.isAuthenticated()) {
            return Promise.of(EnforcementDecision.deny("Not authenticated"));
        }

        // Check authorization
        KernelSecurityManager.Action action = new KernelSecurityManager.Action(
            request.getResource(),
            request.getOperation(),
            request.getScope()
        );

        if (!securityManager.authorizeAction(action, context)) {
            return Promise.of(EnforcementDecision.deny("Not authorized for action: " + action));
        }

        // Check privacy consent if required
        if (request.requiresConsent()) {
            PrivacyManager.DataRequest dataRequest = new PrivacyManager.DataRequest(
                context.getUserId(),
                request.getDataType(),
                request.getPurpose(),
                request.getMetadata()
            );

            return privacyManager.checkConsent(dataRequest, context.getTenantId())
                .then(consentStatus -> {
                    if (consentStatus != PrivacyManager.ConsentStatus.GRANTED &&
                        consentStatus != PrivacyManager.ConsentStatus.NOT_REQUIRED) {
                        return Promise.of(EnforcementDecision.deny("Consent not granted: " + consentStatus));
                    }
                    return Promise.of(EnforcementDecision.allow());
                });
        }

        return Promise.of(EnforcementDecision.allow());
    }

    /**
     * Represents a request to be enforced.
     */
    public static class Request {
        private final String resource;
        private final String operation;
        private final String scope;
        private final String dataType;
        private final String purpose;
        private final boolean requiresConsent;
        private final java.util.Map<String, Object> metadata;

        private Request(Builder builder) {
            this.resource = builder.resource;
            this.operation = builder.operation;
            this.scope = builder.scope;
            this.dataType = builder.dataType;
            this.purpose = builder.purpose;
            this.requiresConsent = builder.requiresConsent;
            this.metadata = builder.metadata;
        }

        public String getResource() { return resource; }
        public String getOperation() { return operation; }
        public String getScope() { return scope; }
        public String getDataType() { return dataType; }
        public String getPurpose() { return purpose; }
        public boolean requiresConsent() { return requiresConsent; }
        public java.util.Map<String, Object> getMetadata() { return metadata; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String resource;
            private String operation;
            private String scope;
            private String dataType;
            private String purpose;
            private boolean requiresConsent = false;
            private java.util.Map<String, Object> metadata = new java.util.HashMap<>();

            public Builder resource(String resource) {
                this.resource = resource;
                return this;
            }

            public Builder operation(String operation) {
                this.operation = operation;
                return this;
            }

            public Builder scope(String scope) {
                this.scope = scope;
                return this;
            }

            public Builder dataType(String dataType) {
                this.dataType = dataType;
                return this;
            }

            public Builder purpose(String purpose) {
                this.purpose = purpose;
                return this;
            }

            public Builder requiresConsent(boolean requiresConsent) {
                this.requiresConsent = requiresConsent;
                return this;
            }

            public Builder metadata(java.util.Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public Request build() {
                return new Request(this);
            }
        }
    }

    /**
     * Enforcement decision result.
     */
    public static class EnforcementDecision {
        private final boolean allowed;
        private final String reason;

        private EnforcementDecision(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }

        public static EnforcementDecision allow() {
            return new EnforcementDecision(true, "Allowed");
        }

        public static EnforcementDecision deny(String reason) {
            return new EnforcementDecision(false, reason);
        }
    }
}
