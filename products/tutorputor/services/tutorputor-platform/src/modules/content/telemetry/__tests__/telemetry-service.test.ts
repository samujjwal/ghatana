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
});
