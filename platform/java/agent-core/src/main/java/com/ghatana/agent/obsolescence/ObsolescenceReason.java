/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

/**
 * Reasons why a mastery item may be obsolete.
 *
 * @doc.type enum
 * @doc.purpose Reasons for obsolescence
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum ObsolescenceReason {
    /**
     * Documentation contradicts actual behavior.
     */
    DOCUMENTATION_CONTRADICTION,

    /**
     * API has changed and the procedure no longer matches.
     */
    API_CHANGE,

    /**
     * Library/framework version is no longer supported.
     */
    VERSION_MISMATCH,

    /**
     * Runtime environment has changed.
     */
    RUNTIME_INCOMPATIBILITY,

    /**
     * Repeated failures when using the procedure.
     */
    REPEATED_FAILURES,

    /**
     * Security vulnerability detected.
     */
    SECURITY_VULNERABILITY,

    /**
     * Better alternative exists.
     */
    SUPERSEDED_BY_ALTERNATIVE,

    /**
     * Pattern is deprecated by the framework.
     */
    DEPRECATED_PATTERN,

    /**
     * Dependency is no longer maintained.
     */
    DEPRECATED_DEPENDENCY
}
