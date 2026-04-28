package com.ghatana.yappc.domain.status;

/**
 * Marker interface for YAPPC domain lifecycle status enums.
 *
 * <p>All canonical YAPPC status enums implement this interface so that generic
 * utilities (loggers, serialisers, observability bridges) can operate on status
 * values without a concrete type dependency.</p>
 *
 * <p>Implementors must be {@code enum} types. The interface purposely carries no
 * methods so that each enum remains free to add domain-specific behaviour.</p>
 *
 * @doc.type interface
 * @doc.purpose Marker for YAPPC canonical lifecycle status enums
 * @doc.layer domain
 * @doc.pattern Marker Interface
 */
public interface Lifecycle {
    // marker — implemented by all canonical YAPPC status enums
}
