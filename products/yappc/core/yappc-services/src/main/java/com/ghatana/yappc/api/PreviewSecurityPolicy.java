/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

/**
 * Security policy for preview sessions, defining CSP, sandbox restrictions, and trust levels.
 *
 * <p>Preview sessions are a security boundary for generated/imported UI, source imports,
 * component execution, clipboard/download permissions, and semi-trusted/untrusted artifacts.
 * This policy enforces runtime security constraints on preview operations.
 *
 * @doc.type class
 * @doc.purpose Security policy for preview sessions with CSP, sandbox, and trust classification
 * @doc.layer api
 * @doc.pattern Policy
 */
public final class PreviewSecurityPolicy {

    private final ContentSecurityPolicy cspPolicy;
    private final SandboxPolicy sandboxPolicy;
    private final TrustLevel defaultTrustLevel;

    public PreviewSecurityPolicy(
            @NotNull ContentSecurityPolicy cspPolicy,
            @NotNull SandboxPolicy sandboxPolicy,
            @NotNull TrustLevel defaultTrustLevel
    ) {
        this.cspPolicy = cspPolicy;
        this.sandboxPolicy = sandboxPolicy;
        this.defaultTrustLevel = defaultTrustLevel;
    }

    public ContentSecurityPolicy cspPolicy() {
        return cspPolicy;
    }

    public SandboxPolicy sandboxPolicy() {
        return sandboxPolicy;
    }

    public TrustLevel defaultTrustLevel() {
        return defaultTrustLevel;
    }

    /**
     * Creates a production-safe preview security policy with strict defaults.
     */
    public static PreviewSecurityPolicy productionDefaults() {
        return policyForTrustLevel(TrustLevel.SEMI_TRUSTED);
    }

    /**
     * Creates a development preview security policy with relaxed defaults.
     */
    public static PreviewSecurityPolicy developmentDefaults() {
        return policyForTrustLevel(TrustLevel.TRUSTED_LOCAL);
    }

    /**
     * Gets the appropriate security policy for a given trust level.
     * 
     * @param trustLevel the trust level
     * @return security policy configured for the trust level
     */
    public static PreviewSecurityPolicy policyForTrustLevel(TrustLevel trustLevel) {
        ContentSecurityPolicy csp = getCspForTrustLevel(trustLevel);
        SandboxPolicy sandbox = getSandboxForTrustLevel(trustLevel);
        return new PreviewSecurityPolicy(csp, sandbox, trustLevel);
    }

    /**
     * Gets the CSP policy for a given trust level.
     */
    private static ContentSecurityPolicy getCspForTrustLevel(TrustLevel trustLevel) {
        return switch (trustLevel) {
            case TRUSTED_LOCAL -> new ContentSecurityPolicy(
                false, // minimal CSP for local trusted artifacts
                "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' https: wss:; font-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self';"
            );
            case TRUSTED_CONTROLLED -> new ContentSecurityPolicy(
                true, // moderate CSP for controlled sources
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' https:; font-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none';"
            );
            case SEMI_TRUSTED -> new ContentSecurityPolicy(
                true, // strict CSP for semi-trusted artifacts
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; font-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none';"
            );
            case UNTRUSTED -> new ContentSecurityPolicy(
                true, // maximum CSP for untrusted artifacts
                "default-src 'self'; script-src 'none'; style-src 'self'; img-src 'self' data:; connect-src 'none'; font-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'none'; frame-ancestors 'none';"
            );
        };
    }

    /**
     * Gets the sandbox policy for a given trust level.
     */
    private static SandboxPolicy getSandboxForTrustLevel(TrustLevel trustLevel) {
        return switch (trustLevel) {
            case TRUSTED_LOCAL -> new SandboxPolicy(
                false, // minimal sandbox for local trusted artifacts
                true,  // allow-scripts
                true,  // allow-modals
                true,  // allow-popups
                true,  // allow-forms
                true,  // allow-same-origin
                true   // allow-downloads
            );
            case TRUSTED_CONTROLLED -> new SandboxPolicy(
                true,  // moderate sandbox for controlled sources
                true,  // allow-scripts
                false, // allow-modals
                false, // allow-popups
                true,  // allow-forms
                true,  // allow-same-origin
                true   // allow-downloads
            );
            case SEMI_TRUSTED -> new SandboxPolicy(
                true,  // strict sandbox for semi-trusted artifacts
                true,  // allow-scripts
                false, // allow-modals
                false, // allow-popups
                false, // allow-forms
                false, // allow-same-origin
                true   // allow-downloads
            );
            case UNTRUSTED -> new SandboxPolicy(
                true,  // maximum sandbox for untrusted artifacts
                false, // allow-scripts - disabled for untrusted
                false, // allow-modals
                false, // allow-popups
                false, // allow-forms
                false, // allow-same-origin
                false  // allow-downloads - disabled for untrusted
            );
        };
    }

    /**
     * Content Security Policy configuration for preview runtime.
     */
    public record ContentSecurityPolicy(
            boolean enabled,
            String policyString
    ) {
        public ContentSecurityPolicy {
            if (enabled && (policyString == null || policyString.isBlank())) {
                throw new IllegalArgumentException("CSP policy string is required when CSP is enabled");
            }
        }
    }

    /**
     * Sandbox policy configuration for preview runtime.
     */
    public record SandboxPolicy(
            boolean enabled,
            boolean allowScripts,
            boolean allowModals,
            boolean allowPopups,
            boolean allowForms,
            boolean allowSameOrigin,
            boolean allowDownloads
    ) {}

    /**
     * Trust classification for preview artifacts.
     */
    public enum TrustLevel {
        /**
         * Trusted local - artifacts generated locally by the user's workspace.
         * Minimal sandbox restrictions, full feature access.
         */
        TRUSTED_LOCAL,

        /**
         * Trusted controlled - artifacts from controlled sources with validation.
         * Moderate sandbox restrictions, limited external network access.
         */
        TRUSTED_CONTROLLED,

        /**
         * Semi-trusted - artifacts from external sources with partial validation.
         * Strict sandbox restrictions, no external network access, limited APIs.
         */
        SEMI_TRUSTED,

        /**
         * Untrusted - artifacts from unknown or unvalidated sources.
         * Maximum sandbox restrictions, isolated execution, no external access.
         */
        UNTRUSTED
    }

    /**
     * Data classification for preview content.
     */
    public enum DataClassification {
        /**
         * No sensitive data, can be displayed without restrictions.
         */
        PUBLIC,

        /**
         * Internal company data, not for external distribution.
         */
        INTERNAL,

        /**
         * Confidential business data, requires access control.
         */
        CONFIDENTIAL,

        /**
         * Highly sensitive data (PII, secrets), requires strict controls.
         */
        RESTRICTED
    }
}
