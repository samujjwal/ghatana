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

import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorSplitManager;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.DynamicFilter;
import io.trino.spi.connector.FixedSplitSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Split manager for EventCloud Trino connector.
 *
 * <p>
 * This class generates splits for parallel data reading from EventCloud. Each
 * split represents a partition range that can be processed independently.</p>
 *
 * <p>
 * <b>Split Generation Strategy:</b></p>
 * <ul>
 * <li>events table - One split per partition</li>
 * <li>event_types table - Single split (small dataset)</li>
 * <li>streams table - Single split (small dataset)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Trino split manager
 * @doc.layer product
 * @doc.pattern SplitManager
 */
public class EventCloudSplitManager implements ConnectorSplitManager {

    /**
     * Default number of partitions for events table.
     */
    private static final int DEFAULT_PARTITION_COUNT = 16;

    /**
     * Default events per partition (for estimation).
     */
    private static final long DEFAULT_EVENTS_PER_PARTITION = 100_000L;

    private final EventCloudConnectorConfig config;

    /**
     * Creates a new split manager.
     *
     * @param config Connector configuration
     */
    public EventCloudSplitManager(EventCloudConnectorConfig config) {
        this.config = config;
    }

    @Override
    public ConnectorSplitSource getSplits(
            ConnectorTransactionHandle transaction,
            ConnectorSession session,
            ConnectorTableHandle table,
            DynamicFilter dynamicFilter,
            Constraint constraint) {

        EventCloudTableHandle tableHandle = (EventCloudTableHandle) table;
        String tableName = tableHandle.getTableNameOnly();

        List<ConnectorSplit> splits = generateSplits(tableName);

        return new FixedSplitSource(splits);
    }

    private List<ConnectorSplit> generateSplits(String tableName) {
        List<ConnectorSplit> splits = new ArrayList<>();
        String tenantId = config.getDefaultTenantId().orElse("default");

        switch (tableName) {
            case EventCloudMetadata.EVENTS_TABLE:
                // Generate partition-based splits for events table
                splits.addAll(generateEventsSplits(tenantId));
                break;

            case EventCloudMetadata.EVENT_TYPES_TABLE:
            case EventCloudMetadata.STREAMS_TABLE:
                // Single split for small metadata tables
                splits.add(new EventCloudSplit(
                        tableName,
                        tenantId,
                        0,
                        0,
                        Long.MAX_VALUE,
                        List.of()
                ));
                break;

            default:
                // Unknown table - return empty splits
                break;
        }

        return splits;
    }

    private List<ConnectorSplit> generateEventsSplits(String tenantId) {
        List<ConnectorSplit> splits = new ArrayList<>();

        // Generate one split per partition
        // In production, this would query EventCloud for actual partition info
        for (int partitionId = 0; partitionId < DEFAULT_PARTITION_COUNT; partitionId++) {
            splits.add(new EventCloudSplit(
                    EventCloudMetadata.EVENTS_TABLE,
                    tenantId,
                    partitionId,
                    0,
                    DEFAULT_EVENTS_PER_PARTITION,
                    List.of() // No preferred nodes - fully remote accessible
            ));
        }

        return splits;
    }

    /**
     * A split source that fetches splits asynchronously.
     *
     * <p>
     * This implementation is used when splits need to be fetched from
     * EventCloud dynamically.</p>
     */
    static class AsyncEventCloudSplitSource implements ConnectorSplitSource {

        private final CompletableFuture<List<ConnectorSplit>> splitsFuture;
        private List<ConnectorSplit> splits;
        private int currentIndex = 0;
        private boolean finished = false;

        AsyncEventCloudSplitSource(CompletableFuture<List<ConnectorSplit>> splitsFuture) {
            this.splitsFuture = splitsFuture;
        }

        @Override
        public CompletableFuture<ConnectorSplitBatch> getNextBatch(int maxSize) {
            if (finished) {
                return CompletableFuture.completedFuture(new ConnectorSplitBatch(List.of(), true));
            }

            return splitsFuture.thenApply(allSplits -> {
                if (splits == null) {
                    splits = allSplits;
                }

                int toIndex = Math.min(currentIndex + maxSize, splits.size());
                List<ConnectorSplit> batch = splits.subList(currentIndex, toIndex);
                currentIndex = toIndex;

                boolean isLastBatch = currentIndex >= splits.size();
                if (isLastBatch) {
                    finished = true;
                }

                return new ConnectorSplitBatch(batch, isLastBatch);
            });
        }

        @Override
        public void close() {
            finished = true;
        }

        @Override
        public boolean isFinished() {
            return finished;
        }
    }
}
