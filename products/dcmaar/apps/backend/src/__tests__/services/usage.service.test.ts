/**
 * Usage Service Tests
 *
 * Tests usage tracking service methods including:
 * - Usage session creation (app and website)
 * - Usage session updates
 * - Session retrieval by device and child
 * - Usage summaries and aggregation
 * - Old session cleanup
 *
 * @doc.type test
 * @doc.purpose Test coverage for usage tracking service
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
import * as usageService from "../../services/usage.service";
import { query } from "../../db";
import { randomString, randomEmail } from "../setup";
import { seedUser, seedChild, seedDevice } from "../helpers/testDb";
import { parentUser, childProfile, androidDevice } from "../fixtures";

describe("UsageService", () => {
  let testUserId: string;
  let testChildId: string;
  let testDeviceId: string;

  beforeEach(async () => {
    // Seed test user directly (no service call) with unique email
    const testUser = { ...parentUser, email: randomEmail() };
    testUserId = await seedUser(testUser);

    // Seed test child directly (no service call)
    const childFixture = { ...childProfile, userId: testUserId };
    testChildId = await seedChild(childFixture, testUserId);

    // Seed test device directly (no service call)
    const deviceFixture = {
      ...androidDevice,
      userId: testUserId,
      childId: testChildId,
    };
    testDeviceId = await seedDevice(deviceFixture, testUserId, testChildId);
  });

  describe("createUsageSession", () => {
    it("should create an app usage session", async () => {
      // GIVEN: Valid app usage session data with all properties
      const sessionData = {
        device_id: testDeviceId,
        session_type: "app" as const,
        item_name: "Instagram",
        category: "social",
        start_time: new Date(),
      };

      // WHEN: Usage session is created
      const session = await usageService.createUsageSession(sessionData);

      // THEN: Session persisted with all specified properties and defaults
      expect(session).toBeDefined();
      expect(session.id).toBeDefined();
      expect(session.device_id).toBe(testDeviceId);
      expect(session.session_type).toBe("app");
      expect(session.item_name).toBe("Instagram");
      expect(session.category).toBe("social");
      expect(session.start_time).toBeDefined();
      expect(session.end_time).toBeNull();
      expect(session.duration_seconds).toBeNull();
    });

    it("should create a website usage session", async () => {
      // GIVEN: Valid website usage session data
      const sessionData = {
        device_id: testDeviceId,
        session_type: "website" as const,
        item_name: "facebook.com",
        category: "social",
        start_time: new Date(),
      };

      // WHEN: Website session is created
      const session = await usageService.createUsageSession(sessionData);

      // THEN: Website session type and domain name are preserved
      expect(session.session_type).toBe("website");
      expect(session.item_name).toBe("facebook.com");
    });

    it("should create session without category", async () => {
      // GIVEN: Session data without category field (optional)
      const sessionData = {
        device_id: testDeviceId,
        session_type: "app" as const,
        item_name: "MyApp",
        start_time: new Date(),
      };

      // WHEN: Session created without category
      const session = await usageService.createUsageSession(sessionData);

      // THEN: Category field is null but session still created
      expect(session.category).toBeNull();
    });

    it("should create completed session with end_time and duration", async () => {
      // GIVEN: Session data with completion time and calculated duration
      const startTime = new Date();
      const endTime = new Date(startTime.getTime() + 3600000); // 1 hour later

      const sessionData = {
        device_id: testDeviceId,
        session_type: "app" as const,
        item_name: "MyApp",
        start_time: startTime,
        end_time: endTime,
        duration_seconds: 3600,
      };

      // WHEN: Completed session is created (with end time provided)
      const session = await usageService.createUsageSession(sessionData);

      // THEN: End time and duration are persisted
      expect(session.end_time).toBeDefined();
      expect(session.duration_seconds).toBe(3600);
    });
  });

  describe("updateUsageSession", () => {
    let testSessionId: string;

    beforeEach(async () => {
      const session = await usageService.createUsageSession({
        device_id: testDeviceId,
        session_type: "app",
        item_name: "TestApp",
        start_time: new Date(),
      });
      testSessionId = session.id;
    });

    it("should update session end_time", async () => {
      // GIVEN: Active session without end time
      // WHEN: End time is set on existing session
      const endTime = new Date();
      const updated = await usageService.updateUsageSession(testSessionId, {
        end_time: endTime,
      });

      // THEN: End time is updated to provided value
      expect(updated?.end_time).toBeDefined();
      expect(new Date(updated!.end_time!).getTime()).toBeCloseTo(
        endTime.getTime(),
        -2
      );
    });

    it("should update session duration", async () => {
      // GIVEN: Session with no duration set
      // WHEN: Duration is explicitly set
      const updated = await usageService.updateUsageSession(testSessionId, {
        duration_seconds: 1200,
      });

      // THEN: Duration is updated
      expect(updated?.duration_seconds).toBe(1200);
    });

    it("should update both end_time and duration", async () => {
      // GIVEN: Session to finalize
      // WHEN: Both end time and duration provided in same update
      const endTime = new Date();
      const updated = await usageService.updateUsageSession(testSessionId, {
        end_time: endTime,
        duration_seconds: 3600,
      });

      // THEN: Both fields are updated
      expect(updated?.end_time).toBeDefined();
      expect(updated?.duration_seconds).toBe(3600);
    });

    it("should return current session if no updates provided", async () => {
      // GIVEN: Existing session
      // WHEN: Update called with empty object (no changes)
      const updated = await usageService.updateUsageSession(testSessionId, {});

      // THEN: Current session returned unchanged
      expect(updated).toBeDefined();
      expect(updated?.id).toBe(testSessionId);
    });

    it("should return null for non-existent session", async () => {
      // GIVEN: Non-existent session ID
      // WHEN: Update attempted on non-existent session
      const updated = await usageService.updateUsageSession(
        "00000000-0000-0000-0000-000000000000",
        {
          end_time: new Date(),
        }
      );

      // THEN: Null returned (not found)
      expect(updated).toBeNull();
    });
  });

  describe("getUsageSessionById", () => {
    let testSessionId: string;

    beforeEach(async () => {
      const session = await usageService.createUsageSession({
        device_id: testDeviceId,
        session_type: "app",
        item_name: "TestApp",
        start_time: new Date(),
      });
      testSessionId = session.id;
    });

    it("should get session by ID", async () => {
      // GIVEN: Session created and ID stored
      // WHEN: Session is retrieved by ID
      const session = await usageService.getUsageSessionById(testSessionId);

      // THEN: Correct session is returned with all properties
      expect(session).toBeDefined();
      expect(session?.id).toBe(testSessionId);
      expect(session?.item_name).toBe("TestApp");
    });

    it("should return null for non-existent session", async () => {
      // GIVEN: Non-existent session ID (all zeros UUID)
      // WHEN: Session lookup is attempted with fake ID
      const session = await usageService.getUsageSessionById(
        "00000000-0000-0000-0000-000000000000"
      );

      // THEN: Null is returned (not found)
      expect(session).toBeNull();
    });
  });

  describe("getUsageSessionsByDevice", () => {
    beforeEach(async () => {
      // Create multiple sessions
      for (let i = 0; i < 5; i++) {
        await usageService.createUsageSession({
          device_id: testDeviceId,
          session_type: i % 2 === 0 ? "app" : "website",
          item_name: `Item ${i}`,
          start_time: new Date(Date.now() - i * 1000000),
        });
      }
    });

    it("should get all sessions for a device", async () => {
      // GIVEN: Device with multiple sessions created in beforeEach
      // WHEN: All sessions for device are retrieved
      const sessions =
        await usageService.getUsageSessionsByDevice(testDeviceId);

      // THEN: All sessions returned with correct device ID
      expect(sessions).toBeDefined();
      expect(sessions.length).toBeGreaterThanOrEqual(5);
      expect(sessions.every((s) => s.device_id === testDeviceId)).toBe(true);
    });

    it("should order sessions by start_time DESC", async () => {
      // GIVEN: Multiple sessions created with different timestamps
      // WHEN: Sessions retrieved for device
      const sessions =
        await usageService.getUsageSessionsByDevice(testDeviceId);

      // THEN: Sessions ordered newest first (descending start_time)
      expect(sessions.length).toBeGreaterThan(1);

      // Verify descending order
      for (let i = 0; i < sessions.length - 1; i++) {
        const currentTime = new Date(sessions[i].start_time).getTime();
        const nextTime = new Date(sessions[i + 1].start_time).getTime();
        expect(currentTime).toBeGreaterThanOrEqual(nextTime);
      }
    });

    it("should respect limit parameter", async () => {
      // GIVEN: Device with 5 sessions
      // WHEN: Retrieve sessions with limit of 2
      const sessions = await usageService.getUsageSessionsByDevice(
        testDeviceId,
        2
      );

      // THEN: At most 2 sessions returned
      expect(sessions.length).toBeLessThanOrEqual(2);
    });

    it("should return empty array for device with no sessions", async () => {
      // GIVEN: Device with no sessions created
      const newDeviceFixture = {
        ...androidDevice,
        userId: testUserId,
        deviceName: "Empty Device",
        deviceFingerprint: randomString(16),
      };
      const newDeviceId = await seedDevice(newDeviceFixture, testUserId);

      // WHEN: Sessions requested for empty device
      const sessions = await usageService.getUsageSessionsByDevice(newDeviceId);

      // THEN: Empty array returned
      expect(sessions).toEqual([]);
    });
  });

  describe("getUsageSessionsByChild", () => {
    let secondDeviceId: string;

    beforeEach(async () => {
      // Create second device for same child
      const device2Fixture = {
        ...androidDevice,
        userId: testUserId,
        childId: testChildId,
        deviceName: "Second Device",
        deviceFingerprint: randomString(16),
      };
      secondDeviceId = await seedDevice(
        device2Fixture,
        testUserId,
        testChildId
      );

      // Create sessions on both devices
      await usageService.createUsageSession({
        device_id: testDeviceId,
        session_type: "app",
        item_name: "App on Device 1",
        start_time: new Date(),
      });

      await usageService.createUsageSession({
        device_id: secondDeviceId,
        session_type: "website",
        item_name: "Website on Device 2",
        start_time: new Date(),
      });
    });

    it("should get sessions from all child devices", async () => {
      // GIVEN: Child with multiple devices, each with sessions
      // WHEN: Sessions for child are retrieved
      const sessions = await usageService.getUsageSessionsByChild(testChildId);

      // THEN: All sessions from all child devices returned
      expect(sessions).toBeDefined();
      expect(sessions.length).toBeGreaterThanOrEqual(2);

      // Should have sessions from both devices
      const deviceIds = sessions.map((s) => s.device_id);
      expect(deviceIds).toContain(testDeviceId);
      expect(deviceIds).toContain(secondDeviceId);
    });

    it("should order sessions by start_time DESC", async () => {
      // GIVEN: Multiple sessions on different devices
      // WHEN: Sessions retrieved for child
      const sessions = await usageService.getUsageSessionsByChild(testChildId);

      // THEN: All sessions ordered newest first across devices
      expect(sessions.length).toBeGreaterThan(1);

      for (let i = 0; i < sessions.length - 1; i++) {
        const currentTime = new Date(sessions[i].start_time).getTime();
        const nextTime = new Date(sessions[i + 1].start_time).getTime();
        expect(currentTime).toBeGreaterThanOrEqual(nextTime);
      }
    });

    it("should respect limit parameter", async () => {
      // GIVEN: Child with multiple sessions
      // WHEN: Retrieve with limit of 1
      const sessions = await usageService.getUsageSessionsByChild(
        testChildId,
        1
      );

      // THEN: At most 1 session returned
      expect(sessions.length).toBeLessThanOrEqual(1);
    });

    it("should not return sessions from other children", async () => {
      // GIVEN: Another child with device and sessions
      // Create another child and device
      const otherChild = await query(
        "INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING id",
        [testUserId, "Other Child", 8]
      );

      const otherDeviceFixture = {
        ...androidDevice,
        userId: testUserId,
        childId: otherChild[0].id,
        deviceName: "Other Device",
        deviceFingerprint: randomString(16),
      };
      const otherDeviceId = await seedDevice(
        otherDeviceFixture,
        testUserId,
        otherChild[0].id
      );

      await usageService.createUsageSession({
        device_id: otherDeviceId,
        session_type: "app",
        item_name: "Other App",
        start_time: new Date(),
      });

      // WHEN: Sessions for testChild retrieved
      const sessions = await usageService.getUsageSessionsByChild(testChildId);

      // THEN: Other child's sessions excluded (user isolation)
      expect(sessions.every((s) => s.device_id !== otherDeviceId)).toBe(true);
    });
  });

  describe("getUsageSummaryByChild", () => {
    beforeEach(async () => {
      const now = new Date();

      // Create multiple sessions with varying durations
      await usageService.createUsageSession({
        device_id: testDeviceId,
        session_type: "app",
        item_name: "Instagram",
        category: "social",
        start_time: now,
        end_time: new Date(now.getTime() + 3600000),
        duration_seconds: 3600,
      });

      await usageService.createUsageSession({
        device_id: testDeviceId,
        session_type: "app",
        item_name: "Instagram",
        category: "social",
        start_time: now,
        end_time: new Date(now.getTime() + 1800000),
        duration_seconds: 1800,
      });

      await usageService.createUsageSession({
        device_id: testDeviceId,
        session_type: "website",
        item_name: "youtube.com",
        category: "entertainment",
        start_time: now,
        end_time: new Date(now.getTime() + 7200000),
        duration_seconds: 7200,
      });
    });

    it("should aggregate usage by app/website", async () => {
      // GIVEN: Multiple sessions with overlapping app names
      // WHEN: Usage summary requested with date range
      const startDate = new Date(Date.now() - 86400000); // 24 hours ago
      const endDate = new Date(Date.now() + 86400000); // 24 hours from now

      const summary = await usageService.getUsageSummaryByChild(
        testChildId,
        startDate,
        endDate
      );

      // THEN: Usage aggregated and totaled by item name
      expect(summary).toBeDefined();
      expect(summary.length).toBeGreaterThan(0);

      // Find Instagram summary (should be aggregated from 2 sessions)
      const instagramSummary = summary.find((s) => s.item_name === "Instagram");
      expect(instagramSummary).toBeDefined();
      expect(Number(instagramSummary!.total_duration_seconds)).toBe(5400); // 3600 + 1800
      expect(Number(instagramSummary!.session_count)).toBe(2);
    });

    it("should order by total duration DESC", async () => {
      // GIVEN: Multiple items with different total durations
      // WHEN: Usage summary retrieved
      const startDate = new Date(Date.now() - 86400000);
      const endDate = new Date(Date.now() + 86400000);

      const summary = await usageService.getUsageSummaryByChild(
        testChildId,
        startDate,
        endDate
      );

      // THEN: Results ordered by duration (highest first)
      expect(summary.length).toBeGreaterThan(1);

      for (let i = 0; i < summary.length - 1; i++) {
        expect(
          Number(summary[i].total_duration_seconds)
        ).toBeGreaterThanOrEqual(Number(summary[i + 1].total_duration_seconds));
      }
    });

    it("should respect date range", async () => {
      // GIVEN: Session created outside target date range (2 days ago)
      // Create session outside range
      const oldDate = new Date(Date.now() - 172800000); // 2 days ago
      await usageService.createUsageSession({
        device_id: testDeviceId,
        session_type: "app",
        item_name: "OldApp",
        start_time: oldDate,
        end_time: new Date(oldDate.getTime() + 3600000),
        duration_seconds: 3600,
      });

      // WHEN: Summary requested for last 24 hours only
      const startDate = new Date(Date.now() - 86400000); // 24 hours ago
      const endDate = new Date(Date.now() + 86400000);

      const summary = await usageService.getUsageSummaryByChild(
        testChildId,
        startDate,
        endDate
      );

      // THEN: Old session excluded from summary (date range enforced)
      // Old session should not be included
      expect(summary.find((s) => s.item_name === "OldApp")).toBeUndefined();
    });

    it("should group by session type, item name, and category", async () => {
      // GIVEN: Sessions of different types (app vs website)
      // WHEN: Summary retrieved
      const startDate = new Date(Date.now() - 86400000);
      const endDate = new Date(Date.now() + 86400000);

      const summary = await usageService.getUsageSummaryByChild(
        testChildId,
        startDate,
        endDate
      );

      // THEN: Separate entries for Instagram (app) and youtube.com (website)
      // Should have separate entries for Instagram (app) and youtube.com (website)
      const appSummaries = summary.filter((s) => s.session_type === "app");
      const websiteSummaries = summary.filter(
        (s) => s.session_type === "website"
      );

      expect(appSummaries.length).toBeGreaterThan(0);
      expect(websiteSummaries.length).toBeGreaterThan(0);
    });
  });

  describe("deleteOldUsageSessions", () => {
    it("should delete sessions older than specified date", async () => {
      // GIVEN: Sessions created at different times (old and recent)
      const now = new Date();
      const oldDate = new Date(now.getTime() - 172800000); // 2 days ago

      // Create old session
      const oldSessionId = await query(
        `INSERT INTO usage_sessions 
         (device_id, session_type, item_name, start_time, end_time, duration_seconds, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id`,
        [
          testDeviceId,
          "app",
          "OldApp",
          oldDate,
          new Date(oldDate.getTime() + 3600000),
          3600,
          oldDate,
        ]
      );

      // Create recent session
      await usageService.createUsageSession({
        device_id: testDeviceId,
        session_type: "app",
        item_name: "NewApp",
        start_time: now,
      });

      // WHEN: deleteOldUsageSessions called with cutoff date (1 day ago)
      const cutoffDate = new Date(now.getTime() - 86400000); // 1 day ago
      const deletedCount = await usageService.deleteOldUsageSessions(cutoffDate);

      // THEN: Old session deleted, count reflects deletion
      expect(deletedCount).toBeGreaterThan(0);

      // Verify old session no longer exists
      const oldSession = await usageService.getUsageSessionById(oldSessionId[0].id);
      expect(oldSession).toBeNull();
    });

    it("should not delete recent sessions", async () => {
      // GIVEN: Recent sessions within retention period
      const now = new Date();

      const recentSessionId = (
        await usageService.createUsageSession({
          device_id: testDeviceId,
          session_type: "app",
          item_name: "RecentApp",
          start_time: now,
        })
      ).id;

      // WHEN: deleteOldUsageSessions called with cutoff in past
      const cutoffDate = new Date(now.getTime() - 86400000); // 1 day ago
      await usageService.deleteOldUsageSessions(cutoffDate);

      // THEN: Recent session preserved
      const preserved = await usageService.getUsageSessionById(recentSessionId);
      expect(preserved).toBeDefined();
      expect(preserved?.id).toBe(recentSessionId);
    });

    it("should return 0 when no old sessions exist", async () => {
      // GIVEN: Only recent sessions, no old sessions
      const now = new Date();

      await usageService.createUsageSession({
        device_id: testDeviceId,
        session_type: "app",
        item_name: "App1",
        start_time: now,
      });

      await usageService.createUsageSession({
        device_id: testDeviceId,
        session_type: "app",
        item_name: "App2",
        start_time: now,
      });

      // WHEN: deleteOldUsageSessions called
      const cutoffDate = new Date(now.getTime() - 172800000); // 2 days ago (before all sessions)
      const deletedCount = await usageService.deleteOldUsageSessions(cutoffDate);

      // THEN: No sessions deleted (all are newer than cutoff)
      expect(deletedCount).toBe(0);
    });
  });
});
