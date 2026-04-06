package com.ghatana.kernel.plugin.runtime;

import io.activej.promise.Promise;

import java.nio.file.Path;

/**
 * Plugin security manager interface for verifying plugin authenticity.
 *
 * @doc.type interface
 * @doc.purpose Plugin signature verification and security enforcement
 * @doc.layer core
 * @doc.pattern Security
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface PluginSecurityManager {

    /**
     * Verify the cryptographic signature of a plugin JAR.
     *
     * @param pluginJar path to the plugin JAR file
     * @return promise resolving to true if the signature is valid
     */
    Promise<Boolean> verifyPluginSignature(Path pluginJar);
}
