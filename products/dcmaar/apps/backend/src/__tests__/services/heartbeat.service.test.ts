/**
 * Heartbeat Service Tests
 *
 * Tests heartbeat tracking service including:
 * - updateHeartbeat - Update device heartbeat
 * - getDeviceStatus - Get device status
 * - getAllDeviceStatuses - List all device statuses for user
 * - markDeviceOffline - Mark device as offline
 * - getStaleDevices - Find devices that haven't sent heartbeat recently
 */

import { describe, it, expect, beforeEach } from "vitest";
import * as heartbeatService from "../../services/heartbeat.service";
import * as deviceService from "../../services/device.service";
import { randomEmail } from "../setup";
import { query } from "../../db";
import { createTestUser } from '../fixtures/user.fixtures';

describe("HeartbeatService", () => {
  let testUserId: string;
  let testDeviceId: string;

  beforeEach(async () => {
    // Create test user
    const user = await createTestUser({ email: randomEmail(), displayName: 'Test User' });
    testUserId = user.user.id;

    // Create test device
    const device = await deviceService.registerDevice(testUserId, {
      device_type: "mobile",
      device_name: "Test iPhone",
      os: "iOS",
      os_version: "16.0",
      fingerprint: `test-fingerprint-${Date.now()}`,
    });
    testDeviceId = device.id;
  });

  describe("updateHeartbeat", () => {
    it("should update device heartbeat", async () => {
      // GIVEN: Active device
      // WHEN: Heartbeat is sent
      const result = await heartbeatService.updateHeartbeat(
        testUserId,
        testDeviceId,
        {
          device_id: testDeviceId,
        }
      );

      // THEN: Device is marked online and status is updated
      expect(result).toBeDefined();
      expect(result.device).toBeDefined();
      expect(result.device.id).toBe(testDeviceId);
      expect(result.device.is_online).toBe(true);

      const status = await heartbeatService.getDeviceStatus(
        testUserId,
        testDeviceId
      );
      expect(status).toBeDefined();
      expect(status?.is_online).toBe(true);
    });

    it("should update existing heartbeat", async () => {
      // GIVEN: Device with existing heartbeat
      // First heartbeat
      const first = await heartbeatService.updateHeartbeat(
        testUserId,
        testDeviceId,
        {
          device_id: testDeviceId,
        }
      );
      const firstSeen = first.device.last_seen_at;

      // Wait a bit
      await new Promise((resolve) => setTimeout(resolve, 100));

      // WHEN: Second heartbeat is sent
      const second = await heartbeatService.updateHeartbeat(
        testUserId,
        testDeviceId,
        {
          device_id: testDeviceId,
        }
      );
      const secondSeen = second.device.last_seen_at;

      // THEN: Timestamp is updated to be more recent
      expect(secondSeen).toBeDefined();
      expect(new Date(secondSeen).getTime()).toBeGreaterThan(
        new Date(firstSeen).getTime()
      );
    });

    it("should handle multiple devices", async () => {
      // GIVEN: Two devices for user
      // Create second device
      const device2 = await deviceService.registerDevice(testUserId, {
        device_type: "desktop",
        device_name: "Test MacBook",
        os: "macOS",
        os_version: "13.0",
        fingerprint: `test-fingerprint-desktop-${Date.now()}`,
      });

      // WHEN: Both devices send heartbeats
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });
      await heartbeatService.updateHeartbeat(testUserId, device2.id, {
        device_id: device2.id,
      });

      // THEN: Both devices are marked online
      const status1 = await heartbeatService.getDeviceStatus(
        testUserId,
        testDeviceId
      );
      const status2 = await heartbeatService.getDeviceStatus(
        testUserId,
        device2.id
      );

      expect(status1?.is_online).toBe(true);
      expect(status2?.is_online).toBe(true);
    });

    it("should update device last_seen timestamp", async () => {
      // GIVEN: Device ready to send heartbeat
      // WHEN: Heartbeat is sent
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });

      // THEN: last_seen_at is updated to current time
      const device = await deviceService.getDeviceById(
        testUserId,
        testDeviceId
      );
      expect(device?.last_seen_at).toBeDefined();

      const lastSeenTime = new Date(device!.last_seen_at!).getTime();
      const now = Date.now();
      expect(now - lastSeenTime).toBeLessThan(5000); // Within 5 seconds
    });

    it("should detect status change from offline to online", async () => {
      // GIVEN: Device marked as offline
      // Mark device as offline first
      await query(`UPDATE devices SET is_active = false WHERE id = $1`, [
        testDeviceId,
      ]);

      // WHEN: Device sends heartbeat
      const result = await heartbeatService.updateHeartbeat(
        testUserId,
        testDeviceId,
        {
          device_id: testDeviceId,
        }
      );

      // THEN: Status change is detected and device is marked online
      expect(result.statusChanged).toBe(true);
      expect(result.wasOffline).toBe(true);
      expect(result.device.is_online).toBe(true);
    });

    it("should calculate connection quality", async () => {
      // GIVEN: Device with various connection metrics
      // WHEN: Heartbeat is sent with connection details
      const result = await heartbeatService.updateHeartbeat(
        testUserId,
        testDeviceId,
        {
          device_id: testDeviceId,
          battery_level: 80,
          wifi_signal: -50,
          network_latency: 30,
        }
      );

      // THEN: Connection quality is calculated from metrics
      expect(result.device.connection_quality).toBeDefined();
      expect(["excellent", "good", "fair", "poor"]).toContain(
        result.device.connection_quality
      );
    });
  });

  describe("getDeviceStatus", () => {
    it("should return online status for recent heartbeat", async () => {
      // GIVEN: Device with recent heartbeat
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });

      // WHEN: Device status is retrieved
      const status = await heartbeatService.getDeviceStatus(
        testUserId,
        testDeviceId
      );

      // THEN: Status shows device is online with timestamp
      expect(status).toBeDefined();
      expect(status?.is_online).toBe(true);
      expect(status?.last_seen_at).toBeDefined();
    });

    it("should return offline for old heartbeat", async () => {
      // GIVEN: Device with heartbeat, then manually marked offline
      // Update heartbeat
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });

      // Manually mark as offline
      await query(`UPDATE devices SET is_active = false WHERE id = $1`, [
        testDeviceId,
      ]);

      // WHEN: Device status is retrieved
      const status = await heartbeatService.getDeviceStatus(
        testUserId,
        testDeviceId
      );

      // THEN: Status shows device is offline
      expect(status).toBeDefined();
      expect(status?.is_online).toBe(false);
    });

    it("should return offline for device with no heartbeat", async () => {
      // GIVEN: Device created but never sent heartbeat, marked offline
      // Device was just created but never sent heartbeat
      // Mark it as offline first (newly created devices are is_active = true by default)
      await query(`UPDATE devices SET is_active = false WHERE id = $1`, [
        testDeviceId,
      ]);

      // WHEN: Device status is retrieved
      const status = await heartbeatService.getDeviceStatus(
        testUserId,
        testDeviceId
      );

      // THEN: Status shows device is offline
      expect(status).toBeDefined();
      expect(status?.is_online).toBe(false);
    });

    it("should return null for non-existent device", async () => {
      // GIVEN: Non-existent device ID
      const nonExistentId = "00000000-0000-0000-0000-000000000000";

      // WHEN: Status is retrieved for non-existent device
      const status = await heartbeatService.getDeviceStatus(
        testUserId,
        nonExistentId
      );

      // THEN: Null is returned
      expect(status).toBeNull();
    });

    it("should return device metadata", async () => {
      // GIVEN: Device with heartbeat
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });

      // WHEN: Device status is retrieved
      const status = await heartbeatService.getDeviceStatus(
        testUserId,
        testDeviceId
      );

      // THEN: Status includes all device metadata
      expect(status).toBeDefined();
      expect(status?.id).toBe(testDeviceId);
      expect(status?.user_id).toBe(testUserId);
      expect(status?.device_name).toBe("Test iPhone");
      expect(status?.device_type).toBe("mobile");
    });
  });

  describe("getAllDeviceStatuses", () => {
    it("should list all devices for user", async () => {
      // GIVEN: User with multiple devices
      // Create multiple devices
      const device2 = await deviceService.registerDevice(testUserId, {
        device_type: "desktop",
        device_name: "Test MacBook",
        os: "macOS",
        os_version: "13.0",
        fingerprint: `test-fingerprint-desktop-${Date.now()}`,
      });

      const device3 = await deviceService.registerDevice(testUserId, {
        device_type: "extension",
        device_name: "Chrome Extension",
        os: "Chrome",
        os_version: "110.0",
        fingerprint: `test-fingerprint-chrome-${Date.now()}`,
      });

      // WHEN: Heartbeats sent for device 1 and 2
      // Send heartbeats for device 1 and 2
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });
      await heartbeatService.updateHeartbeat(testUserId, device2.id, {
        device_id: device2.id,
      });
      // Device 3 has no heartbeat - mark it as offline (newly created devices are is_active = true)
      await query(`UPDATE devices SET is_active = false WHERE id = $1`, [
        device3.id,
      ]);

      // THEN: All devices are listed with correct online/offline status
      const allStatuses =
        await heartbeatService.getAllDeviceStatuses(testUserId);
      expect(allStatuses).toBeDefined();
      expect(allStatuses.length).toBe(3);

      // Filter online devices
      const onlineDevices = allStatuses.filter((d) => d.is_online);
      expect(onlineDevices.length).toBe(2);
      expect(onlineDevices.find((d) => d.id === testDeviceId)).toBeDefined();
      expect(onlineDevices.find((d) => d.id === device2.id)).toBeDefined();

      // Device 3 should be offline
      const device3Status = allStatuses.find((d) => d.id === device3.id);
      expect(device3Status?.is_online).toBe(false);
    });

    it("should return empty array for user with no devices", async () => {
      // GIVEN: User with no devices
      // Create another user with no devices
      const user2 = await createTestUser({ email: randomEmail(), displayName: 'User 2' });

      // WHEN: Device statuses are retrieved for user
      const statuses = await heartbeatService.getAllDeviceStatuses(
        user2.user.id
      );

      // THEN: Empty array is returned
      expect(statuses).toEqual([]);
    });

    it("should not include other users devices", async () => {
      // GIVEN: Two users with their own devices
      // Create another user and their device
      const otherUser = await createTestUser({ email: randomEmail(), displayName: 'Other User' });

      const otherDevice = await deviceService.registerDevice(
        otherUser.user.id,
        {
          device_type: "mobile",
          device_name: "Other Device",
          os: "Android",
          os_version: "13.0",
          fingerprint: `other-fingerprint-${Date.now()}`,
        }
      );

      // WHEN: Both users send heartbeats
      // Send heartbeats for both
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });
      await heartbeatService.updateHeartbeat(
        otherUser.user.id,
        otherDevice.id,
        {
          device_id: otherDevice.id,
        }
      );

      // THEN: Each user only sees their own devices (user isolation)
      const statuses = await heartbeatService.getAllDeviceStatuses(testUserId);

      expect(statuses.length).toBe(1);
      expect(statuses[0].id).toBe(testDeviceId);
    });

    it("should order devices by last_seen_at", async () => {
      // GIVEN: User with multiple devices with different last_seen times
      // Create second device
      const device2 = await deviceService.registerDevice(testUserId, {
        device_type: "desktop",
        device_name: "Test MacBook",
        os: "macOS",
        os_version: "13.0",
        fingerprint: `test-fingerprint-desktop-${Date.now()}`,
      });

      // WHEN: Heartbeats are sent in sequence
      // Send heartbeat for device1 first
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });

      await new Promise((resolve) => setTimeout(resolve, 100));

      // Send heartbeat for device2 later
      await heartbeatService.updateHeartbeat(testUserId, device2.id, {
        device_id: device2.id,
      });

      // THEN: Most recent device is returned first
      const statuses = await heartbeatService.getAllDeviceStatuses(testUserId);

      // Most recent should be first
      expect(statuses[0].id).toBe(device2.id);
      expect(statuses[1].id).toBe(testDeviceId);
    });
  });

  describe("markDeviceOffline", () => {
    it("should mark online device as offline", async () => {
      // GIVEN: Online device
      // Make device online first
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });

      // WHEN: Device is marked offline
      const result = await heartbeatService.markDeviceOffline(
        testUserId,
        testDeviceId
      );

      // THEN: Device status changes to offline
      expect(result).toBeDefined();
      expect(result?.is_online).toBe(false);

      // Verify status changed
      const status = await heartbeatService.getDeviceStatus(
        testUserId,
        testDeviceId
      );
      expect(status?.is_online).toBe(false);
    });

    it("should return null for device already offline", async () => {
      // GIVEN: Device already offline
      // Make device online first, then offline
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });
      await heartbeatService.markDeviceOffline(testUserId, testDeviceId);

      // WHEN: Try to mark offline again
      const result = await heartbeatService.markDeviceOffline(
        testUserId,
        testDeviceId
      );

      // THEN: Null is returned (already offline, no state change)
      expect(result).toBeNull();
    });

    it("should return null for non-existent device", async () => {
      // GIVEN: Non-existent device ID
      const nonExistentId = "00000000-0000-0000-0000-000000000000";

      // WHEN: Mark offline is attempted
      const result = await heartbeatService.markDeviceOffline(
        testUserId,
        nonExistentId
      );

      // THEN: Null is returned
      expect(result).toBeNull();
    });
  });

  describe("getStaleDevices", () => {
    it("should find devices with old heartbeat", async () => {
      // GIVEN: Device with old heartbeat (6 minutes)
      // Send heartbeat
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });

      // WHEN: Make it stale and query for stale devices
      // Make it stale (6 minutes ago)
      await query(
        `UPDATE devices
         SET last_seen_at = NOW() - INTERVAL '6 minutes'
         WHERE id = $1`,
        [testDeviceId]
      );

      // THEN: Device is found in stale devices list (5 minute threshold)
      const staleDevices = await heartbeatService.getStaleDevices(
        testUserId,
        5
      );
      expect(staleDevices).toContain(testDeviceId);
    });

    it("should not return recent devices", async () => {
      // GIVEN: Device with recent heartbeat
      // Send recent heartbeat
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });

      // WHEN: Query for stale devices (5 minute threshold)
      const staleDevices = await heartbeatService.getStaleDevices(
        testUserId,
        5
      );

      // THEN: Recent device is not in stale list
      expect(staleDevices).not.toContain(testDeviceId);
    });

    it("should return empty array for user with no stale devices", async () => {
      // GIVEN: User with no devices
      // WHEN: Query for stale devices
      const staleDevices = await heartbeatService.getStaleDevices(
        testUserId,
        5
      );

      // THEN: Empty array is returned
      expect(staleDevices).toEqual([]);
    });

    it("should respect custom threshold", async () => {
      // GIVEN: Device that is 3 minutes old
      // Send heartbeat
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });

      // Make it 3 minutes old
      await query(
        `UPDATE devices
         SET last_seen_at = NOW() - INTERVAL '3 minutes'
         WHERE id = $1`,
        [testDeviceId]
      );

      // WHEN: Query with different thresholds
      // Not stale with 5 minute threshold
      const staleWith5Min = await heartbeatService.getStaleDevices(
        testUserId,
        5
      );

      // THEN: Device stale status depends on threshold
      expect(staleWith5Min).not.toContain(testDeviceId);

      // Stale with 2 minute threshold
      const staleWith2Min = await heartbeatService.getStaleDevices(
        testUserId,
        2
      );
      expect(staleWith2Min).toContain(testDeviceId);
    });

    it("should only return active devices", async () => {
      // GIVEN: Stale device that is then marked offline
      // Send heartbeat and make it stale
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });
      await query(
        `UPDATE devices
         SET last_seen_at = NOW() - INTERVAL '10 minutes'
         WHERE id = $1`,
        [testDeviceId]
      );

      // WHEN: Mark device offline
      // Mark as offline (is_active = false)
      await heartbeatService.markDeviceOffline(testUserId, testDeviceId);

      // THEN: Device not returned in stale devices (already offline)
      // Should not appear in stale devices (already offline)
      const staleDevices = await heartbeatService.getStaleDevices(
        testUserId,
        5
      );
      expect(staleDevices).not.toContain(testDeviceId);
    });
  });

  describe("edge cases", () => {
    it("should handle rapid successive heartbeats", async () => {
      // GIVEN: Device ready to send heartbeats
      // WHEN: Multiple heartbeats sent in quick succession
      // Send multiple heartbeats in quick succession
      await Promise.all([
        heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
          device_id: testDeviceId,
        }),
        heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
          device_id: testDeviceId,
        }),
        heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
          device_id: testDeviceId,
        }),
      ]);

      // THEN: Device remains online without errors
      const status = await heartbeatService.getDeviceStatus(
        testUserId,
        testDeviceId
      );
      expect(status?.is_online).toBe(true);
    });

    it("should handle heartbeat for inactive device", async () => {
      // GIVEN: Inactive device
      // Deactivate device
      await deviceService.updateDevice(testUserId, testDeviceId, {
        is_active: false,
      });

      // WHEN: Device sends heartbeat
      // Send heartbeat (should reactivate)
      const result = await heartbeatService.updateHeartbeat(
        testUserId,
        testDeviceId,
        {
          device_id: testDeviceId,
        }
      );

      // THEN: Device is reactivated and status change detected
      expect(result.device.is_online).toBe(true);
      expect(result.statusChanged).toBe(true);
    });

    it("should handle concurrent heartbeats from different devices", async () => {
      // GIVEN: Multiple devices for user
      const device2 = await deviceService.registerDevice(testUserId, {
        device_type: "desktop",
        device_name: "Test MacBook",
        os: "macOS",
        os_version: "13.0",
        fingerprint: `test-fingerprint-desktop-${Date.now()}`,
      });

      // WHEN: Both devices send heartbeats concurrently
      // Track heartbeats concurrently
      await Promise.all([
        heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
          device_id: testDeviceId,
        }),
        heartbeatService.updateHeartbeat(testUserId, device2.id, {
          device_id: device2.id,
        }),
      ]);

      // THEN: All devices marked as online
      const allStatuses =
        await heartbeatService.getAllDeviceStatuses(testUserId);
      const onlineDevices = allStatuses.filter((d) => d.is_online);
      expect(onlineDevices.length).toBe(2);
    });

    it("should handle connection quality with poor metrics", async () => {
      // GIVEN: Device with poor connection metrics
      // WHEN: Heartbeat sent with poor metrics
      const result = await heartbeatService.updateHeartbeat(
        testUserId,
        testDeviceId,
        {
          device_id: testDeviceId,
          battery_level: 10,
          wifi_signal: -85,
          network_latency: 250,
        }
      );

      // THEN: Connection quality is rated as poor
      expect(result.device.connection_quality).toBe("poor");
    });

    it("should handle connection quality with excellent metrics", async () => {
      // GIVEN: Device with excellent connection metrics
      // WHEN: Heartbeat sent with excellent metrics
      const result = await heartbeatService.updateHeartbeat(
        testUserId,
        testDeviceId,
        {
          device_id: testDeviceId,
          battery_level: 95,
          wifi_signal: -40,
          network_latency: 20,
        }
      );

      // THEN: Connection quality is rated as excellent
      expect(result.device.connection_quality).toBe("excellent");
    });
  });

  describe("cleanup workflow", () => {
    it("should support stale device cleanup workflow", async () => {
      // GIVEN: Multiple devices with different states
      // Create multiple devices
      const device2 = await deviceService.registerDevice(testUserId, {
        device_type: "desktop",
        device_name: "Test MacBook",
        os: "macOS",
        os_version: "13.0",
        fingerprint: `test-fingerprint-desktop-${Date.now()}`,
      });

      // WHEN: Both devices send heartbeats
      // Both devices send heartbeat
      await heartbeatService.updateHeartbeat(testUserId, testDeviceId, {
        device_id: testDeviceId,
      });
      await heartbeatService.updateHeartbeat(testUserId, device2.id, {
        device_id: device2.id,
      });

      // Make device2 stale (10 minutes old)
      await query(
        `UPDATE devices
         SET last_seen_at = NOW() - INTERVAL '10 minutes'
         WHERE id = $1`,
        [device2.id]
      );

      // THEN: Stale devices identified and marked offline
      // Find stale devices
      const staleDevices = await heartbeatService.getStaleDevices(
        testUserId,
        5
      );
      expect(staleDevices).toContain(device2.id);
      expect(staleDevices).not.toContain(testDeviceId);

      // Mark stale devices as offline
      for (const deviceId of staleDevices) {
        await heartbeatService.markDeviceOffline(testUserId, deviceId);
      }

      // Verify device2 is now offline
      const device2Status = await heartbeatService.getDeviceStatus(
        testUserId,
        device2.id
      );
      expect(device2Status?.is_online).toBe(false);

      // device1 should still be online
      const device1Status = await heartbeatService.getDeviceStatus(
        testUserId,
        testDeviceId
      );
      expect(device1Status?.is_online).toBe(true);
    });
  });
});
