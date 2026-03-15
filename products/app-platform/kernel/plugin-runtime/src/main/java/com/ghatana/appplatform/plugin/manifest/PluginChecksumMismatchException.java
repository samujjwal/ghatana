package com.ghatana.appplatform.plugin.manifest;

/**
 * Thrown when the computed SHA-256 checksum of a plugin artifact does not match
 * the checksum declared in the manifest.
 *
 * @doc.type  class
 * @doc.purpose Signals plugin artifact integrity failure
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public final class PluginChecksumMismatchException extends RuntimeException {

    public PluginChecksumMismatchException(String message) {
        super(message);
    }
}
