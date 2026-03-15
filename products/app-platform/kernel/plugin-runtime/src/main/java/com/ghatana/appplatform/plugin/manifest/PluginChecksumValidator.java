package com.ghatana.appplatform.plugin.manifest;

import com.ghatana.appplatform.plugin.domain.PluginManifest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Validates that the SHA-256 checksum of the plugin artifact matches the value
 * declared in {@link PluginManifest#artifactChecksum()}.
 *
 * <p>Checksum format: lowercase hex-encoded SHA-256 digest (64 characters).
 *
 * @doc.type  class
 * @doc.purpose Plugin artifact integrity check via SHA-256 comparison
 * @doc.layer  product
 * @doc.pattern Service
 */
public final class PluginChecksumValidator {

    private static final String ALGORITHM = "SHA-256";

    /**
     * Computes the SHA-256 of {@code artifactBytes} and compares it against
     * the checksum declared in {@code manifest}.
     *
     * @param manifest      manifest carrying the expected checksum
     * @param artifactBytes raw plugin artifact content
     * @return {@code true} when the computed checksum matches the manifest value
     * @throws PluginChecksumMismatchException when the checksums differ
     */
    public boolean validate(PluginManifest manifest, byte[] artifactBytes) {
        Objects.requireNonNull(manifest,      "manifest");
        Objects.requireNonNull(artifactBytes, "artifactBytes");

        String expected = manifest.artifactChecksum();
        if (expected == null || expected.isBlank()) {
            throw new PluginChecksumMismatchException(
                    "No checksum declared in manifest for plugin: " + manifest.name());
        }

        String computed = computeHex(artifactBytes);
        if (!computed.equalsIgnoreCase(expected)) {
            throw new PluginChecksumMismatchException(
                    "Checksum mismatch for plugin '" + manifest.name()
                    + "': expected=" + expected + " computed=" + computed);
        }
        return true;
    }

    private String computeHex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
