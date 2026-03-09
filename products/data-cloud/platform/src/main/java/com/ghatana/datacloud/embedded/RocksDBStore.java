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
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RocksDB-based persistent storage for embedded Data-Cloud.
 *
 * <p>Implements the {@link EmbeddedStore} contract using Facebook/Meta's RocksDB
 * via JNI bindings. Designed for high-throughput write-heavy workloads with
 * LSM-tree optimization.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Persistent</b> — Data survives JVM restarts</li>
 *   <li><b>High Performance</b> — LSM-tree optimized for sequential writes</li>
 *   <li><b>Embedded</b> — No separate server process required</li>
 *   <li><b>Configurable</b> — Write buffer, compression, bloom filters</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose RocksDB persistent storage backend
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class RocksDBStore implements EmbeddedStore {

    private static final Logger log = LoggerFactory.getLogger(RocksDBStore.class);

    static {
        RocksDB.loadLibrary();
    }

    private final Path dataDirectory;
    private final EmbeddedEventStream events;
    private final RocksDBConfig config;
    private final RocksDB db;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Configuration for RocksDB storage.
     *
     * @param writeBufferSize Size of write buffer (memtable) in bytes
     * @param maxWriteBufferNumber Maximum number of write buffers
     * @param enableCompression Whether to enable Snappy compression
     * @param enableBloomFilter Whether to enable bloom filter for faster lookups
     */
    public record RocksDBConfig(
            long writeBufferSize,
            int maxWriteBufferNumber,
            boolean enableCompression,
            boolean enableBloomFilter
    ) {
        public static RocksDBConfig defaults() {
            return new RocksDBConfig(64 * 1024 * 1024, 3, true, true);
        }

        public static RocksDBConfig highThroughput() {
            return new RocksDBConfig(128 * 1024 * 1024, 5, true, true);
        }

        public static RocksDBConfig lowMemory() {
            return new RocksDBConfig(16 * 1024 * 1024, 2, false, false);
        }
    }

    public RocksDBStore(Path dataDirectory, EmbeddedEventStream events, RocksDBConfig config) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory required");
        this.events = Objects.requireNonNull(events, "events required");
        this.config = config != null ? config : RocksDBConfig.defaults();

        try {
            Files.createDirectories(dataDirectory);

            Options options = new Options()
                    .setCreateIfMissing(true)
                    .setWriteBufferSize(this.config.writeBufferSize())
                    .setMaxWriteBufferNumber(this.config.maxWriteBufferNumber());

            if (this.config.enableCompression()) {
                options.setCompressionType(CompressionType.SNAPPY_COMPRESSION);
            }

            if (this.config.enableBloomFilter()) {
                BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
                        .setFilterPolicy(new BloomFilter(10, false));
                options.setTableFormatConfig(tableConfig);
            }

            this.db = RocksDB.open(options, dataDirectory.toAbsolutePath().toString());
            log.info("RocksDB store opened at: {}", dataDirectory);
        } catch (Exception e) {
            throw new RuntimeException("Failed to open RocksDB at " + dataDirectory, e);
        }
    }

    @Override
    public Promise<Void> put(String key, Record record) {
        checkOpen();
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = RecordCodec.serialize(record);
            db.put(keyBytes, valueBytes);
            return Promise.complete();
        } catch (RocksDBException e) {
            log.error("RocksDB put failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Optional<Record>> get(String key) {
        checkOpen();
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] value = db.get(keyBytes);
            if (value == null) {
                return Promise.of(Optional.empty());
            }
            return Promise.of(Optional.of(RecordCodec.deserialize(value)));
        } catch (RocksDBException e) {
            log.error("RocksDB get failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Boolean> delete(String key) {
        checkOpen();
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] existing = db.get(keyBytes);
            if (existing == null) {
                return Promise.of(false);
            }
            db.delete(keyBytes);
            return Promise.of(true);
        } catch (RocksDBException e) {
            log.error("RocksDB delete failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Boolean> exists(String key) {
        checkOpen();
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            return Promise.of(db.get(keyBytes) != null);
        } catch (RocksDBException e) {
            log.error("RocksDB exists failed: key={}", key, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Long> count() {
        checkOpen();
        long count = 0;
        try (RocksIterator iterator = db.newIterator()) {
            iterator.seekToFirst();
            while (iterator.isValid()) {
                count++;
                iterator.next();
            }
        }
        return Promise.of(count);
    }

    @Override
    public Promise<Void> clear() {
        checkOpen();
        try (RocksIterator iterator = db.newIterator()) {
            iterator.seekToFirst();
            while (iterator.isValid()) {
                db.delete(iterator.key());
                iterator.next();
            }
        } catch (RocksDBException e) {
            log.error("RocksDB clear failed", e);
            return Promise.ofException(e);
        }
        return Promise.complete();
    }

    /**
     * Closes the RocksDB instance and releases resources.
     */
    public Promise<Void> close() {
        if (closed.compareAndSet(false, true)) {
            db.close();
            log.info("RocksDB store closed: {}", dataDirectory);
        }
        return Promise.complete();
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("RocksDB store is closed");
        }
    }
}
