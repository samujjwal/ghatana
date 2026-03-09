/**
 * Memory Router Module - Tiered memory management with automatic lifecycle routing.
 *
 * <p>This module provides intelligent memory tier management that automatically routes
 * data records to appropriate storage tiers based on access patterns, salience scores,
 * and configurable policies. It integrates with the attention module for salience-based
 * routing decisions.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.memory.MemoryTier} - Enumeration of storage tiers (HOT, WARM, COLD, ARCHIVE)</li>
 *   <li>{@link com.ghatana.datacloud.memory.TierPolicy} - Configuration for tier transitions and capacity</li>
 *   <li>{@link com.ghatana.datacloud.memory.MemoryTierRouter} - Routes records to appropriate tiers based on salience</li>
 *   <li>{@link com.ghatana.datacloud.memory.TieredMemoryStore} - Unified interface over tiered storage backends</li>
 *   <li>{@link com.ghatana.datacloud.memory.MemoryReconciler} - Background reconciliation and tier migration</li>
 * </ul>
 *
 * <h2>Tier Characteristics</h2>
 * <table>
 *   <tr><th>Tier</th><th>Latency</th><th>Capacity</th><th>TTL</th><th>Use Case</th></tr>
 *   <tr><td>HOT</td><td>&lt;1ms</td><td>Limited</td><td>Minutes</td><td>Active processing, real-time queries</td></tr>
 *   <tr><td>WARM</td><td>&lt;10ms</td><td>Medium</td><td>Hours</td><td>Recent data, frequent access</td></tr>
 *   <tr><td>COLD</td><td>&lt;100ms</td><td>Large</td><td>Days</td><td>Historical data, occasional access</td></tr>
 *   <tr><td>ARCHIVE</td><td>&lt;1s</td><td>Unlimited</td><td>Years</td><td>Long-term storage, compliance</td></tr>
 * </table>
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li>Attention Module - Uses salience scores for tier placement</li>
 *   <li>Storage Plugin SPI - Delegates to tier-specific storage backends</li>
 *   <li>Global Workspace - Prioritizes HOT tier items for cognitive broadcast</li>
 *   <li>Observability - Emits metrics for tier usage and migration rates</li>
 * </ul>
 *
 * @doc.type package
 * @doc.purpose Tiered memory management with automatic lifecycle routing
 * @doc.layer core
 * @doc.pattern Repository, Strategy
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see com.ghatana.datacloud.attention.SalienceScore
 * @see com.ghatana.datacloud.spi.StoragePlugin
 */
@org.jetbrains.annotations.ApiStatus.Experimental
package com.ghatana.datacloud.memory;
