import { describe, expect, it } from "vitest";
import {
  createOfflineSyncRecord,
  mergeAiDisabledState,
  mergeModuleProgress,
  mergeTelemetryBatch,
  queueOfflineSyncRecord,
  resolveAssessmentAttempt,
  resolveOfflineConflict,
  resolveSimulationCapture,
  type AssessmentAttemptPayload,
  type ModuleProgressPayload,
  type OfflineTelemetryBatchPayload,
  type SimulationCapturePayload,
} from "./offlineSync";

function memoryStorage() {
  const store = new Map<string, string>();
  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
  };
}

describe("offline sync conflict policy", () => {
  it("merges module progress by preserving the strongest local or server evidence", () => {
    const local: ModuleProgressPayload = {
      moduleId: "module-motion",
      lessonId: "lesson-1",
      progressPercent: 80,
      timeSpentSeconds: 320,
      updatedAt: "2026-05-06T12:00:00.000Z",
    };
    const server: ModuleProgressPayload = {
      moduleId: "module-motion",
      lessonId: "lesson-1",
      progressPercent: 65,
      timeSpentSeconds: 500,
      updatedAt: "2026-05-06T11:00:00.000Z",
    };

    expect(mergeModuleProgress(local, server)).toMatchObject({
      progressPercent: 80,
      timeSpentSeconds: 500,
      updatedAt: "2026-05-06T12:00:00.000Z",
    });
  });

  it("keeps simulation captures idempotent and flags deterministic hash conflicts", () => {
    const local: SimulationCapturePayload = {
      simulationRunId: "run-1",
      captureId: "capture-1",
      deterministicHash: "hash-local",
      claimId: "claim-motion",
      evidenceId: "evidence-motion",
      taskId: "task-motion",
      outputState: { distance: 10 },
      processFeatures: { hints: 0 },
      capturedAt: "2026-05-06T12:00:00.000Z",
    };
    const server = { ...local, deterministicHash: "hash-server" };

    const resolution = resolveSimulationCapture(local, server);
    expect(resolution.status).toBe("conflict");
    expect(resolution.conflict?.reason).toBe("hash-mismatch");
  });

  it("blocks divergent offline answers when the server attempt is already submitted", () => {
    const local: AssessmentAttemptPayload = {
      assessmentId: "assessment-1",
      attemptId: "attempt-1",
      status: "draft",
      answers: [{ itemId: "item-1", response: "A", confidence: "high", updatedAt: "2026-05-06T12:00:00.000Z" }],
    };
    const server: AssessmentAttemptPayload = {
      ...local,
      status: "submitted",
      submittedAt: "2026-05-06T12:01:00.000Z",
      answers: [{ itemId: "item-1", response: "B", confidence: "medium", updatedAt: "2026-05-06T12:01:00.000Z" }],
    };

    const resolution = resolveAssessmentAttempt(local, server);
    expect(resolution.status).toBe("conflict");
    expect(resolution.conflict?.reason).toBe("submitted-attempt-changed");
  });

  it("keeps AI disabled when either local or server policy disables it", () => {
    expect(
      mergeAiDisabledState(
        {
          learnerId: "learner-1",
          disabled: false,
          reason: "offline",
          updatedAt: "2026-05-06T12:00:00.000Z",
        },
        {
          learnerId: "learner-1",
          disabled: true,
          reason: "revoked_consent",
          updatedAt: "2026-05-06T11:00:00.000Z",
        },
      ),
    ).toMatchObject({ disabled: true, reason: "revoked_consent" });
  });

  it("deduplicates telemetry events by event id while preserving chronological order", () => {
    const local: OfflineTelemetryBatchPayload = {
      batchId: "batch-1",
      events: [
        { id: "event-2", type: "assess.answer", timestamp: "2026-05-06T12:02:00.000Z" },
        { id: "event-3", type: "assist.hint", timestamp: "2026-05-06T12:03:00.000Z" },
      ],
    };
    const server: OfflineTelemetryBatchPayload = {
      batchId: "batch-1",
      events: [
        { id: "event-1", type: "sim.capture", timestamp: "2026-05-06T12:01:00.000Z" },
        { id: "event-2", type: "assess.answer", timestamp: "2026-05-06T12:02:00.000Z" },
      ],
    };

    expect(mergeTelemetryBatch(local, server).events.map((event) => event.id)).toEqual([
      "event-1",
      "event-2",
      "event-3",
    ]);
  });

  it("stores only the latest queued mutation for an entity key", () => {
    const storage = memoryStorage();
    const first = createOfflineSyncRecord("module.progress", {
      moduleId: "module-motion",
      lessonId: "lesson-1",
      progressPercent: 25,
      timeSpentSeconds: 120,
      updatedAt: "2026-05-06T12:00:00.000Z",
    });
    const second = createOfflineSyncRecord("module.progress", {
      moduleId: "module-motion",
      lessonId: "lesson-1",
      progressPercent: 55,
      timeSpentSeconds: 240,
      updatedAt: "2026-05-06T12:05:00.000Z",
    });

    queueOfflineSyncRecord(first, storage);
    const queue = queueOfflineSyncRecord(second, storage);

    expect(queue).toHaveLength(1);
    expect(queue[0].payload).toMatchObject({ progressPercent: 55 });
  });

  it("marks records as conflicts when a non-telemetry server version advanced past the offline base", () => {
    const record = createOfflineSyncRecord(
      "module.progress",
      {
        moduleId: "module-motion",
        progressPercent: 75,
        timeSpentSeconds: 300,
        updatedAt: "2026-05-06T12:00:00.000Z",
      },
      { baseServerVersion: 3 },
    );

    const resolution = resolveOfflineConflict(record, record.payload, 4);
    expect(resolution.status).toBe("conflict");
    expect(resolution.conflict?.reason).toBe("server-newer");
  });
});
