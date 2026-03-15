package com.ghatana.appplatform.plugin.domain;

/**
 * Defines the sandbox isolation tier for a plugin.
 *
 * <p>Tier determines what the plugin can do at runtime:
 * <ul>
 *   <li>T1 — data-only (JSON/YAML configurations). No code executed.</li>
 *   <li>T2 — scripted rules in a sandboxed runtime (restricted API, no network/filesystem).</li>
 *   <li>T3 — fully trusted, network-capable container (must declare all capabilities).</li>
 * </ul>
 *
 * @doc.type  enum
 * @doc.purpose Declares the allowed execution environment for a plugin
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public enum PluginTier {

    /** Data-only: JSON/YAML configs loaded through K-02. No code execution. */
    T1,

    /** Scripted rules: V8/WASM sandbox with read-only API surface. No network. */
    T2,

    /** Network-enabled container: declares capabilities, egress whitelisted. */
    T3
}
