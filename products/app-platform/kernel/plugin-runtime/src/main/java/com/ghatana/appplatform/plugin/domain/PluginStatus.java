package com.ghatana.appplatform.plugin.domain;

/**
 * Lifecycle status of a registered plugin.
 *
 * @doc.type  enum
 * @doc.purpose Represents the operational state of a plugin in the registry
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public enum PluginStatus {

    /** Registered but awaiting capability approval. */
    PENDING_APPROVAL,

    /** Capabilities approved; plugin loaded and serving requests. */
    ACTIVE,

    /** Temporarily disabled by operator; can be reactivated. */
    SUSPENDED,

    /** Permanently deregistered; cannot be reactivated without re-registration. */
    DISABLED
}
