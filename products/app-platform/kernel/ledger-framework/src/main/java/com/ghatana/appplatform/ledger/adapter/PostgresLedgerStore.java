/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.domain.Direction;
import com.ghatana.appplatform.ledger.domain.Journal;
import com.ghatana.appplatform.ledger.domain.JournalEntry;
import com.ghatana.appplatform.ledger.domain.MonetaryAmount;
import com.ghatana.appplatform.ledger.port.LedgerStore;
import com.ghatana.appplatform.ledger.service.EntryHashChain;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * PostgreSQL-backed implementation of {@link LedgerStore}.
 *
 * <p>All writes are performed inside a single JDBC transaction:
 * <ol>
 *   <li>INSERT into {@code ledger_journal}</li>
 *   <li>INSERT each entry into {@code ledger_journal_entry} with a computed SHA-256 hash</li>
 *   <li>INSERT an {@code outbox} row for the {@code JournalPosted} event (K17-001 atomicity)</li>
 * </ol>
 *
 * <p>The balance trigger on {@code ledger_journal_entry} keeps {@code account_balance}
 * up to date automatically; this store does not manage balances directly.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for LedgerStore port (K16-001/002/003/006, K17-001)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresLedgerStore implements LedgerStore {

    // ── SQL ────────────────────────────────────────────────────────────────────

    private static final String INSERT_JOURNAL = """
            INSERT INTO ledger_journal
                (journal_id, reference, description, fiscal_year,
                 posted_at_bs, posted_at_utc, tenant_id, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String MAX_SEQUENCE = """
            SELECT COALESCE(MAX(sequence_num), 0) FROM ledger_journal_entry WHERE account_id = ?
            """;

    private static final String LAST_HASH = """
            SELECT entry_hash
              FROM ledger_journal_entry
             WHERE account_id = ?
             ORDER BY sequence_num DESC
             LIMIT 1
            """;

    private static final String INSERT_ENTRY = """
            INSERT INTO ledger_journal_entry
                (entry_id, journal_id, account_id, direction, amount, currency_code,
                 description, entry_hash, sequence_num)
            VALUES (?, ?, ?, ?::journal_direction, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_OUTBOX = """
            INSERT INTO outbox
                (id, aggregate_id, aggregate_type, event_type, payload, tenant_id)
            VALUES (?, ?, 'Journal', 'JournalPosted', ?::jsonb, ?)
            """;

    private static final String SELECT_JOURNAL = """
            SELECT j.journal_id, j.reference, j.description, j.fiscal_year,
                   j.posted_at_bs, j.posted_at_utc, j.tenant_id, j.created_by,
                   e.entry_id, e.account_id, e.direction, e.amount, e.currency_code,
                   cr.name AS cur_name, cr.symbol AS cur_symbol,
                   cr.decimal_places, cr.rounding_mode,
                   e.description AS entry_desc, e.entry_hash, e.sequence_num
              FROM ledger_journal j
              JOIN ledger_journal_entry e ON e.journal_id = j.journal_id
              JOIN currency_registry cr ON cr.code = e.currency_code
             WHERE j.journal_id = ?
             ORDER BY e.sequence_num
            """;

    private static final String SELECT_BY_REFERENCE = """
            SELECT j.journal_id, j.reference, j.description, j.fiscal_year,
                   j.posted_at_bs, j.posted_at_utc, j.tenant_id, j.created_by,
                   e.entry_id, e.account_id, e.direction, e.amount, e.currency_code,
                   cr.name AS cur_name, cr.symbol AS cur_symbol,
                   cr.decimal_places, cr.rounding_mode,
                   e.description AS entry_desc, e.entry_hash, e.sequence_num
              FROM ledger_journal j
              JOIN ledger_journal_entry e ON e.journal_id = j.journal_id
              JOIN currency_registry cr ON cr.code = e.currency_code
             WHERE j.reference = ? AND j.tenant_id = ?
             ORDER BY j.posted_at_utc, e.sequence_num
            """;

    private static final String SELECT_BALANCE = """
            SELECT ab.net_balance,
                   cr.code, cr.name AS cur_name, cr.symbol AS cur_symbol,
                   cr.decimal_places, cr.rounding_mode
              FROM account_balance ab
              JOIN currency_registry cr ON cr.code = ab.currency_code
             WHERE ab.account_id = ? AND ab.currency_code = ?
            """;

    private static final String SELECT_RECENT_JOURNALS = """
            SELECT j.journal_id, j.reference, j.description, j.fiscal_year,
                   j.posted_at_bs, j.posted_at_utc, j.tenant_id, j.created_by,
                   e.entry_id, e.account_id, e.direction, e.amount, e.currency_code,
                   cr.name AS cur_name, cr.symbol AS cur_symbol,
                   cr.decimal_places, cr.rounding_mode,
                   e.description AS entry_desc, e.entry_hash, e.sequence_num
              FROM (SELECT journal_id, posted_at_utc
                      FROM ledger_journal
                     WHERE tenant_id = ?
                     ORDER BY posted_at_utc DESC
                     LIMIT ?) recent
              JOIN ledger_journal j ON j.journal_id = recent.journal_id
              JOIN ledger_journal_entry e ON e.journal_id = j.journal_id
              JOIN currency_registry cr ON cr.code = e.currency_code
             ORDER BY recent.posted_at_utc DESC, e.sequence_num
            """;

    private static final String SELECT_BALANCE_AS_OF = """
            SELECT COALESCE(
                       SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount ELSE -e.amount END),
                       0
                   ) AS net_balance,
                   cr.code, cr.name AS cur_name, cr.symbol AS cur_symbol,
                   cr.decimal_places, cr.rounding_mode
              FROM ledger_journal_entry e
              JOIN ledger_journal j ON j.journal_id = e.journal_id
              JOIN currency_registry cr ON cr.code = e.currency_code
             WHERE e.account_id = ?
               AND e.currency_code = ?
               AND j.posted_at_utc <= ?
             GROUP BY cr.code, cr.name, cr.symbol, cr.decimal_places, cr.rounding_mode
            """;

    // ── State ──────────────────────────────────────────────────────────────────

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    public PostgresLedgerStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── LedgerStore implementation ─────────────────────────────────────────────

    @Override
    public Promise<Journal> postJournal(Journal journal) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    insertJournal(conn, journal);
                    insertEntries(conn, journal);
                    insertOutbox(conn, journal);
                    conn.commit();
                    return journal;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        });
    }

    @Override
    public Promise<Optional<Journal>> getJournal(UUID journalId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_JOURNAL)) {
                ps.setObject(1, journalId);
                try (ResultSet rs = ps.executeQuery()) {
                    return Optional.ofNullable(mapJournalFromRows(rs));
                }
            }
        });
    }

    @Override
    public Promise<List<Journal>> getJournalsByReference(String reference, UUID tenantId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_REFERENCE)) {
                ps.setString(1, reference);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    return mapJournalsFromRows(rs);
                }
            }
        });
    }

    @Override
    public Promise<MonetaryAmount> getAccountBalance(UUID accountId, String currencyCode) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BALANCE)) {
                ps.setObject(1, accountId);
                ps.setString(2, currencyCode.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Currency currency = new Currency(
                                rs.getString("code"),
                                rs.getString("cur_name"),
                                rs.getString("cur_symbol"),
                                rs.getInt("decimal_places"),
                                RoundingMode.valueOf(rs.getString("rounding_mode"))
                        );
                        return MonetaryAmount.of(rs.getBigDecimal("net_balance"), currency);
                    }
                    // No balance row yet — account has no postings for this currency
                    return MonetaryAmount.zero(new Currency(
                            currencyCode.toUpperCase(), currencyCode, currencyCode, 2, RoundingMode.HALF_UP));
                }
            }
        });
    }

    @Override
    public Promise<List<Journal>> getRecentJournals(UUID tenantId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_RECENT_JOURNALS)) {
                ps.setObject(1, tenantId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    return mapJournalsFromRows(rs);
                }
            }
        });
    }

    @Override
    public Promise<MonetaryAmount> getAccountBalanceAsOf(UUID accountId, String currencyCode, Instant asOf) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BALANCE_AS_OF)) {
                ps.setObject(1, accountId);
                ps.setString(2, currencyCode.toUpperCase());
                ps.setTimestamp(3, Timestamp.from(asOf));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Currency currency = new Currency(
                                rs.getString("code"),
                                rs.getString("cur_name"),
                                rs.getString("cur_symbol"),
                                rs.getInt("decimal_places"),
                                RoundingMode.valueOf(rs.getString("rounding_mode"))
                        );
                        return MonetaryAmount.of(rs.getBigDecimal("net_balance"), currency);
                    }
                    return MonetaryAmount.zero(new Currency(
                            currencyCode.toUpperCase(), currencyCode, currencyCode, 2, RoundingMode.HALF_UP));
                }
            }
        });
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void insertJournal(Connection conn, Journal journal) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_JOURNAL)) {
            ps.setObject(1, journal.journalId());
            ps.setString(2, journal.reference());
            ps.setString(3, journal.description());
            ps.setString(4, journal.fiscalYear());
            ps.setString(5, journal.postedAtBs());
            ps.setTimestamp(6, Timestamp.from(journal.postedAtUtc()));
            ps.setObject(7, journal.tenantId());
            ps.setString(8, journal.createdBy());
            ps.executeUpdate();
        }
    }

    private void insertEntries(Connection conn, Journal journal) throws SQLException {
        // Per-account sequence tracking to avoid re-querying for every entry
        Map<UUID, Long> seqCache = new HashMap<>();
        Map<UUID, String> hashCache = new HashMap<>();

        try (PreparedStatement maxSeqPs = conn.prepareStatement(MAX_SEQUENCE);
             PreparedStatement lastHashPs = conn.prepareStatement(LAST_HASH);
             PreparedStatement insertPs = conn.prepareStatement(INSERT_ENTRY)) {

            for (JournalEntry entry : journal.entries()) {
                UUID accountId = entry.accountId();

                long seq = seqCache.computeIfAbsent(accountId, id -> {
                    try {
                        maxSeqPs.setObject(1, id);
                        try (ResultSet rs = maxSeqPs.executeQuery()) {
                            return rs.next() ? rs.getLong(1) : 0L;
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }) + 1;
                seqCache.put(accountId, seq);

                String prevHash = hashCache.computeIfAbsent(accountId, id -> {
                    try {
                        lastHashPs.setObject(1, id);
                        try (ResultSet rs = lastHashPs.executeQuery()) {
                            return rs.next() ? rs.getString(1) : EntryHashChain.GENESIS;
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });

                String hash = EntryHashChain.compute(prevHash, entry, seq);
                hashCache.put(accountId, hash);

                insertPs.setObject(1, entry.entryId());
                insertPs.setObject(2, journal.journalId());
                insertPs.setObject(3, accountId);
                insertPs.setString(4, entry.direction().name());
                insertPs.setBigDecimal(5, entry.amount().getAmount());
                insertPs.setString(6, entry.amount().currencyCode());
                insertPs.setString(7, entry.description());
                insertPs.setString(8, hash);
                insertPs.setLong(9, seq);
                insertPs.addBatch();
            }
            insertPs.executeBatch();
        }
    }

    private void insertOutbox(Connection conn, Journal journal) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_OUTBOX)) {
            String payload = serializeJournalPayload(journal);
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, journal.journalId());
            ps.setString(3, payload);
            ps.setObject(4, journal.tenantId());
            ps.executeUpdate();
        }
    }

    private String serializeJournalPayload(Journal journal) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "journalId", journal.journalId().toString(),
                    "reference", journal.reference(),
                    "tenantId", journal.tenantId().toString(),
                    "entryCount", journal.entries().size()
            ));
        } catch (Exception e) {
            return "{\"journalId\":\"" + journal.journalId() + "\"}";
        }
    }

    private static Journal mapJournalFromRows(ResultSet rs) throws SQLException {
        JournalBuilder builder = null;

        while (rs.next()) {
            if (builder == null) {
                builder = readJournalBuilder(rs);
            }
            builder.entries.add(readEntry(rs));
        }
        if (builder == null) return null;
        return builder.build();
    }

    private static List<Journal> mapJournalsFromRows(ResultSet rs) throws SQLException {
        Map<UUID, JournalBuilder> builders = new HashMap<>();
        List<UUID> order = new ArrayList<>();

        while (rs.next()) {
            UUID jid = (UUID) rs.getObject("journal_id");
            if (!builders.containsKey(jid)) {
                order.add(jid);
                builders.put(jid, readJournalBuilder(rs));
            }
            builders.get(jid).entries.add(readEntry(rs));
        }

        List<Journal> journals = new ArrayList<>();
        for (UUID id : order) {
            journals.add(builders.get(id).build());
        }
        return journals;
    }

    private static JournalBuilder readJournalBuilder(ResultSet rs) throws SQLException {
        return new JournalBuilder(
                (UUID) rs.getObject("journal_id"),
                rs.getString("reference"),
                rs.getString("description"),
                rs.getString("fiscal_year"),
                rs.getString("posted_at_bs"),
                rs.getTimestamp("posted_at_utc").toInstant(),
                (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("created_by")
        );
    }

    private static JournalEntry readEntry(ResultSet rs) throws SQLException {
        Currency currency = new Currency(
                rs.getString("currency_code"),
                rs.getString("cur_name"),
                rs.getString("cur_symbol"),
                rs.getInt("decimal_places"),
                RoundingMode.valueOf(rs.getString("rounding_mode"))
        );
        MonetaryAmount amount = MonetaryAmount.of(rs.getBigDecimal("amount"), currency);
        return new JournalEntry(
                (UUID) rs.getObject("entry_id"),
                (UUID) rs.getObject("journal_id"),
                (UUID) rs.getObject("account_id"),
                Direction.valueOf(rs.getString("direction")),
                amount,
                rs.getString("entry_desc"),
                rs.getString("entry_hash"),
                rs.getLong("sequence_num")
        );
    }

    /** Mutable accumulator used when loading multiple journals from ResultSet. */
    private static final class JournalBuilder {
        final UUID journalId;
        final String reference;
        final String description;
        final String fiscalYear;
        final String postedAtBs;
        final Instant postedAtUtc;
        final UUID tenantId;
        final UUID createdBy;
        final List<JournalEntry> entries = new ArrayList<>();

        JournalBuilder(UUID journalId, String reference, String description,
                       String fiscalYear, String postedAtBs, Instant postedAtUtc,
                       UUID tenantId, UUID createdBy) {
            this.journalId = journalId;
            this.reference = reference;
            this.description = description;
            this.fiscalYear = fiscalYear;
            this.postedAtBs = postedAtBs;
            this.postedAtUtc = postedAtUtc;
            this.tenantId = tenantId;
            this.createdBy = createdBy;
        }

        Journal build() {
            return Journal.reconstruct(journalId, reference, description,
                    fiscalYear, postedAtBs, postedAtUtc, tenantId, createdBy, entries);
        }
    }
}
