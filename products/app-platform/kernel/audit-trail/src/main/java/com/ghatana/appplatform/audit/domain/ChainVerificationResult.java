package com.ghatana.appplatform.audit.domain;

import java.util.List;

/**
 * Result of a hash-chain verification pass over a range of audit log entries.
 *
 * @doc.type record
 * @doc.purpose Chain verification result for hash-chain validation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ChainVerificationResult(
    boolean valid,
    List<ChainViolation> violations
) {

    /** A single detected hash mismatch position in the chain. */
    public record ChainViolation(
        long sequenceNumber,
        String expectedHash,
        String actualHash
    ) {}

    public static ChainVerificationResult ok() {
        return new ChainVerificationResult(true, List.of());
    }
}
