package com.ghatana.appplatform.plugin.domain;

/**
 * Thrown when a plugin attempts an operation that violates its declared or
 * approved tier constraints (e.g. a T2 sandbox plugin trying to open a network
 * connection).
 *
 * @doc.type  class
 * @doc.purpose Signals plugin tier constraint violation at runtime
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public final class PluginTierViolationException extends RuntimeException {

    public PluginTierViolationException(String message) {
        super(message);
    }
}
