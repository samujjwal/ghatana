/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.error;

/**
 * Thrown when event schema version is incompatible with the current engine (AEP-005).
 *
 * @doc.type class
 * @doc.purpose Signals event version incompatibility requiring migration
 * @doc.layer product
 * @doc.pattern Exception
 * @since 1.2.0
 */
public final class AepVersionException extends AepException {

    private final int actualVersion;
    private final int minSupportedVersion;
    private final int maxSupportedVersion;

    public AepVersionException(String tenantId, String eventType,
                               int actualVersion, int minSupported, int maxSupported) {
        super(tenantId, "version.check",
              String.format("Unsupported event version %d for type '%s'; supported range [%d, %d]",
                  actualVersion, eventType, minSupported, maxSupported));
        this.actualVersion       = actualVersion;
        this.minSupportedVersion = minSupported;
        this.maxSupportedVersion = maxSupported;
    }

    /** @return the version number found in the event */
    public int actualVersion() {
        return actualVersion;
    }

    /** @return minimum accepted version number */
    public int minSupportedVersion() {
        return minSupportedVersion;
    }

    /** @return maximum accepted version number */
    public int maxSupportedVersion() {
        return maxSupportedVersion;
    }
}
