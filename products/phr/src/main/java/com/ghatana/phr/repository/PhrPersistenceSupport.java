package com.ghatana.phr.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.core.database.migration.FlywayMigration;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared persistence support for PHR repositories.
 *
 * @doc.type class
 * @doc.purpose Centralizes schema migration and JSON mapping for PHR JDBC repositories
 * @doc.layer product
 * @doc.pattern Utility
 */
final class PhrPersistenceSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String[] MIGRATION_LOCATIONS = {"classpath:db/migration/phr-repository"};
    private static final String MIGRATION_TABLE = "phr_repository_schema_history";

    private PhrPersistenceSupport() {
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

    static String writeMapJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize repository payload", exception);
        }
    }

    static Map<String, Object> readMapJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize repository payload", exception);
        }
    }

    static String writeStringSet(Set<String> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Set.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize string set", exception);
        }
    }

    static Set<String> readStringSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize string set", exception);
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
}
