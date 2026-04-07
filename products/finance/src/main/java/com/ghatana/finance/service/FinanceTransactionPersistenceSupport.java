package com.ghatana.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.core.database.migration.FlywayMigration;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * @doc.type class
 * @doc.purpose Centralizes migration and JSON handling for Finance transaction persistence helpers
 * @doc.layer product
 * @doc.pattern Utility
 */
final class FinanceTransactionPersistenceSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String[] MIGRATION_LOCATIONS = {"classpath:db/migration/finance-transactions"};
    private static final String MIGRATION_TABLE = "finance_transaction_schema_history";

    private FinanceTransactionPersistenceSupport() {
    }

    static void migrate(DataSource dataSource) {
        FlywayMigration.builder()
            .dataSource(Objects.requireNonNull(dataSource, "dataSource cannot be null"))
            .locations(MIGRATION_LOCATIONS)
            .table(MIGRATION_TABLE)
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
            .build()
            .migrate();
    }

    static String writeJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize finance transaction metadata", exception);
        }
    }

    static Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize finance transaction metadata", exception);
        }
    }

    static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    static void commitIfNeeded(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }

    static void rollbackIfNeeded(Connection connection) {
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException ignored) {
        }
    }
}