/**
import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
 * Device Service Tests
 *
 * Tests device management service methods including:
 * - Device registration
 * - Device listing with filters
 * - Device pairing/unpairing
 * - Pairing code generation
 * - Device updates and deletion
 * - Device statistics
 */

import * as deviceService from "../../services/device.service";
import { query } from "../../db";
import { randomEmail, randomString } from "../setup";
import { createTestUser } from '../fixtures/user.fixtures';

describe("DeviceService", () => {
  let testUserId: string;
  let testChildId: string;

  beforeEach(async () => {
    // Create test user
    const user = await createTestUser({ email: randomEmail() });
    testUserId = user.user.id;

    // Create test child
    const child = await query(
      "INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING id",
      [testUserId, "Test Child", 10]
    );
    testChildId = child[0].id;
  });

  describe("registerDevice", () => {
    it("should register a mobile device", async () => {
      // GIVEN: Valid mobile device data
      const deviceData = {
        device_type: "mobile" as const,
        device_name: "iPhone 13",
        device_fingerprint: randomString(16),
      };

      // WHEN: Device is registered
      const device = await deviceService.registerDevice(testUserId, deviceData);

      // THEN: Device is created with correct properties
      expect(device).toBeDefined();
      expect(device.device_type).toBe("mobile");
      expect(device.device_name).toBe("iPhone 13");
      expect(device.user_id).toBe(testUserId);
      expect(device.last_seen_at).toBeDefined();
    });

    it("should register a desktop device", async () => {
      // GIVEN: Valid desktop device data
      const deviceData = {
        device_type: "desktop" as const,
        device_name: "Windows Desktop",
        device_fingerprint: randomString(16),
      };

      // WHEN: Device is registered
      const device = await deviceService.registerDevice(testUserId, deviceData);

      // THEN: Device is created as desktop type
      expect(device.device_type).toBe("desktop");
      expect(device.device_name).toBe("Windows Desktop");
    });

    it("should register a browser extension", async () => {
      // GIVEN: Valid browser extension device data
      const deviceData = {
        device_type: "extension" as const,
        device_name: "Chrome Browser",
        device_fingerprint: randomString(16),
      };

      // WHEN: Device is registered
      const device = await deviceService.registerDevice(testUserId, deviceData);

      // THEN: Device is created as extension type
      expect(device.device_type).toBe("extension");
    });

    it("should register device without fingerprint", async () => {
      // GIVEN: Device data without fingerprint
      const deviceData = {
        device_type: "mobile" as const,
        device_name: "Test Device",
      };

      // WHEN: Device is registered
      const device = await deviceService.registerDevice(testUserId, deviceData);

      // THEN: Device is created with null fingerprint
      expect(device).toBeDefined();
      expect(device.device_fingerprint).toBeNull();
    });

    it("should register device with child_id", async () => {
      // GIVEN: Device data with child assignment
      const deviceData = {
        child_id: testChildId,
        device_type: "mobile" as const,
        device_name: "Test Device",
      };

      // WHEN: Device is registered
      const device = await deviceService.registerDevice(testUserId, deviceData);

      // THEN: Device is paired with child
      expect(device.child_id).toBe(testChildId);
    });
  });

  describe("getDevices", () => {
    beforeEach(async () => {
      // Create multiple test devices
      await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Mobile 1",
        child_id: testChildId,
      });

      await deviceService.registerDevice(testUserId, {
        device_type: "desktop",
        device_name: "Desktop 1",
      });

      await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Mobile 2",
      });
    });

    it("should get all devices for user", async () => {
      // GIVEN: User with 3 registered devices

      // WHEN: All devices are retrieved
      const devices = await deviceService.getDevices(testUserId);

      // THEN: All 3 devices are returned
      expect(devices.length).toBe(3);
    });

    it("should filter devices by type", async () => {
      // GIVEN: User with mobile and desktop devices

      // WHEN: Devices filtered by mobile type
      const devices = await deviceService.getDevices(testUserId, {
        device_type: "mobile",
      });

      // THEN: Only mobile devices are returned
      expect(devices.length).toBe(2);
      expect(devices.every((d) => d.device_type === "mobile")).toBe(true);
    });

    it("should filter devices by child_id", async () => {
      // GIVEN: Devices paired and unpaired with child

      // WHEN: Devices filtered by child ID
      const devices = await deviceService.getDevices(testUserId, {
        child_id: testChildId,
      });

      // THEN: Only devices paired with child are returned
      expect(devices.length).toBe(1);
      expect(devices[0].child_id).toBe(testChildId);
    });

    it("should filter devices by active status", async () => {
      // GIVEN: All devices are active

      // WHEN: Devices filtered by active status
      const devices = await deviceService.getDevices(testUserId, {
        is_active: true,
      });

      // THEN: All active devices are returned
      expect(devices.length).toBe(3);
      expect(devices.every((d) => d.is_active === true)).toBe(true);
    });

    it("should combine multiple filters", async () => {
      // GIVEN: Devices with various types and child assignments

      // WHEN: Devices filtered by both type and child ID
      const devices = await deviceService.getDevices(testUserId, {
        device_type: "mobile",
        child_id: testChildId,
      });

      // THEN: Only devices matching all filters are returned
      expect(devices.length).toBe(1);
      expect(devices[0].device_type).toBe("mobile");
      expect(devices[0].child_id).toBe(testChildId);
    });

    it("should order by last_seen_at DESC", async () => {
      // GIVEN: Multiple devices with different last_seen timestamps

      // WHEN: Devices are retrieved
      const devices = await deviceService.getDevices(testUserId);

      // THEN: Devices are ordered by most recently seen first
      for (let i = 0; i < devices.length - 1; i++) {
        expect(
          new Date(devices[i].last_seen_at!).getTime()
        ).toBeGreaterThanOrEqual(
          new Date(devices[i + 1].last_seen_at!).getTime()
        );
      }
    });
  });

  describe("getDeviceById", () => {
    it("should get device by ID", async () => {
      // GIVEN: Registered device
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      // WHEN: Device is retrieved by ID
      const device = await deviceService.getDeviceById(testUserId, created.id);

      // THEN: Correct device is returned
      expect(device).toBeDefined();
      expect(device?.id).toBe(created.id);
      expect(device?.device_name).toBe("Test Device");
    });

    it("should return null for non-existent device", async () => {
      // GIVEN: Non-existent device ID

      // WHEN: Device lookup attempted
      const device = await deviceService.getDeviceById(
        testUserId,
        "00000000-0000-0000-0000-000000000000"
      );

      // THEN: Null is returned
      expect(device).toBeNull();
    });

    it("should not return device from different user", async () => {
      // GIVEN: Device owned by test user
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      // Create another user
      const otherUser = await createTestUser({ email: randomEmail() });

      // WHEN: Different user tries to access device
      const device = await deviceService.getDeviceById(
        otherUser.user.id,
        created.id
      );

      // THEN: Device is not returned (isolation enforced)
      expect(device).toBeNull();
    });
  });

  describe("updateDevice", () => {
    it("should update device name", async () => {
      // GIVEN: Device with old name
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Old Name",
      });

      // WHEN: Device name is updated
      const updated = await deviceService.updateDevice(testUserId, created.id, {
        device_name: "New Name",
      });

      // THEN: Device name is changed
      expect(updated).toBeDefined();
      expect(updated?.device_name).toBe("New Name");
    });

    it("should update child_id", async () => {
      // GIVEN: Unpaired device
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      // WHEN: Device is paired with child
      const updated = await deviceService.updateDevice(testUserId, created.id, {
        child_id: testChildId,
      });

      // THEN: Device is associated with child
      expect(updated?.child_id).toBe(testChildId);
    });

    it("should update is_active status", async () => {
      // GIVEN: Active device
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      // WHEN: Device is deactivated
      const updated = await deviceService.updateDevice(testUserId, created.id, {
        is_active: false,
      });

      // THEN: Device is marked inactive
      expect(updated?.is_active).toBe(false);
    });

    it("should update multiple fields", async () => {
      // GIVEN: Device with original values
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Old Name",
      });

      // WHEN: Multiple fields are updated
      const updated = await deviceService.updateDevice(testUserId, created.id, {
        device_name: "New Name",
        child_id: testChildId,
        is_active: false,
      });

      // THEN: All fields are updated
      expect(updated?.device_name).toBe("New Name");
      expect(updated?.child_id).toBe(testChildId);
      expect(updated?.is_active).toBe(false);
    });

    it("should reject empty updates", async () => {
      // GIVEN: Registered device
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      // WHEN: Empty update attempted
      // THEN: Error is thrown
      await expect(
        deviceService.updateDevice(testUserId, created.id, {})
      ).rejects.toThrow("No fields to update");
    });

    it("should return null for non-existent device", async () => {
      // GIVEN: Non-existent device ID

      // WHEN: Update attempted on non-existent device
      const updated = await deviceService.updateDevice(
        testUserId,
        "00000000-0000-0000-0000-000000000000",
        { device_name: "New Name" }
      );

      // THEN: Null is returned
      expect(updated).toBeNull();
    });

    it("should set child_id to null", async () => {
      // GIVEN: Device paired with child
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
        child_id: testChildId,
      });

      // WHEN: Device is unpaired
      const updated = await deviceService.updateDevice(testUserId, created.id, {
        child_id: null,
      });

      // THEN: Child association is removed
      expect(updated?.child_id).toBeNull();
    });
  });

  describe("deleteDevice", () => {
    it("should soft delete device", async () => {
      // GIVEN: Active device
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      // WHEN: Device is deleted
      const deleted = await deviceService.deleteDevice(testUserId, created.id);

      // THEN: Device is marked as inactive
      expect(deleted).toBe(true);

      // Verify device is marked as inactive
      const device = await deviceService.getDeviceById(testUserId, created.id);
      expect(device?.is_active).toBe(false);
    });

    it("should return false for non-existent device", async () => {
      // GIVEN: Non-existent device ID
      const nonExistentId = "00000000-0000-0000-0000-000000000000";

      // WHEN: Delete is attempted
      const deleted = await deviceService.deleteDevice(
        testUserId,
        nonExistentId
      );

      // THEN: False is returned
      expect(deleted).toBe(false);
    });
  });

  describe("updateDeviceLastSeen", () => {
    it("should update last_seen_at timestamp", async () => {
      // GIVEN: Device with initial last_seen_at timestamp
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      const beforeUpdate = created.last_seen_at;

      // Wait a bit
      await new Promise((resolve) => setTimeout(resolve, 100));

      // WHEN: Last seen timestamp is updated
      await deviceService.updateDeviceLastSeen(created.id);

      // THEN: Timestamp is newer than before
      const updated = await deviceService.getDeviceById(testUserId, created.id);
      expect(updated?.last_seen_at).not.toEqual(beforeUpdate);
    });
  });

  describe("pairDeviceWithChild", () => {
    it("should pair device with child", async () => {
      // GIVEN: Unpaired device and existing child
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      // WHEN: Device is paired with child
      const paired = await deviceService.pairDeviceWithChild(
        testUserId,
        created.id,
        testChildId
      );

      // THEN: Device is associated with child
      expect(paired).toBeDefined();
      expect(paired?.child_id).toBe(testChildId);
    });

    it("should reject pairing with non-existent child", async () => {
      // GIVEN: Device and non-existent child ID
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      // WHEN: Pairing is attempted with non-existent child
      // THEN: Error is thrown
      await expect(
        deviceService.pairDeviceWithChild(
          testUserId,
          created.id,
          "00000000-0000-0000-0000-000000000000"
        )
      ).rejects.toThrow("Child not found");
    });

    it("should reject pairing with child from different user", async () => {
      // GIVEN: Device and child owned by different user
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      // Create another user and child
      const otherUser = await createTestUser({ email: randomEmail() });

      const otherChild = await query(
        "INSERT INTO children (user_id, name) VALUES ($1, $2) RETURNING id",
        [otherUser.user.id, "Other Child"]
      );

      // WHEN: Pairing is attempted with child from different user
      // THEN: Error is thrown (user isolation enforced)
      await expect(
        deviceService.pairDeviceWithChild(
          testUserId,
          created.id,
          otherChild[0].id
        )
      ).rejects.toThrow("Child not found");
    });

    it("should return null for non-existent device", async () => {
      // GIVEN: Non-existent device ID
      const nonExistentId = "00000000-0000-0000-0000-000000000000";

      // WHEN: Pairing is attempted with non-existent device
      const paired = await deviceService.pairDeviceWithChild(
        testUserId,
        nonExistentId,
        testChildId
      );

      // THEN: Null is returned
      expect(paired).toBeNull();
    });
  });

  describe("unpairDevice", () => {
    it("should unpair device from child", async () => {
      // GIVEN: Device paired with child
      const created = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
        child_id: testChildId,
      });

      // WHEN: Device is unpaired
      const unpaired = await deviceService.unpairDevice(testUserId, created.id);

      // THEN: Child association is removed
      expect(unpaired).toBeDefined();
      expect(unpaired?.child_id).toBeNull();
    });

    it("should return null for non-existent device", async () => {
      // GIVEN: Non-existent device ID
      const nonExistentId = "00000000-0000-0000-0000-000000000000";

      // WHEN: Unpair is attempted
      const unpaired = await deviceService.unpairDevice(
        testUserId,
        nonExistentId
      );

      // THEN: Null is returned
      expect(unpaired).toBeNull();
    });
  });

  describe("getDeviceStats", () => {
    beforeEach(async () => {
      // GIVEN: Various devices with different states
      // Create various devices
      await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Mobile 1",
        child_id: testChildId,
      });

      await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Mobile 2",
        child_id: testChildId,
      });

      await deviceService.registerDevice(testUserId, {
        device_type: "desktop",
        device_name: "Desktop 1",
      });

      const inactive = await deviceService.registerDevice(testUserId, {
        device_type: "extension",
        device_name: "Extension 1",
      });

      // Soft delete one device
      await deviceService.deleteDevice(testUserId, inactive.id);
    });

    it("should return correct total count", async () => {
      // WHEN: Device stats are retrieved
      const stats = await deviceService.getDeviceStats(testUserId);

      // THEN: Total count includes all devices (active and inactive)
      expect(stats.total).toBe(4);
    });

    it("should return correct active count", async () => {
      // WHEN: Device stats are retrieved
      const stats = await deviceService.getDeviceStats(testUserId);

      // THEN: Active count excludes soft-deleted devices
      expect(stats.active).toBe(3); // One is inactive
    });

    it("should return count by device type", async () => {
      // WHEN: Device stats are retrieved
      const stats = await deviceService.getDeviceStats(testUserId);

      // THEN: Counts are grouped by device type
      expect(stats.by_type.mobile).toBe(2);
      expect(stats.by_type.desktop).toBe(1);
      expect(stats.by_type.extension).toBe(1);
    });

    it("should return paired and unpaired counts", async () => {
      // WHEN: Device stats are retrieved
      const stats = await deviceService.getDeviceStats(testUserId);

      // THEN: Counts show pairing status
      expect(stats.paired).toBe(2);
      expect(stats.unpaired).toBe(2);
    });
  });

  describe("generateDevicePairingCode", () => {
    it("should generate pairing code", async () => {
      // GIVEN: User and child exist
      // WHEN: Pairing code is generated
      const result = await deviceService.generateDevicePairingCode(
        testUserId,
        testChildId
      );

      // THEN: Code is generated with proper format and expiration
      expect(result.code).toBeDefined();
      expect(result.code.length).toBe(6);
      expect(result.expiresAt).toBeInstanceOf(Date);
      expect(result.expiresAt.getTime()).toBeGreaterThan(Date.now());
    });

    it("should store pairing code in database", async () => {
      // GIVEN: User and child exist
      // WHEN: Pairing code is generated
      const result = await deviceService.generateDevicePairingCode(
        testUserId,
        testChildId
      );

      // THEN: Code is stored in database with correct associations
      const stored = await query(
        "SELECT * FROM device_pairing_requests WHERE pairing_code = $1",
        [result.code]
      );

      expect(stored.length).toBe(1);
      expect(stored[0].child_id).toBe(testChildId);
    });

    it("should replace existing pairing code for same child", async () => {
      // GIVEN: Existing pairing code for child
      const first = await deviceService.generateDevicePairingCode(
        testUserId,
        testChildId
      );

      // WHEN: New pairing code is generated for same child
      const second = await deviceService.generateDevicePairingCode(
        testUserId,
        testChildId
      );

      // THEN: New code replaces old code
      expect(second.code).not.toBe(first.code);

      // First code should no longer exist
      const stored = await query(
        "SELECT * FROM device_pairing_requests WHERE pairing_code = $1",
        [first.code]
      );

      expect(stored.length).toBe(0);
    });
  });

  describe("pairDeviceWithCode", () => {
    it("should pair device with valid code", async () => {
      // GIVEN: Device and valid pairing code
      const device = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      const { code } = await deviceService.generateDevicePairingCode(
        testUserId,
        testChildId
      );

      // WHEN: Device is paired using code
      const result = await deviceService.pairDeviceWithCode(device.id, code);

      // THEN: Device is successfully paired with child
      expect(result.success).toBe(true);
      expect(result.device).toBeDefined();
      expect(result.device?.child_id).toBe(testChildId);
      expect(result.device?.is_paired).toBe(true);
    });

    it("should reject invalid pairing code", async () => {
      // GIVEN: Device and invalid pairing code
      const device = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      // WHEN: Pairing is attempted with invalid code
      const result = await deviceService.pairDeviceWithCode(
        device.id,
        "INVALID"
      );

      // THEN: Pairing is rejected with error
      expect(result.success).toBe(false);
      expect(result.error).toContain("Invalid");
    });

    it("should reject non-existent device", async () => {
      // GIVEN: Valid pairing code but non-existent device
      const { code } = await deviceService.generateDevicePairingCode(
        testUserId,
        testChildId
      );

      // WHEN: Pairing is attempted with non-existent device
      const result = await deviceService.pairDeviceWithCode(
        "00000000-0000-0000-0000-000000000000",
        code
      );

      // THEN: Pairing is rejected with error
      expect(result.success).toBe(false);
      expect(result.error).toContain("Device not found");
    });

    it("should delete pairing code after use", async () => {
      // GIVEN: Device and valid pairing code
      const device = await deviceService.registerDevice(testUserId, {
        device_type: "mobile",
        device_name: "Test Device",
      });

      const { code } = await deviceService.generateDevicePairingCode(
        testUserId,
        testChildId
      );

      // WHEN: Device is successfully paired
      await deviceService.pairDeviceWithCode(device.id, code);

      // THEN: Pairing code is deleted from database
      // Code should be deleted
      const stored = await query(
        "SELECT * FROM device_pairing_requests WHERE pairing_code = $1",
        [code]
      );

      expect(stored.length).toBe(0);
    });
  });

  describe("getActivePairingCode", () => {
    it("should return active pairing code", async () => {
      // GIVEN: Generated pairing code for child
      const { code, expiresAt } = await deviceService.generateDevicePairingCode(
        testUserId,
        testChildId
      );

      // WHEN: Active pairing code is retrieved
      const active = await deviceService.getActivePairingCode(
        testUserId,
        testChildId
      );

      // THEN: Code and expiration match generated values
      expect(active).toBeDefined();
      expect(active?.code).toBe(code);
      expect(active?.expiresAt).toEqual(expiresAt);
    });

    it("should return null if no active code", async () => {
      // GIVEN: No pairing code generated for child
      // WHEN: Active pairing code is retrieved
      const active = await deviceService.getActivePairingCode(
        testUserId,
        testChildId
      );

      // THEN: Null is returned
      expect(active).toBeNull();
    });

    it("should not return code from different child", async () => {
      // GIVEN: Pairing code for one child
      await deviceService.generateDevicePairingCode(testUserId, testChildId);

      // Create another child
      const otherChild = await query(
        "INSERT INTO children (user_id, name) VALUES ($1, $2) RETURNING id",
        [testUserId, "Other Child"]
      );

      // WHEN: Code is retrieved for different child
      const active = await deviceService.getActivePairingCode(
        testUserId,
        otherChild[0].id
      );

      // THEN: Null is returned (child isolation enforced)
      expect(active).toBeNull();
    });
  });
});
