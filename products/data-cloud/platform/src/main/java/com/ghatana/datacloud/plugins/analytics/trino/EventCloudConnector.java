/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
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
package com.ghatana.datacloud.plugins.trino;

import io.trino.spi.connector.Connector;
import io.trino.spi.connector.ConnectorContext;
import io.trino.spi.connector.ConnectorMetadata;
import io.trino.spi.connector.ConnectorRecordSetProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplitManager;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.transaction.IsolationLevel;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main EventCloud connector implementation for Trino.
 *
 * <p>
 * This connector provides SQL access to EventCloud data across all storage
 * tiers. It implements the Trino SPI to enable querying events using standard
 * SQL.</p>
 *
 * <p>
 * <b>Supported Operations:</b></p>
 * <ul>
 * <li>SELECT queries with predicate pushdown</li>
 * <li>Partition pruning on tenant_id and time ranges</li>
 * <li>Parallel query execution via splits</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Main Trino connector for EventCloud
 * @doc.layer product
 * @doc.pattern Connector
 */
public class EventCloudConnector implements Connector {

    private final EventCloudConnectorConfig config;
    private final ConnectorContext context;
    private final EventCloudMetadata metadata;
    private final EventCloudSplitManager splitManager;
    private final EventCloudRecordSetProvider recordSetProvider;
    private final AtomicBoolean started;

    /**
     * Creates a new EventCloud connector.
     *
     * @param config Connector configuration
     * @param context Trino connector context
     */
    public EventCloudConnector(EventCloudConnectorConfig config, ConnectorContext context) {
        this.config = config;
        this.context = context;
        this.metadata = new EventCloudMetadata(config);
        this.splitManager = new EventCloudSplitManager(config);
        this.recordSetProvider = new EventCloudRecordSetProvider(config);
        this.started = new AtomicBoolean(false);
    }

    @Override
    public ConnectorTransactionHandle beginTransaction(
            IsolationLevel isolationLevel,
            boolean readOnly,
            boolean autoCommit) {

        // EventCloud is read-only for now, so we just return a simple handle
        return EventCloudTransactionHandle.INSTANCE;
    }

    @Override
    public ConnectorMetadata getMetadata(
            ConnectorSession session,
            ConnectorTransactionHandle transactionHandle) {
        return metadata;
    }

    @Override
    public ConnectorSplitManager getSplitManager() {
        return splitManager;
    }

    @Override
    public ConnectorRecordSetProvider getRecordSetProvider() {
        return recordSetProvider;
    }

    @Override
    public void shutdown() {
        started.set(false);
        // Clean up resources - no shutdown needed for record set provider
    }

    /**
     * Returns the connector configuration.
     *
     * @return Configuration
     */
    public EventCloudConnectorConfig getConfig() {
        return config;
    }

    /**
     * Simple transaction handle for EventCloud (read-only).
     */
    public enum EventCloudTransactionHandle implements ConnectorTransactionHandle {
        INSTANCE
    }
}
