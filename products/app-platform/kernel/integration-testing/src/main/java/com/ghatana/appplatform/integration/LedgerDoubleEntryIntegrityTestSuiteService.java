package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @doc.type    Service
 * @doc.purpose Ledger double-entry integrity test suite.
 *              Validates that every financial event maintains the fundamental double-entry invariant:
 *              total debits == total credits and trial balance is zero.
 *              Test cases: 1000 random trades posted; simultaneous concurrent trades;
 *              failed settlement → correct reversal; currency conversion (both legs balanced).
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; concurrent-test; assertion
 *
 * STORY-T01-005: Ledger double-entry integrity test suite
 */
public class LedgerDoubleEntryIntegrityTestSuiteService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface TradePostingPort {
        /** Post a trade to the ledger; returns tradeId. */
        String postTrade(String clientId, String symbol, String side, int qty, double price, String currency) throws Exception;
        /** Reverse (cancel) a previously posted trade entry. */
        void reverseTrade(String tradeId) throws Exception;
        double getPositionLedgerBalance(String clientId, String currency) throws Exception;
    }

    public interface LedgerQueryPort {
        /** Returns net sum of all debit-credit entries for a tradeId. Should be 0 for balanced. */
        double getNetForTrade(String tradeId) throws Exception;
        /** Returns global trial balance across all accounts. Should be 0.0. */
        double getTrialBalance() throws Exception;
        long getLedgerEntryCount() throws Exception;
    }

    public interface SettlementSimPort {
        String bookSettlement(String tradeId) throws Exception;
        void failSettlement(String settlementId) throws Exception;
        boolean isReversalPosted(String tradeId) throws Exception;
    }

    public interface CurrencyConversionPort {
        /** Post an NPR/USD cross-currency trade. Returns tradeId. */
        String postCrossRateTrade(String clientId, int qty, double nprPrice, double usdPrice) throws Exception;
        double getNprLegBalance(String tradeId) throws Exception;
        double getUsdLegBalance(String tradeId) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final TradePostingPort tradePosting;
    private final LedgerQueryPort ledgerQuery;
    private final SettlementSimPort settlementSim;
    private final CurrencyConversionPort crossCurrency;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;
    private final Timer oneKTradesDuration;

    public LedgerDoubleEntryIntegrityTestSuiteService(
        javax.sql.DataSource ds,
        TradePostingPort tradePosting,
        LedgerQueryPort ledgerQuery,
        SettlementSimPort settlementSim,
        CurrencyConversionPort crossCurrency,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                  = ds;
        this.tradePosting        = tradePosting;
        this.ledgerQuery         = ledgerQuery;
        this.settlementSim       = settlementSim;
        this.crossCurrency       = crossCurrency;
        this.audit               = audit;
        this.executor            = executor;
        this.suitesPassed        = Counter.builder("integration.ledger.suites_passed").register(registry);
        this.suitesFailed        = Counter.builder("integration.ledger.suites_failed").register(registry);
        this.oneKTradesDuration  = Timer.builder("integration.ledger.1000_trades_duration").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("double_entry_1000_trades", this::thousandTradesBalanced));
            results.add(runScenario("trial_balance",             this::trialBalanceZero));
            results.add(runScenario("concurrent_trades_balanced", this::concurrentTradesBalanced));
            results.add(runScenario("reversal_exact_match",       this::reversalExactMatch));
            results.add(runScenario("currency_conversion_both_legs", this::currencyConversionBothLegs));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed > 0) suitesFailed.increment(); else suitesPassed.increment();
            audit.audit("LEDGER_INTEGRITY_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("LedgerDoubleEntry", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    private void thousandTradesBalanced(String runId) throws Exception {
        long start = System.currentTimeMillis();
        List<String> tradeIds = new ArrayList<>();
        Random rng = new Random(42);
        String[] symbols = {"NABIL", "NTC", "NLIC", "NIMB", "NIFRA"};
        String[] sides   = {"BUY", "SELL"};

        for (int i = 0; i < 1000; i++) {
            String sym  = symbols[rng.nextInt(symbols.length)];
            String side = sides[rng.nextInt(2)];
            int qty     = 10 + rng.nextInt(490);
            double px   = 500 + rng.nextDouble() * 1000;
            String id   = tradePosting.postTrade("BULK-CLIENT-" + (i % 10), sym, side, qty, px, "NPR");
            tradeIds.add(id);
        }
        long elapsed = System.currentTimeMillis() - start;
        oneKTradesDuration.record(elapsed, TimeUnit.MILLISECONDS);

        // Performance gate: 1000 trades must post within 10 seconds
        assertStep(runId, "perf_1000_under_10sec", "1000 trades < 10s", "< 10000ms",
            elapsed < 10_000, elapsed + "ms");

        // Verify double-entry for each trade
        int failures = 0;
        for (String tid : tradeIds) {
            double net = ledgerQuery.getNetForTrade(tid);
            if (Math.abs(net) > 0.0001) failures++;
        }
        assertStep(runId, "double_entry_1000_trades", "all trades balanced", "0 failures",
            failures == 0, failures + " unbalanced");

        // Total entry count
        long count = ledgerQuery.getLedgerEntryCount();
        assertStep(runId, "entry_count", "≥2000 entries (2 per trade)", "≥2000",
            count >= 2000, String.valueOf(count));
    }

    private void trialBalanceZero(String runId) throws Exception {
        double balance = ledgerQuery.getTrialBalance();
        assertStep(runId, "trial_balance", "global trial balance = 0", "0.0",
            Math.abs(balance) < 0.0001, String.valueOf(balance));
    }

    private void concurrentTradesBalanced(String runId) throws Exception {
        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);
        List<String> postedIds = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int t = 0; t < threadCount; t++) {
            final int idx = t;
            pool.submit(() -> {
                try {
                    String id = tradePosting.postTrade("CONC-CLIENT-" + idx, "NABIL", "BUY", 10 + idx, 900.0 + idx, "NPR");
                    postedIds.add(id);
                } catch (Exception ex) {
                    errors.incrementAndGet();
                } finally { latch.countDown(); }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertStep(runId, "concurrent_no_errors", "no concurrent errors", "0",
            errors.get() == 0, errors.get() + " errors");

        int unbalanced = 0;
        for (String tid : postedIds) {
            if (Math.abs(ledgerQuery.getNetForTrade(tid)) > 0.0001) unbalanced++;
        }
        assertStep(runId, "concurrent_balanced", "concurrent trades all balanced", "0 unbalanced",
            unbalanced == 0, unbalanced + " unbalanced");

        double trialBalance = ledgerQuery.getTrialBalance();
        assertStep(runId, "trial_balance_after_concurrent", "trial balance still 0", "0.0",
            Math.abs(trialBalance) < 0.0001, String.valueOf(trialBalance));
    }

    private void reversalExactMatch(String runId) throws Exception {
        String tradeId = tradePosting.postTrade("REVERSAL-CLIENT", "NTC", "BUY", 100, 750.0, "NPR");
        String settlementId = settlementSim.bookSettlement(tradeId);
        settlementSim.failSettlement(settlementId);

        // Allow DTC to process reversal
        Thread.sleep(300);

        assertStep(runId, "reversal_posted", "reversal entry exists", "true",
            settlementSim.isReversalPosted(tradeId), true);

        double netAfterReversal = ledgerQuery.getNetForTrade(tradeId);
        assertStep(runId, "net_after_reversal", "net after reversal = 0", "≈0",
            Math.abs(netAfterReversal) < 0.0001, String.valueOf(netAfterReversal));

        double trialBalance = ledgerQuery.getTrialBalance();
        assertStep(runId, "trial_balance_after_reversal", "trial balance = 0", "≈0",
            Math.abs(trialBalance) < 0.0001, String.valueOf(trialBalance));
    }

    private void currencyConversionBothLegs(String runId) throws Exception {
        // Cross-currency: client pays NPR, receives USD equivalent
        String tradeId = crossCurrency.postCrossRateTrade("FX-CLIENT-001", 1000, 133.5, 1.0);

        double nprLeg = crossCurrency.getNprLegBalance(tradeId);
        double usdLeg = crossCurrency.getUsdLegBalance(tradeId);

        assertStep(runId, "npr_leg_balanced", "NPR leg balanced", "≈0",
            Math.abs(nprLeg) < 0.0001, String.valueOf(nprLeg));
        assertStep(runId, "usd_leg_balanced", "USD leg balanced", "≈0",
            Math.abs(usdLeg) < 0.0001, String.valueOf(usdLeg));

        double net = ledgerQuery.getNetForTrade(tradeId);
        assertStep(runId, "cross_currency_balanced", "cross-currency net = 0", "≈0",
            Math.abs(net) < 0.0001, String.valueOf(net));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String runId = insertRun(name);
            fn.accept(runId);
            markRunStatus(runId, "PASSED");
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) {
            return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return new ScenarioResult(name, false, ex.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, String.valueOf(actual)); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + " expected=" + expected + " actual=" + actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('LedgerDoubleEntry',?) RETURNING run_id"
             )) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE e2e_test_runs SET status=? WHERE run_id=?"
             )) { ps.setString(1, status); ps.setString(2, runId); ps.executeUpdate(); }
        catch (SQLException ignored) {}
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
