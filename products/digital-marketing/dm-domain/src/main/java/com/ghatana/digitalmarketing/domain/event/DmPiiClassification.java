package com.ghatana.digitalmarketing.domain.event;

/**
 * PII classification levels for DMOS domain events.
 *
 * <p>Every {@link DmEvent} carries a PII classification that drives routing,
 * retention, and redaction policy. Higher classifications require additional
 * handling controls (field-level encryption, shorter retention windows, consent
 * proof references).</p>
 *
 * @doc.type class
 * @doc.purpose DMOS event PII classification for F2 event schema (DMOS-F2-001)
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum DmPiiClassification {

    /**
     * No personal information present. Event payload contains only system IDs
     * and aggregate metrics. Suitable for unrestricted analytics and long retention.
     */
    NONE,

    /**
     * Pseudonymous identifiers only (workspace IDs, internal lead IDs, campaign IDs).
     * No direct identifiers such as names or email addresses.
     */
    PSEUDONYMOUS,

    /**
     * Direct personal identifiers present (email, phone, full name, IP address).
     * Requires field-level access control and consent proof reference when processing.
     */
    PERSONAL,

    /**
     * Sensitive personal information present (health, financial, identity documents).
     * Requires explicit consent reference, encryption at rest, and shortest retention.
     */
    SENSITIVE
}
