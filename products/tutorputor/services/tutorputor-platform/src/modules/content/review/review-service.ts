/**
 * Generation Review Service
 *
 * Handles review decisions for generation requests: approve, reject,
 * or request regeneration. Persists auditable review decision history.
 *
 * @doc.type class
 * @doc.purpose Persist and manage generation request review decisions
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type {
  GenerationReviewDecision,
  SubmitReviewDecisionInput,
} from "@tutorputor/contracts/v1/content-studio";

// ---------------------------------------------------------------------------
// Mapper
// ---------------------------------------------------------------------------

function mapDecision(row: Record<string, unknown>): GenerationReviewDecision {
  return {
    id: row.id,
    tenantId: row.tenantId,
    requestId: row.requestId,
    status: (row.status as string)
      .toLowerCase()
      .replace(/_/g, "_") as GenerationReviewDecision["status"],
    ...(row.reviewedBy != null ? { reviewedBy: row.reviewedBy } : {}),
    ...(row.decisionNote != null ? { decisionNote: row.decisionNote } : {}),
    ...(row.regenerateJobIds != null
      ? { regenerateJobIds: row.regenerateJobIds as string[] }
      : {}),
    ...(row.reviewedAt
      ? { reviewedAt: (row.reviewedAt as Date).toISOString() }
      : {}),
    createdAt: (row.createdAt as Date).toISOString(),
    updatedAt: (row.updatedAt as Date).toISOString(),
  };
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class GenerationReviewService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Submit a review decision for a generation request.
   * Validates the request exists and is in a reviewable state.
   */
  async submitDecision(
    tenantId: string,
    reviewedBy: string,
    input: SubmitReviewDecisionInput,
  ): Promise<GenerationReviewDecision> {
    const request = await (this.prisma as any).generationRequest.findFirst({
      where: { id: input.requestId, tenantId },
    });

    if (!request) {
      throw new Error(`Generation request ${input.requestId} not found`);
    }

    const allowedStates = ["PLANNED", "COMPLETED", "FAILED"];
    if (!allowedStates.includes(request.status)) {
      throw new Error(
        `Request in state ${request.status} is not reviewable`,
      );
    }

    const decision = await (this.prisma as any).generationReviewDecision.create(
      {
        data: {
          tenantId,
          requestId: input.requestId,
          status: input.status.toUpperCase().replace(/-/g, "_"),
          reviewedBy,
          decisionNote: input.decisionNote ?? null,
          regenerateJobIds: input.regenerateJobIds ?? null,
          reviewedAt: new Date(),
        },
      },
    );

    // If approved, transition request to COMPLETED (if it wasn't already)
    if (input.status === "approved" && request.status !== "COMPLETED") {
      await (this.prisma as any).generationRequest.update({
        where: { id: input.requestId },
        data: { status: "COMPLETED", completedAt: new Date() },
      });
    }

    return mapDecision(decision);
  }

  /**
   * List all review decisions for a generation request.
   */
  async listDecisions(
    tenantId: string,
    requestId: string,
  ): Promise<GenerationReviewDecision[]> {
    const decisions = await (this.prisma as any).generationReviewDecision.findMany(
      {
        where: { tenantId, requestId },
        orderBy: { createdAt: "desc" },
      },
    );
    return decisions.map(mapDecision);
  }

  /**
   * Get the latest review decision for a request.
   */
  async getLatestDecision(
    tenantId: string,
    requestId: string,
  ): Promise<GenerationReviewDecision | null> {
    const decision = await (this.prisma as any).generationReviewDecision.findFirst(
      {
        where: { tenantId, requestId },
        orderBy: { createdAt: "desc" },
      },
    );
    return decision ? mapDecision(decision) : null;
  }

  /**
   * Ensure there is at least one pending review decision record for the request.
   * This keeps manual-review work visible without duplicating pending rows.
   */
  async ensurePendingDecision(
    tenantId: string,
    requestId: string,
    decisionNote?: string,
  ): Promise<GenerationReviewDecision> {
    const existing = await (this.prisma as any).generationReviewDecision.findFirst(
      {
        where: { tenantId, requestId, status: "PENDING" },
        orderBy: { createdAt: "desc" },
      },
    );

    if (existing) {
      return mapDecision(existing);
    }

    const created = await (this.prisma as any).generationReviewDecision.create({
      data: {
        tenantId,
        requestId,
        status: "PENDING",
        decisionNote: decisionNote ?? null,
      },
    });

    return mapDecision(created);
  }
}
