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
import com.ghatana.datacloud.deployment.DeploymentMode;
import com.ghatana.datacloud.deployment.EmbeddedConfig;
import com.ghatana.datacloud.distributed.ClusterCoordinator;
import com.ghatana.datacloud.record.Record;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Embeddable Data Cloud for in-process integration.
 *
 * <p>Provides a lightweight, embeddable data cloud instance that
 * can run within another application (e.g., AEP - Agentic Event
 * Processor) without requiring a separate server process.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li><b>AEP Integration</b> - Event storage within AEP operators</li>
 *   <li><b>Unit Testing</b> - In-memory storage for tests</li>
 *   <li><b>Edge Deployment</b> - Lightweight local storage</li>
 *   <li><b>Caching Layer</b> - Local cache with persistence</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create embedded instance
 * EmbeddableDataCloud dataCloud = EmbeddableDataCloud.create()
 *     .withStorage(StorageType.IN_MEMORY)
 *     .build();
 *
 * // Start and use
 * dataCloud.start().whenComplete((v, e) -> {
 *     dataCloud.store().put("key", record);
 * });
 *
 * // Shutdown when done
 * dataCloud.shutdown();
 * }</pre>
 *
 * <h2>Architecture</h2>
 * <pre>
 * ┌────────────────────────────────────────────────────────────────┐
 * │                    Host Application (AEP)                      │
 * │  ┌──────────────────────────────────────────────────────────┐  │
 * │  │               EmbeddableDataCloud                        │  │
 * │  │  ┌────────────┐  ┌────────────┐  ┌────────────┐          │  │
 * │  │  │   Store    │  │   Query    │  │   Events   │          │  │
 * │  │  │  (Memory)  │  │  (Index)   │  │  (Stream)  │          │  │
 * │  │  └────────────┘  └────────────┘  └────────────┘          │  │
 * │  │                                                          │  │
 * │  │  ┌──────────────────────────────────────────────────┐    │  │
 * │  │  │        Storage Backend (Memory/RocksDB/H2)       │    │  │
 * │  │  └──────────────────────────────────────────────────┘    │  │
 * │  └──────────────────────────────────────────────────────────┘  │
 * └────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @see DeploymentMode#EMBEDDED
 * @see EmbeddedConfig
 * @doc.type interface
 * @doc.purpose Embeddable data cloud facade
 * @doc.layer core
 * @doc.pattern Facade, Builder
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface EmbeddableDataCloud extends AutoCloseable {

    /**
     * Starts the embedded data cloud.
     *
     * @return completion promise
     */
    Promise<Void> start();

    /**
     * Stops the embedded data cloud.
     *
     * @return completion promise
     */
    Promise<Void> stop();

    /**
     * Returns true if the data cloud is running.
     *
     * @return running state
     */
    boolean isRunning();

    /**
     * Returns the record store.
     *
     * @return record store
     */
    EmbeddedStore store();

    /**
     * Returns the query interface.
     *
     * @return query interface
     */
    EmbeddedQuery query();

    /**
     * Returns the event stream interface.
     *
     * @return event stream
     */
    EmbeddedEventStream events();

    /**
     * Returns the cluster coordinator (standalone implementation).
     *
     * @return cluster coordinator
     */
    ClusterCoordinator coordinator();

    /**
     * Returns the deployment configuration.
     *
     * @return deployment config
     */
    DeploymentConfig config();

    @Override
    default void close() {
        stop().getResult();
    }

    /**
     * Creates a new builder for EmbeddableDataCloud.
     *
     * @return builder
     */
    static Builder create() {
        return new Builder();
    }

    /**
     * Creates an in-memory instance for testing.
     *
     * @return in-memory data cloud
     */
    static EmbeddableDataCloud inMemory() {
        return create()
                .withStorage(EmbeddedConfig.EmbeddedStorageType.IN_MEMORY)
                .build();
    }

    /**
     * Record store operations.
     */
    interface EmbeddedStore {

        /**
         * Puts a record.
         *
         * @param key record key
         * @param record the record
         * @return completion promise
         */
        Promise<Void> put(String key, Record record);

        /**
         * Gets a record by key.
         *
         * @param key record key
         * @return optional containing the record
         */
        Promise<Optional<Record>> get(String key);

        /**
         * Deletes a record.
         *
         * @param key record key
         * @return true if deleted
         */
        Promise<Boolean> delete(String key);

        /**
         * Checks if key exists.
         *
         * @param key record key
         * @return true if exists
         */
        Promise<Boolean> exists(String key);

        /**
         * Returns record count.
         *
         * @return count
         */
        Promise<Long> count();

        /**
         * Clears all records.
         *
         * @return completion promise
         */
        Promise<Void> clear();
    }

    /**
     * Query operations.
     */
    interface EmbeddedQuery {

        /**
         * Finds records matching a predicate.
         *
         * @param predicate filter predicate
         * @return matching records
         */
        Promise<Iterable<Record>> find(Predicate<Record> predicate);

        /**
         * Finds records with limit.
         *
         * @param predicate filter predicate
         * @param limit max results
         * @return matching records
         */
        Promise<Iterable<Record>> find(Predicate<Record> predicate, int limit);

        /**
         * Counts records matching predicate.
         *
         * @param predicate filter predicate
         * @return count
         */
        Promise<Long> count(Predicate<Record> predicate);
    }

    /**
     * Event stream for change notifications.
     */
    interface EmbeddedEventStream {

        /**
         * Subscribes to record changes.
         *
         * @param listener change listener
         */
        void subscribe(ChangeListener listener);

        /**
         * Unsubscribes from changes.
         *
         * @param listener the listener to remove
         */
        void unsubscribe(ChangeListener listener);

        /**
         * Change listener interface.
         */
        @FunctionalInterface
        interface ChangeListener {
            /**
             * Called when a record changes.
             *
             * @param event the change event
             */
            void onChange(ChangeEvent event);
        }

        /**
         * Change event.
         *
         * @param type change type
         * @param key record key
         * @param record the record (null for DELETE)
         * @param previousRecord previous record (null for CREATE)
         */
        record ChangeEvent(
                ChangeType type,
                String key,
                Record record,
                Record previousRecord
        ) {
            public enum ChangeType {
                CREATE, UPDATE, DELETE
            }
        }
    }

    /**
     * Builder for EmbeddableDataCloud.
     */
    final class Builder {
        private EmbeddedConfig.EmbeddedStorageType storageType = EmbeddedConfig.EmbeddedStorageType.IN_MEMORY;
        private String dataDirectory = System.getProperty("java.io.tmpdir") + "/datacloud";
        private boolean enableAI = false;
        private Map<String, Object> options = new ConcurrentHashMap<>();

        private Builder() {
        }

        /**
         * Sets the storage type.
         *
         * @param type storage type
         * @return this builder
         */
        public Builder withStorage(EmbeddedConfig.EmbeddedStorageType type) {
            this.storageType = type;
            return this;
        }

        /**
         * Sets the data directory for persistent storage.
         *
         * @param directory data directory path
         * @return this builder
         */
        public Builder withDataDirectory(String directory) {
            this.dataDirectory = directory;
            return this;
        }

        /**
         * Enables AI features.
         *
         * @param enable true to enable
         * @return this builder
         */
        public Builder withAI(boolean enable) {
            this.enableAI = enable;
            return this;
        }

        /**
         * Adds a custom option.
         *
         * @param key option key
         * @param value option value
         * @return this builder
         */
        public Builder withOption(String key, Object value) {
            this.options.put(key, value);
            return this;
        }

        /**
         * Builds the embeddable data cloud.
         *
         * @return configured instance
         */
        public EmbeddableDataCloud build() {
            EmbeddedConfig embeddedConfig = EmbeddedConfig.builder(storageType)
                    .dataDirectory(dataDirectory)
                    .enableAI(enableAI)
                    .build();
            DeploymentConfig config = DeploymentConfig.embedded(embeddedConfig);
            return new DefaultEmbeddableDataCloud(config, options);
        }
    }
}
