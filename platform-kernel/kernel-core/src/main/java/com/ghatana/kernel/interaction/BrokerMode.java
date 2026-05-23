package com.ghatana.kernel.interaction;

/**
 * Broker execution mode controlling evidence and policy enforcement.
 *
 * <p>Mode determines whether evidence persistence is mandatory and how strictly
 * policy context is validated. Production mode requires real evidence writers
 * and trusted policy context resolution.</p>
 *
 * @doc.type enum
 * @doc.purpose Control broker execution mode for evidence and policy enforcement
 * @doc.layer kernel
 * @doc.pattern Enumeration
 */
public enum BrokerMode {
    /**
     * Production mode with mandatory evidence persistence and trusted policy context.
     * No-op evidence writers are rejected. Caller-supplied policy context is ignored.
     */
    PRODUCTION,

    /**
     * Development mode with mandatory evidence persistence but relaxed policy context.
     * No-op evidence writers are rejected. Caller-supplied policy context is allowed.
     */
    DEVELOPMENT,

    /**
     * Test mode allowing no-op evidence writers and relaxed policy context.
     * Only for unit tests and integration tests.
     */
    TEST
}
