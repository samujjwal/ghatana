/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.runtime;

import java.util.Locale;

/**
 * Execution environment profile for Data Cloud runtime.
 *
 * <p>DC-P1-02: Classifies the runtime context to enforce production hardening rules.
 * Fallback behaviors are only permitted in LOCAL and TEST profiles.
 *
 * <p>Execution Profiles:
 * <ul>
 *   <li><strong>LOCAL</strong>: Developer machine (localhost); relaxed rules for development
 *   <li><strong>TEST</strong>: Automated testing; in-memory implementations allowed
 *   <li><strong>EMBEDDED</strong>: Embedded runtime (custom integrations); limited fallbacks
 *   <li><strong>STAGING</strong>: Pre-production staging; strict validation with durable backing
 *   <li><strong>PRODUCTION</strong>: Production deployment; zero fallbacks, strict enforcement
 *   <li><strong>SOVEREIGN</strong>: Air-gapped sovereign deployment; strict enforcement + compliance
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Runtime execution profile classification
 * @doc.layer product
 * @doc.pattern Configuration
 */
public enum RuntimeProfile {
    /**
     * Local development machine (localhost).
     *
     * <p>Allows:
     * - InMemoryContextStore
     * - In-memory event storage
     * - Relaxed authentication (dev tokens)
     * - Debug logging
     * - Long operation timeouts
     *
     * <p>Restrictions: None (development-friendly)
     */
    LOCAL,

    /**
     * Automated testing environment.
     *
     * <p>Allows:
     * - InMemoryContextStore
     * - Test mocks and stubs
     * - Simplified validation
     * - Testcontainers for integration tests
     *
     * <p>Restrictions:
     * - Must use explicit test profiles
     * - No real production secrets
     */
    TEST,

    /**
     * Embedded runtime for custom integrations.
     *
     * <p>Allows:
     * - InMemoryContextStore (with warning)
     * - Limited fallback modes
     *
     * <p>Restrictions:
     * - Must configure durable backing stores explicitly
     * - Validation enabled
     * - No test-only features
     */
    EMBEDDED,

    /**
     * Staging/pre-production environment.
     *
     * <p>Allows:
     * - Full durable persistence
     * - JDBC-backed context store
     * - Full production validation
     *
     * <p>Restrictions:
     * - No in-memory fallbacks
     * - Full audit logging
     * - Policy evaluation required
     * - Operator review gates for sensitive operations
     */
    STAGING,

    /**
     * Production deployment.
     *
     * <p>Allows:
     * - JDBC-backed context store (required)
     * - Full durable event storage
     * - Complete policy enforcement
     * - Structured logging and tracing
     *
     * <p>Restrictions:
     * - ZERO in-memory fallbacks
     * - ZERO test-only features
     * - All operations must be auditableFailure modes must not degrade silently
     * - Secrets must come from secure vaults
     * - Operator intervention required for high-risk operations
     */
    PRODUCTION,

    /**
     * Air-gapped sovereign deployment (e.g., government, healthcare).
     *
     * <p>Allows:
     * - JDBC-backed context store (required)
     * - Full durable event storage
     * - Strict compliance enforcement (HIPAA, FedRAMP, etc.)
     * - Complete audit trail
     *
     * <p>Restrictions:
     * - ZERO in-memory fallbacks
     * - All data must remain within sovereign boundary
     * - Enhanced encryption requirements
     * - Compliance certifications required
     * - No external service calls without approval
     */
    SOVEREIGN;

    /**
     * Check if this profile allows in-memory fallbacks.
     *
     * @return true if in-memory implementations are permitted
     */
    public boolean allowsInMemoryFallback() {
        return this == LOCAL || this == TEST;
    }

    /**
     * Check if this profile is production-like (requires strict validation).
     *
     * @return true if production-level restrictions apply
     */
    public boolean isProduction() {
        return this == PRODUCTION || this == STAGING || this == SOVEREIGN;
    }

    /**
     * Check if this profile requires durable storage.
     *
     * @return true if in-memory implementations are forbidden
     */
    public boolean requiresDurableStorage() {
        return this == PRODUCTION || this == STAGING || this == SOVEREIGN;
    }

    /**
     * Check if this profile allows debug/relaxed modes.
     *
     * @return true if development-friendly features are permitted
     */
    public boolean isDebugAllowed() {
        return this == LOCAL || this == TEST;
    }

    /**
     * Check if this profile requires external compliance validation.
     *
     * @return true if sovereign/compliance rules apply
     */
    public boolean requiresComplianceValidation() {
        return this == SOVEREIGN;
    }

    /**
     * Resolve the active runtime profile.
     *
     * <p>Resolution order:
     * 1. System property: {@code -Ddc.runtime.profile=LOCAL}
     * 2. Environment variable: {@code DC_RUNTIME_PROFILE=LOCAL}
    * 3. Application config supplied by launcher/bootstrap wiring
     * 4. Default: {@code LOCAL} (safe for development)
     *
     * @return the resolved runtime profile
     */
    public static RuntimeProfile resolve() {
        // Try system property first
        String sysProp = System.getProperty("dc.runtime.profile");
        if (sysProp != null && !sysProp.isBlank()) {
            try {
                return RuntimeProfile.valueOf(sysProp.trim().toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                // Invalid value, fall through to env var
            }
        }

        // Try environment variable
        String envVar = System.getenv("DC_RUNTIME_PROFILE");
        if (envVar != null && !envVar.isBlank()) {
            try {
                return RuntimeProfile.valueOf(envVar.trim().toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                // Invalid value, fall through to default
            }
        }

        // Default: LOCAL (development-friendly)
        return LOCAL;
    }

    /**
     * Validate that a fallback behavior is permitted in the current profile.
     *
     * @param fallbackName Name of the fallback (for error messages)
     * @param activeProfile Current runtime profile
     * @throws IllegalStateException if fallback is not allowed
     */
    public static void validateFallbackPermitted(String fallbackName, RuntimeProfile activeProfile) {
        if (!activeProfile.allowsInMemoryFallback()) {
            throw new IllegalStateException(
                    "Fallback not permitted in " + activeProfile + " profile: " + fallbackName + ". " +
                    "Fallbacks are only allowed in LOCAL and TEST profiles.");
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ENGLISH);
    }
}
