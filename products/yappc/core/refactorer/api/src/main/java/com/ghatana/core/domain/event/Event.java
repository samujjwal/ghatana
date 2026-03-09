/*
 * Placeholder removed to avoid re-introducing a product-local Event type that
 * would conflict with the canonical Event interface in :libs:domain-models.
 *
 * This file intentionally does not declare a public Event type. If a
 * product-specific event helper is required, place it in
 * com.ghatana.refactorer.local.event.LocalEvent (a separate package).
 */
package com.ghatana.core.domain.event;

/**

 * @doc.type class

 * @doc.purpose Handles removed event marker operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

final class RemovedEventMarker {
    // Intentionally empty. Kept to avoid accidental re-creation of Event.java
}
