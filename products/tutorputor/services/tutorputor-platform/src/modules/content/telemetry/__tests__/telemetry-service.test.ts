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
  return {
    explorerEvent: {
      create: vi.fn().mockResolvedValue(makeEventRow()),
      createMany: vi.fn().mockResolvedValue({ count: 2 }),
      findMany: vi.fn().mockResolvedValue([]),
    },
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
});
