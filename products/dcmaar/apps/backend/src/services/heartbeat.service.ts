import { query } from "../db";
import { logger } from "../utils/logger";
import { assessThreatAndHealth } from "./threat-scoring.service";

export interface DeviceStatus {
  id: string;
  user_id: string;
  device_name: string;
  device_type: "desktop" | "mobile" | "extension";
  is_online: boolean;
  last_seen_at: Date;
  online_for: number; // milliseconds
  connection_quality?: "excellent" | "good" | "fair" | "poor" | null;
  is_active?: boolean;
  online_for_ms?: number;
}

interface DeviceRecord {
  id: string;
  user_id: string;
  device_name: string;
  device_type: "desktop" | "mobile" | "extension";
  is_active: boolean;
  last_seen_at: string | Date;
  online_for_ms?: number;
}

interface HeartbeatStats {
  heartbeat_count: number;
  total_minutes: number;
  avg_online_ms: number;
  max_offline_ms: number;
}

export interface HeartbeatData {
  device_id: string;
  battery_level?: number;
  wifi_signal?: number;
  network_latency?: number;
  uptime?: number;
  timestamp?: Date;
}

/**
 * Update device heartbeat and status.
 *
 * <p><b>Purpose</b><br>
 * Records device heartbeat signal to track online/offline status in real-time.
 * Called every 60 seconds by Guardian agents to maintain "alive" status.
 *
 * <p><b>Process</b><br>
 * - Fetch current device status and offline duration
 * - Update last_seen_at to NOW() and mark as active
 * - Detect status change (offline → online transition)
 * - Calculate connection quality from heartbeat data
 * - Return status with change flag for broadcasting
 *
 * <p><b>Timezone Safety</b><br>
 * Uses PostgreSQL NOW() and interval arithmetic to avoid JavaScript timezone issues.
 * See TIMESTAMP_FIX_SUMMARY.md for rationale.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Agent sends heartbeat every 60s
 * const result = await updateHeartbeat(userId, deviceId, {
 *   battery_level: 85,
 *   wifi_signal: -45,
 *   network_latency: 25
 * });
 * if (result.statusChanged) {
 *   broadcastStatusChange(result.device); // Device came online
 * }
 * }</pre>
 *
 * @param userId User who owns the device
 * @param deviceId Device sending heartbeat
 * @param data Heartbeat metrics (battery, WiFi, latency, uptime)
 * @return Device status, whether status changed, and whether it was offline before
 * @throws Error if device not found or update fails
 * @see getDeviceStatus
 * @see markDeviceOffline
 * @see calculateConnectionQuality
 * @doc.type function
 * @doc.purpose Update device heartbeat to track online/offline status
 * @doc.layer product
 * @doc.pattern Service
 */
export async function updateHeartbeat(
  userId: string,
  deviceId: string,
  data: HeartbeatData
): Promise<{
  device: DeviceStatus;
  statusChanged: boolean;
  wasOffline: boolean;
}> {
  try {
    // Get current device status
    const currentDevice = await query<DeviceRecord>(
      `SELECT id, is_active, last_seen_at, user_id, device_name, device_type
       FROM devices
       WHERE id = $1 AND user_id = $2`,
      [deviceId, userId]
    );

    if (!currentDevice.length) {
      throw new Error(`Device not found: ${deviceId}`);
    }

    const device = currentDevice[0];
    const wasOfflineBefore = !device.is_active;

    // Update last_seen_at and mark as active using PostgreSQL NOW() to avoid timezone issues
    const updated = await query<DeviceRecord>(
      `UPDATE devices
       SET last_seen_at = NOW(),
           is_active = true,
           updated_at = NOW()
       WHERE id = $1 AND user_id = $2
       RETURNING 
         id, 
         user_id, 
         device_name,
         device_type,
         is_active,
         last_seen_at,
         EXTRACT(EPOCH FROM (NOW() - COALESCE(last_seen_at, NOW()))) * 1000 as online_for_ms`,
      [deviceId, userId]
    );

    if (!updated.length) {
      throw new Error(`Failed to update device: ${deviceId}`);
    }

    const updatedDevice = updated[0];
    const statusChanged = wasOfflineBefore && updatedDevice.is_active;

    const status: DeviceStatus = {
      id: updatedDevice.id,
      user_id: updatedDevice.user_id,
      device_name: updatedDevice.device_name,
      device_type: updatedDevice.device_type as "desktop" | "mobile" | "extension",
      is_online: updatedDevice.is_active,
      last_seen_at: new Date(updatedDevice.last_seen_at as string),
      online_for: updatedDevice.online_for_ms || 0,
      connection_quality: calculateConnectionQuality(data),
    };

    // Best-effort threat & health scoring via Guardian Java service (non-blocking for callers)
    // Fire-and-forget: log result if available, but do not affect return shape.
    assessThreatAndHealth({
      agentId: deviceId,
      agentType: updatedDevice.device_type,
      agentStatus: updatedDevice.is_active ? "ACTIVE" : "OFFLINE",
      agentMetadata: {
        userId,
        deviceName: updatedDevice.device_name,
      },
      eventData: {},
      deviceMetrics: {
        // Map heartbeat metrics into generic numeric metrics map
        battery_level: data.battery_level ?? 0,
        wifi_signal: data.wifi_signal ?? 0,
        network_latency: data.network_latency ?? 0,
        uptime: data.uptime ?? 0,
      },
    }).then((threat) => {
      if (threat && threat.isThreat) {
        logger.warn("Guardian Java threat service detected elevated risk from heartbeat", {
          deviceId,
          userId,
          threatLevel: threat.threatLevel,
          recommendedAction: threat.recommendedAction,
        });
      }
    }).catch((error) => {
      logger.warn("Guardian Java threat service call failed from heartbeat handler", {
        deviceId,
        userId,
        error: error instanceof Error ? error.message : String(error),
      });
    });

    logger.info("Heartbeat updated", {
      deviceId,
      userId,
      statusChanged,
      wasOffline: wasOfflineBefore,
      connectionQuality: status.connection_quality,
    });

    return {
      device: status,
      statusChanged,
      wasOffline: wasOfflineBefore,
    };
  } catch (error) {
    logger.error("Failed to update heartbeat", { deviceId, userId, error });
    throw error;
  }
}

/**
 * Get current status of a device.
 *
 * <p><b>Purpose</b><br>
 * Fetches real-time device status including online/offline state, last seen timestamp,
 * and duration since last heartbeat.
 *
 * <p><b>Calculations</b><br>
 * - online_for: Milliseconds since last_seen_at (calculated via EXTRACT(EPOCH))
 * - is_online: Based on is_active flag (updated by heartbeats)
 *
 * <p><b>Timezone Safety</b><br>
 * Uses PostgreSQL NOW() for server-side time calculations to ensure consistency.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Check device status
 * const status = await getDeviceStatus(userId, deviceId);
 * if (status && status.is_online) {
 *   console.log(`Device online for ${status.online_for / 1000}s`);
 * }
 * }</pre>
 *
 * @param userId User who owns the device
 * @param deviceId Device to check
 * @return DeviceStatus with current online state, or null if device not found
 * @throws Error if database query fails
 * @see updateHeartbeat
 * @see getAllDeviceStatuses
 * @doc.type function
 * @doc.purpose Fetch real-time device online/offline status
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getDeviceStatus(
  userId: string,
  deviceId: string
): Promise<DeviceStatus | null> {
  try {
    const result = await query<DeviceRecord>(
      `SELECT 
         id,
         user_id,
         device_name,
         device_type,
         is_active,
         last_seen_at,
         EXTRACT(EPOCH FROM (NOW() - last_seen_at)) * 1000 as online_for_ms
       FROM devices
       WHERE id = $1 AND user_id = $2`,
      [deviceId, userId]
    );

    if (!result.length) {
      return null;
    }

    const device = result[0];

    return {
      id: device.id,
      user_id: device.user_id,
      device_name: device.device_name,
      device_type: device.device_type,
      is_online: device.is_active,
      last_seen_at: new Date(device.last_seen_at),
      online_for: device.online_for_ms || 0,
      connection_quality: null, // Will be set by the caller if needed
    };
  } catch (error) {
    logger.error("Failed to get device status", { deviceId, userId, error });
    throw error;
  }
}

/**
 * Get all devices and their online status for a user.
 *
 * <p><b>Purpose</b><br>
 * Lists all user devices with real-time status for dashboard display.
 * Sorted by last_seen_at DESC (most recently active first).
 *
 * <p><b>Calculations</b><br>
 * - online_for: Milliseconds since last_seen_at for each device
 * - is_online: Current is_active flag from heartbeat updates
 *
 * <p><b>Sorting</b><br>
 * Results ordered by last_seen_at DESC to show most active devices first.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get all device statuses for dashboard
 * const statuses = await getAllDeviceStatuses(userId);
 * const onlineCount = statuses.filter(s => s.is_online).length;
 * console.log(`${onlineCount}/${statuses.length} devices online`);
 * }</pre>
 *
 * @param userId User whose devices to fetch
 * @return Array of DeviceStatus objects sorted by last activity
 * @throws Error if database query fails
 * @see getDeviceStatus
 * @see updateHeartbeat
 * @doc.type function
 * @doc.purpose List all user devices with real-time online status
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getAllDeviceStatuses(
  userId: string
): Promise<DeviceStatus[]> {
  try {
    const result = await query<DeviceRecord>(
      `SELECT 
         id,
         user_id,
         device_name,
         device_type,
         is_active,
         last_seen_at,
         EXTRACT(EPOCH FROM (NOW() - last_seen_at)) * 1000 as online_for_ms
       FROM devices
       WHERE user_id = $1
       ORDER BY last_seen_at DESC`,
      [userId]
    );

    return result.map((device) => ({
      id: device.id,
      user_id: device.user_id,
      device_name: device.device_name,
      device_type: device.device_type as "desktop" | "mobile" | "extension",
      is_online: device.is_active,
      last_seen_at: new Date(device.last_seen_at as string),
      online_for: device.online_for_ms || 0,
    }));
  } catch (error) {
    logger.error("Failed to get all device statuses", { userId, error });
    throw error;
  }
}

/**
 * Get device heartbeat statistics for a time period.
 *
 * <p><b>Purpose</b><br>
 * Calculates uptime metrics for device reliability reporting and analytics.
 * Useful for device health monitoring and SLA tracking.
 *
 * <p><b>Metrics Calculated</b><br>
 * - total_heartbeats: Count of heartbeat signals in period
 * - uptime_percentage: (heartbeats / total_minutes) * 100, capped at 100%
 * - avg_online_duration: Average milliseconds between consecutive heartbeats
 * - max_offline_duration: Longest gap between heartbeats (in milliseconds)
 *
 * <p><b>Performance</b><br>
 * Uses window function LEAD() to calculate gaps between consecutive heartbeats.
 * Efficient for time-series analysis of device connectivity.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Get weekly uptime stats
 * const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
 * const stats = await getHeartbeatStats(userId, deviceId, weekAgo, new Date());
 * console.log(`Uptime: ${stats.uptime_percentage.toFixed(2)}%`);
 * console.log(`Avg online: ${(stats.avg_online_duration / 1000).toFixed(1)}s`);
 * }</pre>
 *
 * @param userId User who owns the device
 * @param deviceId Device to analyze
 * @param startDate Start of time period
 * @param endDate End of time period
 * @return Heartbeat statistics including uptime percentage and duration metrics
 * @throws Error if database query fails
 * @see updateHeartbeat
 * @see getDeviceStatus
 * @doc.type function
 * @doc.purpose Calculate device uptime metrics for time period
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getHeartbeatStats(
  userId: string,
  deviceId: string,
  startDate: Date,
  endDate: Date
): Promise<{
  total_heartbeats: number;
  uptime_percentage: number;
  avg_online_duration: number;
  max_offline_duration: number;
}> {
  try {
    // Get all devices (for calculating total time)
    const stats = await query<HeartbeatStats>(
      `WITH heartbeats AS (
         SELECT 
           last_seen_at,
           LEAD(last_seen_at) OVER (ORDER BY last_seen_at) as next_heartbeat
         FROM device_heartbeats
         WHERE device_id = $1 
           AND user_id = $2
           AND last_seen_at BETWEEN $3 AND $4
       )
       SELECT 
         COUNT(*) as heartbeat_count,
         (EXTRACT(EPOCH FROM ($4 - $3)) / 60) as total_minutes,
         COALESCE(AVG(EXTRACT(EPOCH FROM (next_heartbeat - last_seen_at))) * 1000, 0) as avg_online_ms,
         COALESCE(MAX(EXTRACT(EPOCH FROM (next_heartbeat - last_seen_at))) * 1000, 0) as max_offline_ms
       FROM heartbeats
       WHERE next_heartbeat IS NOT NULL`,
      [deviceId, userId, startDate, endDate]
    );

    const statsData = stats[0] || {
      heartbeat_count: 0,
      total_minutes: 0,
      avg_online_ms: 0,
      max_offline_ms: 0
    };

    const uptime_percentage =
      statsData.total_minutes > 0
        ? Math.min(100, (statsData.heartbeat_count / statsData.total_minutes) * 100)
        : 0;

    return {
      total_heartbeats: statsData.heartbeat_count,
      uptime_percentage: uptime_percentage,
      avg_online_duration: statsData.avg_online_ms,
      max_offline_duration: statsData.max_offline_ms,
    };
  } catch (error) {
    logger.error("Failed to get heartbeat stats", { userId, deviceId, error });
    throw error;
  }
}

/**
 * Calculate connection quality based on metrics.
 *
 * <p><b>Purpose</b><br>
 * Assigns quality rating to device connection based on battery, WiFi signal, and latency.
 * Used to display connection health to users.
 *
 * <p><b>Scoring Algorithm</b><br>
 * Starts at 100, deducts points based on metrics:
 * - Battery < 20%: -30 points (critical), < 50%: -10 points (warning)
 * - WiFi signal < -80 dBm: -40 points (poor), < -60 dBm: -20 points (fair)
 * - Latency > 200ms: -40 points (poor), > 100ms: -20 points (fair), > 50ms: -10 points (good)
 *
 * <p><b>Rating Thresholds</b><br>
 * - Excellent: 80-100 points (all metrics healthy)
 * - Good: 60-79 points (minor issues)
 * - Fair: 40-59 points (noticeable degradation)
 * - Poor: 0-39 points (significant connection problems)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const quality = calculateConnectionQuality({
 *   battery_level: 85,
 *   wifi_signal: -45,
 *   network_latency: 25
 * });
 * // Returns "excellent"
 * }</pre>
 *
 * @param data Heartbeat metrics (battery, WiFi, latency)
 * @return Connection quality rating: "excellent" | "good" | "fair" | "poor"
 * @see updateHeartbeat
 * @doc.type function
 * @doc.purpose Calculate connection quality from heartbeat metrics
 * @doc.layer product
 * @doc.pattern Service
 */
function calculateConnectionQuality(
  data: HeartbeatData
): "excellent" | "good" | "fair" | "poor" {
  let qualityScore = 100;

  // Battery level (0-100)
  if (data.battery_level !== undefined) {
    if (data.battery_level < 20) qualityScore -= 30;
    else if (data.battery_level < 50) qualityScore -= 10;
  }

  // WiFi signal (assume -30 to -90 dBm, higher is better)
  if (data.wifi_signal !== undefined) {
    if (data.wifi_signal < -80) qualityScore -= 40;
    else if (data.wifi_signal < -60) qualityScore -= 20;
  }

  // Network latency (milliseconds, lower is better)
  if (data.network_latency !== undefined) {
    if (data.network_latency > 200) qualityScore -= 40;
    else if (data.network_latency > 100) qualityScore -= 20;
    else if (data.network_latency > 50) qualityScore -= 10;
  }

  if (qualityScore >= 80) return "excellent";
  if (qualityScore >= 60) return "good";
  if (qualityScore >= 40) return "fair";
  return "poor";
}

/**
 * Mark a device as offline manually.
 * 
 * <p><b>Purpose</b><br>
 * Allows manual override to mark a device as offline, useful when automatic
 * detection fails or when an admin needs to force a device offline.
 * 
 * <p><b>Process</b><br>
 * 1. Updates device status to offline in the database
 * 2. Returns the updated device status
 * 3. Should trigger any necessary notifications or cleanup
 * 
 * @param userId The ID of the user who owns the device
 * @param deviceId The ID of the device to mark as offline
 * @returns Updated device status or null if device not found
 */
export async function markDeviceOffline(
  userId: string,
  deviceId: string
): Promise<DeviceStatus | null> {
  try {
    // First check if device is already offline
    const currentDevice = await query<DeviceRecord>(
      `SELECT is_active FROM devices WHERE id = $1 AND user_id = $2`,
      [deviceId, userId]
    );

    if (!currentDevice || currentDevice.length === 0) {
      return null;
    }

    // If already offline, return null (no state change)
    if (!currentDevice[0].is_active) {
      return null;
    }

    // Update the device status to offline
    const result = await query<DeviceRecord>(
      `UPDATE devices 
       SET is_active = false, 
           last_seen_at = NOW() 
       WHERE id = $1 AND user_id = $2
       RETURNING *`,
      [deviceId, userId]
    );

    if (!result || result.length === 0) {
      return null;
    }

    const device = result[0];

    return {
      id: device.id,
      user_id: device.user_id,
      device_name: device.device_name,
      device_type: device.device_type,
      is_online: false,
      last_seen_at: new Date(device.last_seen_at),
      online_for: device.online_for_ms || 0,
      is_active: device.is_active
    };
  } catch (error) {
    logger.error('Failed to mark device as offline', { userId, deviceId, error });
    throw error;
  }
}

/**
 * Find all devices with stale heartbeats (no activity within threshold).
 *
 * @param userId User ID
 * @param thresholdMinutes Minutes of inactivity to consider device stale
 * @returns Array of stale device IDs
 */
export async function getStaleDevices(
  userId: string,
  thresholdMinutes: number = 5
): Promise<string[]> {
  try {
    const result = await query<{ id: string }>(
      `SELECT id
       FROM devices
       WHERE user_id = $1
       AND is_active = true
       AND (last_seen_at IS NULL OR last_seen_at < NOW() - INTERVAL '1 minute' * $2)
       ORDER BY last_seen_at ASC`,
      [userId, thresholdMinutes]
    );

    return result.map(row => row.id);
  } catch (error) {
    logger.error('Failed to get stale devices', { userId, thresholdMinutes, error });
    throw error;
  }
}
