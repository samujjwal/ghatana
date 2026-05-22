package com.ghatana.kernel.interaction;

import java.util.Objects;
import java.util.Set;

/**
 * Central policy enforcer for product interaction security.
 *
 * <p>This enforcer provides broker-level security checks for authentication, tenant isolation,
 * purpose-based access control, and consent verification. It ensures that all product interactions
 * comply with organizational security policies before being processed.</p>
 *
 * <p>The enforcer follows a fail-closed approach: any policy violation results in the interaction
 * being blocked with a clear reason code for audit and recovery purposes.</p>
 *
 * @doc.type class
 * @doc.purpose Central policy enforcement for product interaction security
 * @doc.layer kernel
 * @doc.pattern Policy
 */
public final class ProductInteractionPolicyEnforcer {

    private final AuthenticationProvider authenticationProvider;
    private final TenantIsolationProvider tenantIsolationProvider;
    private final PurposeOfUseProvider purposeOfUseProvider;
    private final ConsentVerificationProvider consentVerificationProvider;

    public ProductInteractionPolicyEnforcer(
            AuthenticationProvider authenticationProvider,
            TenantIsolationProvider tenantIsolationProvider,
            PurposeOfUseProvider purposeOfUseProvider,
            ConsentVerificationProvider consentVerificationProvider) {
        this.authenticationProvider = Objects.requireNonNull(authenticationProvider, "authenticationProvider must not be null");
        this.tenantIsolationProvider = Objects.requireNonNull(tenantIsolationProvider, "tenantIsolationProvider must not be null");
        this.purposeOfUseProvider = Objects.requireNonNull(purposeOfUseProvider, "purposeOfUseProvider must not be null");
        this.consentVerificationProvider = Objects.requireNonNull(consentVerificationProvider, "consentVerificationProvider must not be null");
    }

    /**
     * Enforces all security policies for a product interaction request.
     *
     * @param request the interaction request to validate
     * @return a PolicyEnforcementResult indicating whether the interaction is allowed
     */
    public PolicyEnforcementResult enforce(ProductInteractionRequest<?> request) {
        Objects.requireNonNull(request, "request must not be null");

        // Step 1: Authentication
        AuthenticationResult authResult = authenticationProvider.authenticate(request);
        if (!authResult.authenticated()) {
            return PolicyEnforcementResult.denied(
                "authentication_failed",
                authResult.reasonCode(),
                "Interaction denied: authentication failed"
            );
        }

        // Step 2: Tenant isolation
        TenantIsolationResult tenantResult = tenantIsolationProvider.validateTenantAccess(request);
        if (!tenantResult.allowed()) {
            return PolicyEnforcementResult.denied(
                "tenant_isolation_violation",
                tenantResult.reasonCode(),
                "Interaction denied: tenant isolation violation"
            );
        }

        // Step 3: Purpose of use validation
        PurposeOfUseResult purposeResult = purposeOfUseProvider.validatePurpose(request);
        if (!purposeResult.allowed()) {
            return PolicyEnforcementResult.denied(
                "purpose_of_use_violation",
                purposeResult.reasonCode(),
                "Interaction denied: purpose of use violation"
            );
        }

        // Step 4: Consent verification (if applicable)
        ConsentVerificationResult consentResult = consentVerificationProvider.verifyConsent(request);
        if (!consentResult.allowed()) {
            return PolicyEnforcementResult.denied(
                "consent_verification_failed",
                consentResult.reasonCode(),
                "Interaction denied: consent verification failed"
            );
        }

        return PolicyEnforcementResult.allow();
    }

    /**
     * Result of policy enforcement.
     */
    public record PolicyEnforcementResult(
        boolean allowed,
        String violationType,
        String reasonCode,
        String message
    ) {
        public PolicyEnforcementResult {
            if (allowed && violationType != null) {
                throw new IllegalArgumentException("violationType must be null when allowed is true");
            }
            if (allowed && reasonCode != null) {
                throw new IllegalArgumentException("reasonCode must be null when allowed is true");
            }
            if (allowed && message != null) {
                throw new IllegalArgumentException("message must be null when allowed is true");
            }
        }

        public static PolicyEnforcementResult allow() {
            return new PolicyEnforcementResult(true, null, null, null);
        }

        public static PolicyEnforcementResult denied(
                String violationType,
                String reasonCode,
                String message) {
            return new PolicyEnforcementResult(false, violationType, reasonCode, message);
        }
    }

    /**
     * Provider for authentication checks.
     */
    @FunctionalInterface
    public interface AuthenticationProvider {
        AuthenticationResult authenticate(ProductInteractionRequest<?> request);
    }

    /**
     * Result of authentication check.
     */
    public record AuthenticationResult(
        boolean authenticated,
        String actorId,
        String reasonCode
    ) {
        public static AuthenticationResult authenticated(String actorId) {
            return new AuthenticationResult(true, actorId, null);
        }

        public static AuthenticationResult failed(String reasonCode) {
            return new AuthenticationResult(false, null, reasonCode);
        }
    }

    /**
     * Provider for tenant isolation checks.
     */
    @FunctionalInterface
    public interface TenantIsolationProvider {
        TenantIsolationResult validateTenantAccess(ProductInteractionRequest<?> request);
    }

    /**
     * Result of tenant isolation check.
     */
    public record TenantIsolationResult(
        boolean allowed,
        String reasonCode
    ) {
        public static TenantIsolationResult allow() {
            return new TenantIsolationResult(true, null);
        }

        public static TenantIsolationResult denied(String reasonCode) {
            return new TenantIsolationResult(false, reasonCode);
        }
    }

    /**
     * Provider for purpose of use validation.
     */
    @FunctionalInterface
    public interface PurposeOfUseProvider {
        PurposeOfUseResult validatePurpose(ProductInteractionRequest<?> request);
    }

    /**
     * Result of purpose of use validation.
     */
    public record PurposeOfUseResult(
        boolean allowed,
        String reasonCode
    ) {
        public static PurposeOfUseResult allow() {
            return new PurposeOfUseResult(true, null);
        }

        public static PurposeOfUseResult denied(String reasonCode) {
            return new PurposeOfUseResult(false, reasonCode);
        }
    }

    /**
     * Provider for consent verification.
     */
    @FunctionalInterface
    public interface ConsentVerificationProvider {
        ConsentVerificationResult verifyConsent(ProductInteractionRequest<?> request);
    }

    /**
     * Result of consent verification.
     */
    public record ConsentVerificationResult(
        boolean allowed,
        String reasonCode
    ) {
        public static ConsentVerificationResult allow() {
            return new ConsentVerificationResult(true, null);
        }

        public static ConsentVerificationResult denied(String reasonCode) {
            return new ConsentVerificationResult(false, reasonCode);
        }
    }
}
