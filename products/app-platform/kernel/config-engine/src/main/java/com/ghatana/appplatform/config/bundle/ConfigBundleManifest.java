package com.ghatana.appplatform.config.bundle;

import java.time.Instant;
import java.util.Objects;

/**
 * Manifest for a config bundle, including provenance, content hash,
 * and optional digital signature.
 *
 * <p>The {@code contentHash} is the SHA-256 hex digest of the canonical JSON
 * encoding of the bundle's {@code schemas} and {@code entries} arrays (in that
 * order, without the manifest itself). Verifying the hash detects accidental or
 * malicious modification of bundle contents.
 *
 * @param bundleId       UUID identifying this bundle export uniquely
 * @param environment    target environment label (e.g. "production-np", "staging")
 * @param formatVersion  schema version of the bundle format (currently "1.0")
 * @param generatedAt    UTC timestamp when this bundle was exported
 * @param generatedBy    user or service that requested the export
 * @param entryCount     total number of config entries in the bundle
 * @param schemaCount    total number of schema definitions in the bundle
 * @param contentHash    SHA-256 hex digest of the canonical bundle content
 * @param signature      optional Ed25519 signature; null for unsigned bundles
 *
 * @doc.type record
 * @doc.purpose Provenance and integrity metadata for a config bundle
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ConfigBundleManifest(
    String bundleId,
    String environment,
    String formatVersion,
    Instant generatedAt,
    String generatedBy,
    int entryCount,
    int schemaCount,
    String contentHash,
    BundleSignature signature   // null = unsigned
) {
    static final String CURRENT_FORMAT_VERSION = "1.0";

    public ConfigBundleManifest {
        Objects.requireNonNull(bundleId, "bundleId");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(generatedBy, "generatedBy");
        Objects.requireNonNull(contentHash, "contentHash");
        if (environment.isBlank()) throw new IllegalArgumentException("environment must not be blank");
        if (generatedBy.isBlank()) throw new IllegalArgumentException("generatedBy must not be blank");
    }

    /** Returns a copy of this manifest with the given signature attached. */
    public ConfigBundleManifest withSignature(BundleSignature sig) {
        return new ConfigBundleManifest(
            bundleId, environment, formatVersion, generatedAt, generatedBy,
            entryCount, schemaCount, contentHash, sig);
    }

    /** Returns true if this manifest carries a digital signature. */
    public boolean isSigned() {
        return signature != null;
    }
}
