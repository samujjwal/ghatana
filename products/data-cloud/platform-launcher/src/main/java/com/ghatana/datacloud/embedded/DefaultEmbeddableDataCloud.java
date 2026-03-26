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

import com.ghatana.datacloud.deployment.DeploymentConfig;
import com.ghatana.datacloud.deployment.EmbeddedConfig;
import com.ghatana.datacloud.distributed.ClusterCoordinator;
import com.ghatana.datacloud.distributed.NodeInfo;
import com.ghatana.datacloud.distributed.StandaloneClusterCoordinator;
import com.ghatana.datacloud.embedded.EmbeddableDataCloud.EmbeddedEventStream.ChangeEvent;
import com.ghatana.datacloud.embedded.EmbeddableDataCloud.EmbeddedEventStream.ChangeEvent.ChangeType;
import com.ghatana.datacloud.record.Record;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Default implementation of EmbeddableDataCloud with pluggable storage backends.
 *
 * <p>Supports multiple storage backends based on {@link EmbeddedConfig}:
 * <ul>
 *   <li><b>IN_MEMORY</b> - Fast, non-persistent (testing, development)</li>
 *   <li><b>ROCKS_DB</b> - Persistent LSM-tree (production)</li>
 *   <li><b>SQLITE</b> - Persistent SQL (edge/IoT)</li>
 *   <li><b>H2</b> - Persistent SQL (pure Java)</li>
 * </ul>
 *
 * <h2>Storage Backend Selection</h2>
 * <p>Storage backend is determined by {@link EmbeddedConfig#storageType()}:
 * <pre>{@code
 * // In-memory (testing)
 * EmbeddedConfig config = EmbeddedConfig.forTesting();
 * EmbeddableDataCloud dc = EmbeddableDataCloud.create()
 *     .withStorage(config.storageType())
 *     .build();
 *
 * // RocksDB (production)
 * EmbeddedConfig config = EmbeddedConfig.forProduction("/var/data");
 * EmbeddableDataCloud dc = EmbeddableDataCloud.create()
 *     .withStorage(config.storageType())
 *     .withDataDirectory("/var/data")
 *     .build();
 * }</pre>
 *
 * @see EmbeddableDataCloud
 * @see EmbeddedConfig
 * @doc.type class
 * @doc.purpose Default embedded data cloud with pluggable storage
 * @doc.layer core
 * @doc.pattern Facade, Strategy (storage backends)
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public final class DefaultEmbeddableDataCloud implements EmbeddableDataCloud {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmbeddableDataCloud.class);

    private final DeploymentConfig config;
    private final Map<String, Object> options;
    private final StandaloneClusterCoordinator coordinator;
    private final EmbeddedStore store;
    private final EmbeddedQuery query;
    private final EmbeddedEventStream events;
    
    private volatile boolean running;

    /**
     * Creates a new DefaultEmbeddableDataCloud.
     *
     * <p><b>Storage Backend Creation:</b>
     * <ul>
     *   <li>{@link EmbeddedConfig.EmbeddedStorageType#IN_MEMORY} → {@link InMemoryStore}</li>
     *   <li>{@link EmbeddedConfig.EmbeddedStorageType#ROCKS_DB} → {@link RocksDBStore} (TODO)</li>
     *   <li>{@link EmbeddedConfig.EmbeddedStorageType#SQLITE} → {@link SQLiteStore} (TODO)</li>
     *   <li>{@link EmbeddedConfig.EmbeddedStorageType#H2} → {@link H2Store} (TODO)</li>
     * </ul>
     *
     * @param config deployment configuration
     * @param options additional options (cache size, AI config, etc.)
     */
    @SuppressWarnings("unused") // Options reserved for future use
    public DefaultEmbeddableDataCloud(DeploymentConfig config, Map<String, Object> options) {
        this.config = config;
        this.options = Map.copyOf(options);
        
        // Extract embedded config
        EmbeddedConfig embeddedConfig = config.embeddedConfig() != null
                ? config.embeddedConfig()
                : EmbeddedConfig.forTesting();
        
        log.info("Creating embedded Data-Cloud. storageType={}, dataDir={}, enableAI={}, cacheSize={}",
                embeddedConfig.storageType(),
                embeddedConfig.dataDirectory(),
                embeddedConfig.enableAI(),
                embeddedConfig.maxCacheEntries());
        
        // Create coordinator
        this.coordinator = new StandaloneClusterCoordinator(
                "embedded-" + System.nanoTime(),
                "localhost",
                8080
        );
        
        // Create event stream
        this.events = new InMemoryEventStream();
        
        // Create storage backend based on config
        this.store = createStore(embeddedConfig, events);
        this.query = new AdaptiveQuery(store);
        this.running = false;
    }
    
    /**
     * Creates the appropriate storage backend based on configuration.
     *
     * @param config embedded configuration
     * @param events event stream for change notifications
     * @return configured store implementation
     */
    private EmbeddedStore createStore(EmbeddedConfig config, EmbeddedEventStream events) {
        // Cast to InMemoryEventStream for passing to stores that need it
        InMemoryEventStream inMemoryEvents = (InMemoryEventStream) events;
        
        return switch (config.storageType()) {
            case IN_MEMORY -> {
                log.info("Using IN_MEMORY storage (non-persistent)");
                yield new InMemoryStore(inMemoryEvents);
            }
            case ROCKS_DB -> {
                log.info("Using ROCKS_DB storage at: {}", config.dataDirectory());
                yield new RocksDBStore(
                        config.dataDirectory(),
                        events,
                        RocksDBStore.RocksDBConfig.defaults()
                );
            }
            case SQLITE -> {
                log.info("Using SQLITE storage at: {}", config.dataDirectory());
                yield new SQLiteStore(
                        config.dataDirectory().resolve("events.db"),
                        events,
                        SQLiteStore.SQLiteConfig.defaults()
                );
            }
            case H2 -> {
                log.info("Using H2 storage at: {}", config.dataDirectory());
                yield new H2Store(
                        config.dataDirectory().resolve("datacloud.h2"),
                        events,
                        H2Store.H2Config.defaults()
                );
            }
        };
    }

    @Override
    public Promise<Void> start() {
        if (running) {
            return Promise.complete();
        }

        NodeInfo nodeInfo = NodeInfo.builder()
                .nodeId("embedded-node")
                .host("localhost")
                .port(8080) // Embedded mode uses placeholder port
                .grpcPort(9090) // Embedded mode uses placeholder grpc port
                .state(NodeInfo.NodeState.RUNNING)
                .capabilities("storage", "query")
                .labels(Map.of("mode", "embedded"))
                .build();

        return coordinator.join(nodeInfo)
                .map(v -> {
                    running = true;
                    return null;
                });
    }

    @Override
    public Promise<Void> stop() {
        if (!running) {
            return Promise.complete();
        }

        running = false;
        return coordinator.shutdown()
                .then(v -> store.clear());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public EmbeddedStore store() {
        return store;
    }

    @Override
    public EmbeddedQuery query() {
        return query;
    }

    @Override
    public EmbeddedEventStream events() {
        return events;
    }

    @Override
    public ClusterCoordinator coordinator() {
        return coordinator;
    }

    @Override
    public DeploymentConfig config() {
        return config;
    }

    /**
     * Returns the options map.
     *
     * @return immutable options map
     */
    public Map<String, Object> getOptions() {
        return options;
    }

    /**
     * In-memory store implementation.
     */
    private static final class InMemoryStore implements EmbeddedStore {
        private final ConcurrentHashMap<String, Record> storage;
        private final InMemoryEventStream events;

        InMemoryStore(InMemoryEventStream events) {
            this.storage = new ConcurrentHashMap<>();
            this.events = events;
        }

        @Override
        public Promise<Void> put(String key, Record record) {
            Record previous = storage.put(key, record);
            
            if (previous == null) {
                events.emit(new ChangeEvent(ChangeType.CREATE, key, record, null));
            } else {
                events.emit(new ChangeEvent(ChangeType.UPDATE, key, record, previous));
            }
            
            return Promise.complete();
        }

        @Override
        public Promise<Optional<Record>> get(String key) {
            return Promise.of(Optional.ofNullable(storage.get(key)));
        }

        @Override
        public Promise<Boolean> delete(String key) {
            Record removed = storage.remove(key);
            if (removed != null) {
                events.emit(new ChangeEvent(ChangeType.DELETE, key, null, removed));
                return Promise.of(true);
            }
            return Promise.of(false);
        }

        @Override
        public Promise<Boolean> exists(String key) {
            return Promise.of(storage.containsKey(key));
        }

        @Override
        public Promise<Long> count() {
            return Promise.of((long) storage.size());
        }

        @Override
        public Promise<Void> clear() {
            storage.clear();
            return Promise.complete();
        }

        Iterable<Record> allRecords() {
            return storage.values();
        }
    }

    /**
     * Adaptive query implementation that works with any store backend.
     * 
     * <p><b>Implementation Strategy</b><br>
     * <ul>
     *   <li><b>InMemoryStore</b> - Direct iteration over records (O(n))</li>
     *   <li><b>RocksDBStore</b> - Iterator-based scan with prefix matching (future)</li>
     *   <li><b>SQLiteStore</b> - SQL WHERE clauses with indexes (future)</li>
     *   <li><b>H2Store</b> - SQL WHERE clauses with indexes (future)</li>
     * </ul>
     * 
     * <p><b>Future Optimizations</b><br>
     * For persistent stores (RocksDB, SQLite, H2), queries can be optimized with:
     * <ul>
     *   <li>Indexes on commonly queried fields</li>
     *   <li>Query planners and cost-based optimization</li>
     *   <li>Predicate pushdown to native store</li>
     *   <li>Caching of query results</li>
     * </ul>
     */
    private static final class AdaptiveQuery implements EmbeddedQuery {
        private final EmbeddedStore store;

        AdaptiveQuery(EmbeddedStore store) {
            this.store = store;
        }

        @Override
        public Promise<Iterable<Record>> find(Predicate<Record> predicate) {
            // For InMemoryStore: Direct iteration
            if (store instanceof InMemoryStore inMemoryStore) {
                List<Record> results = StreamSupport.stream(
                        inMemoryStore.allRecords().spliterator(), false)
                        .filter(predicate)
                        .collect(Collectors.toList());
                return Promise.of(results);
            }
            
            // For persistent stores: Would iterate via store-specific API
            // Example for RocksDBStore: rocksStore.iterate().filter(predicate)
            // Example for SQLiteStore: sqlStore.query("SELECT * FROM records WHERE ...")
            log.warn("Query on persistent store requires store-specific iteration API");
            return Promise.of(List.of());
        }

        @Override
        public Promise<Iterable<Record>> find(Predicate<Record> predicate, int limit) {
            if (store instanceof InMemoryStore inMemoryStore) {
                List<Record> results = StreamSupport.stream(
                        inMemoryStore.allRecords().spliterator(), false)
                        .filter(predicate)
                        .limit(limit)
                        .collect(Collectors.toList());
                return Promise.of(results);
            }
            
            log.warn("Query on persistent store requires store-specific iteration API");
            return Promise.of(List.of());
        }

        @Override
        public Promise<Long> count(Predicate<Record> predicate) {
            if (store instanceof InMemoryStore inMemoryStore) {
                long count = StreamSupport.stream(
                        inMemoryStore.allRecords().spliterator(), false)
                        .filter(predicate)
                        .count();
                return Promise.of(count);
            }
            
            log.warn("Query on persistent store requires store-specific iteration API");
            return Promise.of(0L);
        }
    }

    /**
     * In-memory event stream implementation.
     */
    private static final class InMemoryEventStream implements EmbeddedEventStream {
        private final List<ChangeListener> listeners;

        InMemoryEventStream() {
            this.listeners = new CopyOnWriteArrayList<>();
        }

        @Override
        public void subscribe(ChangeListener listener) {
            listeners.add(listener);
        }

        @Override
        public void unsubscribe(ChangeListener listener) {
            listeners.remove(listener);
        }

        void emit(ChangeEvent event) {
            for (ChangeListener listener : listeners) {
                try {
                    listener.onChange(event);
                } catch (Exception e) {
                    // Log error but don't propagate
                    System.err.println("Event listener error: " + e.getMessage());
                }
            }
        }
    }
}
