package com.ghatana.appplatform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Ledger-specific meters pre-registered against a {@link MeterRegistry}.
 *
 * <p>Wraps raw Micrometer counters and timers with ledger-domain method names so
 * callers never reference raw metric strings.  All metric names come from
 * {@link FinanceMetricNames}.
 *
 * @doc.type class
 * @doc.purpose Ledger-specific metric registration and recording
 * @doc.layer product
 * @doc.pattern Facade (thin wrapper over Micrometer)
 */
public final class LedgerMetrics {

    private final Counter journalsPosted;
    private final Counter balanceUpdates;
    private final Counter hashChainErrors;
    private final Timer   postingDuration;

    /**
     * Creates and registers all ledger meters with the supplied registry.
     *
     * @param registry Micrometer registry (injected at module startup)
     */
    public LedgerMetrics(MeterRegistry registry) {
        this.journalsPosted  = registry.counter(FinanceMetricNames.LEDGER_JOURNALS_POSTED);
        this.balanceUpdates  = registry.counter(FinanceMetricNames.LEDGER_BALANCE_UPDATES);
        this.hashChainErrors = registry.counter(FinanceMetricNames.LEDGER_HASH_CHAIN_ERRORS);
        this.postingDuration = registry.timer(FinanceMetricNames.LEDGER_POSTING_DURATION);
    }

    /** Increment when a journal entry is successfully posted. */
    public void recordJournalPosted() {
        journalsPosted.increment();
    }

    /** Increment when an account balance is updated. */
    public void recordBalanceUpdate() {
        balanceUpdates.increment();
    }

    /** Increment when a hash-chain integrity violation is detected. */
    public void recordHashChainError() {
        hashChainErrors.increment();
    }

    /**
     * Returns the posting-duration timer for wrapping operations with
     * {@code Timer.record()}.
     *
     * @return pre-registered posting duration timer
     */
    public Timer postingTimer() {
        return postingDuration;
    }
}
