import { query, pool } from "../db";

export interface BlockEvent {
  id: string;
  device_id: string;
  policy_id: string | null;
  event_type: "website" | "app";
  blocked_item: string;
  category: string | null;
  reason: string | null;
  timestamp: Date;
}

export interface CreateBlockEventData {
  device_id: string;
  policy_id?: string;
  event_type: "website" | "app";
  blocked_item: string;
  category?: string;
  reason?: string;
  timestamp?: Date;
}

/**
 * Create a new block event.
 *
 * <p><b>Purpose</b><br>
 * Records when Guardian agent or browser extension blocks a website or app.
 * Used for compliance reporting, analytics, and identifying attempts to access restricted content.
 *
 * <p><b>Event Types</b><br>
 * - website: Browser extension blocked domain or URL
 * - app: Desktop/mobile agent blocked application
 *
 * <p><b>Process</b><br>
 * - Insert block event with device_id, policy_id (if policy-based), blocked_item
 * - Include category (social_media, gaming, adult_content, etc.)
 * - Include reason (policy match, schedule restriction, etc.)
 * - Timestamp defaults to NOW() if not provided
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Browser extension blocks website
 * const event = await createBlockEvent({
 *   device_id: deviceId,
 *   policy_id: policyId,
 *   event_type: "website",
 *   blocked_item: "facebook.com",
 *   category: "social_media",
 *   reason: "School hours policy - no social media 8am-3pm"
 * });
 * }</pre>
 *
 * @param data Block event data (device, type, blocked_item, category, reason)
 * @return Created block event with generated ID
 * @throws Error if database insert fails
 * @see getBlockEventsByDevice
 * @see getBlockEventsByChild
 * @doc.type function
 * @doc.purpose Record website or app block event
 * @doc.layer product
 * @doc.pattern Service
 */
export async function createBlockEvent(
  data: CreateBlockEventData
): Promise<BlockEvent> {
  const result = await query<BlockEvent>(
    `INSERT INTO block_events (device_id, policy_id, event_type, blocked_item, category, reason, timestamp)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING *`,
    [
      data.device_id,
      data.policy_id || null,
      data.event_type,
      data.blocked_item,
      data.category || null,
      data.reason || null,
      data.timestamp || new Date(),
    ]
  );

  return result[0];
}

/**
 * Get a block event by ID.
 *
 * <p><b>Purpose</b><br>
 * Fetches single block event for detail view or audit trail.
 * Used for investigating specific block incidents.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const event = await getBlockEventById(eventId);
 * if (event) {
 *   console.log(`Blocked: ${event.blocked_item} (${event.event_type})`);
 *   console.log(`Reason: ${event.reason}`);
 * }
 * }</pre>
 *
 * @param id Block event ID
 * @return Block event or null if not found
 * @throws Error if database query fails
 * @see createBlockEvent
 * @see getBlockEventsByDevice
 * @doc.type function
 * @doc.purpose Fetch single block event by ID
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getBlockEventById(
  id: string
): Promise<BlockEvent | null> {
  const result = await query<BlockEvent>(
    "SELECT * FROM block_events WHERE id = $1",
    [id]
  );

  return result[0] || null;
}

/**
 * Get block events for a device.
 *
 * <p><b>Purpose</b><br>
 * Lists recent block events for specific device.
 * Used for device-specific activity log and debugging policy enforcement.
 *
 * <p><b>Sorting</b><br>
 * Results ordered by timestamp DESC (most recent first).
 *
 * <p><b>Limit</b><br>
 * Default: 100 events. Adjust limit for pagination or detailed analysis.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get last 50 block events for device
 * const events = await getBlockEventsByDevice(deviceId, 50);
 * console.log(`${events.length} recent blocks`);
 * events.forEach(e => console.log(`${e.timestamp}: ${e.blocked_item}`));
 * }</pre>
 *
 * @param deviceId Device to fetch events for
 * @param limit Maximum events to return (default: 100)
 * @return Array of block events sorted by timestamp DESC
 * @throws Error if database query fails
 * @see createBlockEvent
 * @see getBlockEventsByChild
 * @doc.type function
 * @doc.purpose List block events for specific device
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getBlockEventsByDevice(
  deviceId: string,
  limit: number = 100
): Promise<BlockEvent[]> {
  const result = await query<BlockEvent>(
    `SELECT * FROM block_events
     WHERE device_id = $1
     ORDER BY timestamp DESC
     LIMIT $2`,
    [deviceId, limit]
  );

  return result;
}

/**
 * Get block events for a child (across all their devices).
 *
 * <p><b>Purpose</b><br>
 * Lists recent block events for child across all devices they use.
 * Aggregates blocks from desktop, mobile, and browser extension.
 *
 * <p><b>Process</b><br>
 * - JOIN block_events to devices WHERE child_id matches
 * - Includes events from all devices paired with child
 * - Sort by timestamp DESC (most recent first)
 *
 * <p><b>Limit</b><br>
 * Default: 100 events. Adjust limit for pagination.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get last 100 blocks for child across all devices
 * const events = await getBlockEventsByChild(childId, 100);
 * const websiteBlocks = events.filter(e => e.event_type === 'website');
 * const appBlocks = events.filter(e => e.event_type === 'app');
 * console.log(`${websiteBlocks.length} websites, ${appBlocks.length} apps blocked`);
 * }</pre>
 *
 * @param childId Child to fetch events for
 * @param limit Maximum events to return (default: 100)
 * @return Array of block events sorted by timestamp DESC
 * @throws Error if database query fails
 * @see createBlockEvent
 * @see getBlockEventsByDevice
 * @see getBlockEventStats
 * @doc.type function
 * @doc.purpose List block events for child across all devices
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getBlockEventsByChild(
  childId: string,
  limit: number = 100
): Promise<BlockEvent[]> {
  const result = await query<BlockEvent>(
    `SELECT be.*
     FROM block_events be
     JOIN devices d ON be.device_id = d.id
     WHERE d.child_id = $1
     ORDER BY be.timestamp DESC
     LIMIT $2`,
    [childId, limit]
  );

  return result;
}

/**
 * Get block event statistics for a child.
 *
 * <p><b>Purpose</b><br>
 * Calculates aggregated block counts grouped by event_type, blocked_item, and category.
 * Used for block reports and identifying most frequently blocked content.
 *
 * <p><b>Aggregation</b><br>
 * Groups by:
 * - event_type: website | app
 * - blocked_item: Specific URL, domain, or app name
 * - category: social_media, gaming, adult_content, etc.
 * Counts total blocks for each combination.
 *
 * <p><b>Sorting</b><br>
 * Results ordered by block_count DESC (most blocked first).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get monthly block stats
 * const monthAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
 * const stats = await getBlockEventStats(childId, monthAgo, new Date());
 * console.log(`Top blocked: ${stats[0].blocked_item} (${stats[0].block_count} times)`);
 * console.log(`Category: ${stats[0].category}`);
 * }</pre>
 *
 * @param childId Child to calculate stats for
 * @param startDate Start of time period
 * @param endDate End of time period
 * @return Array of block statistics sorted by count DESC
 * @throws Error if database query fails
 * @see getBlockEventsByChild
 * @see createBlockEvent
 * @doc.type function
 * @doc.purpose Calculate aggregated block counts by item and category
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getBlockEventStats(
  childId: string,
  startDate: Date,
  endDate: Date
): Promise<
  Array<{
    event_type: string;
    blocked_item: string;
    category: string | null;
    block_count: number;
  }>
> {
  const result = await query<{
    event_type: string;
    blocked_item: string;
    category: string | null;
    block_count: string; // PostgreSQL COUNT returns bigint as string
  }>(
    `SELECT 
       be.event_type,
       be.blocked_item,
       be.category,
       COUNT(*) as block_count
     FROM block_events be
     JOIN devices d ON be.device_id = d.id
     WHERE d.child_id = $1
       AND be.timestamp >= $2
       AND be.timestamp <= $3
     GROUP BY be.event_type, be.blocked_item, be.category
     ORDER BY block_count DESC`,
    [childId, startDate, endDate]
  );

  // Convert block_count from string to number
  return result.map((stat) => ({
    ...stat,
    block_count: parseInt(stat.block_count, 10),
  }));
}

/**
 * Delete old block events.
 *
 * <p><b>Purpose</b><br>
 * Removes block events older than specified date to manage database size.
 * Called by scheduled cleanup job (e.g., delete events older than 90 days).
 *
 * <p><b>Process</b><br>
 * - DELETE FROM block_events WHERE timestamp < beforeDate
 * - Returns count of deleted rows
 *
 * <p><b>Retention Policy</b><br>
 * Typical retention: 90 days for compliance, 30 days for analytics.
 * Adjust based on storage capacity and reporting needs.
 *
 * <p><b>Performance</b><br>
 * May be slow for large deletions. Consider:
 * - Batch deletes (DELETE ... LIMIT N)
 * - Partition tables by timestamp
 * - Archive to cold storage before deletion
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Cron job: delete events older than 90 days
 * const ninetyDaysAgo = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000);
 * const deleted = await deleteOldBlockEvents(ninetyDaysAgo);
 * console.log(`Deleted ${deleted} old block events`);
 * }</pre>
 *
 * @param beforeDate Delete events older than this date
 * @return Count of deleted block events
 * @throws Error if database query fails
 * @see createBlockEvent
 * @see getBlockEventsByChild
 * @doc.type function
 * @doc.purpose Delete old block events for database cleanup
 * @doc.layer product
 * @doc.pattern Service
 */
export async function deleteOldBlockEvents(beforeDate: Date): Promise<number> {
  const result = await pool.query(
    "DELETE FROM block_events WHERE timestamp < $1",
    [beforeDate]
  );

  return result.rowCount || 0;
}
