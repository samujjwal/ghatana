/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.platform.billing;

import io.activej.promise.Promise;

/**
 * Contract for posting billing transactions to a financial ledger.
 *
 * <p>The Finance product implements this interface via its
 * {@code LedgerManagementService}. The PHR product calls this interface
 * when a billing encounter is closed or an insurance claim is settled,
 * without coupling to Finance internals.
 *
 * <p>Implementors must guarantee double-entry integrity (each post creates
 * exactly one debit and one credit entry), idempotency on duplicate transaction
 * IDs, and persistence before the returned Promise completes.
 *
 * @doc.type interface
 * @doc.purpose Shared ledger posting contract — Finance implements, PHR calls
 * @doc.layer platform
 * @doc.pattern Port
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public interface LedgerPostingService {

    /**
     * Posts a billing transaction as a double-entry ledger record.
     *
     * <p>Implementations must:
     * <ul>
     *   <li>Create exactly one debit and one credit entry for the transaction.</li>
     *   <li>Be idempotent: a second call with the same
     *       {@link BillingTransaction#getTransactionId()} must succeed without
     *       creating duplicate entries.</li>
     *   <li>Complete the Promise only after the entry is durably persisted.</li>
     * </ul>
     *
     * @param transaction the billing transaction to post
     * @return a Promise containing the ID of the created ledger entry
     * @throws IllegalArgumentException if the transaction is invalid
     */
    Promise<String> postTransaction(BillingTransaction transaction);

    /**
     * Reverses a previously posted billing transaction.
     *
     * <p>A reversal creates an offsetting double-entry record that effectively
     * cancels the original posting. The original entry is not modified.
     *
     * @param originalTransactionId the transaction ID to reverse
     * @param reversalReason        a human-readable reason for the reversal (for audit)
     * @return a Promise containing the ID of the reversal ledger entry
     */
    Promise<String> reverseTransaction(String originalTransactionId, String reversalReason);

    /**
     * Queries the posting status of a previously submitted transaction.
     *
     * @param transactionId the transaction to look up
     * @return a Promise containing the current posting status
     */
    Promise<PostingStatus> getPostingStatus(String transactionId);

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * The durable posting status of a billing transaction in the ledger.
     */
    enum PostingStatus {
        /** The transaction has been durably posted to the ledger. */
        POSTED,
        /** The transaction was posted and then reversed. */
        REVERSED,
        /** The transaction is pending posting (transient state). */
        PENDING,
        /** The posting failed — see error log for details. */
        FAILED,
        /** No record of this transaction exists in the ledger. */
        NOT_FOUND
    }
}
