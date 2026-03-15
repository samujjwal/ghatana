package com.ghatana.appplatform.plugin.manifest;

/**
 * Thrown when a plugin manifest's Ed25519 signature cannot be verified against
 * any trusted publisher key.
 *
 * @doc.type  class
 * @doc.purpose Signals manifest signature verification failure
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public final class PluginSignatureException extends RuntimeException {

    public PluginSignatureException(String message) {
        super(message);
    }

    public PluginSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
