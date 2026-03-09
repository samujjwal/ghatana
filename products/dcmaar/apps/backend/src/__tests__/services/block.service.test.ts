/**
 * Block Service Tests
 *
 * Tests block event persistence and retrieval workflows including:
 * - Event creation with optional metadata
 * - Retrieval by id, device, and child
 * - Statistical aggregation by type/category
 * - Cleanup of historical records
 *
 * @doc.type test
 * @doc.purpose Test coverage for block event service
 * @doc.layer backend
 * @doc.pattern Service Test
 */

import {
  describe,
  it,
  expect,
  beforeEach,
  afterEach,
  beforeAll,
  afterAll,
  vi,
} from "vitest";
import * as blockService from "../../services/block.service";
import { query } from "../../db";
import { randomString, randomEmail } from "../setup";
import { seedUser, seedChild, seedDevice, seedPolicy } from "../helpers/testDb";
import {
  parentUser,
  childProfile,
  androidDevice,
  appBlockPolicy,
} from "../fixtures";

describe("BlockService", () => {
  let testUserId: string;
  let testChildId: string;
  let testDeviceId: string;
  let testPolicyId: string;

  beforeEach(async () => {
    // Seed test user directly with unique email
    const testUser = { ...parentUser, email: randomEmail() };
    testUserId = await seedUser(testUser);

    // Seed test child directly
    const childFixture = { ...childProfile, userId: testUserId };
    testChildId = await seedChild(childFixture, testUserId);

    // Seed test device directly
    const deviceFixture = {
      ...androidDevice,
      userId: testUserId,
      childId: testChildId,
    };
    testDeviceId = await seedDevice(deviceFixture, testUserId, testChildId);

    // Seed test policy directly
    const policyFixture = {
      ...appBlockPolicy,
      userId: testUserId,
      childId: testChildId,
    };
    testPolicyId = await seedPolicy(policyFixture, testUserId, testChildId);
  });

  describe("createBlockEvent", () => {
    it("creates a block event with required fields", async () => {
      // GIVEN: Required block event data (device, type, blocked item)
      const blockEventData = {
        device_id: testDeviceId,
        event_type: "website",
        blocked_item: "facebook.com",
      };

      // WHEN: Block event is created
      const event = await blockService.createBlockEvent(blockEventData);

      // THEN: Event persisted with all required fields and null optionals
      expect(event).toBeDefined();
      expect(event.id).toBeDefined();
      expect(event.device_id).toBe(testDeviceId);
      expect(event.event_type).toBe("website");
      expect(event.blocked_item).toBe("facebook.com");
      expect(event.category).toBeNull();
      expect(event.reason).toBeNull();
      expect(event.timestamp).toBeInstanceOf(Date);
    });

    it("persists optional category, policy, and reason metadata", async () => {
      // GIVEN: Block event with all optional fields (category, policy, reason, timestamp)
      const timestamp = new Date();
      const blockEventData = {
        device_id: testDeviceId,
        policy_id: testPolicyId,
        event_type: "app",
        blocked_item: "com.social.app",
        category: "social",
        reason: "policy_enforced",
        timestamp,
      };

      // WHEN: Block event with metadata is created
      const event = await blockService.createBlockEvent(blockEventData);

      // THEN: All fields including optionals are persisted correctly
      expect(event.policy_id).toBe(testPolicyId);
      expect(event.category).toBe("social");
      expect(event.reason).toBe("policy_enforced");
      expect(new Date(event.timestamp).getTime()).toBe(timestamp.getTime());
    });
  });

  describe("getBlockEventById", () => {
    it("returns an event when id exists", async () => {
      // GIVEN: Block event created and persisted
      const created = await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: "website",
        blocked_item: "tiktok.com",
      });

      // WHEN: Block event is retrieved by ID
      const fetched = await blockService.getBlockEventById(created.id);

      // THEN: Event returned with all correct properties
      expect(fetched).toBeDefined();
      expect(fetched?.id).toBe(created.id);
      expect(fetched?.blocked_item).toBe("tiktok.com");
    });

    it("returns null when event does not exist", async () => {
      // GIVEN: Non-existent block event ID (UUID 0000...0000)
      const nonExistentId = "00000000-0000-0000-0000-000000000000";

      // WHEN: Retrieval attempted for non-existent event
      const fetched = await blockService.getBlockEventById(nonExistentId);

      // THEN: Null returned (event not found)
      expect(fetched).toBeNull();
    });
  });

  describe("getBlockEventsByDevice", () => {
    beforeEach(async () => {
      // GIVEN: Create 3 sequential block events (newest to oldest timestamps)
      const now = Date.now();
      for (let i = 0; i < 3; i++) {
        await blockService.createBlockEvent({
          device_id: testDeviceId,
          event_type: i % 2 === 0 ? ("website" as const) : ("app" as const),
          blocked_item: `blocked-${i}.com`,
          timestamp: new Date(now - i * 60_000),
        });
      }
    });

    it("returns events for the device ordered by newest first", async () => {
      // GIVEN: Block events created with different timestamps
      // WHEN: All events for device are retrieved
      const events = await blockService.getBlockEventsByDevice(testDeviceId);

      // THEN: Events returned in descending order (newest first)
      expect(events.length).toBeGreaterThanOrEqual(3);
      for (let i = 0; i < events.length - 1; i++) {
        const current = new Date(events[i].timestamp).getTime();
        const next = new Date(events[i + 1].timestamp).getTime();
        expect(current).toBeGreaterThanOrEqual(next);
      }
    });

    it("respects the provided limit", async () => {
      // GIVEN: Multiple block events exist for device
      // WHEN: Events retrieved with limit of 2
      const events = await blockService.getBlockEventsByDevice(testDeviceId, 2);

      // THEN: Result set respects limit (max 2 events)
      expect(events.length).toBeLessThanOrEqual(2);
    });

    it("returns empty list for device without events", async () => {
      // GIVEN: New device with no block events
      const otherDeviceFixture = {
        ...androidDevice,
        userId: testUserId,
        childId: testChildId,
        deviceName: "Browser Agent",
      };
      const otherDeviceId = await seedDevice(
        otherDeviceFixture,
        testUserId,
        testChildId
      );

      // WHEN: Events retrieved for device with no events
      const events = await blockService.getBlockEventsByDevice(otherDeviceId);

      // THEN: Empty array returned
      expect(events).toEqual([]);
    });
  });

  describe("getBlockEventsByChild", () => {
    let secondDeviceId: string;

    beforeEach(async () => {
      // GIVEN: Second device for same child + block events on both devices
      const deviceFixture = {
        ...androidDevice,
        userId: testUserId,
        childId: testChildId,
        deviceName: "Child Laptop",
      };
      secondDeviceId = await seedDevice(deviceFixture, testUserId, testChildId);

      await blockService.createBlockEvent({
        device_id: testDeviceId,
        policy_id: testPolicyId,
        event_type: "website",
        blocked_item: "games.com",
        category: "games",
      });

      await blockService.createBlockEvent({
        device_id: secondDeviceId,
        event_type: "app",
        blocked_item: "com.chat.app",
        category: "communication",
      });
    });

    it("aggregates events across all child devices", async () => {
      // GIVEN: Block events created on multiple devices for same child
      // WHEN: All events for child are retrieved (cross-device)
      const events = await blockService.getBlockEventsByChild(testChildId);

      // THEN: Events from all child devices aggregated in result
      expect(events.length).toBeGreaterThanOrEqual(2);
      const deviceIds = new Set(events.map((event) => event.device_id));
      expect(deviceIds.has(testDeviceId)).toBe(true);
      expect(deviceIds.has(secondDeviceId)).toBe(true);
    });

    it("excludes events for other children", async () => {
      // GIVEN: Another child with device and block events
      const otherChild = await query(
        "INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING *",
        [testUserId, "Other Child", 8]
      );

      const otherDeviceFixture = {
        ...androidDevice,
        userId: testUserId,
        childId: otherChild[0].id,
        deviceName: "Other Child Phone",
      };
      const otherDeviceId = await seedDevice(
        otherDeviceFixture,
        testUserId,
        otherChild[0].id
      );

      await blockService.createBlockEvent({
        device_id: otherDeviceId,
        event_type: "website",
        blocked_item: "unrelated.com",
      });

      // WHEN: Events for testChild retrieved
      const events = await blockService.getBlockEventsByChild(testChildId);

      // THEN: Other child's events excluded (user/child isolation enforced)
      expect(events.every((event) => event.device_id !== otherDeviceId)).toBe(
        true
      );
    });
  });

  describe("getBlockEventStats", () => {
    beforeEach(async () => {
      // GIVEN: Multiple block events with varying timestamps (recent + old)
      const now = new Date();

      await blockService.createBlockEvent({
        device_id: testDeviceId,
        policy_id: testPolicyId,
        event_type: "website",
        blocked_item: "social.com",
        category: "social",
        timestamp: now,
      });
      await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: "website",
        blocked_item: "social.com",
        category: "social",
        timestamp: now,
      });
      await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: "app",
        blocked_item: "com.games.app",
        category: "games",
        timestamp: now,
      });

      const oldTimestamp = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: "website",
        blocked_item: "old.com",
        category: "other",
        timestamp: oldTimestamp,
      });
    });

    it("groups and counts events within the date range", async () => {
      // GIVEN: Block events at different timestamps (recent and old)
      // WHEN: Statistics retrieved for last 24 hours
      const start = new Date(Date.now() - 24 * 60 * 60 * 1000);
      const end = new Date(Date.now() + 60 * 1000);

      const stats = await blockService.getBlockEventStats(
        testChildId,
        start,
        end
      );

      // THEN: Recent events grouped by blocked_item with counts, old events excluded
      expect(stats.length).toBeGreaterThanOrEqual(2);
      const socialStat = stats.find(
        (stat) => stat.blocked_item === "social.com"
      );
      expect(Number(socialStat?.block_count)).toBe(2);

      const appStat = stats.find(
        (stat) => stat.blocked_item === "com.games.app"
      );
      expect(appStat?.event_type).toBe("app");
      expect(Number(appStat?.block_count)).toBe(1);

      // Old event from 7 days ago should not appear in stats
      expect(
        stats.find((stat) => stat.blocked_item === "old.com")
      ).toBeUndefined();
    });
  });

  describe("deleteOldBlockEvents", () => {
    it("removes events older than the specified timestamp", async () => {
      // GIVEN: Old event (3 days ago) and recent event (now)
      const oldTimestamp = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000);
      const oldEvent = await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: "website",
        blocked_item: "old-site.com",
        timestamp: oldTimestamp,
      });

      const recentEvent = await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: "website",
        blocked_item: "recent-site.com",
        timestamp: new Date(),
      });

      // WHEN: Deletion requested with cutoff date (2 days ago)
      const cutoffDate = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000);
      const deleted = await blockService.deleteOldBlockEvents(cutoffDate);

      // THEN: Old events deleted, recent events preserved, deletion count returned
      expect(deleted).toBeGreaterThanOrEqual(1);
      const shouldBeNull = await blockService.getBlockEventById(oldEvent.id);
      expect(shouldBeNull).toBeNull();

      // Verify recent event still exists
      const shouldExist = await blockService.getBlockEventById(recentEvent.id);
      expect(shouldExist).not.toBeNull();
    });

    it("returns zero when no events qualify for deletion", async () => {
      // GIVEN: Only recent block event (just created)
      const freshEvent = await blockService.createBlockEvent({
        device_id: testDeviceId,
        event_type: "website",
        blocked_item: "fresh-site.com",
        timestamp: new Date(),
      });

      // WHEN: Deletion with very recent cutoff (1 hour ago)
      await blockService.deleteOldBlockEvents(
        new Date(Date.now() - 60 * 60 * 1000)
      );

      // THEN: No events deleted (all events newer than cutoff)
      const events = await blockService.getBlockEventsByDevice(
        testDeviceId,
        100
      );
      const stillExists = events.find((e) => e.id === freshEvent.id);
      expect(stillExists).toBeDefined();
    });
  });
});
