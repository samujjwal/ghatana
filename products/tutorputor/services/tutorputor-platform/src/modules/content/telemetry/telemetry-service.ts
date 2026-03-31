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
import { createLearnerProfileService } from "../../learning/learner-profile-service.js";

interface ExplorerEvent {
  id: string;
  tenantId: string;
  userId?: string;
  sessionId?: string;
  eventType: string;
  query?: string;
  assetId?: string;
  assetType?: string;
  position?: number;
  score?: number;
  feedbackLabel?: string;
  feedbackScore?: number;
  metadata?: Record<string, unknown>;
  occurredAt: string;
}

interface TrackExplorerEventInput {
  userId?: string;
  sessionId?: string;
  eventType: string;
  query?: string;
  assetId?: string;
  assetType?: string;
  position?: number;
  score?: number;
  feedbackLabel?: string;
  feedbackScore?: number;
  metadata?: Record<string, unknown>;
  occurredAt?: string;
}

interface TrackBatchEventsInput {
  events: TrackExplorerEventInput[];
}

const POSITIVE_FEEDBACK = new Set(["positive", "helpful", "relevant"]);
const NEGATIVE_FEEDBACK = new Set(["negative", "not_relevant", "unhelpful"]);

interface LearnerSignalAsset {
  id: string;
  conceptId: string | null;
  assetType: string | null;
}

// ---------------------------------------------------------------------------
// Mapper
// ---------------------------------------------------------------------------

function mapEvent(row: Record<string, unknown>): ExplorerEvent {
  return {
    id: String(row.id),
    tenantId: String(row.tenantId),
    ...(typeof row.userId === "string" ? { userId: row.userId } : {}),
    ...(typeof row.sessionId === "string" ? { sessionId: row.sessionId } : {}),
    eventType: (
      row.eventType as string
    ).toLowerCase() as ExplorerEvent["eventType"],
    ...(typeof row.query === "string" ? { query: row.query } : {}),
    ...(typeof row.assetId === "string" ? { assetId: row.assetId } : {}),
    ...(typeof row.assetType === "string" ? { assetType: row.assetType } : {}),
    ...(typeof row.position === "number" ? { position: row.position } : {}),
    ...(typeof row.score === "number" ? { score: row.score } : {}),
    ...(typeof row.feedbackLabel === "string" ? { feedbackLabel: row.feedbackLabel } : {}),
    ...(typeof row.feedbackScore === "number" ? { feedbackScore: row.feedbackScore } : {}),
    ...(row.metadata && typeof row.metadata === "object"
      ? { metadata: row.metadata as Record<string, unknown> }
      : {}),
    occurredAt: (row.occurredAt as Date).toISOString(),
  };
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class TelemetryService {
  private readonly learnerProfileService;

  constructor(private readonly prisma: PrismaClient) {
    this.learnerProfileService = createLearnerProfileService(prisma as never);
  }

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

    await this.markRecommendationStateStale(
      tenantId,
      input.assetId ? [input.assetId] : [],
      [input.eventType],
    );
    await this.applyLearnerFeedbackSignals(tenantId, [input]);

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

    await this.markRecommendationStateStale(
      tenantId,
      input.events
        .map((evt: any) => evt.assetId)
        .filter((assetId: unknown): assetId is string => typeof assetId === "string"),
      input.events.map((evt: any) => evt.eventType),
    );
    await this.applyLearnerFeedbackSignals(tenantId, input.events);

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
    const where: Record<string, unknown> = { tenantId, assetId };
    if (eventTypes && eventTypes.length > 0) {
      where.eventType = { in: eventTypes.map((t: any) => t.toUpperCase()) };
    }

    const rows = await (this.prisma as any).explorerEvent.findMany({
      where,
      orderBy: { occurredAt: "desc" },
      take: 500,
    });

    return rows.map(mapEvent);
  }

  private async markRecommendationStateStale(
    tenantId: string,
    assetIds: string[],
    eventTypes: string[],
  ): Promise<void> {
    const relevantTypes = new Set([
      "click",
      "asset_complete",
      "next_step_select",
      "ranking_feedback",
    ]);

    if (
      assetIds.length === 0 ||
      !eventTypes.some((eventType: any) => relevantTypes.has(eventType))
    ) {
      return;
    }

    await (this.prisma as any).contentAsset.updateMany({
      where: {
        tenantId,
        id: { in: [...new Set(assetIds)] },
      },
      data: {
        recommendationStatus: "STALE",
      },
    });
  }

  private async applyLearnerFeedbackSignals(
    tenantId: string,
    events: TrackExplorerEventInput[],
  ): Promise<void> {
    const actionable = events.filter(
      (event: any) =>
        typeof event.userId === "string" &&
        typeof event.assetId === "string" &&
        (event.eventType === "asset_complete" ||
          event.eventType === "ranking_feedback"),
    );

    if (actionable.length === 0) {
      return;
    }

    const assets = (await (this.prisma as any).contentAsset.findMany({
      where: {
        tenantId,
        id: { in: actionable.map((event: any) => event.assetId) },
      },
      select: {
        id: true,
        conceptId: true,
        assetType: true,
      },
    })) as LearnerSignalAsset[];
    const assetMap = new Map<string, LearnerSignalAsset>(
      assets.map((asset: any) => [asset.id, asset]),
    );

    for (const event of actionable) {
      const asset = assetMap.get(event.assetId!);
      if (!asset?.conceptId || !event.userId) {
        continue;
      }

      if (event.eventType === "asset_complete") {
        const durationSeconds =
          typeof event.metadata?.["durationSeconds"] === "number"
            ? Number(event.metadata["durationSeconds"])
            : undefined;
        await this.learnerProfileService.updateMastery(tenantId, event.userId, {
          conceptId: asset.conceptId,
          correct: true,
          confidence: 0.45,
          attempts: 1,
          modalityUsed: inferModalityFromAssetType(asset.assetType),
          ...(durationSeconds !== undefined
            ? { timeSpentSeconds: durationSeconds }
            : {}),
          ...(event.occurredAt ? { sessionStartedAt: event.occurredAt } : {}),
        });
        continue;
      }

      const label = String(event.feedbackLabel ?? "").toLowerCase();
      if (POSITIVE_FEEDBACK.has(label)) {
        await this.learnerProfileService.updateMastery(tenantId, event.userId, {
          conceptId: asset.conceptId,
          correct: true,
          confidence: 0.35,
          attempts: 1,
          modalityUsed: inferModalityFromAssetType(asset.assetType),
          ...(event.occurredAt ? { sessionStartedAt: event.occurredAt } : {}),
        });
      } else if (NEGATIVE_FEEDBACK.has(label)) {
        await this.learnerProfileService.recordKnowledgeGap(
          tenantId,
          event.userId,
          {
            conceptId: asset.conceptId,
            prerequisiteId: `${asset.conceptId}-foundation`,
            severity: "MEDIUM",
            detectedBy: "LEARNER_REPORTED",
            evidence: {
              source: "explorer_telemetry",
              feedbackLabel: label,
              assetId: asset.id,
            },
          },
        );
      }
    }
  }
}

function inferModalityFromAssetType(assetType: string | null | undefined) {
  const normalized = String(assetType ?? "").toLowerCase();
  if (
    normalized === "simulation" ||
    normalized === "animation" ||
    normalized === "video" ||
    normalized === "image"
  ) {
    return "VISUAL" as const;
  }
  if (normalized === "audio") {
    return "AUDITORY" as const;
  }
  return "READING" as const;
}
