package com.ghatana.core.event.cloud;

import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.types.identity.PartitionId;

import java.time.Instant;
import java.util.Objects;

/**
 * Result of successfully appending an event to EventCloud, containing assigned partition/offset/timestamp.
 *
 * <p><b>Purpose</b><br>
 * Immutable value object returned by EventCloud.append() operations. Contains partition assignment, offset
 * within partition, and server-side append timestamp. Used for idempotency caching, offset tracking, and
 * acknowledgment to producers. Enables exactly-once processing when cached by idempotencyKey.
 *
 * <p><b>Architecture Role</b><br>
 * Result value object for EventCloud append operations. Used by IngestionService to acknowledge event receipt,
 * by consumers for offset tracking, and by idempotency cache for duplicate detection. Foundation for at-least-once
 * delivery with exactly-once processing semantics.
 * @doc.type record
 * @doc.purpose Immutable result of EventCloud append operations with partition, offset, and timestamp
 * @doc.layer core
 * @doc.pattern Value Object, Result Object
 *
 *
 * <p><b>Usage Examples</b><br>
 *
 * <pre>{@code
 * // Example 1: Basic append acknowledgment
 * EventCloud cloud = getEventCloud();
 * EventRecord event = buildEvent();
 * 
 * cloud.append(new AppendRequest(event, AppendOptions.defaults()))
 *     .whenComplete((result, ex) -> {
 *         if (ex == null) {
 *             log.info("Event appended to partition {} at offset {} (timestamp: {})",
 *                 result.partitionId(),
 *                 result.offset(),
 *                 result.appendTime());
 *         }
 *     });
 * // Result: partition=3, offset=12345, appendTime=2025-01-15T10:30:00Z
 * }</pre>
 *
 * <pre>{@code
 * // Example 2: Idempotency check (duplicate detection)
 * EventCloud cloud = getEventCloud();
 * IdempotencyKey key = IdempotencyKey.of("order-12345");
 * 
 * EventRecord event = EventRecord.builder()
 *     .idempotencyKey(Optional.of(key))
 *     .build();
 * 
 * // First append
 * AppendResult result1 = cloud.append(
 *     new AppendRequest(event, AppendOptions.defaults())).getResult();
 * log.info("First append: {} @ {}", result1.partitionId(), result1.offset());
 * 
 * // Duplicate append (same idempotencyKey)
 * AppendResult result2 = cloud.append(
 *     new AppendRequest(event, AppendOptions.defaults())).getResult();
 * 
 * // Same result (cached)
 * assert result1.equals(result2);
 * log.info("Duplicate detected - same partition/offset returned");
 * }</pre>
 *
 * <pre>{@code
 * // Example 3: Offset tracking for consumer checkpointing
 * EventCloud cloud = getEventCloud();
 * Map<PartitionId, Offset> checkpoints = new ConcurrentHashMap<>();
 * 
 * cloud.append(new AppendRequest(event, AppendOptions.defaults()))
 *     .whenComplete((result, ex) -> {
 *         if (ex == null) {
 *             // Track latest offset per partition
 *             checkpoints.put(result.partitionId(), result.offset());
 *             
 *             log.info("Checkpoint updated: partition {} @ offset {}",
 *                 result.partitionId(), result.offset());
 *         }
 *     });
 * 
 * // Resume from checkpoint on restart
 * Offset resumeFrom = checkpoints.get(PartitionId.of(3));
 * }</pre>
 *
 * <pre>{@code
 * // Example 4: Batch append result processing
 * EventCloud cloud = getEventCloud();
 * List<AppendRequest> requests = buildBatchRequests();
 * 
 * cloud.appendBatch(requests).whenComplete((results, ex) -> {
 *     if (ex == null) {
 *         // Process each result
 *         results.forEach(result -> {
 *             log.info("Batch item appended: partition={} offset={} time={}",
 *                 result.partitionId(),
 *                 result.offset(),
 *                 result.appendTime());
 *         });
 *         
 *         // Aggregate statistics
 *         Map<PartitionId, Long> countsPerPartition = results.stream()
 *             .collect(Collectors.groupingBy(
 *                 AppendResult::partitionId,
 *                 Collectors.counting()));
 *         
 *         log.info("Batch distributed across {} partitions", countsPerPartition.size());
 *     }
 * });
 * }</pre>
 *
 * <pre>{@code
 * // Example 5: Timestamp-based ordering (event-time vs append-time)
 * EventRecord event = EventRecord.builder()
 *     .occurrenceTime(Instant.parse("2025-01-15T10:00:00Z"))  // Event time
 *     .detectionTime(Instant.parse("2025-01-15T10:05:00Z"))   // Detection time
 *     .build();
 * 
 * AppendResult result = cloud.append(
 *     new AppendRequest(event, AppendOptions.defaults())).getResult();
 * 
 * // Compare timestamps
 * log.info("Event occurred at: {}", event.occurrenceTime());    // 10:00:00
 * log.info("System detected at: {}", event.detectionTime());    // 10:05:00
 * log.info("Stored at: {}", result.appendTime());               // 10:05:01
 * 
 * // appendTime >= detectionTime >= occurrenceTime (typical)
 * }</pre>
 *
 * <p><b>Field Descriptions</b><br>
 * - **partitionId**: Partition where event was stored (assigned by EventCloud)
 * - **offset**: Sequential offset within partition (0-indexed, monotonic)
 * - **appendTime**: Server timestamp when event was durably stored (UTC)
 *
 * <p><b>Partition Assignment</b><br>
 * EventCloud assigns partition based on:
 * - Hash-based partitioning (e.g., tenantId + eventId hash)
 * - Round-robin (for even distribution)
 * - Custom partitioning strategy (implementation-specific)
 *
 * Result includes assigned partitionId for offset tracking.
 *
 * <p><b>Offset Semantics</b><br>
 * - **Sequential**: Within partition, offsets increment monotonically (0, 1, 2, ...)
 * - **Unique**: (partitionId, offset) tuple uniquely identifies event in log
 * - **Immutable**: Once assigned, never changes
 * - **Gapless**: No gaps in offset sequence (unless deletion/compaction)
 *
 * <p><b>Timestamp Semantics</b><br>
 * - **appendTime**: Server-side UTC timestamp when event durably stored
 * - **Ordering**: appendTime may differ from event.occurrenceTime (out-of-order events)
 * - **Accuracy**: Millisecond precision (database timestamp)
 * - **Use case**: Audit logs, retention policies, time-travel queries
 *
 * <p><b>Best Practices</b><br>
 * - Cache AppendResult by idempotencyKey for duplicate detection
 * - Track latest offset per partition for consumer checkpointing
 * - Use appendTime for audit logs, not event.occurrenceTime
 * - Log partition assignment for debugging distribution issues
 * - Store (partitionId, offset) for exactly-once processing acknowledgment
 *
 * <p><b>Performance Characteristics</b><br>
 * - **Construction**: O(1) field assignment
 * - **Memory**: ~100 bytes per instance
 * - **Equality**: O(1) record comparison
 * - **Immutability**: Thread-safe, zero-copy
 *
 * <p><b>Thread Safety</b><br>
 * ✅ **Thread-safe** - Immutable record (all fields final).
 * Safe to share across threads, cache in concurrent maps.
 *
 * <p><b>Integration Points</b><br>
 * - EventCloud: Returned by append/appendBatch operations
 * - Idempotency cache: Cached by (tenantId, idempotencyKey)
 * - Consumer checkpoints: Track latest offset per partition
 * - Metrics: Measure append latency, partition distribution
 *
 * @see EventCloud
 * @see EventRecord
 * @see PartitionId
 * @see Offset
 * @since 2.0.0
 * @doc.type record
 * @doc.purpose Result of appending event to EventCloud with partition/offset/timestamp
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record AppendResult(
    PartitionId partitionId,
    Offset offset,
    Instant appendTime
) {
    public AppendResult {
        Objects.requireNonNull(partitionId, "partitionId required");
        Objects.requireNonNull(offset, "offset required");
        Objects.requireNonNull(appendTime, "appendTime required");
    }
}
