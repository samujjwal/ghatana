package com.ghatana.appplatform.audit.domain;

import java.time.Instant;

/**
 * Receipt returned after a successful audit log write, carrying the cryptographic
 * proof of the hash chain position for this entry.
 *
 * @doc.type record
 * @doc.purpose Cryptographic receipt for an audit log write
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AuditReceipt(
    String auditId,
    long sequenceNumber,
    String previousHash,
    String currentHash,
    Instant timestamp
) {}
