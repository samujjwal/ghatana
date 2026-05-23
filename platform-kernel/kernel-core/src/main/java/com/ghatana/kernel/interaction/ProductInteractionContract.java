package com.ghatana.kernel.interaction;

import java.util.Set;

/**
 * Manifest-declared product interaction contract policy.
 *
 * <p>Represents the policy constraints declared in kernel-product.yaml for a
 * product interaction contract. Used by the broker to enforce manifest-level
 * policy independent of caller-supplied context.</p>
 *
 * @doc.type record
 * @doc.purpose Capture manifest-declared policy constraints for product interactions
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public record ProductInteractionContract(
        String contractId,
        String contractVersion,
        String providerProductId,
        Set<String> consumerProductIds,
        boolean requiresAuth,
        boolean requiresTenant,
        boolean requiresConsent,
        String piiClassification,
        String tenantScope,
        Set<String> allowedCallerRoles,
        Set<String> allowedPurposes,
        Set<String> allowedLifecyclePhases,
        boolean degradedModeAllowed
) {
    public ProductInteractionContract {
        if (contractId == null || contractId.isBlank()) {
            throw new IllegalArgumentException("contractId must not be blank");
        }
        if (contractVersion == null || contractVersion.isBlank()) {
            throw new IllegalArgumentException("contractVersion must not be blank");
        }
        if (providerProductId == null || providerProductId.isBlank()) {
            throw new IllegalArgumentException("providerProductId must not be blank");
        }
        if (consumerProductIds == null || consumerProductIds.isEmpty()) {
            throw new IllegalArgumentException("consumerProductIds must not be null or empty");
        }
    }
}
