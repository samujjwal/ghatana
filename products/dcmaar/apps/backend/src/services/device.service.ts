import { query } from "../db";
import {
  generatePairingCode,
  getPairingExpiration as _getPairingExpiration,
  isPairingExpired as _isPairingExpired,
} from "../utils/pairing";

export interface Device {
  id: string;
  user_id: string;
  child_id: string | null;
  device_type: "desktop" | "mobile" | "extension";
  device_name: string;
  device_fingerprint: string | null;
  pairing_code: string | null;
  pairing_expires: Date | null;
  is_paired: boolean;
  last_seen_at: Date | null;
  is_active: boolean;
  created_at: Date;
  updated_at: Date;
}

export interface CreateDeviceData {
  child_id?: string;
  device_type: "desktop" | "mobile" | "extension";
  device_name: string;
  device_fingerprint?: string;
}

export interface UpdateDeviceData {
  child_id?: string | null;
  device_name?: string;
  is_active?: boolean;
}

/**
 * Register new device for monitoring and control.
 *
 * <p><b>Purpose</b><br>
 * Creates device record for Guardian agent (desktop, mobile, or browser extension).
 * Sets initial last_seen_at timestamp to NOW() for online status tracking.
 *
 * <p><b>Device Types</b><br>
 * - desktop: Native desktop agent (Windows/macOS/Linux)
 * - mobile: React Native mobile app (Android/iOS)
 * - extension: Browser extension (Chrome/Firefox/Edge)
 *
 * <p><b>Process</b><br>
 * 1. Create device record with user ownership
 * 2. Set last_seen_at to NOW() (device is online)
 * 3. Optional: Associate with child immediately
 * 4. Optional: Store device fingerprint for duplicate detection
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const device = await registerDevice(userId, {
 *   device_type: 'desktop',
 *   device_name: 'Home Desktop',
 *   child_id: childId, // optional
 *   device_fingerprint: 'hw-hash-123' // optional
 * });
 * }</pre>
 *
 * @param userId User ID (parent account)
 * @param data Device registration data (type, name, optional child/fingerprint)
 * @return Promise resolving to created Device object
 * @see getDevices
 * @see pairDeviceWithChild
 * @doc.type function
 * @doc.purpose Register new monitoring device
 * @doc.layer product
 * @doc.pattern Service
 */
export async function registerDevice(
  userId: string,
  data: CreateDeviceData
): Promise<Device> {
  const result = await query<Device>(
    `INSERT INTO devices (user_id, child_id, device_type, device_name, device_fingerprint, last_seen_at)
     VALUES ($1, $2, $3, $4, $5, NOW())
     RETURNING *`,
    [
      userId,
      data.child_id || null,
      data.device_type,
      data.device_name,
      data.device_fingerprint || null,
    ]
  );

  return result[0];
}

/**
 * Retrieve all devices for user with optional filtering.
 *
 * <p><b>Purpose</b><br>
 * Lists user's registered devices with support for filtering by child, type, or status.
 * Defaults to showing only active devices (excludes soft-deleted).
 *
 * <p><b>Filters</b><br>
 * - child_id: Show only devices assigned to specific child
 * - device_type: Filter by desktop, mobile, or extension
 * - is_active: Include/exclude soft-deleted devices (default: true = active only)
 *
 * <p><b>Sorting</b><br>
 * Results ordered by last_seen_at DESC (most recently seen first).
 * Devices never seen (NULL last_seen_at) appear last.
 *
 * @param userId User ID to fetch devices for
 * @param filters Optional filter criteria (child_id, device_type, is_active)
 * @return Promise resolving to array of Device objects (may be empty)
 * @see getDeviceById
 * @see registerDevice
 * @doc.type function
 * @doc.purpose List user devices with filtering
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getDevices(
  userId: string,
  filters?: {
    child_id?: string;
    device_type?: string;
    is_active?: boolean;
  }
): Promise<Device[]> {
  let query_text = "SELECT * FROM devices WHERE user_id = $1";
  const params: unknown[] = [userId];
  let paramIndex = 2;

  if (filters?.child_id) {
    query_text += ` AND child_id = $${paramIndex++}`;
    params.push(filters.child_id);
  }

  if (filters?.device_type) {
    query_text += ` AND device_type = $${paramIndex++}`;
    params.push(filters.device_type);
  }

  // Default to only active devices (exclude soft-deleted)
  const isActiveFilter =
    filters?.is_active !== undefined ? filters.is_active : true;
  query_text += ` AND is_active = $${paramIndex++}`;
  params.push(isActiveFilter);

  query_text += " ORDER BY last_seen_at DESC NULLS LAST";

  return await query<Device>(query_text, params);
}

/**
 * Retrieve single device by ID with ownership verification.
 *
 * <p><b>Purpose</b><br>
 * Fetches device details ensuring user owns the device (prevents unauthorized access).
 *
 * <p><b>Security</b><br>
 * Query filters by both device ID AND user ID to prevent cross-user device access.
 *
 * @param userId User ID (must match device owner)
 * @param deviceId Device ID to fetch
 * @return Promise resolving to Device object if found and owned by user, null otherwise
 * @see getDevices
 * @doc.type function
 * @doc.purpose Fetch device by ID with ownership check
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getDeviceById(
  userId: string,
  deviceId: string
): Promise<Device | null> {
  const result = await query<Device>(
    "SELECT * FROM devices WHERE id = $1 AND user_id = $2",
    [deviceId, userId]
  );

  return result.length > 0 ? result[0] : null;
}

/**
 * Update device metadata (name, child assignment, active status).
 *
 * <p><b>Purpose</b><br>
 * Allows partial updates to device record. Automatically updates updated_at timestamp.
 *
 * <p><b>Updatable Fields</b><br>
 * - child_id: Assign/unassign child (set to null to unpair)
 * - device_name: Rename device
 * - is_active: Soft delete (false) or reactivate (true)
 *
 * <p><b>Dynamic SQL</b><br>
 * Builds UPDATE query dynamically based on provided fields.
 * Throws error if no fields provided (prevents no-op updates).
 *
 * @param userId User ID (ownership verification)
 * @param deviceId Device ID to update
 * @param updates Object with optional fields to update
 * @return Promise resolving to updated Device object if found, null if not owned by user
 * @throws Error if no fields provided
 * @see pairDeviceWithChild
 * @see unpairDevice
 * @doc.type function
 * @doc.purpose Update device metadata
 * @doc.layer product
 * @doc.pattern Service
 */
export async function updateDevice(
  userId: string,
  deviceId: string,
  updates: UpdateDeviceData
): Promise<Device | null> {
  const fields: string[] = [];
  const values: unknown[] = [];
  let paramIndex = 1;

  if (updates.child_id !== undefined) {
    fields.push(`child_id = $${paramIndex++}`);
    values.push(updates.child_id);
  }

  if (updates.device_name !== undefined) {
    fields.push(`device_name = $${paramIndex++}`);
    values.push(updates.device_name);
  }

  if (updates.is_active !== undefined) {
    fields.push(`is_active = $${paramIndex++}`);
    values.push(updates.is_active);
  }

  if (fields.length === 0) {
    throw new Error("No fields to update");
  }

  values.push(deviceId, userId);

  const result = await query<Device>(
    `UPDATE devices SET ${fields.join(", ")}, updated_at = NOW()
     WHERE id = $${paramIndex++} AND user_id = $${paramIndex++}
     RETURNING *`,
    values
  );

  return result.length > 0 ? result[0] : null;
}

/**
 * Soft-delete device (mark as inactive without removing data).
 *
 * <p><b>Purpose</b><br>
 * Deactivates device while preserving historical data for analytics/compliance.
 * Device can be reactivated later via updateDevice({ is_active: true }).
 *
 * <p><b>Soft Delete Benefits</b><br>
 * - Preserves usage history for reporting
 * - Allows data recovery if deleted accidentally
 * - Maintains referential integrity (foreign keys remain valid)
 *
 * <p><b>Note</b><br>
 * Soft-deleted devices excluded from getDevices() by default (is_active filter).
 *
 * @param userId User ID (ownership verification)
 * @param deviceId Device ID to soft-delete
 * @return Promise resolving to true if deleted, false if not found/not owned
 * @see updateDevice
 * @doc.type function
 * @doc.purpose Soft-delete device (preserve data)
 * @doc.layer product
 * @doc.pattern Service
 */
export async function deleteDevice(
  userId: string,
  deviceId: string
): Promise<boolean> {
  const result = await query(
    "UPDATE devices SET is_active = false, updated_at = NOW() WHERE id = $1 AND user_id = $2 RETURNING id",
    [deviceId, userId]
  );

  return result.length > 0;
}

/**
 * Update device heartbeat timestamp to track online status.
 *
 * <p><b>Purpose</b><br>
 * Called by device agents to signal they're still online/active.
 * Used by stale device detection to identify offline devices.
 *
 * <p><b>Heartbeat Interval</b><br>
 * Agents should call this every 60 seconds. Devices with last_seen_at > 5 minutes
 * ago are considered stale (see heartbeat.service.ts getStaleDevices).
 *
 * <p><b>Timezone Safety</b><br>
 * Uses PostgreSQL NOW() for timezone-safe timestamp.
 * See TIMESTAMP_FIX_SUMMARY.md for details.
 *
 * @param deviceId Device ID to update heartbeat for
 * @return Promise resolving when timestamp updated (void)
 * @see heartbeat.service.ts
 * @doc.type function
 * @doc.purpose Update device last-seen timestamp
 * @doc.layer product
 * @doc.pattern Service
 */
export async function updateDeviceLastSeen(deviceId: string): Promise<void> {
  await query("UPDATE devices SET last_seen_at = NOW() WHERE id = $1", [
    deviceId,
  ]);
}

/**
 * Associate device with child for monitoring.
 *
 * <p><b>Purpose</b><br>
 * Pairs device with specific child account. Required before policies can be enforced.
 *
 * <p><b>Process</b><br>
 * 1. Verify child exists and belongs to user (prevents unauthorized pairing)
 * 2. Update device child_id to establish association
 * 3. Device policies now apply based on child's policy assignments
 *
 * <p><b>Security</b><br>
 * Both device AND child must belong to same user to prevent cross-user attacks.
 *
 * @param userId User ID (ownership verification for both device and child)
 * @param deviceId Device ID to pair
 * @param childId Child ID to pair device with
 * @return Promise resolving to updated Device object if successful, null if device not found
 * @throws Error if child not found or not owned by user
 * @see unpairDevice
 * @see updateDevice
 * @doc.type function
 * @doc.purpose Pair device with child for monitoring
 * @doc.layer product
 * @doc.pattern Service
 */
export async function pairDeviceWithChild(
  userId: string,
  deviceId: string,
  childId: string
): Promise<Device | null> {
  // Verify child belongs to user
  const childCheck = await query(
    "SELECT id FROM children WHERE id = $1 AND user_id = $2",
    [childId, userId]
  );

  if (childCheck.length === 0) {
    throw new Error("Child not found");
  }

  const result = await query<Device>(
    `UPDATE devices SET child_id = $1, updated_at = NOW()
     WHERE id = $2 AND user_id = $3
     RETURNING *`,
    [childId, deviceId, userId]
  );

  return result.length > 0 ? result[0] : null;
}

/**
 * Disassociate device from child (remove monitoring).
 *
 * <p><b>Purpose</b><br>
 * Unpairs device from child by setting child_id to NULL.
 * Device remains registered but policies no longer enforced.
 *
 * <p><b>Use Cases</b><br>
 * - Child stopped using this device
 * - Reassigning device to different child (unpair then pair with new child)
 * - Parent wants to stop monitoring this specific device
 *
 * @param userId User ID (ownership verification)
 * @param deviceId Device ID to unpair
 * @return Promise resolving to updated Device object if successful, null if not found
 * @see pairDeviceWithChild
 * @doc.type function
 * @doc.purpose Unpair device from child
 * @doc.layer product
 * @doc.pattern Service
 */
export async function unpairDevice(
  userId: string,
  deviceId: string
): Promise<Device | null> {
  const result = await query<Device>(
    `UPDATE devices SET child_id = NULL, updated_at = NOW()
     WHERE id = $1 AND user_id = $2
     RETURNING *`,
    [deviceId, userId]
  );

  return result.length > 0 ? result[0] : null;
}

/**
 * Get comprehensive device statistics for user dashboard.
 *
 * <p><b>Purpose</b><br>
 * Provides aggregated device metrics for dashboard overview.
 * All counts run in parallel for performance.
 *
 * <p><b>Statistics Returned</b><br>
 * - total: All devices (including soft-deleted)
 * - active: Non-deleted devices (is_active = true)
 * - by_type: Count grouped by desktop/mobile/extension
 * - paired: Devices assigned to children (child_id NOT NULL)
 * - unpaired: Devices not yet assigned (child_id IS NULL)
 *
 * <p><b>Performance</b><br>
 * Uses Promise.all() to run all 4 COUNT queries concurrently.
 *
 * @param userId User ID to get statistics for
 * @return Promise resolving to statistics object
 * @doc.type function
 * @doc.purpose Get device count statistics
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getDeviceStats(userId: string): Promise<{
  total: number;
  active: number;
  by_type: Record<string, number>;
  paired: number;
  unpaired: number;
}> {
  const [totalResult, activeResult, byTypeResult, pairedResult] =
    await Promise.all([
      query<{ count: string }>(
        "SELECT COUNT(*) as count FROM devices WHERE user_id = $1",
        [userId]
      ),
      query<{ count: string }>(
        "SELECT COUNT(*) as count FROM devices WHERE user_id = $1 AND is_active = true",
        [userId]
      ),
      query<{ device_type: string; count: string }>(
        "SELECT device_type, COUNT(*) as count FROM devices WHERE user_id = $1 GROUP BY device_type",
        [userId]
      ),
      query<{ count: string }>(
        "SELECT COUNT(*) as count FROM devices WHERE user_id = $1 AND child_id IS NOT NULL",
        [userId]
      ),
    ]);

  const by_type: Record<string, number> = {};
  byTypeResult.forEach((row) => {
    by_type[row.device_type] = parseInt(row.count);
  });

  const total = parseInt(totalResult[0].count);
  const paired = parseInt(pairedResult[0].count);

  return {
    total,
    active: parseInt(activeResult[0].count),
    by_type,
    paired,
    unpaired: total - paired,
  };
}

/**
 * Generate 6-digit pairing code for device-child association.
 *
 * <p><b>Purpose</b><br>
 * Creates short-lived pairing code displayed to parent for device setup.
 * Child enters code on device to establish monitoring link.
 *
 * <p><b>Process</b><br>
 * 1. Generate random 6-digit code (e.g., "123456")
 * 2. Store with 15-minute expiry (PostgreSQL NOW() + INTERVAL)
 * 3. Return code + database-calculated expiry time
 * 4. If code already exists for child, replace it (ON CONFLICT UPDATE)
 *
 * <p><b>Timezone Safety</b><br>
 * Uses PostgreSQL `NOW() + INTERVAL '15 minutes'` for expiry calculation
 * instead of JavaScript Date to avoid timezone offset issues.
 * Returns database-calculated expiry via RETURNING clause for consistency.
 * See TIMESTAMP_FIX_SUMMARY.md for details.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { code, expiresAt } = await generateDevicePairingCode(userId, childId);
 * // Display code to parent: "Enter code 123456 on device"
 * // expiresAt from database (UTC) for consistent display
 * }</pre>
 *
 * @param userId User ID (parent account)
 * @param childId Child ID to generate code for
 * @return Promise resolving to object with code (6 digits) and database expiry timestamp
 * @see pairDeviceWithCode
 * @see getActivePairingCode
 * @doc.type function
 * @doc.purpose Generate device pairing code with expiry
 * @doc.layer product
 * @doc.pattern Service
 */
export async function generateDevicePairingCode(
  userId: string,
  childId: string
): Promise<{ code: string; expiresAt: Date }> {
  const code = generatePairingCode();

  // Store pairing code using PostgreSQL NOW() + INTERVAL to avoid timezone issues
  // Return the database-calculated expiry time for consistency
  const result = await query<{ expires_at: Date }>(
    `INSERT INTO device_pairing_requests (user_id, child_id, pairing_code, expires_at)
     VALUES ($1, $2, $3, NOW() + INTERVAL '15 minutes')
     ON CONFLICT (child_id) DO UPDATE SET
       pairing_code = EXCLUDED.pairing_code,
       expires_at = NOW() + INTERVAL '15 minutes'
     RETURNING expires_at`,
    [userId, childId, code]
  );

  return { code, expiresAt: result[0].expires_at };
}

/**
 * Associate device with child using pairing code (device-initiated flow).
 *
 * <p><b>Purpose</b><br>
 * Allows device to pair itself with child by entering 6-digit code.
 * Alternative to parent-initiated pairDeviceWithChild().
 *
 * <p><b>Process</b><br>
 * 1. Validate code exists AND not expired (SQL: expires_at > NOW())
 * 2. If expired, clean up and return expiry error
 * 3. If invalid, return error (prevents brute force)
 * 4. Update device with child_id from pairing request
 * 5. Delete used pairing code (single-use)
 *
 * <p><b>Security</b><br>
 * - Codes expire after 15 minutes
 * - Single-use (deleted after pairing)
 * - Expiry check in SQL WHERE clause (timezone-safe)
 * - Distinguishes expired vs. invalid codes (different error messages)
 *
 * <p><b>Timezone Safety</b><br>
 * Expiry validation uses `WHERE expires_at > NOW()` in PostgreSQL for
 * timezone-safe comparison. See TIMESTAMP_FIX_SUMMARY.md.
 *
 * @param deviceId Device ID attempting to pair
 * @param pairingCode 6-digit code entered by user (e.g., "123456")
 * @return Promise resolving to success object with device if paired, error message if failed
 * @see generateDevicePairingCode
 * @doc.type function
 * @doc.purpose Pair device with child using code
 * @doc.layer product
 * @doc.pattern Service
 */
export async function pairDeviceWithCode(
  deviceId: string,
  pairingCode: string
): Promise<{ success: boolean; device?: Device; error?: string }> {
  // Find pairing request by code AND check expiry in the database
  // to avoid timezone issues with JavaScript Date comparisons
  const pairingRequests = await query<{
    user_id: string;
    child_id: string;
    expires_at: Date;
  }>(
    `SELECT user_id, child_id, expires_at 
     FROM device_pairing_requests 
     WHERE pairing_code = $1 AND expires_at > NOW()`,
    [pairingCode]
  );

  if (pairingRequests.length === 0) {
    // Check if code exists but is expired
    const expiredCheck = await query<{ expires_at: Date }>(
      `SELECT expires_at FROM device_pairing_requests WHERE pairing_code = $1`,
      [pairingCode]
    );

    if (expiredCheck.length > 0) {
      // Code exists but is expired - clean it up
      await query(
        "DELETE FROM device_pairing_requests WHERE pairing_code = $1",
        [pairingCode]
      );
      return { success: false, error: "Pairing code has expired" };
    }

    return { success: false, error: "Invalid pairing code" };
  }

  const request = pairingRequests[0];

  // Update device with child_id
  const result = await query<Device>(
    `UPDATE devices 
     SET child_id = $1, is_paired = true, updated_at = NOW()
     WHERE id = $2
     RETURNING *`,
    [request.child_id, deviceId]
  );

  if (result.length === 0) {
    return { success: false, error: "Device not found" };
  }

  // Clean up used pairing code
  await query("DELETE FROM device_pairing_requests WHERE pairing_code = $1", [
    pairingCode,
  ]);

  return { success: true, device: result[0] };
}

/**
 * Retrieve current active pairing code for child (if exists and not expired).
 *
 * <p><b>Purpose</b><br>
 * Allows parent to retrieve previously generated pairing code if still valid.
 * Used in UI to display "Current code: 123456" without regenerating.
 *
 * <p><b>Behavior</b><br>
 * - Returns code + expiry if valid code exists
 * - Returns null if no code or expired
 * - Expired codes remain in DB (cleaned up by pairDeviceWithCode or background job)
 *
 * <p><b>Timezone Safety</b><br>
 * Uses `WHERE expires_at > NOW()` in PostgreSQL for timezone-safe expiry check.
 *
 * @param userId User ID (ownership verification)
 * @param childId Child ID to check for active code
 * @return Promise resolving to object with code + expiry if active, null if none/expired
 * @see generateDevicePairingCode
 * @doc.type function
 * @doc.purpose Get active pairing code for child
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getActivePairingCode(
  userId: string,
  childId: string
): Promise<{ code: string; expiresAt: Date } | null> {
  const result = await query<{ pairing_code: string; expires_at: Date }>(
    `SELECT pairing_code, expires_at 
     FROM device_pairing_requests 
     WHERE user_id = $1 AND child_id = $2 AND expires_at > NOW()`,
    [userId, childId]
  );

  if (result.length === 0) {
    return null;
  }

  return {
    code: result[0].pairing_code,
    expiresAt: result[0].expires_at,
  };
}
