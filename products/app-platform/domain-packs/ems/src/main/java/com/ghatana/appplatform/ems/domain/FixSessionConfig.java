/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ems.domain;

/**
 * Per-counterparty FIX session configuration parameters.
 *
 * <p>Values default to standard FIX 4.4 recommendations when not overridden.
 * The composition root wires these from config-engine lookups (namespace "ems.fix").
 *
 * @param heartbeatIntervalSeconds heartbeat interval in seconds (FIX tag 108)
 * @param sessionTimeoutSeconds    timeout before marking a session as DISCONNECTED
 *
 * @doc.type record
 * @doc.purpose Configurable FIX session parameters (K02 / D02-010)
 * @doc.layer Domain
 * @doc.pattern ValueObject
 */
public record FixSessionConfig(
        int heartbeatIntervalSeconds,
        int sessionTimeoutSeconds
) {
    /** Default config: 30s heartbeat, 60s timeout. */
    public static final FixSessionConfig DEFAULT = new FixSessionConfig(30, 60);

    public FixSessionConfig {
        if (heartbeatIntervalSeconds <= 0) {
            throw new IllegalArgumentException("heartbeatIntervalSeconds must be > 0");
        }
        if (sessionTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("sessionTimeoutSeconds must be > 0");
        }
    }
}
