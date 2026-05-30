/**
 * Action queue tests
 *
 * Tests for low-connectivity action queue functionality.
 *
 * @doc.type test
 * @doc.purpose Verify action queue behavior
 * @doc.layer mobile
 */

import {
  enqueueAction,
  loadActionQueue,
  getActionQueueStats,
  getPendingActions,
  getFailedActions,
  clearCompletedActions,
  clearActionQueue,
  retryAction,
  deleteAction,
  syncActions,
  pruneOldActions,
  type QueuedAction,
  type ActionType,
} from "../actionQueue";
import {
  phiSet,
  resetPhiStorageAdapter,
  setPhiStorageAdapter,
  type PhiStorageAdapter,
} from "../phiEncryptedStorage";
import type { MobileSession } from "../../types";

describe("Action Queue", () => {
  const actionQueueStorage = new Map<string, string>();
  const mockSession: MobileSession = {
    principalId: "patient-123",
    tenantId: "tenant-1",
    role: "patient",
    name: "Test Patient",
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
  };

  beforeEach(() => {
    actionQueueStorage.clear();
    const adapter: PhiStorageAdapter = {
      async setItem(key: string, value: string): Promise<void> {
        actionQueueStorage.set(key, value);
      },
      async getItem(key: string): Promise<string | null> {
        return actionQueueStorage.get(key) ?? null;
      },
      async removeItem(key: string): Promise<void> {
        actionQueueStorage.delete(key);
      },
      async clearAllPhi(): Promise<void> {
        actionQueueStorage.clear();
      },
    };
    setPhiStorageAdapter(adapter);
  });

  afterEach(() => {
    resetPhiStorageAdapter();
    actionQueueStorage.clear();
  });

  describe("enqueueAction", () => {
    it("should add an action to the queue", async () => {
      const action = await enqueueAction("create_consent", {
        grantee: "Hospital A",
      });

      expect(action.type).toBe("create_consent");
      expect(action.status).toBe("pending");
      expect(action.retryCount).toBe(0);
    });

    it("should generate unique action IDs", async () => {
      const action1 = await enqueueAction("create_consent", {
        grantee: "Hospital A",
      });
      const action2 = await enqueueAction("book_appointment", {
        providerId: "provider-1",
      });

      expect(action1.id).not.toBe(action2.id);
    });

    it("should set timestamp on enqueue", async () => {
      const before = Date.now();
      const action = await enqueueAction("create_consent", {
        grantee: "Hospital A",
      });
      const after = Date.now();

      const actionTime = new Date(action.timestamp).getTime();
      expect(actionTime).toBeGreaterThanOrEqual(before);
      expect(actionTime).toBeLessThanOrEqual(after);
    });
  });

  describe("loadActionQueue", () => {
    it("should return empty array when queue is empty", async () => {
      const queue = await loadActionQueue();
      expect(queue).toEqual([]);
    });

    it("should load all queued actions", async () => {
      await enqueueAction("create_consent", { grantee: "Hospital A" });
      await enqueueAction("book_appointment", { providerId: "provider-1" });

      const queue = await loadActionQueue();
      expect(queue).toHaveLength(2);
    });
  });

  describe("getActionQueueStats", () => {
    it("should return zero stats for empty queue", async () => {
      const stats = await getActionQueueStats();
      expect(stats).toEqual({
        total: 0,
        pending: 0,
        syncing: 0,
        failed: 0,
        completed: 0,
      });
    });

    it("should count actions by status", async () => {
      const queue = await loadActionQueue();
      queue.push(
        {
          id: "1",
          type: "create_consent",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "pending",
        },
        {
          id: "2",
          type: "book_appointment",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "pending",
        },
        {
          id: "3",
          type: "submit_vitals",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "completed",
        },
        {
          id: "4",
          type: "report_symptom",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "failed",
        },
      );
      await phiSet("phr_action_queue", JSON.stringify(queue));

      const stats = await getActionQueueStats();
      expect(stats.total).toBe(4);
      expect(stats.pending).toBe(2);
      expect(stats.completed).toBe(1);
      expect(stats.failed).toBe(1);
    });
  });

  describe("getPendingActions", () => {
    it("should return only pending actions", async () => {
      const queue = await loadActionQueue();
      queue.push(
        {
          id: "1",
          type: "create_consent",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "pending",
        },
        {
          id: "2",
          type: "book_appointment",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "completed",
        },
      );
      await phiSet("phr_action_queue", JSON.stringify(queue));

      const pending = await getPendingActions();
      expect(pending).toHaveLength(1);
      expect(pending[0]?.id).toBe("1");
    });
  });

  describe("getFailedActions", () => {
    it("should return only failed actions", async () => {
      const queue = await loadActionQueue();
      queue.push(
        {
          id: "1",
          type: "create_consent",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "failed",
          error: "Network error",
        },
        {
          id: "2",
          type: "book_appointment",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "pending",
        },
      );
      await phiSet("phr_action_queue", JSON.stringify(queue));

      const failed = await getFailedActions();
      expect(failed).toHaveLength(1);
      expect(failed[0]?.id).toBe("1");
      expect(failed[0]?.error).toBe("Network error");
    });
  });

  describe("clearCompletedActions", () => {
    it("should remove completed actions from queue", async () => {
      const queue = await loadActionQueue();
      queue.push(
        {
          id: "1",
          type: "create_consent",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "completed",
        },
        {
          id: "2",
          type: "book_appointment",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "pending",
        },
      );
      await phiSet("phr_action_queue", JSON.stringify(queue));

      await clearCompletedActions();

      const remaining = await loadActionQueue();
      expect(remaining).toHaveLength(1);
      expect(remaining[0]?.id).toBe("2");
    });
  });

  describe("clearActionQueue", () => {
    it("should remove all actions", async () => {
      await enqueueAction("create_consent", { grantee: "Hospital A" });
      await enqueueAction("book_appointment", { providerId: "provider-1" });

      await clearActionQueue();

      const queue = await loadActionQueue();
      expect(queue).toHaveLength(0);
    });
  });

  describe("retryAction", () => {
    it("should reset failed action to pending", async () => {
      const queue = await loadActionQueue();
      queue.push({
        id: "1",
        type: "create_consent",
        payload: {},
        timestamp: new Date().toISOString(),
        retryCount: 1,
        status: "failed",
        error: "Network error",
      });
      await phiSet("phr_action_queue", JSON.stringify(queue));

      await retryAction("1");

      const updated = await loadActionQueue();
      const action = updated.find((a) => a.id === "1");
      expect(action?.status).toBe("pending");
      expect(action?.retryCount).toBe(2);
      expect(action?.error).toBeUndefined();
    });

    it("should throw if action not found", async () => {
      await expect(retryAction("nonexistent")).rejects.toThrow(
        "Action nonexistent not found",
      );
    });

    it("should throw if max retries exceeded", async () => {
      const queue = await loadActionQueue();
      queue.push({
        id: "1",
        type: "create_consent",
        payload: {},
        timestamp: new Date().toISOString(),
        retryCount: 3,
        status: "failed",
        error: "Network error",
      });
      await phiSet("phr_action_queue", JSON.stringify(queue));

      await expect(retryAction("1")).rejects.toThrow("exceeded max retries");
    });
  });

  describe("deleteAction", () => {
    it("should remove action from queue", async () => {
      const queue = await loadActionQueue();
      queue.push(
        {
          id: "1",
          type: "create_consent",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "pending",
        },
        {
          id: "2",
          type: "book_appointment",
          payload: {},
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "pending",
        },
      );
      await phiSet("phr_action_queue", JSON.stringify(queue));

      await deleteAction("1");

      const remaining = await loadActionQueue();
      expect(remaining).toHaveLength(1);
      expect(remaining[0]?.id).toBe("2");
    });
  });

  describe("syncActions", () => {
    it("should execute pending actions with executor", async () => {
      const queue = await loadActionQueue();
      queue.push(
        {
          id: "1",
          type: "create_consent",
          payload: { grantee: "Hospital A" },
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "pending",
        },
        {
          id: "2",
          type: "book_appointment",
          payload: { providerId: "provider-1" },
          timestamp: new Date().toISOString(),
          retryCount: 0,
          status: "pending",
        },
      );
      await phiSet("phr_action_queue", JSON.stringify(queue));

      const executor = jest.fn().mockResolvedValue(undefined);
      const result = await syncActions(mockSession, executor);

      expect(result.synced).toBe(2);
      expect(result.failed).toBe(0);
      expect(executor).toHaveBeenCalledTimes(2);
    });

    it("should handle executor failures", async () => {
      const queue = await loadActionQueue();
      queue.push({
        id: "1",
        type: "create_consent",
        payload: {},
        timestamp: new Date().toISOString(),
        retryCount: 0,
        status: "pending",
      });
      await phiSet("phr_action_queue", JSON.stringify(queue));

      const executor = jest.fn().mockRejectedValue(new Error("Network error"));
      const result = await syncActions(mockSession, executor);

      expect(result.synced).toBe(0);
      expect(result.failed).toBe(1);

      const updated = await loadActionQueue();
      const action = updated.find((a) => a.id === "1");
      expect(action?.status).toBe("failed");
      expect(action?.error).toBe("Network error");
    });

    it("should return zero stats when no pending actions", async () => {
      const executor = jest.fn();
      const result = await syncActions(mockSession, executor);

      expect(result.synced).toBe(0);
      expect(result.failed).toBe(0);
      expect(executor).not.toHaveBeenCalled();
    });
  });

  describe("pruneOldActions", () => {
    it("should remove completed actions older than 7 days", async () => {
      const queue = await loadActionQueue();
      const oldDate = new Date(
        Date.now() - 8 * 24 * 60 * 60 * 1000,
      ).toISOString();
      const recentDate = new Date().toISOString();

      queue.push(
        {
          id: "1",
          type: "create_consent",
          payload: {},
          timestamp: oldDate,
          retryCount: 0,
          status: "completed",
        },
        {
          id: "2",
          type: "book_appointment",
          payload: {},
          timestamp: recentDate,
          retryCount: 0,
          status: "completed",
        },
        {
          id: "3",
          type: "submit_vitals",
          payload: {},
          timestamp: oldDate,
          retryCount: 0,
          status: "pending",
        },
      );
      await phiSet("phr_action_queue", JSON.stringify(queue));

      const removed = await pruneOldActions();

      expect(removed).toBe(1);

      const remaining = await loadActionQueue();
      expect(remaining).toHaveLength(2);
      expect(remaining.find((a) => a.id === "1")).toBeUndefined();
    });

    it("should not remove pending actions regardless of age", async () => {
      const queue = await loadActionQueue();
      const oldDate = new Date(
        Date.now() - 8 * 24 * 60 * 60 * 1000,
      ).toISOString();

      queue.push({
        id: "1",
        type: "create_consent",
        payload: {},
        timestamp: oldDate,
        retryCount: 0,
        status: "pending",
      });
      await phiSet("phr_action_queue", JSON.stringify(queue));

      const removed = await pruneOldActions();

      expect(removed).toBe(0);

      const remaining = await loadActionQueue();
      expect(remaining).toHaveLength(1);
    });
  });
});
