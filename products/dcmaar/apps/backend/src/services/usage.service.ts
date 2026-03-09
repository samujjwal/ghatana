import { query, pool } from "../db";

export interface UsageSession {
  id: string;
  device_id: string;
  session_type: "app" | "website";
  item_name: string;
  category: string | null;
  start_time: Date;
  end_time: Date | null;
  duration_seconds: number | null;
  created_at: Date;
}

export interface CreateUsageData {
  device_id: string;
  session_type: "app" | "website";
  item_name: string;
  category?: string;
  start_time: Date;
  end_time?: Date;
  duration_seconds?: number;
}

export interface UpdateUsageData {
  end_time?: Date;
  duration_seconds?: number;
}

/**
 * Create a new usage session.
 *
 * <p><b>Purpose</b><br>
 * Records when child starts using an app or website for screen time tracking.
 * Guardian agents send usage session start events every time a new app/website is accessed.
 *
 * <p><b>Session Types</b><br>
 * - app: Desktop or mobile application usage
 * - website: Browser website/domain usage
 *
 * <p><b>Process</b><br>
 * - Create usage_sessions record with start_time
 * - end_time and duration_seconds initially NULL
 * - Updated later via updateUsageSession() when session ends
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Agent detects child opened YouTube
 * const session = await createUsageSession({
 *   device_id: deviceId,
 *   session_type: "website",
 *   item_name: "youtube.com",
 *   category: "entertainment",
 *   start_time: new Date()
 * });
 * // Later: updateUsageSession(session.id, { end_time, duration_seconds })
 * }</pre>
 *
 * @param data Usage session data (device, type, item, category, start_time)
 * @return Created usage session with generated ID
 * @throws Error if database insert fails
 * @see updateUsageSession
 * @see getUsageSessionsByDevice
 * @doc.type function
 * @doc.purpose Record app or website usage session start
 * @doc.layer product
 * @doc.pattern Service
 */
export async function createUsageSession(
  data: CreateUsageData
): Promise<UsageSession> {
  const result = await query<UsageSession>(
    `INSERT INTO usage_sessions (device_id, session_type, item_name, category, start_time, end_time, duration_seconds)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING *`,
    [
      data.device_id,
      data.session_type,
      data.item_name,
      data.category || null,
      data.start_time,
      data.end_time || null,
      data.duration_seconds || null,
    ]
  );

  return result[0];
}

/**
 * Update an existing usage session (e.g., to set end_time).
 *
 * <p><b>Purpose</b><br>
 * Sets end_time and duration_seconds when usage session completes.
 * Called by Guardian agents when child closes app or navigates away from website.
 *
 * <p><b>Dynamic SQL</b><br>
 * Builds UPDATE statement based on provided fields.
 * If no fields provided, returns existing session unchanged.
 *
 * <p><b>Updatable Fields</b><br>
 * - end_time: When session ended
 * - duration_seconds: Calculated duration (end_time - start_time in seconds)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Agent detects child closed YouTube after 45 minutes
 * const endTime = new Date();
 * const duration = (endTime - startTime) / 1000; // seconds
 * await updateUsageSession(sessionId, {
 *   end_time: endTime,
 *   duration_seconds: duration
 * });
 * }</pre>
 *
 * @param id Usage session to update
 * @param data Fields to update (end_time, duration_seconds)
 * @return Updated session or null if not found
 * @throws Error if database query fails
 * @see createUsageSession
 * @see getUsageSessionById
 * @doc.type function
 * @doc.purpose Update usage session with end time and duration
 * @doc.layer product
 * @doc.pattern Service
 */
export async function updateUsageSession(
  id: string,
  data: UpdateUsageData
): Promise<UsageSession | null> {
  const updates: string[] = [];
  const values: unknown[] = [];
  let paramCount = 1;

  if (data.end_time !== undefined) {
    updates.push(`end_time = $${paramCount++}`);
    values.push(data.end_time);
  }

  if (data.duration_seconds !== undefined) {
    updates.push(`duration_seconds = $${paramCount++}`);
    values.push(data.duration_seconds);
  }

  if (updates.length === 0) {
    // No updates to perform
    return getUsageSessionById(id);
  }

  values.push(id);

  const result = await query<UsageSession>(
    `UPDATE usage_sessions
     SET ${updates.join(", ")}
     WHERE id = $${paramCount}
     RETURNING *`,
    values
  );

  return result[0] || null;
}

/**
 * Get a usage session by ID.
 *
 * <p><b>Purpose</b><br>
 * Fetches single usage session for detail view or validation.
 * Used internally by updateUsageSession() when no updates provided.
 *
 * @param id Usage session ID
 * @return Usage session or null if not found
 * @throws Error if database query fails
 * @see createUsageSession
 * @see updateUsageSession
 * @doc.type function
 * @doc.purpose Fetch single usage session by ID
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getUsageSessionById(
  id: string
): Promise<UsageSession | null> {
  const result = await query<UsageSession>(
    "SELECT * FROM usage_sessions WHERE id = $1",
    [id]
  );

  return result[0] || null;
}

/**
 * Get usage sessions for a device.
 *
 * <p><b>Purpose</b><br>
 * Lists recent usage sessions for specific device.
 * Used for device-specific activity log and screen time breakdown.
 *
 * <p><b>Sorting</b><br>
 * Results ordered by start_time DESC (most recent first).
 *
 * @param deviceId Device to fetch sessions for
 * @param limit Maximum sessions to return (default: 100)
 * @return Array of usage sessions sorted by start_time DESC
 * @throws Error if database query fails
 * @see createUsageSession
 * @see getUsageSessionsByChild
 * @doc.type function
 * @doc.purpose List usage sessions for specific device
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getUsageSessionsByDevice(
  deviceId: string,
  limit: number = 100
): Promise<UsageSession[]> {
  const result = await query<UsageSession>(
    `SELECT * FROM usage_sessions
     WHERE device_id = $1
     ORDER BY start_time DESC
     LIMIT $2`,
    [deviceId, limit]
  );

  return result;
}

/**
 * Get usage sessions for a child (across all their devices).
 *
 * <p><b>Purpose</b><br>
 * Lists recent usage sessions for child across all devices they use.
 * Aggregates sessions from desktop, mobile, and browser.
 *
 * <p><b>Process</b><br>
 * - JOIN usage_sessions to devices WHERE child_id matches
 * - Includes sessions from all devices paired with child
 * - Sort by start_time DESC (most recent first)
 *
 * @param childId Child to fetch sessions for
 * @param limit Maximum sessions to return (default: 100)
 * @return Array of usage sessions sorted by start_time DESC
 * @throws Error if database query fails
 * @see createUsageSession
 * @see getUsageSessionsByDevice
 * @see getUsageSummaryByChild
 * @doc.type function
 * @doc.purpose List usage sessions for child across all devices
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getUsageSessionsByChild(
  childId: string,
  limit: number = 100
): Promise<UsageSession[]> {
  const result = await query<UsageSession>(
    `SELECT us.*
     FROM usage_sessions us
     JOIN devices d ON us.device_id = d.id
     WHERE d.child_id = $1
     ORDER BY us.start_time DESC
     LIMIT $2`,
    [childId, limit]
  );

  return result;
}

/**
 * Get usage summary for a child (total usage by app/website).
 *
 * <p><b>Purpose</b><br>
 * Calculates aggregated screen time grouped by app/website for usage reports.
 * Shows which apps/sites child spent most time on during time period.
 *
 * <p><b>Aggregation</b><br>
 * Groups by:
 * - session_type: app | website
 * - item_name: Specific app name or domain
 * - category: productivity, entertainment, social_media, etc.
 * Sums total_duration_seconds and counts sessions for each combination.
 *
 * <p><b>Sorting</b><br>
 * Results ordered by total_duration_seconds DESC (most time spent first).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get weekly usage summary
 * const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
 * const summary = await getUsageSummaryByChild(childId, weekAgo, new Date());
 * console.log(`Top app: ${summary[0].item_name}`);
 * console.log(`Time spent: ${(summary[0].total_duration_seconds / 3600).toFixed(1)} hours`);
 * }</pre>
 *
 * @param childId Child to calculate summary for
 * @param startDate Start of time period
 * @param endDate End of time period
 * @return Array of usage summaries sorted by duration DESC
 * @throws Error if database query fails
 * @see getUsageSessionsByChild
 * @see createUsageSession
 * @doc.type function
 * @doc.purpose Calculate aggregated screen time by app/website
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getUsageSummaryByChild(
  childId: string,
  startDate: Date,
  endDate: Date
): Promise<
  Array<{
    session_type: string;
    item_name: string;
    category: string | null;
    total_duration_seconds: number;
    session_count: number;
  }>
> {
  const result = await query<{
    session_type: string;
    item_name: string;
    category: string | null;
    total_duration_seconds: number;
    session_count: number;
  }>(
    `SELECT 
       us.session_type,
       us.item_name,
       us.category,
       COALESCE(SUM(us.duration_seconds), 0) as total_duration_seconds,
       COUNT(*) as session_count
     FROM usage_sessions us
     JOIN devices d ON us.device_id = d.id
     WHERE d.child_id = $1
       AND us.start_time >= $2
       AND us.start_time <= $3
     GROUP BY us.session_type, us.item_name, us.category
     ORDER BY total_duration_seconds DESC`,
    [childId, startDate, endDate]
  );

  return result;
}

/**
 * Delete usage sessions older than a certain date.
 *
 * <p><b>Purpose</b><br>
 * Removes old usage sessions to manage database size.
 * Called by scheduled cleanup job (e.g., delete sessions older than 90 days).
 *
 * <p><b>Retention Policy</b><br>
 * Typical retention: 90 days for reports, 365 days for compliance.
 * Adjust based on storage capacity and reporting requirements.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Cron job: delete sessions older than 90 days
 * const ninetyDaysAgo = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000);
 * const deleted = await deleteOldUsageSessions(ninetyDaysAgo);
 * console.log(`Deleted ${deleted} old usage sessions`);
 * }</pre>
 *
 * @param beforeDate Delete sessions older than this date
 * @return Count of deleted usage sessions
 * @throws Error if database query fails
 * @see createUsageSession
 * @see getUsageSessionsByChild
 * @doc.type function
 * @doc.purpose Delete old usage sessions for database cleanup
 * @doc.layer product
 * @doc.pattern Service
 */
export async function deleteOldUsageSessions(
  beforeDate: Date
): Promise<number> {
  const result = await pool.query(
    "DELETE FROM usage_sessions WHERE created_at < $1",
    [beforeDate]
  );

  return result.rowCount || 0;
}
