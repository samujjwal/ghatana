package com.ghatana.phr.kernel.extension;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.extension.KernelExtension;
import com.ghatana.kernel.module.KernelModule;

import java.util.Map;
import java.util.Set;

/**
 * Extends kernel with healthcare-specific consent policies (Nepal Directive 2081,
 * FHIR Consent, Emergency Access).
 *
 * <p>This extension provides regulatory compliance for healthcare data management
 * following Nepal's Directive 2081 and Privacy Act 2075, along with FHIR R4
 * consent management and emergency access capabilities.</p>
 *
 * @doc.type class
 * @doc.purpose Healthcare consent policy extension — Nepal regulations + FHIR + emergency access
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class HealthcareConsentKernelExtension implements KernelExtension {

    private static final String EXTENSION_ID = "healthcare-consent";
    private static final String VERSION = "1.0.0";
    private static final String NAME = "Healthcare Consent Extension";

    private volatile KernelContext context;
    private volatile ConsentPolicyRegistry policyRegistry;

    @Override
    public String getExtensionId() {
        return EXTENSION_ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return new KernelDescriptor.Builder()
            .withKernelId(EXTENSION_ID)
            .withVersion(VERSION)
            .withCapability(KernelCapability.Core.SECURITY_FRAMEWORK)
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of(
            new KernelCapability(
                "consent.healthcare",
                "Healthcare Consent Management",
                "Nepal Directive 2081 and FHIR R4 compliant consent management",
                KernelCapability.CapabilityType.SECURITY,
                Map.of(
                    "standards", "directive-2081,privacy-act-2075,fhir-r4",
                    "granularity", "fine_grained",
                    "emergency_access", "true",
                    "audit_level", "detailed"
                )
            )
        );
    }

    @Override
    public void onModuleInitialized(KernelContext context) {
        this.context = context;
        this.policyRegistry = new ConsentPolicyRegistry();

        // Register Nepal Directive 2081 consent policy
        policyRegistry.register(new NepalHealthcareConsentPolicy());

        // Register FHIR R4 Consent policy
        policyRegistry.register(new FhirConsentPolicy());

        // Register Emergency Access policy for life-threatening situations
        policyRegistry.register(new EmergencyAccessConsentPolicy());

        // Make registry available via context if dependency system supports it
        if (context.hasDependency(ConsentPolicyRegistry.class)) {
            // Already registered
        }
    }

    @Override
    public void onModuleStarted(KernelContext context) {
        // Initialize consent validation pipelines
        // Start background consent status monitoring
    }

    @Override
    public void onModuleStopped(KernelContext context) {
        // Cleanup consent monitoring
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        // Compatible if host has security framework capability
        return hostModule.getCapabilities().stream()
            .anyMatch(c -> c.getCapabilityId().equals("security.framework") ||
                          c.getCapabilityId().equals("consent.healthcare"));
    }

    @Override
    public int getPriority() {
        return 100; // High priority for security-related extensions
    }

    /**
     * Gets the consent policy registry.
     *
     * @return the policy registry
     */
    public ConsentPolicyRegistry getPolicyRegistry() {
        return policyRegistry;
    }

    // ==================== Inner Classes for Policies ====================

    /**
     * Registry for consent policies.
     */
    public static class ConsentPolicyRegistry {
        private final Map<String, ConsentPolicy> policies = new java.util.concurrent.ConcurrentHashMap<>();

        public void register(ConsentPolicy policy) {
            policies.put(policy.getPolicyId(), policy);
        }

        public ConsentPolicy getPolicy(String policyId) {
            return policies.get(policyId);
        }

        public Set<String> getAllPolicyIds() {
            return Set.copyOf(policies.keySet());
        }
    }

    /**
     * Base interface for consent policies.
     */
    public interface ConsentPolicy {
        String getPolicyId();
        String getName();
        String getDescription();
        boolean requiresExplicitConsent();
        boolean allowsEmergencyAccess();
        int getDataRetentionYears();
    }

    /**
     * Nepal Directive 2081 healthcare consent policy.
     */
    public static class NepalHealthcareConsentPolicy implements ConsentPolicy {
        @Override
        public String getPolicyId() { return "nepal-directive-2081"; }

        @Override
        public String getName() { return "Nepal Directive 2081 Consent Policy"; }

        @Override
        public String getDescription() {
            return "Compliant with Nepal Directive 2081 for electronic health records management";
        }

        @Override
        public boolean requiresExplicitConsent() { return true; }

        @Override
        public boolean allowsEmergencyAccess() { return true; }

        @Override
        public int getDataRetentionYears() { return 7; }
    }

    /**
     * FHIR R4 Consent policy.
     */
    public static class FhirConsentPolicy implements ConsentPolicy {
        @Override
        public String getPolicyId() { return "fhir-r4-consent"; }

        @Override
        public String getName() { return "FHIR R4 Consent Policy"; }

        @Override
        public String getDescription() {
            return "FHIR R4 compliant consent resource and policy enforcement";
        }

        @Override
        public boolean requiresExplicitConsent() { return true; }

        @Override
        public boolean allowsEmergencyAccess() { return true; }

        @Override
        public int getDataRetentionYears() { return 7; }
    }

    /**
     * Emergency access consent policy for life-threatening situations.
     */
    public static class EmergencyAccessConsentPolicy implements ConsentPolicy {
        @Override
        public String getPolicyId() { return "emergency-access"; }

        @Override
        public String getName() { return "Emergency Access Consent Policy"; }

        @Override
        public String getDescription() {
            return "Break-glass access for life-threatening emergencies with full audit trail";
        }

        @Override
        public boolean requiresExplicitConsent() { return false; }

        @Override
        public boolean allowsEmergencyAccess() { return true; }

        @Override
        public int getDataRetentionYears() { return 10; } // Extended retention for audit
    }
}
