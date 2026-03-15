package com.ghatana.appplatform.plugin.manifest;

/**
 * Thrown when the current platform version does not satisfy the semver range
 * declared in a plugin manifest's {@code platformVersionRange} field.
 *
 * @doc.type  class
 * @doc.purpose Signals plugin-platform version incompatibility
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public final class PluginVersionIncompatibleException extends RuntimeException {

    public PluginVersionIncompatibleException(String message) {
        super(message);
    }
}
