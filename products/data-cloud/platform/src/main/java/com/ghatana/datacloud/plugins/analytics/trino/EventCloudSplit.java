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

import io.trino.spi.connector.ConnectorSplit;

import java.util.List;
import java.util.Objects;

/**
 * Represents a unit of work for parallel EventCloud data reading.
 *
 * <p>
 * Each split corresponds to a partition range that can be processed
 * independently by Trino workers.</p>
 *
 * @doc.type class
 * @doc.purpose Trino split definition
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class EventCloudSplit implements ConnectorSplit {

    private final String tableName;
    private final String tenantId;
    private final int partitionId;
    private final long startOffset;
    private final long endOffset;
    private final List<String> preferredNodes;

    /**
     * Creates a new split.
     *
     * @param tableName The table this split belongs to
     * @param tenantId The tenant ID
     * @param partitionId The partition ID
     * @param startOffset The starting offset (inclusive)
     * @param endOffset The ending offset (exclusive)
     * @param preferredNodes List of preferred worker nodes
     */
    public EventCloudSplit(
            String tableName,
            String tenantId,
            int partitionId,
            long startOffset,
            long endOffset,
            List<String> preferredNodes) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.partitionId = partitionId;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.preferredNodes = preferredNodes != null ? List.copyOf(preferredNodes) : List.of();
    }

    /**
     * Gets the table name.
     *
     * @return The table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Gets the tenant ID.
     *
     * @return The tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the partition ID.
     *
     * @return The partition ID
     */
    public int getPartitionId() {
        return partitionId;
    }

    /**
     * Gets the starting offset (inclusive).
     *
     * @return The start offset
     */
    public long getStartOffset() {
        return startOffset;
    }

    /**
     * Gets the ending offset (exclusive).
     *
     * @return The end offset
     */
    public long getEndOffset() {
        return endOffset;
    }

    /**
     * Gets the number of events in this split.
     *
     * @return The event count
     */
    public long getEventCount() {
        return endOffset - startOffset;
    }

    @Override
    public boolean isRemotelyAccessible() {
        // All EventCloud data is accessible from any node
        return true;
    }

    @Override
    public List<io.trino.spi.HostAddress> getAddresses() {
        // Return preferred nodes as host addresses
        return preferredNodes.stream()
                .map(node -> io.trino.spi.HostAddress.fromString(node))
                .toList();
    }

    @Override
    public Object getInfo() {
        return this;
    }

    @Override
    public long getRetainedSizeInBytes() {
        // Approximate memory footprint
        return 64 + tableName.length() * 2L + tenantId.length() * 2L
                + preferredNodes.stream().mapToLong(s -> s.length() * 2L).sum();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        EventCloudSplit that = (EventCloudSplit) obj;
        return partitionId == that.partitionId
                && startOffset == that.startOffset
                && endOffset == that.endOffset
                && Objects.equals(tableName, that.tableName)
                && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, tenantId, partitionId, startOffset, endOffset);
    }

    @Override
    public String toString() {
        return "EventCloudSplit{"
                + "tableName='" + tableName + '\''
                + ", tenantId='" + tenantId + '\''
                + ", partitionId=" + partitionId
                + ", startOffset=" + startOffset
                + ", endOffset=" + endOffset
                + ", eventCount=" + getEventCount()
                + '}';
    }
}
