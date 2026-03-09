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
package com.ghatana.datacloud.event.spi;

import com.ghatana.datacloud.event.model.Event;
import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SPI for pluggable event archive backends.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides long-term storage and retrieval for events. Implementations must
 * ensure:
 * <ul>
 * <li><b>Durability</b>: Events stored with configurable retention</li>
 * <li><b>Time-Travel</b>: Query events at specific points in time</li>
 * <li><b>Compression</b>: Efficient storage with compaction</li>
 * <li><b>Isolation</b>: Tenant separation enforced on all operations</li>
 * </ul>
 *
 * <p>
 * <b>Implementations</b><br>
 * <ul>
 * <li><b>DeltaLakeArchivePlugin</b>: ACID-compliant lakehouse storage</li>
 * <li><b>IcebergArchivePlugin</b>: Apache Iceberg table format</li>
 * <li><b>S3ArchivePlugin</b>: S3/Glacier cold storage</li>
 * </ul>
 *
 * <p>
 * <b>Archive Tiers</b><br>
 * <pre>
 * L2 (Cool)  → Delta Lake/Iceberg → Analytics queries (weeks-months)
 * L3 (Cold)  → S3 Standard        → Infrequent access (months-years)
 * L4 (Frozen)→ S3 Glacier        → Compliance archive (years+)
 * </pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * All methods are async (Promise-based) and safe for concurrent calls.
 *
 * @see com.ghatana.platform.plugin.Plugin
 * @see Event
 * @see StoragePlugin
 * @doc.type interface
 * @doc.purpose SPI for event archive backends
 * @doc.layer spi
 * @doc.pattern Plugin, Strategy
 */
public interface ArchivePlugin extends Plugin {

    // ==================== Archive Operations ====================
    /**
     * Archive events to long-term storage.
     *
     * <p>
     * Events are written as a batch with ACID guarantees if supported.</p>
     *
     * @param events events to archive (must have tenantId)
     * @return Promise with archive result containing statistics
     */
    Promise<ArchiveResult> archive(List<Event> events);

    /**
     * Archive events from a specific time range.
     *
     * <p>
     * Typically used for scheduled archival of events from warm storage.</p>
     *
     * @param tenantId tenant for isolation
     * @param startTime start time (inclusive)
     * @param endTime end time (exclusive)
     * @return Promise with archive result
     */
    Promise<ArchiveResult> archiveByTimeRange(String tenantId, Instant startTime, Instant endTime);

    // ==================== Restore Operations ====================
    /**
     * Restore events from archive by time range.
     *
     * @param startTime start time (inclusive)
     * @param endTime end time (exclusive)
     * @param eventType optional event type filter
     * @return Promise with list of restored events
     */
    Promise<List<Event>> restore(Instant startTime, Instant endTime, Optional<String> eventType);

    /**
     * Restore events for a specific tenant.
     *
     * @param tenantId tenant for isolation
     * @param startTime start time (inclusive)
     * @param endTime end time (exclusive)
     * @return Promise with list of restored events
     */
    Promise<List<Event>> restoreForTenant(String tenantId, Instant startTime, Instant endTime);

    // ==================== Time-Travel Queries ====================
    /**
     * Query events at a specific version/snapshot.
     *
     * <p>
     * Supports time-travel queries for historical analysis.</p>
     *
     * @param version snapshot version to query
     * @return Promise with events at that version
     */
    Promise<List<Event>> queryAtVersion(long version);

    /**
     * Query events as of a specific timestamp.
     *
     * <p>
     * Retrieves the state of archived events at a point in time.</p>
     *
     * @param timestamp point in time to query
     * @return Promise with events as of that timestamp
     */
    Promise<List<Event>> queryAtTimestamp(Instant timestamp);

    // ==================== Maintenance Operations ====================
    /**
     * Compact archived data to reduce file count and improve query performance.
     *
     * @return Promise with compaction result
     */
    Promise<CompactionResult> compact();

    /**
     * Vacuum old files that are no longer needed.
     *
     * <p>
     * Removes files beyond the retention period.</p>
     *
     * @param retentionPeriod minimum retention before cleanup
     * @return Promise with vacuum result
     */
    Promise<VacuumResult> vacuum(Duration retentionPeriod);

    /**
     * Get archive statistics.
     *
     * @return Promise with statistics about archived data
     */
    Promise<ArchiveStatistics> getStatistics();

    // ==================== Result Classes ====================
    /**
     * Result of an archive operation.
     */
    record ArchiveResult(
            boolean success,
            long eventsArchived,
            long bytesWritten,
            long durationMillis,
            Optional<String> archiveLocation,
            Optional<String> errorMessage
            ) {

        public static ArchiveResult success(long eventsArchived, long bytesWritten,
                long durationMillis, String archiveLocation) {
            return new ArchiveResult(true, eventsArchived, bytesWritten,
                    durationMillis, Optional.ofNullable(archiveLocation), Optional.empty());
        }

        public static ArchiveResult failure(String errorMessage) {
            return new ArchiveResult(false, 0, 0, 0, Optional.empty(), Optional.of(errorMessage));
        }

        public static ArchiveResult empty() {
            return new ArchiveResult(true, 0, 0, 0, Optional.empty(), Optional.empty());
        }
    }

    /**
     * Result of a compaction operation.
     */
    record CompactionResult(
            boolean success,
            int filesCompacted,
            int filesRemoved,
            long bytesReclaimed,
            long durationMillis,
            Optional<String> errorMessage
            ) {

        public static CompactionResult success(int filesCompacted, int filesRemoved,
                long bytesReclaimed, long durationMillis) {
            return new CompactionResult(true, filesCompacted, filesRemoved,
                    bytesReclaimed, durationMillis, Optional.empty());
        }

        public static CompactionResult failure(String errorMessage) {
            return new CompactionResult(false, 0, 0, 0, 0, Optional.of(errorMessage));
        }
    }

    /**
     * Result of a vacuum operation.
     */
    record VacuumResult(
            boolean success,
            int filesDeleted,
            long bytesReclaimed,
            long durationMillis,
            Optional<String> errorMessage
            ) {

        public static VacuumResult success(int filesDeleted, long bytesReclaimed, long durationMillis) {
            return new VacuumResult(true, filesDeleted, bytesReclaimed, durationMillis, Optional.empty());
        }

        public static VacuumResult failure(String errorMessage) {
            return new VacuumResult(false, 0, 0, 0, Optional.of(errorMessage));
        }
    }

    /**
     * Archive statistics.
     */
    record ArchiveStatistics(
            long totalEventsArchived,
            long totalBytesStored,
            int totalFiles,
            int totalPartitions,
            long currentVersion,
            Instant oldestEvent,
            Instant newestEvent
            ) {

    }
}
