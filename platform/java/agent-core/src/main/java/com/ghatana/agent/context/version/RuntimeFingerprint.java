/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.context.version;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Fingerprint of runtime environment including JVM, OS, and platform versions.
 *
 * @doc.type record
 * @doc.purpose Fingerprint of runtime environment
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record RuntimeFingerprint(
        @NotNull String javaVersion,
        @NotNull String jvmVersion,
        @NotNull String osName,
        @NotNull String osVersion,
        @NotNull String osArch,
        @NotNull Map<String, String> additionalInfo,
        @NotNull String digest
) {
    public RuntimeFingerprint {
        Objects.requireNonNull(javaVersion, "javaVersion must not be null");
        Objects.requireNonNull(jvmVersion, "jvmVersion must not be null");
        Objects.requireNonNull(osName, "osName must not be null");
        Objects.requireNonNull(osVersion, "osVersion must not be null");
        Objects.requireNonNull(osArch, "osArch must not be null");
        Objects.requireNonNull(additionalInfo, "additionalInfo must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        additionalInfo = Map.copyOf(additionalInfo);
    }

    /**
     * Creates a runtime fingerprint from system properties.
     *
     * @return runtime fingerprint
     */
    @NotNull
    public static RuntimeFingerprint fromSystem() {
        String javaVersion = System.getProperty("java.version", "unknown");
        String jvmVersion = System.getProperty("java.vm.version", "unknown");
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");

        Map<String, String> additionalInfo = Map.of(
                "java.vendor", System.getProperty("java.vendor", "unknown"),
                "java.home", System.getProperty("java.home", "unknown"),
                "user.name", System.getProperty("user.name", "unknown"),
                "user.dir", System.getProperty("user.dir", "unknown")
        );

        String digest = computeDigest(javaVersion, jvmVersion, osName, osVersion, osArch, additionalInfo);
        return new RuntimeFingerprint(javaVersion, jvmVersion, osName, osVersion, osArch, additionalInfo, digest);
    }

    /**
     * Computes a digest from runtime information.
     *
     * @return digest string
     */
    @NotNull
    private static String computeDigest(
            @NotNull String javaVersion,
            @NotNull String jvmVersion,
            @NotNull String osName,
            @NotNull String osVersion,
            @NotNull String osArch,
            @NotNull Map<String, String> additionalInfo
    ) {
        return Integer.toHexString(Objects.hash(javaVersion, jvmVersion, osName, osVersion, osArch, additionalInfo));
    }

    /**
     * Returns true if this fingerprint matches another.
     *
     * @param other other fingerprint
     * @return true if digests match
     */
    public boolean matches(@NotNull RuntimeFingerprint other) {
        return this.digest.equals(other.digest);
    }
}
