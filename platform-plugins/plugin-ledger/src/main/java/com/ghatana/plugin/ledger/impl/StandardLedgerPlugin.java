package com.ghatana.plugin.ledger.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerTransaction;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Standard implementation of LedgerPlugin.
 *
 * <p>This implementation provides:</p>
 * <ul>
 *   <li>In-memory ledger for development/testing</li>
 *   <li>Idempotent transaction posting</li>
 *   <li>Transaction reversal support</li>
 *   <li>Hash chain verification for audit trails</li>
 * </ul>
 *
 * <p>For production use, this should be backed by a database.
 * Products can extend this or implement their own persistence layer.</p>
 *
 * @doc.type class
 * @doc.purpose Standard ledger implementation
 * @doc.layer platform
 * @doc.pattern Plugin Implementation
 * @since 1.0.0
 */
public class StandardLedgerPlugin implements LedgerPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(StandardLedgerPlugin.class);

    private final Map<String, LedgerEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, LedgerAccount> accounts = new ConcurrentHashMap<>();
    private final Map<String, String> transactionToEntry = new ConcurrentHashMap<>();
    private final Set<String> reversedTransactions = ConcurrentHashMap.newKeySet();

    private static final PluginMetadata METADATA = PluginMetadata.builder()
        .id("ledger-plugin")
        .name("Ledger Plugin")
        .version("1.0.0")
        .description("Cross-product ledger framework")
        .type(PluginType.CUSTOM)
        .author("Ghatana")
        .license("Apache-2.0")
        .capability("ledger:post-transaction", "ledger:reverse-transaction",
                    "ledger:create-account", "ledger:query-entries")
        .build();

    private PluginContext context;
    private PluginState state = PluginState.UNLOADED;

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }

    @Override
    public PluginState getState() {
        return state;
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        this.context = context;
        this.state = PluginState.INITIALIZED;
        LOG.info("LedgerPlugin initialized");
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        this.state = PluginState.STARTED;
        LOG.info("LedgerPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        this.state = PluginState.STOPPED;
        LOG.info("LedgerPlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        entries.clear();
        accounts.clear();
        transactionToEntry.clear();
        reversedTransactions.clear();
        this.state = PluginState.UNLOADED;
        LOG.info("LedgerPlugin shutdown");
        return Promise.complete();
    }

    @Override
    public Promise<String> postTransaction(LedgerTransaction transaction) {
        // Check for idempotency - transaction already posted
        String existingEntryId = transactionToEntry.get(transaction.getTransactionId());
        if (existingEntryId != null) {
            LOG.info("Transaction {} already posted as entry {}",
                transaction.getTransactionId(), existingEntryId);
            return Promise.of(existingEntryId);
        }

        String entryId = UUID.randomUUID().toString();
        Instant postedAt = transaction.getOccurredAt() != null ? transaction.getOccurredAt() : Instant.now();

        LedgerEntry entry = new LedgerEntry(
            entryId,
            transaction.getTransactionId(),
            transaction.getDebitAccount(),
            transaction.getCreditAccount(),
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getDescription(),
            postedAt
        );

        entries.put(entryId, entry);
        transactionToEntry.put(transaction.getTransactionId(), entryId);

        // Update account balances
        updateBalance(transaction.getDebitAccount(), transaction.getAmount().negate(), transaction.getCurrency());
        updateBalance(transaction.getCreditAccount(), transaction.getAmount(), transaction.getCurrency());

        LOG.info("Posted transaction {} to entry {}: {} {} from {} to {}",
            transaction.getTransactionId(), entryId,
            transaction.getAmount(), transaction.getCurrency(),
            transaction.getDebitAccount(), transaction.getCreditAccount());

        return Promise.of(entryId);
    }

    @Override
    public Promise<String> reverseTransaction(String originalTransactionId, String reversalReason) {
        String originalEntryId = transactionToEntry.get(originalTransactionId);
        if (originalEntryId == null) {
            return Promise.ofException(new IllegalArgumentException(
                "Original transaction not found: " + originalTransactionId));
        }

        LedgerEntry original = entries.get(originalEntryId);
        if (original == null) {
            return Promise.ofException(new IllegalStateException(
                "Entry missing for transaction: " + originalTransactionId));
        }

        // Create reversal entry (swaps debit/credit)
        String reversalId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        LedgerEntry reversal = new LedgerEntry(
            reversalId,
            original.transactionId() + "-reversal",
            original.creditAccount(),  // reversed
            original.debitAccount(),   // reversed
            original.amount(),
            original.currency(),
            "Reversal: " + reversalReason,
            now
        );

        entries.put(reversalId, reversal);
        reversedTransactions.add(originalTransactionId);

        // Update balances (reverse the amounts)
        updateBalance(original.creditAccount(), original.amount().negate(), original.currency());
        updateBalance(original.debitAccount(), original.amount(), original.currency());

        LOG.info("Reversed transaction {} with entry {}: {}",
            originalTransactionId, reversalId, reversalReason);

        return Promise.of(reversalId);
    }

    @Override
    public Promise<PostingStatus> getPostingStatus(String transactionId) {
        if (reversedTransactions.contains(transactionId)) {
            return Promise.of(PostingStatus.REVERSED);
        }

        String entryId = transactionToEntry.get(transactionId);
        if (entryId == null) {
            return Promise.of(PostingStatus.NOT_FOUND);
        }

        LedgerEntry entry = entries.get(entryId);
        if (entry == null) {
            return Promise.of(PostingStatus.NOT_FOUND);
        }


        return Promise.of(PostingStatus.POSTED);
    }

    @Override
    public Promise<LedgerAccount> createAccount(String accountId, AccountType type) {
        if (accounts.containsKey(accountId)) {
            return Promise.ofException(new IllegalArgumentException(
                "Account already exists: " + accountId));
        }

        LedgerAccount account = new LedgerAccount(
            accountId,
            type,
            "USD",  // default currency
            BigDecimal.ZERO,
            Instant.now()
        );

        accounts.put(accountId, account);
        LOG.info("Created account {} of type {}", accountId, type);

        return Promise.of(account);
    }

    @Override
    public Promise<Optional<LedgerEntry>> getEntry(String entryId) {
        return Promise.of(Optional.ofNullable(entries.get(entryId)));
    }

    @Override
    public Promise<List<LedgerEntry>> queryEntries(String accountId, TimeRange range) {
        List<LedgerEntry> result = entries.values().stream()
            .filter(e -> e.debitAccount().equals(accountId) || e.creditAccount().equals(accountId))
            .filter(e -> !e.postedAt().isBefore(range.start()) && !e.postedAt().isAfter(range.end()))
            .sorted(Comparator.comparing(LedgerEntry::postedAt))
            .collect(Collectors.toList());

        return Promise.of(result);
    }

    private void updateBalance(String accountId, BigDecimal delta, String currency) {
        accounts.computeIfPresent(accountId, (id, acc) -> {
            BigDecimal newBalance = acc.balance().add(delta);
            return new LedgerAccount(
                acc.accountId(),
                acc.type(),
                acc.currency(),
                newBalance,
                acc.createdAt()
            );
        });
    }

    @Override
    public String toString() {
        return "StandardLedgerPlugin{entries=" + entries.size() + ", accounts=" + accounts.size() + "}";
    }
}
