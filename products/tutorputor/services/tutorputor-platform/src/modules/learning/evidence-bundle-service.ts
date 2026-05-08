/**
 * Evidence Bundle Service
 *
 * Manages claim-level evidence bundles as first-class persisted objects.
 * Aggregates evidence from examples, simulations, animations, and assessments.
 *
 * @doc.type service
 * @doc.purpose Manage claim-level evidence bundles
 * @doc.layer product
 * @doc.pattern Service
 */

import type { TutorPrismaClient } from "@tutorputor/core/db";
import { Prisma } from "@tutorputor/core/db";
import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "EvidenceBundleService" });

export type EvidenceBundleService = {
  /**
   * Create or update an evidence bundle for a claim
   */
  upsertClaimEvidenceBundle(args: {
    tenantId: string;
    experienceId: string;
    claimRef: string;
    userId?: string;
  }): Promise<void>;

  /**
   * Add evidence item to a claim's bundle
   */
  addEvidenceToBundle(args: {
    tenantId: string;
    claimRef: string;
    evidenceItem: {
      type: "example" | "simulation" | "animation" | "assessment";
      sourceId: string;
      confidence: number;
      metadata: Record<string, unknown>;
    };
  }): Promise<void>;

  /**
   * Get evidence bundle for a claim
   */
  getClaimEvidenceBundle(args: {
    tenantId: string;
    claimRef: string;
  }): Promise<EvidenceBundle | null>;

  /**
   * Recalculate bundle metrics (coverage, confidence, contradictions)
   */
  recalculateBundleMetrics(args: {
    tenantId: string;
    claimRef: string;
  }): Promise<void>;
};

export interface EvidenceBundle {
  id: string;
  tenantId: string;
  userId: string | null;
  claimId: string;
  experienceId: string | null;
  coverageScore: number;
  contradictionDetected: boolean;
  confidenceScore: number;
  evidenceCount: number;
  evidenceItems: Record<string, unknown>;
  validationStatus: string;
  validationErrors: Record<string, unknown> | null;
  createdAt: Date;
  updatedAt: Date;
}

export function createEvidenceBundleService(
  prisma: TutorPrismaClient,
): EvidenceBundleService {
  return {
    async upsertClaimEvidenceBundle({ tenantId, experienceId, claimRef, userId }) {
      // Check if bundle exists
      const existing = await prisma.evidenceBundle.findFirst({
        where: {
          tenantId,
          claimId: claimRef,
        },
      });

      if (existing) {
        // Update existing bundle
        await prisma.evidenceBundle.update({
          where: { id: existing.id },
          data: {
            experienceId,
            userId: userId ?? undefined,
            updatedAt: new Date(),
          },
        });
        logger.info(
          { tenantId, claimRef, bundleId: existing.id },
          "Updated existing evidence bundle",
        );
      } else {
        // Create new bundle
        await prisma.evidenceBundle.create({
          data: {
            tenantId,
            userId: userId ?? null,
            claimId: claimRef,
            experienceId,
            coverageScore: 0.0,
            contradictionDetected: false,
            confidenceScore: 0.0,
            evidenceCount: 0,
            evidenceItems: [],
            validationStatus: "pending",
            validationErrors: null,
          },
        });
        logger.info(
          { tenantId, claimRef, experienceId },
          "Created new evidence bundle",
        );
      }
    },

    async addEvidenceToBundle({ tenantId, claimRef, evidenceItem }) {
      const bundle = await prisma.evidenceBundle.findFirst({
        where: {
          tenantId,
          claimId: claimRef,
        },
      });

      if (!bundle) {
        logger.warn(
          { tenantId, claimRef },
          "Evidence bundle not found, cannot add evidence",
        );
        return;
      }

      const currentItems = Array.isArray(bundle.evidenceItems)
        ? (bundle.evidenceItems as unknown[])
        : [];
      const updatedItems = [...currentItems, evidenceItem];

      await prisma.evidenceBundle.update({
        where: { id: bundle.id },
        data: {
          evidenceItems: updatedItems as Prisma.InputJsonValue,
          evidenceCount: updatedItems.length,
          updatedAt: new Date(),
        },
      });

      // Recalculate metrics after adding evidence
      await this.recalculateBundleMetrics({ tenantId, claimRef });

      logger.info(
        { tenantId, claimRef, bundleId: bundle.id, evidenceType: evidenceItem.type },
        "Added evidence to bundle",
      );
    },

    async getClaimEvidenceBundle({ tenantId, claimRef }) {
      const bundle = await prisma.evidenceBundle.findFirst({
        where: {
          tenantId,
          claimId: claimRef,
        },
      });

      if (!bundle) {
        return null;
      }

      return {
        id: bundle.id,
        tenantId: bundle.tenantId,
        userId: bundle.userId,
        claimId: bundle.claimId,
        experienceId: bundle.experienceId,
        coverageScore: bundle.coverageScore,
        contradictionDetected: bundle.contradictionDetected,
        confidenceScore: bundle.confidenceScore,
        evidenceCount: bundle.evidenceCount,
        evidenceItems: bundle.evidenceItems as Record<string, unknown>,
        validationStatus: bundle.validationStatus,
        validationErrors: bundle.validationErrors as Record<string, unknown> | null,
        createdAt: bundle.createdAt,
        updatedAt: bundle.updatedAt,
      };
    },

    async recalculateBundleMetrics({ tenantId, claimRef }) {
      const bundle = await prisma.evidenceBundle.findFirst({
        where: {
          tenantId,
          claimId: claimRef,
        },
      });

      if (!bundle) {
        return;
      }

      const items = Array.isArray(bundle.evidenceItems)
        ? (bundle.evidenceItems as Array<{ confidence: number; type: string }>)
        : [];

      // Calculate average confidence
      const confidenceScore =
        items.length > 0
          ? items.reduce((sum, item) => sum + (item.confidence || 0), 0) /
            items.length
          : 0.0;

      // Coverage score based on evidence count and diversity
      const uniqueTypes = new Set(items.map((item) => item.type));
      const coverageScore = Math.min(
        1.0,
        (items.length * 0.3 + uniqueTypes.size * 0.2) / 2,
      );

      // Detect contradictions (simplified: low confidence items with high confidence items)
      const lowConfidenceItems = items.filter((item) => item.confidence < 0.5);
      const highConfidenceItems = items.filter((item) => item.confidence > 0.8);
      const contradictionDetected =
        lowConfidenceItems.length > 0 && highConfidenceItems.length > 0;

      await prisma.evidenceBundle.update({
        where: { id: bundle.id },
        data: {
          confidenceScore,
          coverageScore,
          contradictionDetected,
          evidenceCount: items.length,
          updatedAt: new Date(),
        },
      });

      logger.debug(
        { tenantId, claimRef, bundleId: bundle.id },
        "Recalculated bundle metrics",
        { confidenceScore, coverageScore, contradictionDetected },
      );
    },
  };
}
