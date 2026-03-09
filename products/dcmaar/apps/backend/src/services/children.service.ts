import { query } from "../db";

export interface Child {
  id: string;
  user_id: string;
  name: string;
  birth_date: string;
  avatar_url: string | null;
  is_active: boolean;
  created_at: Date;
  updated_at: Date;
}

export interface CreateChildData {
  name: string;
  birth_date: string;
  avatar_url?: string;
}

export interface UpdateChildData {
  name?: string;
  birth_date?: string;
  avatar_url?: string;
  is_active?: boolean;
}

/**
 * Create a new child profile.
 *
 * <p><b>Purpose</b><br>
 * Creates a child profile for parental monitoring and control.
 * Each child can have multiple devices and policies assigned.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const child = await createChild(userId, {
 *   name: "Emma",
 *   birth_date: "2015-06-15",
 *   avatar_url: "https://cdn.example.com/avatars/emma.jpg"
 * });
 * }</pre>
 *
 * @param userId User creating the child profile
 * @param data Child profile data (name, birth_date, avatar_url)
 * @return Created child with generated ID
 * @throws Error if database insert fails
 * @see getChildren
 * @see updateChild
 * @doc.type function
 * @doc.purpose Create child profile for monitoring
 * @doc.layer product
 * @doc.pattern Service
 */
export async function createChild(
  userId: string,
  data: CreateChildData
): Promise<Child> {
  const result = await query<Child>(
    `INSERT INTO children (user_id, name, birth_date, avatar_url)
     VALUES ($1, $2, $3, $4)
     RETURNING *`,
    [userId, data.name, data.birth_date, data.avatar_url || null]
  );

  return result[0];
}

/**
 * Get all children for a user.
 *
 * <p><b>Purpose</b><br>
 * Lists child profiles with optional filtering by active status.
 * Used for dashboard child selector and profile management.
 *
 * <p><b>Filtering</b><br>
 * - activeOnly=true: Only active children (is_active = true)
 * - activeOnly=false/undefined: All children including soft-deleted
 *
 * <p><b>Sorting</b><br>
 * Results ordered by created_at ASC (oldest first, stable ordering).
 *
 * @param userId User whose children to fetch
 * @param activeOnly Filter to active children only (default: false)
 * @return Array of child profiles sorted by creation date
 * @throws Error if database query fails
 * @see createChild
 * @see getChildById
 * @doc.type function
 * @doc.purpose List child profiles with optional active filter
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getChildren(
  userId: string,
  activeOnly?: boolean
): Promise<Child[]> {
  let query_text = "SELECT * FROM children WHERE user_id = $1";
  const params: unknown[] = [userId];

  if (activeOnly) {
    query_text += " AND is_active = true";
  }

  query_text += " ORDER BY created_at ASC";

  return await query<Child>(query_text, params);
}

/**
 * Get a single child by ID.
 *
 * <p><b>Purpose</b><br>
 * Fetches specific child with ownership verification.
 * Used for child detail view and profile editing.
 *
 * <p><b>Security</b><br>
 * Filters by both child ID AND user ID to prevent unauthorized access.
 *
 * @param userId User who owns the child
 * @param childId Child to fetch
 * @return Child profile or null if not found
 * @throws Error if database query fails
 * @see getChildren
 * @see updateChild
 * @doc.type function
 * @doc.purpose Fetch single child profile with ownership check
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getChildById(
  userId: string,
  childId: string
): Promise<Child | null> {
  const result = await query<Child>(
    "SELECT * FROM children WHERE id = $1 AND user_id = $2",
    [childId, userId]
  );

  return result.length > 0 ? result[0] : null;
}

/**
 * Update a child profile.
 *
 * <p><b>Purpose</b><br>
 * Updates child profile metadata (name, birth_date, avatar, active status).
 *
 * <p><b>Dynamic SQL</b><br>
 * Builds UPDATE statement based on provided fields.
 * Always updates updated_at to NOW() for change tracking.
 *
 * <p><b>Updatable Fields</b><br>
 * - name: Display name
 * - birth_date: Birth date (YYYY-MM-DD format)
 * - avatar_url: Profile picture URL
 * - is_active: Active/inactive status (soft delete flag)
 *
 * @param userId User who owns the child
 * @param childId Child to update
 * @param updates Fields to update
 * @return Updated child or null if not found
 * @throws Error if no fields provided or database query fails
 * @see createChild
 * @see getChildById
 * @doc.type function
 * @doc.purpose Update child profile metadata
 * @doc.layer product
 * @doc.pattern Service
 */
export async function updateChild(
  userId: string,
  childId: string,
  updates: UpdateChildData
): Promise<Child | null> {
  const fields: string[] = [];
  const values: unknown[] = [];
  let paramIndex = 1;

  if (updates.name !== undefined) {
    fields.push(`name = $${paramIndex++}`);
    values.push(updates.name);
  }

  if (updates.birth_date !== undefined) {
    fields.push(`birth_date = $${paramIndex++}`);
    values.push(updates.birth_date);
  }

  if (updates.avatar_url !== undefined) {
    fields.push(`avatar_url = $${paramIndex++}`);
    values.push(updates.avatar_url);
  }

  if (updates.is_active !== undefined) {
    fields.push(`is_active = $${paramIndex++}`);
    values.push(updates.is_active);
  }

  if (fields.length === 0) {
    throw new Error("No fields to update");
  }

  values.push(childId, userId);

  const result = await query<Child>(
    `UPDATE children SET ${fields.join(", ")}, updated_at = NOW()
     WHERE id = $${paramIndex++} AND user_id = $${paramIndex++}
     RETURNING *`,
    values
  );

  return result.length > 0 ? result[0] : null;
}

/**
 * Delete a child profile (soft delete).
 *
 * <p><b>Purpose</b><br>
 * Soft-deletes child by setting is_active = false.
 * Preserves historical data and maintains referential integrity.
 *
 * <p><b>Soft Delete Benefits</b><br>
 * - Preserves usage history, blocks, policies
 * - Allows recovery if deletion was accidental
 * - Maintains foreign key relationships
 *
 * @param userId User who owns the child
 * @param childId Child to delete
 * @return true if deleted, false if not found
 * @throws Error if database query fails
 * @see updateChild
 * @see getChildren
 * @doc.type function
 * @doc.purpose Soft-delete child profile (preserve data)
 * @doc.layer product
 * @doc.pattern Service
 */
export async function deleteChild(
  userId: string,
  childId: string
): Promise<boolean> {
  const result = await query(
    "UPDATE children SET is_active = false, updated_at = NOW() WHERE id = $1 AND user_id = $2 RETURNING id",
    [childId, userId]
  );

  return result.length > 0;
}

/**
 * Get child statistics.
 *
 * <p><b>Purpose</b><br>
 * Calculates dashboard metrics for single child.
 * Shows devices, policies, today's blocks, and today's screen time.
 *
 * <p><b>Metrics Calculated</b><br>
 * - total_devices: Active devices paired with child
 * - active_policies: Enabled policies for child
 * - total_blocks_today: Block events since midnight
 * - screen_time_today: Usage duration since midnight (in seconds)
 *
 * <p><b>Performance</b><br>
 * Uses Promise.all() to run 4 queries concurrently.
 *
 * @param userId User who owns the child
 * @param childId Child to calculate stats for
 * @return Child statistics
 * @throws Error if database query fails
 * @see getChildrenBatchStats
 * @see getChildById
 * @doc.type function
 * @doc.purpose Calculate child dashboard metrics
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getChildStats(
  userId: string,
  childId: string
): Promise<{
  total_devices: number;
  active_policies: number;
  total_blocks_today: number;
  screen_time_today: number;
}> {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const [devicesResult, policiesResult, blocksResult, screenTimeResult] =
    await Promise.all([
      query<{ count: string }>(
        "SELECT COUNT(*) as count FROM devices WHERE child_id = $1 AND is_active = true",
        [childId]
      ),
      query<{ count: string }>(
        "SELECT COUNT(*) as count FROM policies WHERE child_id = $1 AND enabled = true",
        [childId]
      ),
      query<{ count: string }>(
        `SELECT COUNT(*) as count FROM block_events be
       JOIN devices d ON be.device_id = d.id
       WHERE d.child_id = $1 AND be.timestamp >= $2`,
        [childId, today]
      ),
      query<{ total: string }>(
        `SELECT COALESCE(SUM(us.duration_seconds), 0) as total 
       FROM usage_sessions us
       JOIN devices d ON us.device_id = d.id
       WHERE d.child_id = $1 AND us.start_time >= $2`,
        [childId, today]
      ),
    ]);

  return {
    total_devices: parseInt(devicesResult[0].count),
    active_policies: parseInt(policiesResult[0].count),
    total_blocks_today: parseInt(blocksResult[0].count),
    screen_time_today: parseInt(screenTimeResult[0].total),
  };
}

/**
 * Calculate age from birth date.
 *
 * <p><b>Purpose</b><br>
 * Calculates current age in years from birth date string.
 * Handles leap years and month/day edge cases correctly.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const age = calculateAge("2015-06-15");
 * console.log(`Child is ${age} years old`);
 * }</pre>
 *
 * @param birthDate Birth date in YYYY-MM-DD format
 * @return Age in years
 * @see createChild
 * @see updateChild
 * @doc.type function
 * @doc.purpose Calculate age from birth date
 * @doc.layer product
 * @doc.pattern Service
 */
export function calculateAge(birthDate: string): number {
  const birth = new Date(birthDate);
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const monthDiff = today.getMonth() - birth.getMonth();

  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
    age--;
  }

  return age;
}

/**
 * Get stats for multiple children in one query (batch operation).
 *
 * <p><b>Purpose</b><br>
 * Calculates stats for multiple children efficiently using batch queries.
 * Used for dashboard showing all children with metrics.
 *
 * <p><b>Performance Optimization</b><br>
 * - Single query per metric using ANY($1) instead of N queries
 * - Promise.all() for concurrent execution
 * - GROUP BY for aggregation at database level
 * - Much faster than calling getChildStats() N times
 *
 * <p><b>Process</b><br>
 * - Initialize all children with zero stats
 * - Run 4 batch queries with ANY(childIds)
 * - Fill in actual counts from GROUP BY results
 *
 * @param userId User who owns the children
 * @param childIds Array of child IDs to fetch stats for
 * @return Map of childId to stats
 * @throws Error if database query fails
 * @see getChildStats
 * @see getChildren
 * @doc.type function
 * @doc.purpose Batch calculate stats for multiple children
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getChildrenBatchStats(
  userId: string,
  childIds: string[]
): Promise<
  Record<
    string,
    {
      total_devices: number;
      active_policies: number;
      total_blocks_today: number;
      screen_time_today: number;
    }
  >
> {
  if (childIds.length === 0) {
    return {};
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  // Batch query for all children at once
  const [devicesResult, policiesResult, blocksResult, screenTimeResult] =
    await Promise.all([
      query<{ child_id: string; count: string }>(
        `SELECT child_id, COUNT(*) as count 
       FROM devices 
       WHERE child_id = ANY($1) AND is_active = true 
       GROUP BY child_id`,
        [childIds]
      ),
      query<{ child_id: string; count: string }>(
        `SELECT child_id, COUNT(*) as count 
       FROM policies 
       WHERE child_id = ANY($1) AND enabled = true 
       GROUP BY child_id`,
        [childIds]
      ),
      query<{ child_id: string; count: string }>(
        `SELECT child_id, COUNT(*) as count 
       FROM block_events 
       WHERE child_id = ANY($1) AND timestamp >= $2 
       GROUP BY child_id`,
        [childIds, today]
      ),
      query<{ child_id: string; total: string }>(
        `SELECT child_id, COALESCE(SUM(duration_seconds), 0) as total 
       FROM usage_sessions 
       WHERE child_id = ANY($1) AND start_time >= $2 
       GROUP BY child_id`,
        [childIds, today]
      ),
    ]);

  // Build result map
  const stats: Record<string, any> = {};

  // Initialize all children with zero stats
  childIds.forEach((id) => {
    stats[id] = {
      total_devices: 0,
      active_policies: 0,
      total_blocks_today: 0,
      screen_time_today: 0,
    };
  });

  // Fill in actual counts
  devicesResult.forEach((row) => {
    stats[row.child_id].total_devices = parseInt(row.count);
  });

  policiesResult.forEach((row) => {
    stats[row.child_id].active_policies = parseInt(row.count);
  });

  blocksResult.forEach((row) => {
    stats[row.child_id].total_blocks_today = parseInt(row.count);
  });

  screenTimeResult.forEach((row) => {
    stats[row.child_id].screen_time_today = parseInt(row.total);
  });

  return stats;
}
