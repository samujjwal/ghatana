package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Handles many-to-one matching (multiple internal entries sum to one external)
 *              and one-to-many matching (one internal entry matches multiple external entries).
 *              Matching is constrained to same date range, same client, same currency.
 *              Provides a complete audit trail for composite matches.
 *              Satisfies STORY-D13-009.
 * @doc.layer   Domain
 * @doc.pattern Partition-subset enumeration; amount-sum matching; INSERT-only match audit;
 *              ON CONFLICT idempotency.
 */
public class CompositeMatchingService {

    private static final Logger log = LoggerFactory.getLogger(CompositeMatchingService.class);

    private static final int    MAX_SUBSET_SIZE    = 10;  // prevent combinatorial explosion
    private static final BigDecimal AMOUNT_EPSILON = new BigDecimal("0.01");

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          manyToOneCounter;
    private final Counter          oneToManyCounter;

    public CompositeMatchingService(HikariDataSource dataSource, Executor executor,
                                    MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.manyToOneCounter = registry.counter("recon.composite.many_to_one");
        this.oneToManyCounter = registry.counter("recon.composite.one_to_many");
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record UnmatchedEntry(String entryId, String clientId, String currency,
                                 BigDecimal amount, LocalDate transactionDate) {}

    public record CompositeMatch(String matchGroupId, String matchType, List<String> statementEntryIds,
                                 List<String> internalTxIds, BigDecimal totalAmount, String currency,
                                 double confidenceScore) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<Integer> runCompositeMatching(String reconRunId, LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> {
            int matched = 0;
            try (Connection conn = dataSource.getConnection()) {
                List<UnmatchedEntry> unmatchedExternal = loadUnmatchedStatementEntries(conn, runDate);
                List<UnmatchedEntry> unmatchedInternal = loadUnmatchedInternalEntries(conn, runDate);

                // Many-to-one: multiple internal → one external
                for (UnmatchedEntry ext : unmatchedExternal) {
                    List<UnmatchedEntry> candidates = filter(unmatchedInternal, ext.clientId(), ext.currency());
                    List<UnmatchedEntry> subset = findSubsetSummingTo(candidates, ext.amount());
                    if (!subset.isEmpty()) {
                        String groupId = UUID.randomUUID().toString();
                        persistCompositeMatch(conn, reconRunId, groupId, "MANY_TO_ONE",
                                List.of(ext.entryId()), subset.stream().map(UnmatchedEntry::entryId).toList(),
                                ext.amount(), ext.currency());
                        matched += subset.size();
                        manyToOneCounter.increment();
                    }
                }

                // One-to-many: one internal → multiple external
                for (UnmatchedEntry intl : unmatchedInternal) {
                    List<UnmatchedEntry> candidates = filter(unmatchedExternal, intl.clientId(), intl.currency());
                    List<UnmatchedEntry> subset = findSubsetSummingTo(candidates, intl.amount());
                    if (!subset.isEmpty()) {
                        String groupId = UUID.randomUUID().toString();
                        persistCompositeMatch(conn, reconRunId, groupId, "ONE_TO_MANY",
                                subset.stream().map(UnmatchedEntry::entryId).toList(),
                                List.of(intl.entryId()), intl.amount(), intl.currency());
                        matched += subset.size();
                        oneToManyCounter.increment();
                    }
                }
            }
            return matched;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<UnmatchedEntry> loadUnmatchedStatementEntries(Connection conn, LocalDate runDate)
            throws SQLException {
        List<UnmatchedEntry> list = new ArrayList<>();
        String sql = """
                SELECT se.entry_id, se.client_id, se.currency, se.amount, se.transaction_date
                FROM statement_entries se
                WHERE se.recon_run_id IS NULL
                  AND se.transaction_date BETWEEN ? AND ?
                ORDER BY se.amount DESC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate.minusDays(2));
            ps.setObject(2, runDate.plusDays(2));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new UnmatchedEntry(rs.getString("entry_id"), rs.getString("client_id"),
                            rs.getString("currency"), rs.getBigDecimal("amount"),
                            rs.getObject("transaction_date", LocalDate.class)));
                }
            }
        }
        return list;
    }

    private List<UnmatchedEntry> loadUnmatchedInternalEntries(Connection conn, LocalDate runDate)
            throws SQLException {
        List<UnmatchedEntry> list = new ArrayList<>();
        String sql = """
                SELECT it.tx_id, it.client_id, it.currency, it.amount, it.transaction_date
                FROM internal_transactions it
                WHERE it.recon_match_id IS NULL
                  AND it.transaction_date BETWEEN ? AND ?
                ORDER BY it.amount DESC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate.minusDays(2));
            ps.setObject(2, runDate.plusDays(2));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new UnmatchedEntry(rs.getString("tx_id"), rs.getString("client_id"),
                            rs.getString("currency"), rs.getBigDecimal("amount"),
                            rs.getObject("transaction_date", LocalDate.class)));
                }
            }
        }
        return list;
    }

    private List<UnmatchedEntry> filter(List<UnmatchedEntry> entries, String clientId, String currency) {
        return entries.stream()
                .filter(e -> e.clientId().equals(clientId) && e.currency().equals(currency))
                .toList();
    }

    /**
     * Greedy subset search: sorted descending, accumulate until sum reached.
     * Limits subset to MAX_SUBSET_SIZE to prevent combinatorial explosion.
     */
    private List<UnmatchedEntry> findSubsetSummingTo(List<UnmatchedEntry> candidates, BigDecimal target) {
        List<UnmatchedEntry> result = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (UnmatchedEntry e : candidates) {
            if (result.size() >= MAX_SUBSET_SIZE) break;
            if (running.add(e.amount()).compareTo(target) <= 0) {
                result.add(e);
                running = running.add(e.amount());
            }
            if (running.subtract(target).abs().compareTo(AMOUNT_EPSILON) < 0) break;
        }
        if (running.subtract(target).abs().compareTo(AMOUNT_EPSILON) >= 0) {
            return List.of(); // no match
        }
        return result;
    }

    private void persistCompositeMatch(Connection conn, String reconRunId, String groupId,
                                       String matchType, List<String> statementIds,
                                       List<String> internalIds, BigDecimal amount, String currency)
            throws SQLException {
        String sql = """
                INSERT INTO composite_recon_matches
                    (match_group_id, recon_run_id, match_type, statement_entry_ids,
                     internal_tx_ids, total_amount, currency, matched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (match_group_id) DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, reconRunId);
            ps.setString(3, matchType);
            ps.setArray(4, conn.createArrayOf("text", statementIds.toArray()));
            ps.setArray(5, conn.createArrayOf("text", internalIds.toArray()));
            ps.setBigDecimal(6, amount);
            ps.setString(7, currency);
            ps.executeUpdate();
        }
    }
}
