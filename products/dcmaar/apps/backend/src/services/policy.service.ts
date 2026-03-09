import { query, transaction } from "../db";
import { PoolClient } from "pg";

export interface Policy {
  id: string;
  user_id: string;
  child_id: string | null;
  device_id: string | null;
  name: string;
  policy_type: "website" | "app" | "category" | "schedule";
  enabled: boolean;
  priority: number;
  config: Record<string, any>;
  created_at: Date;
  updated_at: Date;
}

export interface CreatePolicyData {
  child_id?: string;
  device_id?: string;
  name: string;
  policy_type: "website" | "app" | "category" | "schedule";
  enabled?: boolean;
  priority?: number;
  config: Record<string, any>;
}

export interface UpdatePolicyData {
  name?: string;
  enabled?: boolean;
  priority?: number;
  config?: Record<string, any>;
}

/**
 * Create a new blocking policy.
 *
 * <p><b>Purpose</b><br>
 * Creates a policy to block websites, apps, or categories based on schedule or rules.
 * Policies can be scoped to specific child, device, or global (all children/devices).
 *
 * <p><b>Scope Hierarchy</b><br>
 * - Device-specific: Applies only to one device (highest priority)
 * - Child-specific: Applies to all devices used by child (medium priority)
 * - Global: Applies to all children/devices (lowest priority)
 *
 * <p><b>Policy Types</b><br>
 * - website: Block specific domains or URL patterns
 * - app: Block specific applications by name or bundle ID
 * - category: Block entire categories (social media, gaming, etc.)
 * - schedule: Time-based restrictions (block during school hours, bedtime, etc.)
 *
 * <p><b>Priority</b><br>
 * Higher priority policies override lower priority ones.
 * Default priority: 0. Use positive integers for explicit ordering.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Block social media during school hours for specific child
 * const policy = await createPolicy(userId, {
 *   child_id: childId,
 *   name: "School Hours - No Social Media",
 *   policy_type: "category",
 *   enabled: true,
 *   priority: 10,
 *   config: {
 *     category: "social_media",
 *     schedule: { start: "08:00", end: "15:00", days: [1,2,3,4,5] }
 *   }
 * });
 * }</pre>
 *
 * @param userId User creating the policy
 * @param data Policy configuration (name, type, scope, config)
 * @return Created policy with generated ID
 * @throws Error if database insert fails
 * @see getPolicies
 * @see getPoliciesForDevice
 * @doc.type function
 * @doc.purpose Create blocking policy for websites, apps, or schedules
 * @doc.layer product
 * @doc.pattern Service
 */
export async function createPolicy(
  userId: string,
  data: CreatePolicyData
): Promise<Policy> {
  const result = await query<Policy>(
    `INSERT INTO policies (user_id, child_id, device_id, name, policy_type, enabled, priority, config)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
     RETURNING *`,
    [
      userId,
      data.child_id || null,
      data.device_id || null,
      data.name,
      data.policy_type,
      data.enabled !== undefined ? data.enabled : true,
      data.priority || 0,
      JSON.stringify(data.config),
    ]
  );

  return result[0];
}

/**
 * Get all policies for a user.
 *
 * <p><b>Purpose</b><br>
 * Lists policies with optional filtering by child, device, type, or enabled status.
 * Results sorted by priority DESC, then created_at DESC (newest first).
 *
 * <p><b>Filters</b><br>
 * - child_id: Policies for specific child (includes global policies)
 * - device_id: Policies for specific device
 * - policy_type: Filter by website | app | category | schedule
 * - enabled: Filter by enabled/disabled status
 *
 * <p><b>Sorting</b><br>
 * Results ordered by:
 * 1. priority DESC (highest priority first)
 * 2. created_at DESC (newest first among same priority)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get all enabled website blocking policies for child
 * const policies = await getPolicies(userId, {
 *   child_id: childId,
 *   policy_type: "website",
 *   enabled: true
 * });
 * }</pre>
 *
 * @param userId User whose policies to fetch
 * @param filters Optional filters (child, device, type, enabled)
 * @return Array of policies sorted by priority
 * @throws Error if database query fails
 * @see createPolicy
 * @see getPolicyById
 * @doc.type function
 * @doc.purpose List policies with filtering and priority sorting
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getPolicies(
  userId: string,
  filters?: {
    child_id?: string;
    device_id?: string;
    policy_type?: string;
    enabled?: boolean;
  }
): Promise<Policy[]> {
  let query_text = "SELECT * FROM policies WHERE user_id = $1";
  const params: unknown[] = [userId];
  let paramIndex = 2;

  if (filters?.child_id) {
    query_text += ` AND child_id = $${paramIndex++}`;
    params.push(filters.child_id);
  }

  if (filters?.device_id) {
    query_text += ` AND device_id = $${paramIndex++}`;
    params.push(filters.device_id);
  }

  if (filters?.policy_type) {
    query_text += ` AND policy_type = $${paramIndex++}`;
    params.push(filters.policy_type);
  }

  if (filters?.enabled !== undefined) {
    query_text += ` AND enabled = $${paramIndex++}`;
    params.push(filters.enabled);
  }

  query_text += " ORDER BY priority DESC, created_at DESC";

  return await query<Policy>(query_text, params);
}

/**
 * Get a single policy by ID.
 *
 * <p><b>Purpose</b><br>
 * Fetches specific policy with ownership verification.
 * Used for policy detail view and edit forms.
 *
 * <p><b>Security</b><br>
 * Filters by both policy ID AND user ID to prevent unauthorized access.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const policy = await getPolicyById(userId, policyId);
 * if (policy) {
 *   console.log(`Policy: ${policy.name} (${policy.policy_type})`);
 * }
 * }</pre>
 *
 * @param userId User who owns the policy
 * @param policyId Policy to fetch
 * @return Policy object or null if not found
 * @throws Error if database query fails
 * @see getPolicies
 * @see updatePolicy
 * @doc.type function
 * @doc.purpose Fetch single policy by ID with ownership check
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getPolicyById(
  userId: string,
  policyId: string
): Promise<Policy | null> {
  const result = await query<Policy>(
    "SELECT * FROM policies WHERE id = $1 AND user_id = $2",
    [policyId, userId]
  );

  return result.length > 0 ? result[0] : null;
}

/**
 * Update a policy.
 *
 * <p><b>Purpose</b><br>
 * Updates policy metadata (name, enabled, priority, config).
 * Cannot change policy_type, child_id, or device_id after creation.
 *
 * <p><b>Dynamic SQL</b><br>
 * Builds UPDATE statement dynamically based on provided fields.
 * Always updates updated_at to NOW() for change tracking.
 *
 * <p><b>Updatable Fields</b><br>
 * - name: Display name of policy
 * - enabled: Enable/disable policy without deleting
 * - priority: Change evaluation order
 * - config: Modify policy rules (URLs, apps, schedules, etc.)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Disable policy temporarily
 * const updated = await updatePolicy(userId, policyId, {
 *   enabled: false
 * });
 *
 * // Update blocking rules
 * await updatePolicy(userId, policyId, {
 *   config: {
 *     domains: ["facebook.com", "instagram.com", "tiktok.com"]
 *   }
 * });
 * }</pre>
 *
 * @param userId User who owns the policy
 * @param policyId Policy to update
 * @param updates Fields to update (name, enabled, priority, config)
 * @return Updated policy or null if not found
 * @throws Error if no fields provided or database query fails
 * @see createPolicy
 * @see getPolicyById
 * @doc.type function
 * @doc.purpose Update policy metadata and configuration
 * @doc.layer product
 * @doc.pattern Service
 */
export async function updatePolicy(
  userId: string,
  policyId: string,
  updates: UpdatePolicyData
): Promise<Policy | null> {
  const fields: string[] = [];
  const values: unknown[] = [];
  let paramIndex = 1;

  if (updates.name !== undefined) {
    fields.push(`name = $${paramIndex++}`);
    values.push(updates.name);
  }

  if (updates.enabled !== undefined) {
    fields.push(`enabled = $${paramIndex++}`);
    values.push(updates.enabled);
  }

  if (updates.priority !== undefined) {
    fields.push(`priority = $${paramIndex++}`);
    values.push(updates.priority);
  }

  if (updates.config !== undefined) {
    fields.push(`config = $${paramIndex++}`);
    values.push(JSON.stringify(updates.config));
  }

  if (fields.length === 0) {
    throw new Error("No fields to update");
  }

  values.push(policyId, userId);

  const result = await query<Policy>(
    `UPDATE policies SET ${fields.join(", ")}, updated_at = NOW()
     WHERE id = $${paramIndex++} AND user_id = $${paramIndex++}
     RETURNING *`,
    values
  );

  return result.length > 0 ? result[0] : null;
}

/**
 * Delete a policy.
 *
 * <p><b>Purpose</b><br>
 * Permanently removes policy from database.
 * Prefer disabling (enabled = false) for temporary deactivation.
 *
 * <p><b>Hard Delete</b><br>
 * This is a permanent deletion (not soft delete).
 * Policy cannot be recovered after deletion.
 *
 * <p><b>Security</b><br>
 * Filters by both policy ID AND user ID to prevent unauthorized deletion.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Delete policy (prefer disabling instead)
 * const deleted = await deletePolicy(userId, policyId);
 * if (deleted) {
 *   console.log("Policy permanently deleted");
 * }
 * }</pre>
 *
 * @param userId User who owns the policy
 * @param policyId Policy to delete
 * @return true if deleted, false if not found
 * @throws Error if database query fails
 * @see updatePolicy
 * @see togglePolicies
 * @doc.type function
 * @doc.purpose Permanently delete policy
 * @doc.layer product
 * @doc.pattern Service
 */
export async function deletePolicy(
  userId: string,
  policyId: string
): Promise<boolean> {
  const result = await query(
    "DELETE FROM policies WHERE id = $1 AND user_id = $2 RETURNING id",
    [policyId, userId]
  );

  return result.length > 0;
}

/**
 * Get policies for device sync (used by browser extension and agents).
 *
 * <p><b>Purpose</b><br>
 * Fetches all applicable policies for device to enforce locally.
 * Used by Guardian agents and browser extension to sync blocking rules.
 *
 * <p><b>Policy Resolution Hierarchy</b><br>
 * Policies are ordered by scope priority:
 * 1. Device-specific policies (device_id matches)
 * 2. Child-specific policies (child_id matches, device_id is NULL)
 * 3. Global user policies (both child_id and device_id are NULL)
 *
 * Within each scope, policies sorted by:
 * - priority DESC (higher priority first)
 * - created_at DESC (newer first)
 *
 * <p><b>Process</b><br>
 * - Lookup device to get child_id and user_id
 * - Query policies WHERE enabled = true AND (device matches OR child matches OR global)
 * - Order by scope priority, then policy priority
 * - Return all matching policies for client-side enforcement
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Guardian agent syncs policies on startup
 * const policies = await getPoliciesForDevice(deviceId);
 * for (const policy of policies) {
 *   applyPolicyRules(policy.policy_type, policy.config);
 * }
 * }</pre>
 *
 * @param deviceId Device requesting policy sync
 * @return Array of applicable policies ordered by priority
 * @throws Error if device not found or database query fails
 * @see createPolicy
 * @see getPolicies
 * @doc.type function
 * @doc.purpose Fetch policies for device to enforce locally
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getPoliciesForDevice(
  deviceId: string
): Promise<Policy[]> {
  // Get device to find child_id and user_id
  const deviceResult = await query<{ child_id: string; user_id: string }>(
    `SELECT c.user_id, d.child_id 
     FROM devices d
     JOIN children c ON d.child_id = c.id
     WHERE d.id = $1`,
    [deviceId]
  );

  if (deviceResult.length === 0) {
    throw new Error("Device not found");
  }

  const { child_id, user_id } = deviceResult[0];

  // Get policies applicable to this device
  // Priority: device-specific > child-specific > user-global
  const policies = await query<Policy>(
    `SELECT * FROM policies 
     WHERE user_id = $1 
     AND enabled = true
     AND (
       device_id = $2 
       OR (device_id IS NULL AND child_id = $3)
       OR (device_id IS NULL AND child_id IS NULL)
     )
     ORDER BY 
       CASE 
         WHEN device_id IS NOT NULL THEN 1
         WHEN child_id IS NOT NULL THEN 2
         ELSE 3
       END,
       priority DESC,
       created_at DESC`,
    [user_id, deviceId, child_id]
  );

  return policies;
}

/**
 * Bulk enable/disable policies.
 *
 * <p><b>Purpose</b><br>
 * Enables or disables multiple policies in a single transaction.
 * Useful for "pause all policies" or "resume all policies" scenarios.
 *
 * <p><b>Process</b><br>
 * - Update enabled = true/false for all policies in policyIds array
 * - Update updated_at to NOW() for change tracking
 * - Filter by user_id to prevent unauthorized modifications
 * - Return count of policies actually updated
 *
 * <p><b>Atomicity</b><br>
 * Uses single UPDATE with ANY() clause for atomic bulk update.
 * All policies updated or none (database transaction).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Pause all policies temporarily (e.g., during parent override)
 * const policyIds = ["id1", "id2", "id3"];
 * const count = await togglePolicies(userId, policyIds, false);
 * console.log(`Paused ${count} policies`);
 *
 * // Resume policies later
 * await togglePolicies(userId, policyIds, true);
 * }</pre>
 *
 * @param userId User who owns the policies
 * @param policyIds Array of policy IDs to toggle
 * @param enabled true to enable, false to disable
 * @return Count of policies actually updated
 * @throws Error if database query fails
 * @see updatePolicy
 * @see deletePolicy
 * @doc.type function
 * @doc.purpose Bulk enable/disable multiple policies
 * @doc.layer product
 * @doc.pattern Service
 */
export async function togglePolicies(
  userId: string,
  policyIds: string[],
  enabled: boolean
): Promise<number> {
  const result = await query(
    `UPDATE policies 
     SET enabled = $1, updated_at = NOW()
     WHERE user_id = $2 AND id = ANY($3)
     RETURNING id`,
    [enabled, userId, policyIds]
  );

  return result.length;
}

/**
 * Get policy statistics for a user.
 *
 * <p><b>Purpose</b><br>
 * Calculates policy counts for dashboard display and analytics.
 * Shows total, enabled, disabled, and breakdown by policy type.
 *
 * <p><b>Metrics Calculated</b><br>
 * - total: Total policy count
 * - enabled: Count of enabled policies
 * - disabled: Total - enabled (inactive policies)
 * - by_type: Count grouped by policy_type (website, app, category, schedule)
 *
 * <p><b>Performance</b><br>
 * Uses Promise.all() to run 3 queries concurrently:
 * - Total count
 * - Enabled count
 * - Count by type (GROUP BY)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const stats = await getPolicyStats(userId);
 * console.log(`${stats.enabled}/${stats.total} policies active`);
 * console.log(`Website blocks: ${stats.by_type.website || 0}`);
 * console.log(`App blocks: ${stats.by_type.app || 0}`);
 * console.log(`Schedule restrictions: ${stats.by_type.schedule || 0}`);
 * }</pre>
 *
 * @param userId User whose stats to calculate
 * @return Policy statistics with total, enabled, disabled, and type breakdown
 * @throws Error if database query fails
 * @see getPolicies
 * @see createPolicy
 * @doc.type function
 * @doc.purpose Calculate policy count statistics
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getPolicyStats(userId: string): Promise<{
  total: number;
  enabled: number;
  disabled: number;
  by_type: Record<string, number>;
}> {
  const [totalResult, enabledResult, byTypeResult] = await Promise.all([
    query<{ count: string }>(
      "SELECT COUNT(*) as count FROM policies WHERE user_id = $1",
      [userId]
    ),
    query<{ count: string }>(
      "SELECT COUNT(*) as count FROM policies WHERE user_id = $1 AND enabled = true",
      [userId]
    ),
    query<{ policy_type: string; count: string }>(
      "SELECT policy_type, COUNT(*) as count FROM policies WHERE user_id = $1 GROUP BY policy_type",
      [userId]
    ),
  ]);

  const by_type: Record<string, number> = {};
  byTypeResult.forEach((row) => {
    by_type[row.policy_type] = parseInt(row.count);
  });

  return {
    total: parseInt(totalResult[0].count),
    enabled: parseInt(enabledResult[0].count),
    disabled: parseInt(totalResult[0].count) - parseInt(enabledResult[0].count),
    by_type,
  };
}
