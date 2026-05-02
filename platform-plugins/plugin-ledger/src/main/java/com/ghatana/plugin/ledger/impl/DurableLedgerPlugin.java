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

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Durable, JDBC-backed implementation of {@link LedgerPlugin}.
 *
 * <p>All ledger entries and account states are stored in a relational database,
 * surviving restarts and node replacement. Idempotency is enforced by the
 * {@code transaction_id} unique constraint â€” duplicate posts are silently
 * returned as their original entry ID.
 *
 * <p>Call {@link #ensureSchema()} once during application startup before the
 * plugin is started (e.g. from a Flyway migration or an initialisation hook).
 *
 * <p>For development and testing where durability is not required, prefer
 * {@link StandardLedgerPlugin} instead.
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed ledger plugin with idempotent posting
 * @doc.layer platform
 * @doc.pattern Plugin Implementation, Adapter
 * @since 1.1.0
 */
public final class DurableLedgerPlugin implements LedgerPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(DurableLedgerPlugin.class);
    private static final String ENTRIES_TABLE = "plugin_ledger_entries";
    private static final String ACCOUNTS_TABLE = "plugin_ledger_accounts";

    private static final PluginMetadata METADATA = PluginMetadata.builder()
            .id("durable-ledger-plugin")
            .name("Durable Ledger Plugin")
            .version("1.1.0")
            .description("JDBC-backed durable double-entry ledger with idempotent posting")
            .type(PluginType.CUSTOM)
            .author("Ghatana")
            .license("Apache-2.0")
            .capability("ledger:post-transaction", "ledger:reverse-transaction",
                        "ledger:create-account", "ledger:query-entries")
            .build();

    private final DataSource dataSource;
    private PluginState state = PluginState.UNLOADED;

    /**
     * Creates a new durable ledger plugin backed by the given data source.
     *
     * @param dataSource the JDBC data source; must not be null
     */
    public DurableLedgerPlugin(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    /**
     * Creates the backing tables if they do not yet exist.
     *
     * <p>Idempotent â€” safe to call on every application startup.
     */
    public void ensureSchema() {
        String accountsDdl = """
                CREATE TABLE IF NOT EXISTS %s (
                  account_id   VARCHAR(256) PRIMARY KEY,
                  account_type VARCHAR(64)  NOT NULL,
                  currency     VARCHAR(8)   NOT NULL DEFAULT 'USD',
                  balance      NUMERIC(30, 10) NOT NULL DEFAULT 0,
                  created_at   BIGINT       NOT NULL
                )
                """.formatted(ACCOUNTS_TABLE);

        String entriesDdl = """
                CREATE TABLE IF NOT EXISTS %s (
                  entry_id        VARCHAR(128) PRIMARY KEY,
                  transaction_id  VARCHAR(256) NOT NULL UNIQUE,
                  debit_account   VARCHAR(256) NOT NULL,
                  credit_account  VARCHAR(256) NOT NULL,
                  amount          NUMERIC(30, 10) NOT NULL,
                  currency        VARCHAR(8)   NOT NULL,
                  description     TEXT,
                  posted_at       BIGINT       NOT NULL,
                  reversed        BOOLEAN      NOT NULL DEFAULT FALSE
                )
                """.formatted(ENTRIES_TABLE);

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(accountsDdl)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(entriesDdl)) {
                ps.executeUpdate();
            }
            LOG.info("DurableLedgerPlugin: schema ensured");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialise ledger schema", e);
        }
    }

    // -------------------------------------------------------------------------
    // Plugin lifecycle
    // -------------------------------------------------------------------------

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
        state = PluginState.INITIALIZED;
        LOG.info("DurableLedgerPlugin initialized");
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        state = PluginState.STARTED;
        LOG.info("DurableLedgerPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        state = PluginState.STOPPED;
        LOG.info("DurableLedgerPlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        state = PluginState.UNLOADED;
        LOG.info("DurableLedgerPlugin shutdown");
        return Promise.complete();
    }

    // -------------------------------------------------------------------------
    // LedgerPlugin operations
    // -------------------------------------------------------------------------

    @Override
    public Promise<String> postTransaction(LedgerTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction cannot be null");

        // Idempotency check â€” return existing entry ID if already posted
        Optional<String> existing = findEntryIdForTransaction(transaction.getTransactionId());
        if (existing.isPresent()) {
            LOG.info("Transaction {} already posted as entry {}",
                    transaction.getTransactionId(), existing.get());
            return Promise.of(existing.get());
        }

        String entryId = UUID.randomUUID().toString();
        Instant postedAt = transaction.getOccurredAt() != null ? transaction.getOccurredAt() : Instant.now();

        String sql = """
                INSERT INTO %s
                  (entry_id, transaction_id, debit_account, credit_account,
                   amount, currency, description, posted_at, reversed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)
                """.formatted(ENTRIES_TABLE);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entryId);
            ps.setString(2, transaction.getTransactionId());
            ps.setString(3, transaction.getDebitAccount());
            ps.setString(4, transaction.getCreditAccount());
            ps.setBigDecimal(5, transaction.getAmount());
            ps.setString(6, transaction.getCurrency());
            ps.setString(7, transaction.getDescription());
            ps.setLong(8, postedAt.toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to post transaction " + transaction.getTransactionId(), e);
        }

        // Adjust account balances
        adjustBalance(transaction.getDebitAccount(), transaction.getAmount().negate(), transaction.getCurrency());
        adjustBalance(transaction.getCreditAccount(), transaction.getAmount(), transaction.getCurrency());

        LOG.info("Posted transaction {} to entry {}: {} {} from {} to {}",
                transaction.getTransactionId(), entryId,
                transaction.getAmount(), transaction.getCurrency(),
                transaction.getDebitAccount(), transaction.getCreditAccount());

        return Promise.of(entryId);
    }

    @Override
    public Promise<String> reverseTransaction(String originalTransactionId, String reversalReason) {
        Objects.requireNonNull(originalTransactionId, "originalTransactionId cannot be null");

        LedgerEntry original = loadEntryByTransactionId(originalTransactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Original transaction not found: " + originalTransactionId));

        // Mark original as reversed
        markReversed(original.entryId());

        // Post the reversal entry with debit/credit swapped
        String reversalEntryId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO %s
                  (entry_id, transaction_id, debit_account, credit_account,
                   amount, currency, description, posted_at, reversed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)
                """.formatted(ENTRIES_TABLE);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reversalEntryId);
            ps.setString(2, originalTransactionId + "-reversal");
            ps.setString(3, original.creditAccount());  // swapped
            ps.setString(4, original.debitAccount());   // swapped
            ps.setBigDecimal(5, original.amount());
            ps.setString(6, original.currency());
            ps.setString(7, "Reversal: " + reversalReason);
            ps.setLong(8, Instant.now().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to post reversal for transaction " + originalTransactionId, e);
        }

        // Adjust balances back
        adjustBalance(original.creditAccount(), original.amount().negate(), original.currency());
        adjustBalance(original.debitAccount(), original.amount(), original.currency());

        LOG.info("Reversed transaction {} with reversal entry {}", originalTransactionId, reversalEntryId);
        return Promise.of(reversalEntryId);
    }

    @Override
    public Promise<PostingStatus> getPostingStatus(String transactionId) {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Optional<LedgerEntry> entry = loadEntryByTransactionId(transactionId);
        if (entry.isEmpty()) {
            return Promise.of(PostingStatus.NOT_FOUND);
        }
        return Promise.of(entry.get().entryId() != null && isReversed(transactionId)
                ? PostingStatus.REVERSED : PostingStatus.POSTED);
    }

    @Override
    public Promise<LedgerAccount> createAccount(String accountId, AccountType type) {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        String sql = """
                INSERT INTO %s (account_id, account_type, currency, balance, created_at)
                VALUES (?, ?, 'USD', 0, ?)
                """.formatted(ACCOUNTS_TABLE);

        Instant now = Instant.now();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountId);
            ps.setString(2, type.name());
            ps.setLong(3, now.toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                return Promise.ofException(
                        new IllegalArgumentException("Account already exists: " + accountId));
            }
            throw new IllegalStateException("Failed to create account " + accountId, e);
        }

        return Promise.of(new LedgerAccount(accountId, type, "USD", BigDecimal.ZERO, now));
    }

    @Override
    public Promise<Optional<LedgerEntry>> getEntry(String entryId) {
        Objects.requireNonNull(entryId, "entryId cannot be null");
        String sql = """
                SELECT entry_id, transaction_id, debit_account, credit_account,
                       amount, currency, description, posted_at
                  FROM %s WHERE entry_id = ?
                """.formatted(ENTRIES_TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                return Promise.of(rs.next() ? Optional.of(rowToEntry(rs)) : Optional.empty());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load entry " + entryId, e);
        }
    }

    @Override
    public Promise<List<LedgerEntry>> queryEntries(String accountId, TimeRange range) {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(range, "range cannot be null");

        String sql = """
                SELECT entry_id, transaction_id, debit_account, credit_account,
                       amount, currency, description, posted_at
                  FROM %s
                 WHERE (debit_account = ? OR credit_account = ?)
                   AND posted_at >= ? AND posted_at <= ?
                 ORDER BY posted_at ASC
                """.formatted(ENTRIES_TABLE);

        List<LedgerEntry> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountId);
            ps.setString(2, accountId);
            ps.setLong(3, range.start().toEpochMilli());
            ps.setLong(4, range.end().toEpochMilli());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToEntry(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query entries for account " + accountId, e);
        }
        return Promise.of(result);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Optional<String> findEntryIdForTransaction(String transactionId) {
        String sql = "SELECT entry_id FROM %s WHERE transaction_id = ?".formatted(ENTRIES_TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString("entry_id")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Idempotency check failed for " + transactionId, e);
        }
    }

    private Optional<LedgerEntry> loadEntryByTransactionId(String transactionId) {
        String sql = """
                SELECT entry_id, transaction_id, debit_account, credit_account,
                       amount, currency, description, posted_at
                  FROM %s WHERE transaction_id = ?
                """.formatted(ENTRIES_TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rowToEntry(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load entry for transaction " + transactionId, e);
        }
    }

    private boolean isReversed(String transactionId) {
        String sql = "SELECT reversed FROM %s WHERE transaction_id = ?".formatted(ENTRIES_TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("reversed");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check reversal status for " + transactionId, e);
        }
    }

    private void markReversed(String entryId) {
        String sql = "UPDATE %s SET reversed = TRUE WHERE entry_id = ?".formatted(ENTRIES_TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entryId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to mark entry reversed: " + entryId, e);
        }
    }

    private void adjustBalance(String accountId, BigDecimal delta, String currency) {
        String updateSql = "UPDATE %s SET balance = balance + ? WHERE account_id = ?".formatted(ACCOUNTS_TABLE);
        String insertSql = """
                INSERT INTO %s (account_id, account_type, currency, balance, created_at)
                VALUES (?, 'ASSET', ?, ?, ?)
                """.formatted(ACCOUNTS_TABLE);
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setBigDecimal(1, delta);
                update.setString(2, accountId);
                int updatedRows = update.executeUpdate();
                if (updatedRows > 0) {
                    return;
                }
            }

            try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                insert.setString(1, accountId);
                insert.setString(2, currency);
                insert.setBigDecimal(3, delta);
                insert.setLong(4, Instant.now().toEpochMilli());
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to adjust balance for account " + accountId, e);
        }
    }

    private LedgerEntry rowToEntry(ResultSet rs) throws SQLException {
        return new LedgerEntry(
                rs.getString("entry_id"),
                rs.getString("transaction_id"),
                rs.getString("debit_account"),
                rs.getString("credit_account"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("description"),
                Instant.ofEpochMilli(rs.getLong("posted_at")));
    }
}
