/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.spec;

/**
 * Thrown when an {@code agentSpecVersion} value is recognised but not supported
 * by the current version of {@link AgentSpecLoader}.
 *
 * <p>The exception message always includes the unsupported version string and
 * the set of currently supported versions so that callers can provide an
 * actionable migration hint.
 *
 * @doc.type class
 * @doc.purpose Signals that the spec format version is not supported by this loader
 * @doc.layer platform
 * @doc.pattern Exception
 */
public final class UnsupportedSpecVersionException extends RuntimeException {

    private final String unsupportedVersion;

    /**
     * Creates an exception for the given unsupported spec version.
     *
     * @param unsupportedVersion the version string that caused the rejection
     * @param supportedVersions  comma-separated list of supported version strings
     */
    public UnsupportedSpecVersionException(String unsupportedVersion, String supportedVersions) {
        super("Unsupported agentSpecVersion '" + unsupportedVersion + "'. "
                + "Supported versions: [" + supportedVersions + "]. "
                + "Upgrade or re-compile the spec to a supported version.");
        this.unsupportedVersion = unsupportedVersion;
    }

    /**
     * Returns the version string that triggered this exception.
     *
     * @return the unsupported version string
     */
    public String getUnsupportedVersion() {
        return unsupportedVersion;
    }
}
