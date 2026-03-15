package com.ghatana.appplatform.plugin.domain;

/**
 * A capability that a plugin declares it requires.
 *
 * <p>Capabilities gate what a plugin may do at runtime. Every API call from a plugin
 * is checked against its declared capabilities before execution.
 *
 * <p>High-risk capabilities ({@code EXECUTE_NETWORK}, {@code WRITE_DATA}) require
 * security-team approval before the plugin enters {@link PluginStatus#ACTIVE}.
 *
 * @doc.type  record
 * @doc.purpose Declares a single capability needed by a plugin
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public record PluginCapability(String name, String parameter) {

    // ── well-known capability names ──────────────────────────────────────────

    /** Read K-02 configuration values (read-only). */
    public static final String READ_CONFIG    = "READ_CONFIG";

    /** Read K-15 calendar / holiday data (read-only). */
    public static final String READ_CALENDAR  = "READ_CALENDAR";

    /** Query reference data from D-11 (read-only). */
    public static final String QUERY_REF_DATA = "QUERY_REF_DATA";

    /**
     * Establish outbound network connections.
     * {@code parameter} must name the allowed endpoint (URL prefix).
     * <b>High-risk</b> — requires security-team approval.
     */
    public static final String EXECUTE_NETWORK = "EXECUTE_NETWORK";

    /**
     * Write data to platform storage.
     * <b>High-risk</b> — requires security-team approval.
     */
    public static final String WRITE_DATA = "WRITE_DATA";

    /** Emit log entries. */
    public static final String EMIT_LOGS = "EMIT_LOGS";

    /** Use the periodic timer API (heartbeat scheduling). */
    public static final String TIMER = "TIMER";

    // ── factory helpers ──────────────────────────────────────────────────────

    /** Creates a simple capability without a parameter (e.g. READ_CONFIG). */
    public static PluginCapability of(String name) {
        return new PluginCapability(name, null);
    }

    /** Creates a parameterised capability (e.g. EXECUTE_NETWORK with an endpoint). */
    public static PluginCapability of(String name, String parameter) {
        return new PluginCapability(name, parameter);
    }

    /** Returns {@code true} when this capability is classified as high-risk. */
    public boolean isHighRisk() {
        return EXECUTE_NETWORK.equals(name) || WRITE_DATA.equals(name);
    }

    @Override
    public String toString() {
        return parameter == null ? name : name + ":" + parameter;
    }
}
