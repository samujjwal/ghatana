/**
 * Telemetry Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify explorer telemetry ingestion and retrieval
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { beforeEach, describe, expect, it, vi } from "vitest";
import { TelemetryService } from "../telemetry-service";

function makeEventRow(overrides: Record<string, unknown> = {}) {
  return {
    id: "evt-1",
    tenantId: "tenant-1",
    userId: "user-1",
    sessionId: "session-1",
    eventType: "CLICK",
    query: "newton laws",
    assetId: "asset-1",
    assetType: "explainer",
    position: 1,
    score: 0.82,
    feedbackLabel: null,
    feedbackScore: null,
    metadata: { source: "search" },
    occurredAt: new Date("2025-06-01T00:00:00Z"),
    ...overrides,
  };
}

function makePrisma() {
  const tx = {
    learnerMastery: {
      upsert: vi.fn().mockResolvedValue({}),
    },
    learnerProfile: {
      update: vi.fn().mockResolvedValue({}),
    },
    knowledgeGap: {
      create: vi.fn().mockResolvedValue({}),
    },
  };

  return {
    explorerEvent: {
      create: vi.fn().mockResolvedValue(makeEventRow()),
      createMany: vi.fn().mockResolvedValue({ count: 2 }),
      findMany: vi.fn().mockResolvedValue([]),
    },
    learningEvent: {
      createMany: vi.fn().mockResolvedValue({ count: 3 }),
      findMany: vi.fn().mockResolvedValue([]),
      deleteMany: vi.fn().mockResolvedValue({ count: 0 }),
    },
    contentAsset: {
      findMany: vi.fn().mockResolvedValue([
        { id: "asset-1", conceptId: "concept-1", assetType: "EXPLAINER" },
        { id: "asset-2", conceptId: "concept-2", assetType: "SIMULATION" },
      ]),
      updateMany: vi.fn().mockResolvedValue({ count: 1 }),
    },
    learnerProfile: {
      findUnique: vi.fn().mockResolvedValue({
        id: "profile-1",
        tenantId: "tenant-1",
        userId: "user-1",
        preferredDifficulty: "MEDIUM",
        preferredModality: "MIXED",
        preferredPacing: "ADAPTIVE",
        preferredSessionMinutes: 30,
        notificationFrequency: "daily",
        preferredTimeOfDay: null,
        streakDays: 0,
        lastActiveAt: new Date(),
        visualLearningScore: 0.25,
        auditoryLearningScore: 0.25,
        kinestheticLearningScore: 0.25,
      readingLearningScore: 0.25,
      }),
      update: vi.fn().mockResolvedValue({}),
    },
    learnerMastery: {
      findUnique: vi.fn().mockResolvedValue(null),
      upsert: tx.learnerMastery.upsert,
    },
    knowledgeGap: {
      findFirst: vi.fn().mockResolvedValue(null),
      create: tx.knowledgeGap.create,
    },
    user: {
      findFirst: vi.fn().mockResolvedValue({ id: "user-1" }),
    },
    $transaction: vi.fn(async (arg: any) => {
      if (typeof arg === "function") {
        return arg(tx as any);
      }
      return Promise.all(arg);
    }),
  };
}

describe("TelemetryService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: TelemetryService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new TelemetryService(prisma as never);
  });

  it("tracks a single explorer event", async () => {
    const result = await service.trackEvent("tenant-1", {
      userId: "user-1",
      sessionId: "session-1",
      eventType: "click",
      query: "newton laws",
      assetId: "asset-1",
      assetType: "explainer",
      position: 1,
      score: 0.82,
      metadata: { source: "search" },
    });

    expect(prisma.explorerEvent.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          eventType: "CLICK",
          tenantId: "tenant-1",
        }),
      }),
    );
    expect(result.eventType).toBe("click");
    expect(result.assetId).toBe("asset-1");
    expect(prisma.contentAsset.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          id: { in: ["asset-1"] },
        }),
        data: { recommendationStatus: "STALE" },
      }),
    );
  });

  it("tracks a batch of explorer events", async () => {
    const result = await service.trackBatch("tenant-1", {
      events: [
        {
          eventType: "impression",
          query: "force",
          assetId: "asset-1",
          position: 0,
        },
        {
          eventType: "click",
          query: "force",
          assetId: "asset-2",
          position: 1,
        },
      ],
    });

    expect(prisma.explorerEvent.createMany).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.arrayContaining([
          expect.objectContaining({ eventType: "IMPRESSION" }),
          expect.objectContaining({ eventType: "CLICK" }),
        ]),
      }),
    );
    expect(result.count).toBe(2);
    expect(prisma.contentAsset.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          id: { in: ["asset-1", "asset-2"] },
        }),
      }),
    );
  });

  it("gets recent asset events with event-type filtering", async () => {
    prisma.explorerEvent.findMany.mockResolvedValue([
      makeEventRow({ id: "evt-1", eventType: "CLICK" }),
      makeEventRow({ id: "evt-2", eventType: "ASSET_COMPLETE" }),
    ]);

    const result = await service.getAssetEvents("tenant-1", "asset-1", [
      "click",
      "asset_complete",
    ]);

    expect(prisma.explorerEvent.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          assetId: "asset-1",
          eventType: { in: ["CLICK", "ASSET_COMPLETE"] },
        }),
      }),
    );
    expect(result).toHaveLength(2);
    expect(result[1].eventType).toBe("asset_complete");
  });

  it("updates learner mastery from completion telemetry", async () => {
    await service.trackEvent("tenant-1", {
      userId: "user-1",
      eventType: "asset_complete",
      assetId: "asset-1",
      metadata: { durationSeconds: 42 },
    });

    expect(prisma.learnerMastery.upsert).toHaveBeenCalled();
    expect(prisma.contentAsset.findMany).toHaveBeenCalled();
  });

  it("records knowledge gaps from negative feedback telemetry", async () => {
    await service.trackEvent("tenant-1", {
      userId: "user-1",
      eventType: "ranking_feedback",
      assetId: "asset-2",
      feedbackLabel: "negative",
    });

    expect(prisma.knowledgeGap.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          conceptId: "concept-2",
          detectedBy: "LEARNER_REPORTED",
        }),
      }),
    );
  });

  it("validates and persists typed learning telemetry event batches", async () => {
    const result = await service.ingestLearningTelemetryBatch("tenant-1", "user-1", {
      events: [
        {
          type: "sim.capture",
          timestamp: "2026-05-06T00:00:00.000Z",
          actor: { id: "user-1" },
          context: {
            tenantId: "tenant-1",
            learningUnitId: "module-1",
            claimId: "claim-1",
            sessionId: "session-1",
            platform: "web",
          },
          object: {
            simulationId: "sim-1",
            runId: "run-1",
            captureId: "capture-1",
            claimId: "claim-1",
            evidenceId: "evidence-1",
            taskId: "task-1",
          },
          result: {
            processFeatures: { attempts: 2 },
            outputState: { distance: 12 },
            validEvidence: true,
          },
        },
        {
          type: "assess.answer",
          timestamp: "2026-05-06T00:00:01.000Z",
          actor: { id: "user-1" },
          context: {
            tenantId: "tenant-1",
            learningUnitId: "module-1",
            claimId: "claim-1",
            sessionId: "session-1",
            platform: "web",
          },
          object: {
            assessmentId: "assessment-1",
            attemptId: "attempt-1",
            itemId: "item-1",
            taskId: "task-1",
            claimId: "claim-1",
            evidenceId: "evidence-1",
          },
          result: {
            response: "12",
            correct: true,
            score: 1,
            maxScore: 1,
            confidence: "medium",
            durationMs: 2000,
          },
        },
        {
          type: "assist.hint",
          timestamp: "2026-05-06T00:00:02.000Z",
          actor: { id: "user-1" },
          context: {
            tenantId: "tenant-1",
            learningUnitId: "module-1",
            claimId: "claim-1",
            sessionId: "session-1",
            platform: "web",
          },
          object: {
            moduleId: "module-1",
            claimId: "claim-1",
            taskId: "task-1",
            hintId: "hint-1",
          },
          result: {
            source: "ai_tutor",
            level: "nudge",
            accepted: true,
          },
        },
      ],
    });

    expect(result.count).toBe(3);
    expect(prisma.learningEvent.createMany).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.arrayContaining([
          expect.objectContaining({
            tenantId: "tenant-1",
            userId: "user-1",
            moduleId: "module-1",
            eventType: "sim.capture",
          }),
          expect.objectContaining({
            eventType: "assess.answer",
          }),
          expect.objectContaining({
            eventType: "assist.hint",
          }),
        ]),
        skipDuplicates: true,
      }),
    );
  });

  it("rejects learning telemetry when actor and tenant do not match request context", async () => {
    await expect(
      service.ingestLearningTelemetryBatch("tenant-1", "user-1", {
        events: [
          {
            type: "sim.start",
            timestamp: "2026-05-06T00:00:00.000Z",
            actor: { id: "different-user" },
            context: {
              tenantId: "tenant-1",
              sessionId: "session-1",
              platform: "web",
            },
            object: {
              id: "sim-1",
              name: "Simulation",
              blueprintId: "blueprint-1",
            },
          },
        ],
      }),
    ).rejects.toThrow("actor does not match");
  });

  it("aggregates dashboard telemetry from persisted learning events", async () => {
    prisma.learningEvent.findMany.mockResolvedValue([
      { eventType: "sim.capture", payload: {} },
      { eventType: "assess.answer", payload: {} },
      { eventType: "assist.hint", payload: {} },
      { eventType: "ai.tutor.response", payload: {} },
    ]);

    const summary = await service.getLearningTelemetryDashboardSummary(
      "tenant-1",
      "user-1",
    );

    expect(prisma.learningEvent.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { tenantId: "tenant-1", userId: "user-1" },
      }),
    );
    expect(summary).toEqual({
      totalEvents: 4,
      byType: {
        "sim.capture": 1,
        "assess.answer": 1,
        "assist.hint": 1,
        "ai.tutor.response": 1,
      },
      simulationRuns: 1,
      assessmentAnswers: 1,
      hints: 1,
      aiInteractions: 1,
    });
  });

  it("exports and deletes privacy-targeted telemetry by user/run/attempt", async () => {
    prisma.learningEvent.findMany.mockResolvedValue([
      {
        id: "event-1",
        eventType: "sim.capture",
        payload: { object: { runId: "run-1" } },
      },
      {
        id: "event-2",
        eventType: "assess.answer",
        payload: { object: { attemptId: "attempt-1" } },
      },
      {
        id: "event-3",
        eventType: "assist.hint",
        payload: { object: { hintId: "hint-1" } },
      },
    ]);
    prisma.learningEvent.deleteMany.mockResolvedValue({ count: 1 });

    const exported = await service.exportLearningTelemetryForPrivacy({
      tenantId: "tenant-1",
      userId: "user-1",
      runId: "run-1",
    });
    const deleted = await service.deleteLearningTelemetryForPrivacy({
      tenantId: "tenant-1",
      userId: "user-1",
      attemptId: "attempt-1",
    });

    expect(exported).toEqual([
      {
        id: "event-1",
        eventType: "sim.capture",
        payload: { object: { runId: "run-1" } },
      },
    ]);
    expect(prisma.learningEvent.deleteMany).toHaveBeenCalledWith({
      where: {
        tenantId: "tenant-1",
        id: { in: ["event-2"] },
      },
    });
    expect(deleted).toEqual({ count: 1 });
  });
});
