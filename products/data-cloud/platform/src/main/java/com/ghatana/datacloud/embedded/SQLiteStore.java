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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite-based persistent storage for embedded Data-Cloud.
 *
 * <p>Implements the {@link EmbeddedStore} contract using SQLite via JDBC.
 * Ideal for lightweight, single-file persistent storage on edge devices
 * or development environments.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Persistent</b> — Single-file database, survives restarts</li>
 *   <li><b>Lightweight</b> — Minimal resource consumption</li>
 *   <li><b>WAL Mode</b> — Write-Ahead Logging for concurrent read/write</li>
 *   <li><b>JSON Serialization</b> — Records stored as JSON BLOBs via {@link RecordCodec}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose SQLite persistent storage backend
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class SQLiteStore implements EmbeddedStore {

    private static final Logger log = LoggerFactory.getLogger(SQLiteStore.class);

    private final Path databaseFile;
    private final EmbeddedEventStream events;
    private final SQLiteConfig config;

    /**
     * Configuration for SQLite storage.
     *
     * @param enableWAL Whether to use Write-Ahead Logging mode
     * @param cacheSize SQLite cache size in pages
     * @param busyTimeout Timeout in milliseconds when database is locked
     * @param enableForeignKeys Whether to enforce foreign key constraints
     */
    public record SQLiteConfig(
            boolean enableWAL,
            int cacheSize,
            int busyTimeout,
            boolean enableForeignKeys
    ) {
        public static SQLiteConfig defaults() {
            return new SQLiteConfig(true, 2000, 5000, false);
        }

        public static SQLiteConfig highPerformance() {
            return new SQLiteConfig(true, 10000, 10000, false);
        }

        public static SQLiteConfig lowMemory() {
            return new SQLiteConfig(false, 500, 2000, false);
        }
    }

    public SQLiteStore(Path databaseFile, EmbeddedEventStream events, SQLiteConfig config) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile required");
        this.events = Objects.requireNonNull(events, "events required");
        this.config = config != null ? config : SQLiteConfig.defaults();

        try {
            if (databaseFile.getParent() != null) {
                java.nio.file.Files.createDirectories(databaseFile.getParent());
            }
            initializeSchema();
            log.info("SQLiteStore initialized: file={}", databaseFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SQLiteStore", e);
        }
    }

    @Override
    public Promise<Void> put(String key, Record record) {
        Objects.requireNonNull(key, "key required");
        Objects.requireNonNull(record, "record required");

        try (var conn = getConnection();
             var stmt = conn.prepareStatement(
                 "INSERT OR REPLACE INTO records (record_key, data, created_at, updated_at) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, key);
            stmt.setBytes(2, RecordCodec.serialize(record));
            stmt.setLong(3, System.currentTimeMillis());
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
            return Promise.complete();
        } catch (Exception e) {
            log.error("SQLite put failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Optional<Record>> get(String key) {
        Objects.requireNonNull(key, "key required");

        try (var conn = getConnection();
             var stmt = conn.prepareStatement("SELECT data FROM records WHERE record_key = ?")) {
            stmt.setString(1, key);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Promise.of(Optional.of(RecordCodec.deserialize(rs.getBytes("data"))));
                }
                return Promise.of(Optional.empty());
            }
        } catch (Exception e) {
            log.error("SQLite get failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Boolean> delete(String key) {
        Objects.requireNonNull(key, "key required");

        try (var conn = getConnection();
             var stmt = conn.prepareStatement("DELETE FROM records WHERE record_key = ?")) {
            stmt.setString(1, key);
            return Promise.of(stmt.executeUpdate() > 0);
        } catch (Exception e) {
            log.error("SQLite delete failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Boolean> exists(String key) {
        Objects.requireNonNull(key, "key required");

        try (var conn = getConnection();
             var stmt = conn.prepareStatement("SELECT 1 FROM records WHERE record_key = ? LIMIT 1")) {
            stmt.setString(1, key);
            try (var rs = stmt.executeQuery()) {
                return Promise.of(rs.next());
            }
        } catch (Exception e) {
            log.error("SQLite exists failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Long> count() {
        try (var conn = getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM records")) {
            return Promise.of(rs.next() ? rs.getLong("cnt") : 0L);
        } catch (Exception e) {
            log.error("SQLite count failed", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Void> clear() {
        try (var conn = getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM records");
            return Promise.complete();
        } catch (Exception e) {
            log.error("SQLite clear failed", e);
            return Promise.ofException(e);
        }
    }

    private Connection getConnection() throws Exception {
        String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        Connection conn = DriverManager.getConnection(url);
        try (var stmt = conn.createStatement()) {
            if (config.enableWAL()) {
                stmt.execute("PRAGMA journal_mode=WAL");
            }
            stmt.execute("PRAGMA cache_size=" + config.cacheSize());
            stmt.execute("PRAGMA busy_timeout=" + config.busyTimeout());
            if (config.enableForeignKeys()) {
                stmt.execute("PRAGMA foreign_keys=ON");
            }
        }
        return conn;
    }

    private void initializeSchema() throws Exception {
        try (var conn = getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS records (
                    record_key TEXT PRIMARY KEY,
                    data BLOB NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_updated_at ON records(updated_at)");
        }
    }

    /**
     * Closes the SQLite store.
     */
    public Promise<Void> close() {
        log.info("SQLiteStore closed: {}", databaseFile);
        return Promise.complete();
    }
}
