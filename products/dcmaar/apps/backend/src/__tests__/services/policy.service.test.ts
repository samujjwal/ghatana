/**
import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest';
 * Policy Service Tests
 *
 * Tests policy management service methods including:
 * - Policy creation (website, app, category, schedule)
 * - Policy listing with filters
 * - Policy updates and deletion
 * - Policy priority and ordering
 * - Device sync with policy hierarchy
 * - Bulk operations
 * - Policy statistics
 */

import * as policyService from "../../services/policy.service";
import * as deviceService from "../../services/device.service";
import { query } from "../../db";
import { randomEmail, randomString } from "../setup";
import { createTestUser } from '../fixtures/user.fixtures';

describe("PolicyService", () => {
  let testUserId: string;
  let testChildId: string;
  let testDeviceId: string;

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

    // Create test device
    const device = await deviceService.registerDevice(testUserId, {
      device_type: "mobile",
      device_name: "Test Device",
      device_fingerprint: randomString(16),
      child_id: testChildId,
    });
    testDeviceId = device.id;
  });

  describe("createPolicy", () => {
    it("should create a website blocking policy", async () => {
      // GIVEN: Valid website blocking policy data with domains to block
      const policyData = {
        name: "Block Social Media",
        policy_type: "website" as const,
        config: {
          blockedDomains: ["facebook.com", "twitter.com"],
          blockSubdomains: true,
        },
      };

      // WHEN: Policy creation is requested
      const policy = await policyService.createPolicy(testUserId, policyData);

      // THEN: Policy created with all specified properties and default values
      expect(policy).toBeDefined();
      expect(policy.name).toBe("Block Social Media");
      expect(policy.policy_type).toBe("website");
      expect(policy.user_id).toBe(testUserId);
      expect(policy.enabled).toBe(true);
      expect(policy.priority).toBe(0);
      expect(policy.config).toEqual(policyData.config);
    });

    it("should create an app blocking policy", async () => {
      // GIVEN: Valid app blocking policy data with specific app package names
      const policyData = {
        name: "Block Games",
        policy_type: "app" as const,
        config: {
          blockedApps: ["com.game.example", "com.game.another"],
        },
      };

      // WHEN: App blocking policy is created
      const policy = await policyService.createPolicy(testUserId, policyData);

      // THEN: App policy persists with correct type and configuration
      expect(policy.policy_type).toBe("app");
      expect(policy.config).toEqual(policyData.config);
    });

    it("should create a category blocking policy", async () => {
      // GIVEN: Valid category blocking policy for content categories
      const policyData = {
        name: "Block Adult Content",
        policy_type: "category" as const,
        config: {
          blockedCategories: ["adult", "gambling", "violence"],
        },
      };

      // WHEN: Category blocking policy is created
      const policy = await policyService.createPolicy(testUserId, policyData);

      // THEN: Category policy type is correctly stored
      expect(policy.policy_type).toBe("category");
    });

    it("should create a schedule policy", async () => {
      // GIVEN: Valid schedule policy with allowed time windows and weekdays
      const policyData = {
        name: "School Hours",
        policy_type: "schedule" as const,
        config: {
          allowedHours: { start: "09:00", end: "15:00" },
          weekdays: [1, 2, 3, 4, 5],
        },
      };

      // WHEN: Schedule policy is created
      const policy = await policyService.createPolicy(testUserId, policyData);

      // THEN: Schedule policy type is correctly stored
      expect(policy.policy_type).toBe("schedule");
    });

    it("should create policy with child_id", async () => {
      // GIVEN: Policy data targeting specific child
      const policyData = {
        child_id: testChildId,
        name: "Child-specific Policy",
        policy_type: "website" as const,
        config: { blockedDomains: ["example.com"] },
      };

      // WHEN: Child-specific policy is created
      const policy = await policyService.createPolicy(testUserId, policyData);

      // THEN: Policy is associated with correct child
      expect(policy.child_id).toBe(testChildId);
    });

    it("should create policy with device_id", async () => {
      // GIVEN: Policy data targeting specific device
      const policyData = {
        device_id: testDeviceId,
        name: "Device-specific Policy",
        policy_type: "website" as const,
        config: { blockedDomains: ["example.com"] },
      };

      // WHEN: Device-specific policy is created
      const policy = await policyService.createPolicy(testUserId, policyData);

      // THEN: Policy is associated with correct device
      expect(policy.device_id).toBe(testDeviceId);
    });

    it("should create policy with custom priority", async () => {
      // GIVEN: Policy data with explicit priority value
      const policyData = {
        name: "High Priority Policy",
        policy_type: "website" as const,
        priority: 50,
        config: { blockedDomains: ["example.com"] },
      };

      // WHEN: Policy with custom priority is created
      const policy = await policyService.createPolicy(testUserId, policyData);

      // THEN: Custom priority is preserved in policy
      expect(policy.priority).toBe(50);
    });

    it("should create disabled policy", async () => {
      // GIVEN: Policy data with enabled flag set to false
      const policyData = {
        name: "Disabled Policy",
        policy_type: "website" as const,
        enabled: false,
        config: { blockedDomains: ["example.com"] },
      };

      // WHEN: Disabled policy is created
      const policy = await policyService.createPolicy(testUserId, policyData);

      // THEN: Policy is created in disabled state
      expect(policy.enabled).toBe(false);
    });
  });

  describe("getPolicies", () => {
    beforeEach(async () => {
      // Create multiple test policies
      await policyService.createPolicy(testUserId, {
        name: "Website Policy 1",
        policy_type: "website",
        child_id: testChildId,
        config: { blockedDomains: ["test1.com"] },
      });

      await policyService.createPolicy(testUserId, {
        name: "App Policy 1",
        policy_type: "app",
        config: { blockedApps: ["app1"] },
      });

      await policyService.createPolicy(testUserId, {
        name: "Disabled Policy",
        policy_type: "website",
        enabled: false,
        config: { blockedDomains: ["disabled.com"] },
      });
    });

    it("should list all user policies", async () => {
      // GIVEN: User with multiple policies created in beforeEach
      // WHEN: All policies for user are retrieved
      const policies = await policyService.getPolicies(testUserId);

      // THEN: All policies returned in list
      expect(policies).toBeDefined();
      expect(policies.length).toBeGreaterThanOrEqual(3);
    });

    it("should filter policies by child_id", async () => {
      // GIVEN: Multiple policies, some child-specific
      // WHEN: Policies filtered by child_id
      const policies = await policyService.getPolicies(testUserId, {
        child_id: testChildId,
      });

      // THEN: Only policies for specified child are returned
      expect(policies.length).toBeGreaterThan(0);
      expect(policies.every((p) => p.child_id === testChildId)).toBe(true);
    });

    it("should filter policies by policy_type", async () => {
      // GIVEN: Multiple policies of different types (website, app, etc.)
      // WHEN: Policies filtered by type (website)
      const policies = await policyService.getPolicies(testUserId, {
        policy_type: "website",
      });

      // THEN: Only website-type policies are returned
      expect(policies.length).toBeGreaterThan(0);
      expect(policies.every((p) => p.policy_type === "website")).toBe(true);
    });

    it("should filter enabled policies", async () => {
      // GIVEN: Mix of enabled and disabled policies
      // WHEN: Policies filtered for enabled=true
      const policies = await policyService.getPolicies(testUserId, {
        enabled: true,
      });

      // THEN: Only enabled policies are returned
      expect(policies.every((p) => p.enabled === true)).toBe(true);
    });

    it("should filter disabled policies", async () => {
      // GIVEN: Mix of enabled and disabled policies
      // WHEN: Policies filtered for enabled=false
      const policies = await policyService.getPolicies(testUserId, {
        enabled: false,
      });

      // THEN: Only disabled policies are returned (at least the one from beforeEach)
      expect(policies.every((p) => p.enabled === false)).toBe(true);
      expect(policies.length).toBeGreaterThan(0);
    });

    it("should filter by device_id", async () => {
      // GIVEN: Device-specific policy created
      await policyService.createPolicy(testUserId, {
        device_id: testDeviceId,
        name: "Device Policy",
        policy_type: "website",
        config: { blockedDomains: ["device.com"] },
      });

      // WHEN: Policies filtered by device_id
      const policies = await policyService.getPolicies(testUserId, {
        device_id: testDeviceId,
      });

      // THEN: Only policies for specified device are returned
      expect(policies.length).toBeGreaterThan(0);
      expect(policies.every((p) => p.device_id === testDeviceId)).toBe(true);
    });

    it("should order policies by priority and creation date", async () => {
      // GIVEN: Policies with different priority levels
      await policyService.createPolicy(testUserId, {
        name: "Low Priority",
        policy_type: "website",
        priority: 10,
        config: { blockedDomains: ["low.com"] },
      });

      await policyService.createPolicy(testUserId, {
        name: "High Priority",
        policy_type: "website",
        priority: 90,
        config: { blockedDomains: ["high.com"] },
      });

      // WHEN: All policies retrieved without filter
      const policies = await policyService.getPolicies(testUserId);

      // THEN: Policies ordered by priority (higher first), then creation date
      // First policy should have higher priority than second
      const firstPolicy = policies[0];
      const secondPolicy = policies[1];
      expect(firstPolicy.priority).toBeGreaterThanOrEqual(
        secondPolicy.priority
      );
    });
  });

  describe("getPolicyById", () => {
    let testPolicyId: string;

    beforeEach(async () => {
      const policy = await policyService.createPolicy(testUserId, {
        name: "Test Policy",
        policy_type: "website",
        config: { blockedDomains: ["test.com"] },
      });
      testPolicyId = policy.id;
    });

    it("should get policy by ID", async () => {
      // GIVEN: Policy created and ID stored
      // WHEN: Policy is retrieved by ID
      const policy = await policyService.getPolicyById(
        testUserId,
        testPolicyId
      );

      // THEN: Correct policy is returned with all properties
      expect(policy).toBeDefined();
      expect(policy?.id).toBe(testPolicyId);
      expect(policy?.name).toBe("Test Policy");
    });

    it("should return null for non-existent policy", async () => {
      // GIVEN: Non-existent policy ID (all zeros UUID)
      // WHEN: Policy lookup is attempted with fake ID
      const policy = await policyService.getPolicyById(
        testUserId,
        "00000000-0000-0000-0000-000000000000"
      );

      // THEN: Null is returned (not found)
      expect(policy).toBeNull();
    });

    it("should reject access to another user policy", async () => {
      // GIVEN: Policy created by testUser, different user attempts access
      const otherUser = await createTestUser({ email: randomEmail() });

      // WHEN: Other user tries to retrieve testUser's policy
      const policy = await policyService.getPolicyById(
        otherUser.user.id,
        testPolicyId
      );

      // THEN: Access denied (null returned due to user isolation)
      expect(policy).toBeNull();
    });
  });

  describe("updatePolicy", () => {
    let testPolicyId: string;

    beforeEach(async () => {
      const policy = await policyService.createPolicy(testUserId, {
        name: "Original Policy",
        policy_type: "website",
        priority: 10,
        config: { blockedDomains: ["original.com"] },
      });
      testPolicyId = policy.id;
    });

    it("should update policy name", async () => {
      // GIVEN: Policy with original name
      // WHEN: Policy name is updated
      const updated = await policyService.updatePolicy(
        testUserId,
        testPolicyId,
        {
          name: "Updated Policy",
        }
      );

      // THEN: Name is changed while other fields preserved
      expect(updated?.name).toBe("Updated Policy");
    });

    it("should update policy enabled status", async () => {
      // GIVEN: Policy in enabled state
      // WHEN: Policy enabled flag is toggled to false
      const updated = await policyService.updatePolicy(
        testUserId,
        testPolicyId,
        {
          enabled: false,
        }
      );

      // THEN: Policy is now disabled
      expect(updated?.enabled).toBe(false);
    });

    it("should update policy priority", async () => {
      // GIVEN: Policy with priority 10
      // WHEN: Priority is updated to 75
      const updated = await policyService.updatePolicy(
        testUserId,
        testPolicyId,
        {
          priority: 75,
        }
      );

      // THEN: Priority is changed
      expect(updated?.priority).toBe(75);
    });

    it("should update policy config", async () => {
      // GIVEN: Policy with original domain list
      // WHEN: Config is updated with new domains
      const newConfig = {
        blockedDomains: ["new1.com", "new2.com"],
        blockSubdomains: false,
      };

      const updated = await policyService.updatePolicy(
        testUserId,
        testPolicyId,
        {
          config: newConfig,
        }
      );

      // THEN: Configuration is updated with new domains
      expect(updated?.config).toEqual(newConfig);
    });

    it("should update multiple fields", async () => {
      // GIVEN: Policy with original values
      // WHEN: Multiple fields updated together (name, enabled, priority)
      const updated = await policyService.updatePolicy(
        testUserId,
        testPolicyId,
        {
          name: "Multi Update",
          enabled: false,
          priority: 99,
        }
      );

      // THEN: All specified fields are updated
      expect(updated?.name).toBe("Multi Update");
      expect(updated?.enabled).toBe(false);
      expect(updated?.priority).toBe(99);
    });

    it("should reject empty updates", async () => {
      // GIVEN: Policy to update
      // WHEN: Update called with empty object (no fields to change)
      // THEN: Error thrown
      await expect(
        policyService.updatePolicy(testUserId, testPolicyId, {})
      ).rejects.toThrow("No fields to update");
    });

    it("should return null for non-existent policy", async () => {
      // GIVEN: Non-existent policy ID
      // WHEN: Update attempted on non-existent policy
      const updated = await policyService.updatePolicy(
        testUserId,
        "00000000-0000-0000-0000-000000000000",
        {
          name: "Test",
        }
      );

      // THEN: Null returned (not found)
      expect(updated).toBeNull();
    });

    it("should reject access to another user policy", async () => {
      // GIVEN: Policy created by testUser, different user attempts update
      const otherUser = await createTestUser({ email: randomEmail() });

      // WHEN: Other user tries to update testUser's policy
      const updated = await policyService.updatePolicy(
        otherUser.user.id,
        testPolicyId,
        {
          name: "Hacked",
        }
      );

      // THEN: Access denied (null returned due to user isolation)
      expect(updated).toBeNull();
    });
  });

  describe("deletePolicy", () => {
    let testPolicyId: string;

    beforeEach(async () => {
      const policy = await policyService.createPolicy(testUserId, {
        name: "To Delete",
        policy_type: "website",
        config: { blockedDomains: ["delete.com"] },
      });
      testPolicyId = policy.id;
    });

    it("should delete policy", async () => {
      // GIVEN: Policy exists in database
      // WHEN: Policy is deleted
      const deleted = await policyService.deletePolicy(
        testUserId,
        testPolicyId
      );

      // THEN: Delete operation returns true and policy no longer accessible
      expect(deleted).toBe(true);

      // Verify policy is deleted
      const policy = await policyService.getPolicyById(
        testUserId,
        testPolicyId
      );
      expect(policy).toBeNull();
    });

    it("should return false for non-existent policy", async () => {
      // GIVEN: Non-existent policy ID
      // WHEN: Delete attempted on non-existent policy
      const deleted = await policyService.deletePolicy(
        testUserId,
        "00000000-0000-0000-0000-000000000000"
      );

      // THEN: Delete returns false (nothing deleted)
      expect(deleted).toBe(false);
    });

    it("should reject access to another user policy", async () => {
      // GIVEN: Policy created by testUser, different user attempts deletion
      const otherUser = await createTestUser({ email: randomEmail() });

      // WHEN: Other user tries to delete testUser's policy
      const deleted = await policyService.deletePolicy(
        otherUser.user.id,
        testPolicyId
      );

      // THEN: Access denied (false returned due to user isolation)
      expect(deleted).toBe(false);
    });
  });

  describe("getPoliciesForDevice", () => {
    it("should get device-specific policies", async () => {
      // GIVEN: Device-specific policy created for test device
      // Create device-specific policy
      await policyService.createPolicy(testUserId, {
        device_id: testDeviceId,
        name: "Device Policy",
        policy_type: "website",
        config: { blockedDomains: ["device.com"] },
      });

      // WHEN: Policies for specific device are requested
      const policies = await policyService.getPoliciesForDevice(testDeviceId);

      // THEN: Device policy is included in results
      expect(policies).toBeDefined();
      expect(policies.length).toBeGreaterThan(0);
      expect(policies.some((p) => p.device_id === testDeviceId)).toBe(true);
    });

    it("should get child-specific policies for device", async () => {
      // GIVEN: Child-specific policy created (applies to all devices of that child)
      // Create child-specific policy
      await policyService.createPolicy(testUserId, {
        child_id: testChildId,
        name: "Child Policy",
        policy_type: "website",
        config: { blockedDomains: ["child.com"] },
      });

      // WHEN: Policies for device with this child are requested
      const policies = await policyService.getPoliciesForDevice(testDeviceId);

      // THEN: Child policy is included (but not device-specific)
      expect(
        policies.some((p) => p.child_id === testChildId && !p.device_id)
      ).toBe(true);
    });

    it("should get user-global policies for device", async () => {
      // GIVEN: Global policy created (applies to all devices for user)
      // Create global policy
      await policyService.createPolicy(testUserId, {
        name: "Global Policy",
        policy_type: "website",
        config: { blockedDomains: ["global.com"] },
      });

      // WHEN: Policies for any device are requested
      const policies = await policyService.getPoliciesForDevice(testDeviceId);

      // THEN: Global policy is included (no child or device scope)
      expect(policies.some((p) => !p.child_id && !p.device_id)).toBe(true);
    });

    it("should prioritize device > child > global policies", async () => {
      // GIVEN: Policies at all hierarchy levels (device, child, global)
      // Create policies at all levels
      await policyService.createPolicy(testUserId, {
        name: "Global",
        policy_type: "website",
        priority: 50,
        config: { blockedDomains: ["global.com"] },
      });

      await policyService.createPolicy(testUserId, {
        child_id: testChildId,
        name: "Child",
        policy_type: "website",
        priority: 50,
        config: { blockedDomains: ["child.com"] },
      });

      await policyService.createPolicy(testUserId, {
        device_id: testDeviceId,
        name: "Device",
        policy_type: "website",
        priority: 50,
        config: { blockedDomains: ["device.com"] },
      });

      // WHEN: All policies for device are retrieved
      const policies = await policyService.getPoliciesForDevice(testDeviceId);

      // THEN: Device policies appear first, then child, then global
      // Find each policy type
      const devicePolicy = policies.find((p) => p.device_id === testDeviceId);
      const childPolicy = policies.find(
        (p) => p.child_id === testChildId && !p.device_id
      );
      const globalPolicy = policies.find((p) => !p.child_id && !p.device_id);

      expect(devicePolicy).toBeDefined();
      expect(childPolicy).toBeDefined();
      expect(globalPolicy).toBeDefined();

      // Device policy should come first in evaluation order
      const deviceIndex = policies.indexOf(devicePolicy!);
      const childIndex = policies.indexOf(childPolicy!);
      const globalIndex = policies.indexOf(globalPolicy!);

      expect(deviceIndex).toBeLessThan(childIndex);
      expect(childIndex).toBeLessThan(globalIndex);
    });

    it("should only return enabled policies", async () => {
      // GIVEN: Disabled policy created for device
      // Create disabled policy
      await policyService.createPolicy(testUserId, {
        name: "Disabled",
        policy_type: "website",
        enabled: false,
        config: { blockedDomains: ["disabled.com"] },
      });

      // WHEN: Policies for device are retrieved
      const policies = await policyService.getPoliciesForDevice(testDeviceId);

      // THEN: Only enabled policies are returned (disabled policy excluded)
      expect(policies.every((p) => p.enabled === true)).toBe(true);
    });

    it("should throw error for non-existent device", async () => {
      // GIVEN: Non-existent device ID
      // WHEN: Policies for non-existent device are requested
      // THEN: Error is thrown
      await expect(
        policyService.getPoliciesForDevice(
          "00000000-0000-0000-0000-000000000000"
        )
      ).rejects.toThrow("Device not found");
    });
  });

  describe("togglePolicies", () => {
    let policyIds: string[];

    beforeEach(async () => {
      policyIds = [];

      for (let i = 0; i < 3; i++) {
        const policy = await policyService.createPolicy(testUserId, {
          name: `Policy ${i}`,
          policy_type: "website",
          config: { blockedDomains: [`test${i}.com`] },
        });
        policyIds.push(policy.id);
      }
    });

    it("should disable multiple policies", async () => {
      // GIVEN: Three enabled policies
      // WHEN: All policies toggled to disabled state
      const count = await policyService.togglePolicies(
        testUserId,
        policyIds,
        false
      );

      // THEN: All policies disabled, count returns 3
      expect(count).toBe(3);

      // Verify all policies are disabled
      const policies = await policyService.getPolicies(testUserId, {
        enabled: false,
      });

      expect(policies.length).toBeGreaterThanOrEqual(3);
    });

    it("should enable multiple policies", async () => {
      // GIVEN: Three policies (first disabled via previous test)
      // Disable first
      await policyService.togglePolicies(testUserId, policyIds, false);

      // WHEN: All policies re-enabled
      // Then enable
      const count = await policyService.togglePolicies(
        testUserId,
        policyIds,
        true
      );

      // THEN: All policies enabled, count returns 3
      expect(count).toBe(3);

      // Verify all policies are enabled
      for (const id of policyIds) {
        const policy = await policyService.getPolicyById(testUserId, id);
        expect(policy?.enabled).toBe(true);
      }
    });

    it("should return 0 for non-existent policies", async () => {
      // GIVEN: List of non-existent policy IDs
      // WHEN: Toggle attempted on non-existent policies
      const count = await policyService.togglePolicies(
        testUserId,
        [
          "00000000-0000-0000-0000-000000000001",
          "00000000-0000-0000-0000-000000000002",
        ],
        false
      );

      // THEN: No policies toggled, count returns 0
      expect(count).toBe(0);
    });

    it("should not affect other user policies", async () => {
      // GIVEN: Other user's policies created
      const otherUser = await createTestUser({ email: randomEmail() });

      // WHEN: Attempt to toggle testUser policies using otherUser ID
      const count = await policyService.togglePolicies(
        otherUser.user.id,
        policyIds,
        false
      );

      // THEN: No policies toggled (user isolation), count returns 0
      expect(count).toBe(0);

      // Original user's policies should still be enabled
      for (const id of policyIds) {
        const policy = await policyService.getPolicyById(testUserId, id);
        expect(policy?.enabled).toBe(true);
      }
    });
  });

  describe("getPolicyStats", () => {
    beforeEach(async () => {
      // Create policies of different types
      await policyService.createPolicy(testUserId, {
        name: "Website 1",
        policy_type: "website",
        config: { blockedDomains: ["test1.com"] },
      });

      await policyService.createPolicy(testUserId, {
        name: "Website 2",
        policy_type: "website",
        enabled: false,
        config: { blockedDomains: ["test2.com"] },
      });

      await policyService.createPolicy(testUserId, {
        name: "App 1",
        policy_type: "app",
        config: { blockedApps: ["app1"] },
      });

      await policyService.createPolicy(testUserId, {
        name: "Schedule 1",
        policy_type: "schedule",
        config: { allowedHours: { start: "09:00", end: "17:00" } },
      });
    });

    it("should calculate total policies", async () => {
      // GIVEN: User with multiple policies created in beforeEach
      // WHEN: Policy statistics are calculated
      const stats = await policyService.getPolicyStats(testUserId);

      // THEN: Total count includes all policies (at least 4)
      expect(stats.total).toBeGreaterThanOrEqual(4);
    });

    it("should calculate enabled policies", async () => {
      // GIVEN: Mix of enabled and disabled policies
      // WHEN: Policy statistics are calculated
      const stats = await policyService.getPolicyStats(testUserId);

      // THEN: Enabled count reflects only active policies (at least 3)
      expect(stats.enabled).toBeGreaterThanOrEqual(3);
    });

    it("should calculate disabled policies", async () => {
      // GIVEN: At least one disabled policy created
      // WHEN: Policy statistics are calculated
      const stats = await policyService.getPolicyStats(testUserId);

      // THEN: Disabled count reflects inactive policies (at least 1)
      expect(stats.disabled).toBeGreaterThanOrEqual(1);
    });

    it("should group policies by type", async () => {
      // GIVEN: Policies of different types (website, app, schedule)
      // WHEN: Policy statistics are calculated
      const stats = await policyService.getPolicyStats(testUserId);

      // THEN: Statistics grouped by type with correct counts
      expect(stats.by_type).toBeDefined();
      expect(stats.by_type.website).toBeGreaterThanOrEqual(2);
      expect(stats.by_type.app).toBeGreaterThanOrEqual(1);
      expect(stats.by_type.schedule).toBeGreaterThanOrEqual(1);
    });

    it("should return zeros for user with no policies", async () => {
      // GIVEN: New user with no policies
      const newUser = await createTestUser({ email: randomEmail() });

      // WHEN: Policy statistics are calculated for empty user
      const stats = await policyService.getPolicyStats(newUser.user.id);

      // THEN: All counts are zero, by_type is empty
      expect(stats.total).toBe(0);
      expect(stats.enabled).toBe(0);
      expect(stats.disabled).toBe(0);
      expect(Object.keys(stats.by_type).length).toBe(0);
    });
  });
});
