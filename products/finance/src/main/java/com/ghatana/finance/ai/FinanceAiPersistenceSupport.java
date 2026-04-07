package com.ghatana.finance.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.core.database.migration.FlywayMigration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Shared persistence support for finance AI repositories.
 *
 * @doc.type class
 * @doc.purpose Centralizes schema migration and JSON mapping for finance AI JDBC repositories
 * @doc.layer product
 * @doc.pattern Utility
 */
final class FinanceAiPersistenceSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String[] MIGRATION_LOCATIONS = {"classpath:db/migration/finance-ai"};
    private static final String MIGRATION_TABLE = "finance_ai_schema_history";

    private FinanceAiPersistenceSupport() {
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
            throw new IllegalStateException("Failed to serialize JSON payload", exception);
        }
    }

    static Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize JSON payload", exception);
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
            // Preserve the original repository failure.
        }
    }
}