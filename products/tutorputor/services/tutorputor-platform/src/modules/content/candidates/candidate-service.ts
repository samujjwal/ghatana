/**
 * Regeneration Candidate Service
 *
 * Detects and manages regeneration candidates — assets that have been flagged
 * for re-generation based on quality signals, policy violations, staleness, or
 * learner outcome data.
 *
 * @doc.type class
 * @doc.purpose Detect and queue stale or low-quality assets for regeneration
 * @doc.layer product
 * @doc.pattern Service
 */

import { Prisma, type PrismaClient } from "@tutorputor/core/db";

type PersistedRegenerationTrigger =
  | "POOR_DISCOVERY_PERFORMANCE"
  | "POOR_LEARNING_OUTCOMES"
  | "MISCONCEPTION_PATTERN"
  | "STALE_CURRICULUM"
  | "SAFETY_CONCERN"
  | "LOW_EVALUATION_SCORE"
  | "MANUAL_FLAGGED";
type PersistedRiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

function toPersistedTrigger(trigger: string): PersistedRegenerationTrigger {
  const normalized = trigger.toUpperCase().replace(/-/g, "_");
  if (normalized === "POOR_ENGAGEMENT") {
    return "POOR_DISCOVERY_PERFORMANCE";
  }
  return normalized as PersistedRegenerationTrigger;
}

function toPersistedSeverity(severity: string): PersistedRiskLevel {
  return severity.toUpperCase() as PersistedRiskLevel;
}

function toInputJsonValue(
  value: Record<string, unknown>,
): Prisma.InputJsonValue {
  return JSON.parse(JSON.stringify(value)) as Prisma.InputJsonValue;
}

interface RegenerationCandidate {
  id: string;
  tenantId: string;
  assetId: string;
  assetType?: string;
  trigger: string;
  severity: string;
  reason: string;
  evidence?: Record<string, unknown>;
  priority: number;
  status: string;
  generationRequestId?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  createdAt: string;
  updatedAt: string;
}

interface CreateRegenerationCandidateInput {
  assetId: string;
  assetType?: string;
  trigger: string;
  severity?: string;
  reason: string;
  evidence?: Record<string, unknown>;
  priority?: number;
}

// ---------------------------------------------------------------------------
// Mapper
// ---------------------------------------------------------------------------

function mapCandidate(row: Record<string, unknown>): RegenerationCandidate {
  return {
    id: String(row.id),
    tenantId: String(row.tenantId),
    assetId: String(row.assetId),
    ...(typeof row.assetType === "string" ? { assetType: row.assetType } : {}),
    trigger: (
      row.trigger as string
    ).toLowerCase() as RegenerationCandidate["trigger"],
    severity: (
      row.severity as string
    ).toLowerCase() as RegenerationCandidate["severity"],
    reason: String(row.reason),
    ...(row.evidence && typeof row.evidence === "object"
      ? { evidence: row.evidence as Record<string, unknown> }
      : {}),
    priority: Number(row.priority ?? 0),
    status: (
      row.status as string
    ).toLowerCase() as RegenerationCandidate["status"],
    ...(typeof row.generationRequestId === "string"
      ? { generationRequestId: row.generationRequestId }
      : {}),
    ...(typeof row.resolvedBy === "string"
      ? { resolvedBy: row.resolvedBy }
      : {}),
    ...(row.resolvedAt
      ? { resolvedAt: (row.resolvedAt as Date).toISOString() }
      : {}),
    createdAt: (row.createdAt as Date).toISOString(),
    updatedAt: (row.updatedAt as Date).toISOString(),
  };
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class RegenerationCandidateService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Create a new regeneration candidate.
   */
  async createCandidate(
    tenantId: string,
    input: CreateRegenerationCandidateInput,
  ): Promise<RegenerationCandidate> {
    const row = await this.prisma.regenerationCandidate.create({
      data: {
        tenantId,
        assetId: input.assetId,
        assetType: input.assetType ?? null,
        trigger: toPersistedTrigger(input.trigger),
        severity: toPersistedSeverity(input.severity ?? "medium"),
        reason: input.reason,
        evidence: input.evidence
          ? toInputJsonValue(input.evidence)
          : Prisma.JsonNull,
        priority: input.priority ?? 50,
        status: "OPEN",
      },
    });

    return mapCandidate(row);
  }

  /**
   * List open regeneration candidates for a tenant, optionally filtered.
   */
  async listOpenCandidates(
    tenantId: string,
    filter?: { assetId?: string; trigger?: string },
  ): Promise<RegenerationCandidate[]> {
    const where: Record<string, unknown> = { tenantId, status: "OPEN" };
    if (filter?.assetId) where.assetId = filter.assetId;
    if (filter?.trigger) where.trigger = toPersistedTrigger(filter.trigger);

    const rows = await this.prisma.regenerationCandidate.findMany({
      where,
      orderBy: [{ priority: "desc" }, { createdAt: "asc" }],
    });

    return rows.map(mapCandidate);
  }

  /**
   * Dismiss a regeneration candidate (will not be regenerated).
   */
  async dismissCandidate(
    tenantId: string,
    candidateId: string,
    resolvedBy: string,
  ): Promise<RegenerationCandidate> {
    const existing = await this.prisma.regenerationCandidate.findFirst({
      where: { id: candidateId, tenantId },
    });

    if (!existing) {
      throw new Error(`Regeneration candidate ${candidateId} not found`);
    }

    const row = await this.prisma.regenerationCandidate.update({
      where: { id: candidateId },
      data: {
        status: "DISMISSED",
        resolvedBy,
        resolvedAt: new Date(),
      },
    });

    return mapCandidate(row);
  }

  /**
   * Mark a candidate as queued (a generation request has been submitted).
   */
  async queueCandidate(
    tenantId: string,
    candidateId: string,
    generationRequestId: string,
  ): Promise<RegenerationCandidate> {
    const existing = await this.prisma.regenerationCandidate.findFirst({
      where: { id: candidateId, tenantId },
    });

    if (!existing) {
      throw new Error(`Regeneration candidate ${candidateId} not found`);
    }

    const row = await this.prisma.regenerationCandidate.update({
      where: { id: candidateId },
      data: {
        status: "QUEUED",
        generationRequestId,
      },
    });

    return mapCandidate(row);
  }

  /**
   * Automatically detect low-engagement assets and create candidates.
   * Looks for assets with many RANKING_FEEDBACK events with negative labels.
   */
  async detectFromFeedback(tenantId: string): Promise<number> {
    // Find assets with recent negative feedback (>=3 negative signals)
    const negativeEvents = await this.prisma.explorerEvent.groupBy({
      by: ["assetId"],
      where: {
        tenantId,
        eventType: "RANKING_FEEDBACK",
        feedbackLabel: "negative",
        assetId: { not: null },
      },
      _count: { assetId: true },
      having: { assetId: { _count: { gte: 3 } } },
    });

    let created = 0;
    for (const event of negativeEvents) {
      if (!event.assetId) continue;

      // Skip if already has an open candidate for this trigger
      const existing = await this.prisma.regenerationCandidate.findFirst({
        where: {
          tenantId,
          assetId: event.assetId,
          trigger: toPersistedTrigger("poor_discovery_performance"),
          status: { in: ["OPEN", "QUEUED"] },
        },
      });

      if (existing) continue;

      await this.createCandidate(tenantId, {
        assetId: event.assetId,
        trigger: "poor_discovery_performance",
        severity: "medium",
        reason: `Asset received ${event._count.assetId} negative feedback signals`,
        evidence: { negativeCount: event._count.assetId },
        priority: 60,
      });
      created++;
    }

    return created;
  }
}
