package com.ghatana.finance.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * @doc.type class
 * @doc.purpose Persists transaction-processing idempotency state in JDBC storage so results survive restarts
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class JdbcTransactionProcessingIdempotencyStore implements TransactionIdempotencyStore {

    private static final String INSERT_SQL = """
        INSERT INTO finance_transaction_idempotency (
            transaction_id,
            fingerprint,
            status,
            message,
            metadata_json,
            expires_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
    private static final String SELECT_SQL = """
        SELECT fingerprint, status, message, metadata_json, expires_at
        FROM finance_transaction_idempotency
        WHERE transaction_id = ?
        """;
    private static final String DELETE_SQL = "DELETE FROM finance_transaction_idempotency WHERE transaction_id = ?";
    private static final String MISSING_TABLE_SQL_STATE = "42P01";
    private static final String DUPLICATE_KEY_SQL_STATE = "23505";

    private final DataSource dataSource;
    private final Duration ttl;
    private final Clock clock;

    public JdbcTransactionProcessingIdempotencyStore(DataSource dataSource, Duration ttl, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        FinanceTransactionPersistenceSupport.migrate(dataSource);
    }

    @Override
    public TransactionResult get(String transactionId, String fingerprint) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");

        try {
            return withInitializedSchema(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    PersistedEntry entry = load(connection, transactionId);
                    if (entry == null) {
                        return null;
                    }

                    Instant now = Instant.now(clock);
                    if (entry.isExpired(now)) {
                        delete(connection, transactionId);
                        FinanceTransactionPersistenceSupport.commitIfNeeded(connection);
                        return null;
                    }
                    if (!entry.fingerprint().equals(fingerprint)) {
                        throw conflict(transactionId);
                    }
                    return entry.result();
                }
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read finance transaction idempotency state", exception);
        }
    }

    @Override
    public TransactionResult putIfAbsent(String transactionId, String fingerprint, TransactionResult result) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        Objects.requireNonNull(result, "result must not be null");

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(ttl);

        try {
            return withInitializedSchema(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    while (true) {
                        PersistedEntry existing = load(connection, transactionId);
                        if (existing != null) {
                            if (existing.isExpired(now)) {
                                delete(connection, transactionId);
                                FinanceTransactionPersistenceSupport.commitIfNeeded(connection);
                                continue;
                            }
                            if (!existing.fingerprint().equals(fingerprint)) {
                                throw conflict(transactionId);
                            }
                            return existing.result();
                        }

                        if (insert(connection, transactionId, fingerprint, result, expiresAt, now)) {
                            FinanceTransactionPersistenceSupport.commitIfNeeded(connection);
                            return result;
                        }
                    }
                }
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to persist finance transaction idempotency state", exception);
        }
    }

    private PersistedEntry load(Connection connection, String transactionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new PersistedEntry(
                    resultSet.getString("fingerprint"),
                    new TransactionResult(
                        resultSet.getString("status"),
                        resultSet.getString("message"),
                        FinanceTransactionPersistenceSupport.readJson(resultSet.getString("metadata_json"))
                    ),
                    FinanceTransactionPersistenceSupport.toInstant(resultSet.getTimestamp("expires_at"))
                );
            }
        }
    }

    private boolean insert(
            Connection connection,
            String transactionId,
            String fingerprint,
            TransactionResult result,
            Instant expiresAt,
            Instant updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, transactionId);
            statement.setString(2, fingerprint);
            statement.setString(3, result.getStatus());
            statement.setString(4, result.getMessage());
            statement.setString(5, FinanceTransactionPersistenceSupport.writeJson(result.getMetadata()));
            statement.setTimestamp(6, FinanceTransactionPersistenceSupport.toTimestamp(expiresAt));
            statement.setTimestamp(7, FinanceTransactionPersistenceSupport.toTimestamp(updatedAt));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            if (isDuplicateKey(exception)) {
                FinanceTransactionPersistenceSupport.rollbackIfNeeded(connection);
                return false;
            }
            throw exception;
        }
    }

    private static void delete(Connection connection, String transactionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, transactionId);
            statement.executeUpdate();
        }
    }

    private <T> T withInitializedSchema(SqlOperation<T> operation) throws SQLException {
        try {
            return operation.run();
        } catch (SQLException exception) {
            if (!isMissingTable(exception)) {
                throw exception;
            }
            FinanceTransactionPersistenceSupport.migrate(dataSource);
            return operation.run();
        }
    }

    private static boolean isMissingTable(SQLException exception) {
        return MISSING_TABLE_SQL_STATE.equals(exception.getSQLState());
    }

    private static boolean isDuplicateKey(SQLException exception) {
        return DUPLICATE_KEY_SQL_STATE.equals(exception.getSQLState());
    }

    private static IllegalStateException conflict(String transactionId) {
        return new IllegalStateException(
            "Transaction '" + transactionId + "' was already processed with different content"
        );
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }

    private record PersistedEntry(String fingerprint, TransactionResult result, Instant expiresAt) {
        private boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }
}