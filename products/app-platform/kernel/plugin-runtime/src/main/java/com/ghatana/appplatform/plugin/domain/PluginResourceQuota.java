package com.ghatana.appplatform.plugin.domain;

/**
 * Resource quota limits declared in a plugin manifest.
 *
 * <p>These limits are enforced at runtime:
 * <ul>
 *   <li>T2 plugins: enforced via sandbox limits (memory, wall-clock timeout).</li>
 *   <li>T3 plugins: enforced via cgroups / Kubernetes resource quotas.</li>
 * </ul>
 *
 * @doc.type  record
 * @doc.purpose Carries the resource quota configuration for a plugin
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public record PluginResourceQuota(
        /** Max heap memory in megabytes (default 64). */
        int maxMemoryMb,

        /** Max CPU execution wall-clock timeout per invocation in milliseconds (default 100). */
        int maxCpuMs,

        /** Max outbound API calls per minute (default 0 = unlimited for T1/T2). */
        int maxApiCallsPerMinute,

        /** Max inbound/outbound payload size per call in kilobytes (default 256). */
        int maxPayloadKb
) {

    /** Default quota applied when none is specified in the manifest. */
    public static final PluginResourceQuota DEFAULT = new PluginResourceQuota(64, 100, 0, 256);

    public PluginResourceQuota {
        if (maxMemoryMb <= 0) throw new IllegalArgumentException("maxMemoryMb must be > 0");
        if (maxCpuMs <= 0)    throw new IllegalArgumentException("maxCpuMs must be > 0");
        if (maxPayloadKb <= 0) throw new IllegalArgumentException("maxPayloadKb must be > 0");
    }
}
