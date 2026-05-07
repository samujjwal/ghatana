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
import { Prisma } from "@tutorputor/core/db";
import type { LearningTelemetryEvent } from "@tutorputor/contracts/v1/telemetry-events";
import { XAPI_VERBS } from "@tutorputor/contracts/v1/telemetry-events";
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

export interface LearningTelemetryBatchInput {
  events: LearningTelemetryEvent[];
}

export interface PrivacyTelemetryTarget {
  tenantId: string;
  userId: string;
  runId?: string;
  attemptId?: string;
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
    ...(typeof row.feedbackLabel === "string"
      ? { feedbackLabel: row.feedbackLabel }
      : {}),
    ...(typeof row.feedbackScore === "number"
      ? { feedbackScore: row.feedbackScore }
      : {}),
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

  async ingestLearningTelemetryBatch(
    tenantId: string,
    userId: string,
    input: LearningTelemetryBatchInput,
  ): Promise<{ count: number }> {
    if (input.events.length === 0) {
      throw new Error("Learning telemetry batch must contain at least one event");
    }

    const data = input.events.map((event) => {
      this.assertValidLearningTelemetryEvent(tenantId, userId, event);
      return {
        tenantId,
        userId,
        moduleId: event.context.learningUnitId ?? null,
        eventType: event.type,
        payload: event as unknown as Prisma.InputJsonValue,
        timestamp: new Date(event.timestamp),
      };
    });

    const result = await this.prisma.learningEvent.createMany({
      data,
      skipDuplicates: true,
    });

    return { count: result.count };
  }

  async getLearningTelemetryDashboardSummary(
    tenantId: string,
    userId?: string,
  ): Promise<{
    totalEvents: number;
    byType: Record<string, number>;
    simulationRuns: number;
    assessmentAnswers: number;
    hints: number;
    aiInteractions: number;
  }> {
    const rows = await this.prisma.learningEvent.findMany({
      where: {
        tenantId,
        ...(userId ? { userId } : {}),
      },
      select: {
        eventType: true,
        payload: true,
      },
    });
    const byType: Record<string, number> = {};

    for (const row of rows) {
      const eventType = String(row.eventType);
      byType[eventType] = (byType[eventType] ?? 0) + 1;
    }

    return {
      totalEvents: rows.length,
      byType,
      simulationRuns: rows.filter((row) =>
        String(row.eventType).startsWith("sim."),
      ).length,
      assessmentAnswers: byType["assess.answer"] ?? 0,
      hints: byType["assist.hint"] ?? 0,
      aiInteractions: rows.filter((row) =>
        String(row.eventType).startsWith("ai."),
      ).length,
    };
  }

  async exportLearningTelemetryForPrivacy(
    target: PrivacyTelemetryTarget,
  ): Promise<Array<{ id: string; eventType: string; payload: unknown }>> {
    const rows = await this.findPrivacyTargetedLearningEvents(target);
    return rows.map((row) => ({
      id: String(row.id),
      eventType: String(row.eventType),
      payload: row.payload,
    }));
  }

  async deleteLearningTelemetryForPrivacy(
    target: PrivacyTelemetryTarget,
  ): Promise<{ count: number }> {
    const rows = await this.findPrivacyTargetedLearningEvents(target);
    if (rows.length === 0) {
      return { count: 0 };
    }

    const result = await this.prisma.learningEvent.deleteMany({
      where: {
        tenantId: target.tenantId,
        id: { in: rows.map((row) => String(row.id)) },
      },
    });
    return { count: result.count };
  }

  /**
   * Record a single explorer interaction event.
   */
  async trackEvent(
    tenantId: string,
    input: TrackExplorerEventInput,
  ): Promise<ExplorerEvent> {
    const row = await this.prisma.explorerEvent.create({
      data: {
        tenantId,
        userId: input.userId ?? null,
        sessionId: input.sessionId ?? null,
        eventType: input.eventType.toUpperCase().replace(/-/g, "_") as
          | "IMPRESSION"
          | "CLICK"
          | "QUERY_REFORMULATION"
          | "ASSET_START"
          | "ASSET_COMPLETE"
          | "NEXT_STEP_SELECT"
          | "RANKING_FEEDBACK",
        query: input.query ?? null,
        assetId: input.assetId ?? null,
        assetType: input.assetType ?? null,
        position: input.position ?? null,
        score: input.score ?? null,
        feedbackLabel: input.feedbackLabel ?? null,
        feedbackScore: input.feedbackScore ?? null,
        metadata:
          input.metadata != null
            ? (input.metadata as Prisma.InputJsonValue)
            : Prisma.JsonNull,
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
      eventType: evt.eventType.toUpperCase().replace(/-/g, "_") as
        | "IMPRESSION"
        | "CLICK"
        | "QUERY_REFORMULATION"
        | "ASSET_START"
        | "ASSET_COMPLETE"
        | "NEXT_STEP_SELECT"
        | "RANKING_FEEDBACK",
      query: evt.query ?? null,
      assetId: evt.assetId ?? null,
      assetType: evt.assetType ?? null,
      position: evt.position ?? null,
      score: evt.score ?? null,
      feedbackLabel: evt.feedbackLabel ?? null,
      feedbackScore: evt.feedbackScore ?? null,
      metadata:
        evt.metadata != null
          ? (evt.metadata as Prisma.InputJsonValue)
          : Prisma.JsonNull,
      occurredAt: evt.occurredAt ? new Date(evt.occurredAt) : new Date(),
    }));

    const result = await this.prisma.explorerEvent.createMany({
      data,
      skipDuplicates: true,
    });

    await this.markRecommendationStateStale(
      tenantId,
      input.events
        .map((evt) => evt.assetId)
        .filter(
          (assetId: unknown): assetId is string => typeof assetId === "string",
        ),
      input.events.map((evt) => evt.eventType),
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
      where.eventType = { in: eventTypes.map((t) => t.toUpperCase()) };
    }

    const rows = await this.prisma.explorerEvent.findMany({
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
      !eventTypes.some((eventType) => relevantTypes.has(eventType))
    ) {
      return;
    }

    await this.prisma.contentAsset.updateMany({
      where: {
        tenantId,
        id: { in: [...new Set(assetIds)] },
      },
      data: {
        recommendationStatus: "STALE",
      },
    });
  }

  private assertValidLearningTelemetryEvent(
    tenantId: string,
    userId: string,
    event: LearningTelemetryEvent,
  ): void {
    if (!(event.type in XAPI_VERBS)) {
      throw new Error(`Unsupported learning telemetry event type: ${event.type}`);
    }
    if (event.actor.id !== userId) {
      throw new Error("Learning telemetry actor does not match authenticated user");
    }
    if (event.context.tenantId !== tenantId) {
      throw new Error("Learning telemetry tenant does not match request tenant");
    }
    if (!event.context.sessionId) {
      throw new Error("Learning telemetry event requires a sessionId");
    }
  }

  private async findPrivacyTargetedLearningEvents(target: PrivacyTelemetryTarget) {
    const rows = await this.prisma.learningEvent.findMany({
      where: {
        tenantId: target.tenantId,
        userId: target.userId,
      },
      select: {
        id: true,
        eventType: true,
        payload: true,
      },
    });

    return rows.filter((row) =>
      this.matchesPrivacyTarget(row.payload, target),
    );
  }

  private matchesPrivacyTarget(
    payload: unknown,
    target: PrivacyTelemetryTarget,
  ): boolean {
    if (!target.runId && !target.attemptId) {
      return true;
    }
    const serialized = JSON.stringify(payload);
    return (
      (!target.runId || serialized.includes(`"runId":"${target.runId}"`)) &&
      (!target.attemptId ||
        serialized.includes(`"attemptId":"${target.attemptId}"`))
    );
  }

  private async applyLearnerFeedbackSignals(
    tenantId: string,
    events: TrackExplorerEventInput[],
  ): Promise<void> {
    const actionable = events.filter(
      (event) =>
        typeof event.userId === "string" &&
        typeof event.assetId === "string" &&
        (event.eventType === "asset_complete" ||
          event.eventType === "ranking_feedback"),
    );

    if (actionable.length === 0) {
      return;
    }

    const assets = (await this.prisma.contentAsset.findMany({
      where: {
        tenantId,
        id: {
          in: actionable
            .map((event) => event.assetId)
            .filter(
              (assetId): assetId is string => typeof assetId === "string",
            ),
        },
      },
      select: {
        id: true,
        conceptId: true,
        assetType: true,
      },
    })) as LearnerSignalAsset[];
    const assetMap = new Map<string, LearnerSignalAsset>(
      assets.map((asset) => [asset.id, asset]),
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
