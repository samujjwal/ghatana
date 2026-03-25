/**
 * Explorer Telemetry Service
 *
 * Records explorer interaction events: impressions, clicks, query reformulations,
 * asset completion signals, and ranking feedback. Used for outcome-aware
 * recommendation recompute (P4.2).
 *
 * @doc.type class
 * @doc.purpose Persist explorer interaction events for ranking feedback
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type {
  ExplorerEvent,
  TrackExplorerEventInput,
  TrackBatchEventsInput,
} from "@tutorputor/contracts/v1/content-studio";

// ---------------------------------------------------------------------------
// Mapper
// ---------------------------------------------------------------------------

function mapEvent(row: any): ExplorerEvent {
  return {
    id: row.id,
    tenantId: row.tenantId,
    userId: row.userId ?? undefined,
    sessionId: row.sessionId ?? undefined,
    eventType: (
      row.eventType as string
    ).toLowerCase() as ExplorerEvent["eventType"],
    query: row.query ?? undefined,
    assetId: row.assetId ?? undefined,
    assetType: row.assetType ?? undefined,
    position: row.position ?? undefined,
    score: row.score ?? undefined,
    feedbackLabel: row.feedbackLabel ?? undefined,
    feedbackScore: row.feedbackScore ?? undefined,
    metadata: row.metadata ?? undefined,
    occurredAt: (row.occurredAt as Date).toISOString(),
  };
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class TelemetryService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Record a single explorer interaction event.
   */
  async trackEvent(
    tenantId: string,
    input: TrackExplorerEventInput,
  ): Promise<ExplorerEvent> {
    const row = await (this.prisma as any).explorerEvent.create({
      data: {
        tenantId,
        userId: input.userId ?? null,
        sessionId: input.sessionId ?? null,
        eventType: input.eventType.toUpperCase().replace(/-/g, "_"),
        query: input.query ?? null,
        assetId: input.assetId ?? null,
        assetType: input.assetType ?? null,
        position: input.position ?? null,
        score: input.score ?? null,
        feedbackLabel: input.feedbackLabel ?? null,
        feedbackScore: input.feedbackScore ?? null,
        metadata: input.metadata ?? null,
        occurredAt: input.occurredAt ? new Date(input.occurredAt) : new Date(),
      },
    });

    return mapEvent(row);
  }

  /**
   * Record a batch of explorer interaction events.
   */
  async trackBatch(
    tenantId: string,
    input: TrackBatchEventsInput,
  ): Promise<{ count: number }> {
    const data = input.events.map((evt: TrackExplorerEventInput) => ({
      tenantId,
      userId: evt.userId ?? null,
      sessionId: evt.sessionId ?? null,
      eventType: evt.eventType.toUpperCase().replace(/-/g, "_"),
      query: evt.query ?? null,
      assetId: evt.assetId ?? null,
      assetType: evt.assetType ?? null,
      position: evt.position ?? null,
      score: evt.score ?? null,
      feedbackLabel: evt.feedbackLabel ?? null,
      feedbackScore: evt.feedbackScore ?? null,
      metadata: evt.metadata ?? null,
      occurredAt: evt.occurredAt ? new Date(evt.occurredAt) : new Date(),
    }));

    const result = await (this.prisma as any).explorerEvent.createMany({
      data,
      skipDuplicates: true,
    });

    return { count: result.count };
  }

  /**
   * Get events for an asset (e.g. for computing engagement score).
   */
  async getAssetEvents(
    tenantId: string,
    assetId: string,
    eventTypes?: string[],
  ): Promise<ExplorerEvent[]> {
    const where: any = { tenantId, assetId };
    if (eventTypes && eventTypes.length > 0) {
      where.eventType = { in: eventTypes.map((t) => t.toUpperCase()) };
    }

    const rows = await (this.prisma as any).explorerEvent.findMany({
      where,
      orderBy: { occurredAt: "desc" },
      take: 500,
    });

    return rows.map(mapEvent);
  }
}
