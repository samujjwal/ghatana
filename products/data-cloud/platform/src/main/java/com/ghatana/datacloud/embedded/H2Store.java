/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.embedded;

import com.ghatana.datacloud.embedded.EmbeddableDataCloud.EmbeddedEventStream;
import com.ghatana.datacloud.embedded.EmbeddableDataCloud.EmbeddedStore;
import com.ghatana.datacloud.record.Record;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Objects;
import java.util.Optional;

/**
 * H2-based persistent storage for embedded Data-Cloud.
 *
 * <p>Implements the {@link EmbeddedStore} contract using the H2 pure-Java database.
 * Ideal for cross-platform deployments, CI/CD pipelines, and development environments
 * where no native dependencies should be required.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Pure Java</b> — No native dependencies, runs anywhere JVM runs</li>
 *   <li><b>Persistent</b> — File-backed storage survives restarts</li>
 *   <li><b>In-Memory Mode</b> — Available for testing via {@code H2Config.inMemory()}</li>
 *   <li><b>JSON Serialization</b> — Records stored as BLOBs via {@link RecordCodec}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose H2 persistent storage backend (pure Java)
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class H2Store implements EmbeddedStore {

    private static final Logger log = LoggerFactory.getLogger(H2Store.class);

    private final Path databaseFile;
    private final EmbeddedEventStream events;
    private final H2Config config;
    private final Connection connection;

    /**
     * Configuration for H2 storage.
     *
     * @param mode Database mode (EMBEDDED or MEMORY)
     * @param cacheSize Cache size in KB
     * @param pageSize Page size in bytes
     * @param mvStore Whether to use MVStore engine
     * @param compress Whether to compress database file
     */
    public record H2Config(
            String mode,
            int cacheSize,
            int pageSize,
            boolean mvStore,
            boolean compress
    ) {
        public static H2Config defaults() {
            return new H2Config("EMBEDDED", 16384, 2048, true, false);
        }

        public static H2Config highPerformance() {
            return new H2Config("EMBEDDED", 65536, 4096, true, false);
        }

        public static H2Config lowMemory() {
            return new H2Config("EMBEDDED", 4096, 1024, true, true);
        }

        public static H2Config inMemory() {
            return new H2Config("MEMORY", 8192, 2048, true, false);
        }
    }

    public H2Store(Path databaseFile, EmbeddedEventStream events, H2Config config) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile required");
        this.events = Objects.requireNonNull(events, "events required");
        this.config = config != null ? config : H2Config.defaults();

        // Build JDBC URL
        String jdbcUrl;
        if ("MEMORY".equals(this.config.mode())) {
            jdbcUrl = "jdbc:h2:mem:" + databaseFile.getFileName()
                    + ";CACHE_SIZE=" + this.config.cacheSize()
                    + ";DB_CLOSE_DELAY=-1";
        } else {
            jdbcUrl = "jdbc:h2:" + databaseFile.toAbsolutePath()
                    + ";CACHE_SIZE=" + this.config.cacheSize()
                    + ";PAGE_SIZE=" + this.config.pageSize();
        }

        try {
            if (!"MEMORY".equals(this.config.mode()) && databaseFile.getParent() != null) {
                Files.createDirectories(databaseFile.getParent());
            }
            this.connection = DriverManager.getConnection(jdbcUrl);
            initializeSchema();
            log.info("H2Store initialized: url={}", jdbcUrl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize H2Store", e);
        }
    }

    @Override
    public Promise<Void> put(String key, Record record) {
        Objects.requireNonNull(key, "key required");
        Objects.requireNonNull(record, "record required");

        try (var stmt = connection.prepareStatement(
                "MERGE INTO records (record_key, data, updated_at) KEY(record_key) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
            stmt.setString(1, key);
            stmt.setBytes(2, RecordCodec.serialize(record));
            stmt.executeUpdate();
            return Promise.complete();
        } catch (SQLException e) {
            log.error("H2 put failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Optional<Record>> get(String key) {
        Objects.requireNonNull(key, "key required");

        try (var stmt = connection.prepareStatement("SELECT data FROM records WHERE record_key = ?")) {
            stmt.setString(1, key);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Promise.of(Optional.of(RecordCodec.deserialize(rs.getBytes("data"))));
                }
                return Promise.of(Optional.empty());
            }
        } catch (SQLException e) {
            log.error("H2 get failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Boolean> delete(String key) {
        Objects.requireNonNull(key, "key required");

        try (var stmt = connection.prepareStatement("DELETE FROM records WHERE record_key = ?")) {
            stmt.setString(1, key);
            return Promise.of(stmt.executeUpdate() > 0);
        } catch (SQLException e) {
            log.error("H2 delete failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Boolean> exists(String key) {
        Objects.requireNonNull(key, "key required");

        try (var stmt = connection.prepareStatement("SELECT 1 FROM records WHERE record_key = ? LIMIT 1")) {
            stmt.setString(1, key);
            try (var rs = stmt.executeQuery()) {
                return Promise.of(rs.next());
            }
        } catch (SQLException e) {
            log.error("H2 exists failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Long> count() {
        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM records")) {
            return Promise.of(rs.next() ? rs.getLong(1) : 0L);
        } catch (SQLException e) {
            log.error("H2 count failed", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Void> clear() {
        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM records");
            return Promise.complete();
        } catch (SQLException e) {
            log.error("H2 clear failed", e);
            return Promise.ofException(e);
        }
    }

    private void initializeSchema() throws SQLException {
        try (var stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS records (
                    record_key VARCHAR(512) PRIMARY KEY,
                    data BLOB NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_updated_at ON records(updated_at)");
        }
    }

    /**
     * Closes the H2 database connection.
     */
    public Promise<Void> close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.error("Error closing H2 connection", e);
        }
        log.info("H2Store closed");
        return Promise.complete();
    }
}
