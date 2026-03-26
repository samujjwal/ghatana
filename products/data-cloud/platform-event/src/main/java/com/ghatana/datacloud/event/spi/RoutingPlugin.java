package com.ghatana.datacloud.event.spi;

import com.ghatana.datacloud.event.common.PartitionId;
import com.ghatana.datacloud.event.model.Event;
import com.ghatana.datacloud.event.model.EventStream;
import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * SPI for pluggable partition routing strategies.
 *
 * <p><b>Purpose</b><br>
 * Determines which partition an event is assigned to. Implementations must ensure:
 * <ul>
 *   <li><b>Consistency</b>: Same key always routes to same partition</li>
 *   <li><b>Distribution</b>: Events spread evenly across partitions</li>
 *   <li><b>Affinity</b>: Related events (same key) colocate in partition</li>
 * </ul>
 *
 * <p><b>Implementations</b><br>
 * <ul>
 *   <li><b>HashRoutingPlugin</b>: hash(key) % partitionCount</li>
 *   <li><b>ConsistentHashRoutingPlugin</b>: Consistent hashing for stability</li>
 *   <li><b>KeyBasedRoutingPlugin</b>: Explicit partition in event</li>
 *   <li><b>RoundRobinRoutingPlugin</b>: Sequential assignment</li>
 *   <li><b>StickyRoutingPlugin</b>: Same source → same partition</li>
 * </ul>
 *
 * <p><b>Partition Key Extraction</b><br>
 * The partition key is extracted from the event using the stream's
 * partitionKeyExpression:
 * <pre>
 * "payload.customerId"  → Extract customerId from payload
 * "headers.region"      → Extract region from headers
 * "correlationId"       → Use correlationId as key
 * </pre>
 *
 * <p><b>Routing Strategies</b><br>
 * <pre>
 * Hash          → Simple, fast, may cause hot spots
 * Consistent    → Stable during partition changes
 * Key-based     → Producer controls partition
 * Round-robin   → Best distribution, no ordering guarantee
 * </pre>
 *
 * @see com.ghatana.platform.plugin.Plugin
 * @see EventStream
 * @doc.type interface
 * @doc.purpose SPI for partition routing strategies
 * @doc.layer spi
 * @doc.pattern Plugin, Strategy
 */
public interface RoutingPlugin extends Plugin {

    // ==================== Routing Operations ====================

    /**
     * Route event to a partition.
     *
     * @param event event to route
     * @param stream target stream (for partition count and key expression)
     * @return Promise with assigned partition ID
     */
    Promise<PartitionId> route(Event event, EventStream stream);

    /**
     * Route event by explicit key.
     *
     * @param partitionKey explicit partition key
     * @param partitionCount total number of partitions
     * @return Promise with assigned partition ID
     */
    Promise<PartitionId> routeByKey(String partitionKey, int partitionCount);

    /**
     * Route batch of events.
     *
     * <p>More efficient than routing one by one.</p>
     *
     * @param events events to route
     * @param stream target stream
     * @return Promise with map of event ID to partition ID
     */
    Promise<Map<String, PartitionId>> routeBatch(
        java.util.List<Event> events,
        EventStream stream
    );

    // ==================== Key Extraction ====================

    /**
     * Extract partition key from event using expression.
     *
     * <p>Expression examples:
     * <ul>
     *   <li>"payload.customerId" → event.data.get("customerId")</li>
     *   <li>"headers.region" → event.headers.get("region")</li>
     *   <li>"correlationId" → event.correlationId</li>
     *   <li>"eventId" → event.id</li>
     * </ul></p>
     *
     * @param event event to extract key from
     * @param keyExpression expression for key extraction
     * @return extracted key (or null if not found)
     */
    String extractPartitionKey(Event event, String keyExpression);

    // ==================== Capabilities ====================

    /**
     * Get routing plugin capabilities.
     *
     * @return capabilities descriptor
     */
    Capabilities capabilities();

    /**
     * Routing plugin capabilities.
     */
    interface Capabilities {
        /**
         * Strategy name for identification.
         */
        String strategyName();

        /**
         * Supports sticky routing (producer affinity).
         */
        boolean supportsStickyRouting();

        /**
         * Supports weighted partitions.
         */
        boolean supportsWeightedPartitions();

        /**
         * Maintains ordering for same key.
         */
        boolean maintainsKeyOrdering();

        /**
         * Stable during partition count changes.
         */
        boolean stableDuringRebalance();
    }

    // ==================== Routing Strategy Enum ====================

    /**
     * Built-in routing strategies.
     */
    enum Strategy {
        /**
         * hash(key) % partitionCount
         */
        HASH,

        /**
         * Consistent hashing ring
         */
        CONSISTENT_HASH,

        /**
         * Explicit partition in event
         */
        KEY_BASED,

        /**
         * Sequential assignment
         */
        ROUND_ROBIN,

        /**
         * Same source → same partition
         */
        STICKY,

        /**
         * Custom strategy
         */
        CUSTOM
    }
}
