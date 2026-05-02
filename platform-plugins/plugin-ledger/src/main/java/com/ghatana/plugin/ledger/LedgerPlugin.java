package com.ghatana.plugin.ledger;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Ledger Plugin — double-entry ledger for cross-scope accounting.
 *
 * <p>Provides product-agnostic double-entry ledger functionality that can be
 * shared across any product area requiring accounting primitives.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Idempotent transaction posting</li>
 *   <li>Transaction reversal support</li>
 *   <li>Multi-currency support</li>
 *   <li>Account management</li>
 *   <li>Audit trail integration</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Double-entry ledger plugin interface
 * @doc.layer platform
 * @doc.pattern Plugin
 * @since 1.2.0
 */
public interface LedgerPlugin extends Plugin {

    /**
     * Posts a transaction to the ledger.
     *
     * @param transaction the transaction to post
     * @return Promise containing the ledger entry ID
     */
    Promise<String> postTransaction(LedgerTransaction transaction);

    /**
     * Reverses a previously posted transaction.
     *
     * @param originalTransactionId the original transaction ID
     * @param reversalReason the reason for reversal
     * @return Promise containing the reversal entry ID
     */
    Promise<String> reverseTransaction(String originalTransactionId, String reversalReason);

    /**
     * Gets the posting status of a transaction.
     *
     * @param transactionId the transaction ID
     * @return Promise containing the posting status
     */
    Promise<PostingStatus> getPostingStatus(String transactionId);

    /**
     * Creates a new ledger account.
     *
     * @param accountId the account identifier
     * @param type the account type
     * @return Promise containing the created account
     */
    Promise<LedgerAccount> createAccount(String accountId, AccountType type);

    /**
     * Gets a ledger entry by ID.
     *
     * @param entryId the entry ID
     * @return Promise containing the entry if found
     */
    Promise<Optional<LedgerEntry>> getEntry(String entryId);

    /**
     * Queries ledger entries for an account.
     *
     * @param accountId the account ID
     * @param range the time range
     * @return Promise containing matching entries
     */
    Promise<List<LedgerEntry>> queryEntries(String accountId, TimeRange range);

    /**
     * Account types supported by the ledger.
     */
    enum AccountType {
        ASSET,
        LIABILITY,
        EQUITY,
        REVENUE,
        EXPENSE
    }

    /**
     * Posting status of a transaction.
     */
    enum PostingStatus {
        NOT_FOUND,
        POSTED,
        REVERSED
    }

    /**
     * Ledger account.
     */
    record LedgerAccount(
        String accountId,
        AccountType type,
        String currency,
        BigDecimal balance,
        Instant createdAt
    ) {}

    /**
     * Ledger entry.
     */
    record LedgerEntry(
        String entryId,
        String transactionId,
        String debitAccount,
        String creditAccount,
        BigDecimal amount,
        String currency,
        String description,
        Instant postedAt
    ) {}

    /**
     * Time range for queries.
     */
    record TimeRange(Instant start, Instant end) {}
}
