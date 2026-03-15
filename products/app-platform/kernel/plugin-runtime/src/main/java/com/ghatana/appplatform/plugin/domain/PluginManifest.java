package com.ghatana.appplatform.plugin.domain;

import java.util.List;
import java.util.Objects;

/**
 * Immutable plugin manifest declaring identity, security, and runtime requirements.
 *
 * <p>The manifest is serialised to JSON, placed inside the plugin artifact, and
 * signed with an Ed25519 key before distribution. The platform verifies the
 * signature before loading any plugin.
 *
 * <p>Required fields:
 * <ul>
 *   <li>{@code name} — unique plugin name (reverse-domain recommended).</li>
 *   <li>{@code version} — semantic version.</li>
 *   <li>{@code tier} — T1 / T2 / T3.</li>
 *   <li>{@code author} — publisher identity string.</li>
 *   <li>{@code entryPoint} — class or script entry point.</li>
 *   <li>{@code artifactChecksum} — SHA-256 hex of the plugin artifact bytes.</li>
 *   <li>{@code platformVersionRange} — semver range, e.g. {@code ">=2.0.0 <3.0.0"}.</li>
 * </ul>
 *
 * @doc.type  record
 * @doc.purpose Carries all metadata needed to verify and load a plugin
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public record PluginManifest(
        String name,
        PluginVersion version,
        PluginTier tier,
        String author,
        String entryPoint,
        String artifactChecksum,
        String platformVersionRange,
        List<PluginCapability> capabilities,
        List<String> dependsOn,
        PluginResourceQuota resourceQuota,
        /** Optional Ed25519 signature (base64) over the canonical JSON form of the manifest. */
        String signature,
        /** Key ID identifying the trusted publisher key that produced the signature. */
        String signingKeyId,
        /** Optional migration script name for state migration on version upgrade. */
        String migrationScript
) {

    public PluginManifest {
        Objects.requireNonNull(name,                "name");
        Objects.requireNonNull(version,             "version");
        Objects.requireNonNull(tier,                "tier");
        Objects.requireNonNull(author,              "author");
        Objects.requireNonNull(entryPoint,          "entryPoint");
        Objects.requireNonNull(artifactChecksum,    "artifactChecksum");
        Objects.requireNonNull(platformVersionRange, "platformVersionRange");
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        dependsOn    = dependsOn    == null ? List.of() : List.copyOf(dependsOn);
        if (resourceQuota == null) resourceQuota = PluginResourceQuota.DEFAULT;
    }

    /** Returns {@code true} when the manifest carries an Ed25519 signature. */
    public boolean isSigned() {
        return signature != null && !signature.isBlank();
    }

    /** Convenience: collect all high-risk capabilities declared by this plugin. */
    public List<PluginCapability> highRiskCapabilities() {
        return capabilities.stream().filter(PluginCapability::isHighRisk).toList();
    }
}
