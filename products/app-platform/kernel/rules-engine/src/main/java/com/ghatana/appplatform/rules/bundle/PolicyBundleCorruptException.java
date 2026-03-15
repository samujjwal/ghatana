/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.bundle;

/**
 * Thrown when a policy bundle's content does not match its stored SHA-256 hash (K03-002).
 *
 * <p>Indicates storage corruption or tampering. OPA should retain the previously active
 * bundle when this exception is raised.
 *
 * @doc.type class
 * @doc.purpose Exception for corrupt or tampered policy bundle content
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class PolicyBundleCorruptException extends RuntimeException {

    private final String bundleId;

    public PolicyBundleCorruptException(String bundleId) {
        super("Policy bundle content does not match stored hash: bundleId=" + bundleId);
        this.bundleId = bundleId;
    }

    /** @return the bundle ID that failed integrity verification */
    public String bundleId() {
        return bundleId;
    }
}
