/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.kernel.service;

import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerTransaction;
import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.CircuitBreakerProfiles;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finance implementation of {@link LedgerPlugin} — bridges the platform
 * billing contract to the Finance {@link LedgerManagementService}.
 *
 * <p>Each {@link LedgerTransaction} is converted to a {@link LedgerManagementService.LedgerEntryRequest}
 * and posted through the existing double-entry ledger. Duplicate transaction IDs are
 * detected via an in-memory idempotency map and return the previous entry ID without
 * creating a second posting.
 *
 * @doc.type class
 * @doc.purpose Finance adapter — translates platform LedgerTransaction to double-entry ledger posting
 * @doc.layer product
 * @doc.pattern Adapter
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class BillingLedgerAdapter implements LedgerPlugin {

    private static final Logger log = LoggerFactory.getLogger(BillingLedgerAdapter.class);
    private static final int DEFAULT_LEDGER_BULKHEAD_LIMIT = 32;

    private final LedgerManagementService ledgerService;
    private final CircuitBreaker ledgerCircuitBreaker;
    private final Bulkhead ledgerBulkhead;
    /** Idempotency map: transactionId -> ledger entry ID. */
    private final Map<String, String> postedEntries = new ConcurrentHashMap<>();
    /** Tracks reversal linkages: original transactionId -> reversal ledger entry ID. */
    private final Map<String, String> reversalEntries = new ConcurrentHashMap<>();

    /**
     * Creates a new {@code BillingLedgerAdapter}.
     *
     * @param ledgerService the Finance ledger service to post entries through
     */
    public BillingLedgerAdapter(LedgerManagementService ledgerService) {
        this(
            ledgerService,
            CircuitBreakerProfiles.strict("finance-ledger-posting"),
            Bulkhead.of("finance-ledger-posting", DEFAULT_LEDGER_BULKHEAD_LIMIT)
        );
    }

    /**
     * Creates a BillingLedgerAdapter with explicit resilience controls.
     */
    public BillingLedgerAdapter(LedgerManagementService ledgerService,
                                CircuitBreaker ledgerCircuitBreaker,
                                Bulkhead ledgerBulkhead) {
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService must not be null");
        this.ledgerCircuitBreaker = Objects.requireNonNull(ledgerCircuitBreaker,
            "ledgerCircuitBreaker must not be null");
        this.ledgerBulkhead = Objects.requireNonNull(ledgerBulkhead, "ledgerBulkhead must not be null");
    }

    // =========================================================================
    // LedgerPostingService
    // =========================================================================

    @Override
    public Promise<String> postTransaction(LedgerTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");

        String txId = transaction.getTransactionId();

        // Idempotency: return the existing entry ID if already posted.
        String existing = postedEntries.get(txId);
        if (existing != null) {
            log.debug("Idempotent skip: transaction '{}' already posted as entry '{}'", txId, existing);
            return Promise.of(existing);
        }

        LedgerManagementService.LedgerEntryRequest request = new LedgerManagementService.LedgerEntryRequest(
            transaction.getDebitAccount(),
            transaction.getCreditAccount(),
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getDescription() + " [src=" + transaction.getSourceId()
                + (transaction.getExternalReferenceId() != null
                    ? ", ref=" + transaction.getExternalReferenceId() : "")
                + "]",
            transaction.getType().name()
        );

        return Promise.ofBlocking(Runnable::run, () -> executeWithResilience(() ->
            ledgerService.postEntry(request).toCompletableFuture().join()))
            .map(entry -> {
                postedEntries.put(txId, entry.getId());
                log.info("Posted billing transaction '{}' → ledger entry '{}' [{} {} {} -> {}]",
                    txId, entry.getId(), transaction.getAmount(), transaction.getCurrency(),
                    transaction.getDebitAccount(), transaction.getCreditAccount());
                return entry.getId();
            });
    }

    @Override
    public Promise<String> reverseTransaction(String originalTransactionId, String reversalReason) {
        Objects.requireNonNull(originalTransactionId, "originalTransactionId must not be null");
        Objects.requireNonNull(reversalReason,        "reversalReason must not be null");

        // Look up the original entry so we can create an offsetting entry.
        String originalEntryId = postedEntries.get(originalTransactionId);
        if (originalEntryId == null) {
            return Promise.ofException(
                new IllegalStateException("Cannot reverse unknown transaction: " + originalTransactionId));
        }

        // Look up original entry details from the ledger.
        return Promise.ofBlocking(Runnable::run, () -> executeWithResilience(() ->
            ledgerService.getEntry(originalEntryId).toCompletableFuture().join()))
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException(
                        "Ledger entry not found for reversal: " + originalEntryId));
                }
                LedgerManagementService.LedgerEntry original = opt.get();
                // A reversal swaps debit/credit and posts a negative description.
                LedgerManagementService.LedgerEntryRequest reversalRequest =
                    new LedgerManagementService.LedgerEntryRequest(
                        original.getCreditAccount(),   // intentionally swapped
                        original.getDebitAccount(),    // intentionally swapped
                        original.getAmount(),
                        original.getCurrency(),
                        "REVERSAL of " + originalEntryId + ": " + reversalReason,
                        "REVERSAL"
                    );

                return Promise.ofBlocking(Runnable::run, () -> executeWithResilience(() ->
                    ledgerService.postEntry(reversalRequest).toCompletableFuture().join()))
                    .map(reversal -> {
                        reversalEntries.put(originalTransactionId, reversal.getId());
                        log.info("Reversed transaction '{}' with ledger entry '{}'",
                            originalTransactionId, reversal.getId());
                        return reversal.getId();
                    });
            });
    }

    @Override
    public Promise<LedgerPlugin.PostingStatus> getPostingStatus(String transactionId) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");

        if (!postedEntries.containsKey(transactionId)) {
            return Promise.of(LedgerPlugin.PostingStatus.NOT_FOUND);
        }
        if (reversalEntries.containsKey(transactionId)) {
            return Promise.of(LedgerPlugin.PostingStatus.REVERSED);
        }
        return Promise.of(LedgerPlugin.PostingStatus.POSTED);
    }

    private <T> T executeWithResilience(java.util.concurrent.Callable<T> operation) {
        try {
            return ledgerBulkhead.tryExecuteBlocking(() ->
                ledgerCircuitBreaker.executeSync(operation));
        } catch (Exception exception) {
            throw new IllegalStateException("Ledger resilient execution failed", exception);
        }
    }

    @Override
    public Promise<LedgerPlugin.LedgerAccount> createAccount(String accountId, LedgerPlugin.AccountType type) {
        // Not implemented in this adapter - would need to integrate with ledger management service
        return Promise.ofException(new UnsupportedOperationException("createAccount not implemented"));
    }

    @Override
    public Promise<java.util.Optional<LedgerPlugin.LedgerEntry>> getEntry(String entryId) {
        // Not implemented in this adapter
        return Promise.ofException(new UnsupportedOperationException("getEntry not implemented"));
    }

    @Override
    public Promise<java.util.List<LedgerPlugin.LedgerEntry>> queryEntries(String accountId, LedgerPlugin.TimeRange range) {
        // Not implemented in this adapter
        return Promise.ofException(new UnsupportedOperationException("queryEntries not implemented"));
    }

    // Plugin interface methods
    @Override
    public com.ghatana.platform.plugin.PluginMetadata metadata() {
        return com.ghatana.platform.plugin.PluginMetadata.builder()
            .id("billing-ledger-adapter")
            .name("Billing Ledger Adapter")
            .version("1.0.0")
            .build();
    }

    @Override
    public com.ghatana.platform.plugin.PluginState getState() {
        return com.ghatana.platform.plugin.PluginState.STARTED;
    }

    @Override
    public Promise<Void> initialize(com.ghatana.platform.plugin.PluginContext context) {
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        return Promise.complete();
    }
}
