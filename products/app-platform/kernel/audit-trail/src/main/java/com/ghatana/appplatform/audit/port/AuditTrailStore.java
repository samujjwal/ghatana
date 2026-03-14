package com.ghatana.appplatform.audit.port;

import com.ghatana.appplatform.audit.domain.AuditEntry;
import com.ghatana.appplatform.audit.domain.AuditReceipt;
import com.ghatana.appplatform.audit.domain.ChainVerificationResult;
import io.activej.promise.Promise;

/**
 * Hexagonal port — cryptographic audit store operations.
 *
 * <p>Implementations must persist entries to an append-only store, maintain the
 * per-tenant SHA-256 hash chain, and support chain integrity verification.
 * All operations return ActiveJ {@link Promise}; blocking I/O must be wrapped
 * in {@code Promise.ofBlocking} by the adapter.
 *
 * @doc.type interface
 * @doc.purpose Hexagonal port for the cryptographic audit trail store
 * @doc.layer product
 * @doc.pattern Port
 */
public interface AuditTrailStore {

    /**
     * Append an audit entry and advance the per-tenant hash chain.
     *
     * @param entry fully built {@link AuditEntry}
     * @return promise of the {@link AuditReceipt} containing the hash chain proof
     */
    Promise<AuditReceipt> log(AuditEntry entry);

    /**
     * Verify chain integrity for a tenant over an optional sequence range.
     *
     * @param tenantId       tenant scope
     * @param fromSequence   inclusive start (0 = beginning)
     * @param toSequence     inclusive end (null = latest)
     * @return promise of the verification result
     */
    Promise<ChainVerificationResult> verifyChain(
            String tenantId,
            long fromSequence,
            Long toSequence);
}
