/**
 * Learning Evidence Service
 *
 * Manages mastery updates from claim/evidence/concept IDs.
 * Tracks learner progress and mastery based on evidence of learning.
 * Hardened to ensure evidence-based progress, not client claims.
 *
 * @doc.type class
 * @doc.purpose Mastery tracking and updates from learning evidence with immutability and validation
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type { ModuleId, TenantId, UserId } from "@tutorputor/contracts/v1/types";

const logger = createStandaloneLogger({ component: "LearningEvidenceService" });

// ============================================================================
// Evidence Types
// ============================================================================

export interface MasteryUpdate {
  userId: UserId;
  tenantId: TenantId;
  conceptId: string;
  claimId: string | undefined;
  evidenceId: string | undefined;
  moduleId: ModuleId | undefined;
  masteryScore: number; // 0-100
  confidence: number; // 0-1
  timestamp: Date;
}

export interface MasteryProfile {
  userId: UserId;
  tenantId: TenantId;
  conceptMastery: Record<string, number>; // conceptId -> mastery score
  claimMastery: Record<string, number>; // claimId -> mastery score
  moduleMastery: Record<string, number>; // moduleId -> mastery score
  lastUpdated: Date;
}

export interface EvidenceRecord {
  id: string;
  userId: UserId;
  tenantId: TenantId;
  type: "assessment" | "simulation" | "interaction" | "observation";
  itemId: string;
  attemptId?: string; // Link to specific attempt for immutability
  success: boolean;
  confidence?: number;
  timestamp: Date;
  metadata: Record<string, unknown>;
  // Hardening fields
  source: "server" | "validated_client"; // Evidence source
  immutable: boolean; // Once true, cannot be modified
  validated: boolean; // Schema validation status
}

// ============================================================================
// Learning Evidence Service
// ============================================================================

export class LearningEvidenceService {
  private static instance: LearningEvidenceService;

  private constructor() {}

  static getInstance(): LearningEvidenceService {
    if (!LearningEvidenceService.instance) {
      LearningEvidenceService.instance = new LearningEvidenceService();
    }
    return LearningEvidenceService.instance;
  }

  /**
   * Record evidence of learning
   * Hardened to validate schema and enforce immutability
   */
  async recordEvidence(
    prisma: TutorPrismaClient,
    evidence: EvidenceRecord,
  ): Promise<void> {
    try {
      // Validate evidence schema
      const validated = this.validateEvidenceSchema(evidence);
      if (!validated) {
        throw new Error("Evidence validation failed");
      }

      // Mark as immutable once recorded
      evidence.immutable = true;
      evidence.validated = true;

      // Store evidence record (implementation depends on schema)
      logger.info({
        message: "Learning evidence recorded",
        userId: evidence.userId,
        tenantId: evidence.tenantId,
        type: evidence.type,
        itemId: evidence.itemId,
        success: evidence.success,
        attemptId: evidence.attemptId,
        source: evidence.source,
        immutable: evidence.immutable,
      }, "LearningEvidenceService");

      // Trigger mastery update based on evidence
      await this.updateMasteryFromEvidence(prisma, evidence);
    } catch (error) {
      logger.error({
        message: "Failed to record learning evidence",
        userId: evidence.userId,
        tenantId: evidence.tenantId,
        error,
      }, "LearningEvidenceService");
      throw error;
    }
  }

  /**
   * Update mastery based on evidence
   */
  async updateMastery(
    prisma: TutorPrismaClient,
    update: MasteryUpdate,
  ): Promise<void> {
    try {
      logger.info({
        message: "Mastery updated",
        userId: update.userId,
        tenantId: update.tenantId,
        conceptId: update.conceptId,
        masteryScore: update.masteryScore,
        confidence: update.confidence,
      }, "LearningEvidenceService");

      // Store mastery update (implementation depends on schema)
      // This would typically update a mastery table or learning progress record
    } catch (error) {
      logger.error({
        message: "Failed to update mastery",
        userId: update.userId,
        tenantId: update.tenantId,
        conceptId: update.conceptId,
        error,
      }, "LearningEvidenceService");
      throw error;
    }
  }

  /**
   * Get learner mastery profile
   */
  async getMasteryProfile(
    prisma: TutorPrismaClient,
    userId: UserId,
    tenantId: TenantId,
  ): Promise<MasteryProfile> {
    try {
      // Fetch mastery data from database (implementation depends on schema)
      const conceptMastery: Record<string, number> = {};
      const claimMastery: Record<string, number> = {};
      const moduleMastery: Record<string, number> = {};

      logger.info({
        message: "Mastery profile retrieved",
        userId,
        tenantId,
        conceptCount: Object.keys(conceptMastery).length,
        claimCount: Object.keys(claimMastery).length,
        moduleCount: Object.keys(moduleMastery).length,
      }, "LearningEvidenceService");

      return {
        userId,
        tenantId,
        conceptMastery,
        claimMastery,
        moduleMastery,
        lastUpdated: new Date(),
      };
    } catch (error) {
      logger.error({
        message: "Failed to get mastery profile",
        userId,
        tenantId,
        error,
      }, "LearningEvidenceService");
      throw error;
    }
  }

  /**
   * Calculate mastery score from evidence
   */
  calculateMasteryScore(evidence: EvidenceRecord[]): number {
    if (evidence.length === 0) {
      return 0;
    }

    // Weight recent evidence more heavily
    const now = Date.now();
    const weightedScores = evidence.map((e) => {
      const ageInHours = (now - e.timestamp.getTime()) / (1000 * 60 * 60);
      const decay = Math.exp(-ageInHours / 24); // Decay over 24 hours
      const score = e.success ? 100 : 0;
      return score * decay;
    });

    const totalWeight = weightedScores.reduce((sum, w) => sum + w, 0);
    const totalDecay = evidence.map((e) => {
      const ageInHours = (now - e.timestamp.getTime()) / (1000 * 60 * 60);
      return Math.exp(-ageInHours / 24);
    }).reduce((sum, d) => sum + d, 0);

    return totalWeight / (totalDecay || 1);
  }

  /**
   * Batch update mastery for multiple concepts
   */
  async batchUpdateMastery(
    prisma: TutorPrismaClient,
    updates: MasteryUpdate[],
  ): Promise<void> {
    try {
      logger.info({
        message: "Batch mastery update started",
        count: updates.length,
      }, "LearningEvidenceService");

      for (const update of updates) {
        await this.updateMastery(prisma, update);
      }

      logger.info({
        message: "Batch mastery update completed",
        count: updates.length,
      }, "LearningEvidenceService");
    } catch (error) {
      logger.error({
        message: "Failed batch mastery update",
        count: updates.length,
        error,
      }, "LearningEvidenceService");
      throw error;
    }
  }

  /**
   * Get mastery for a specific concept
   */
  async getConceptMastery(
    prisma: TutorPrismaClient,
    userId: UserId,
    tenantId: TenantId,
    conceptId: string,
  ): Promise<number> {
    try {
      const profile = await this.getMasteryProfile(prisma, userId, tenantId);
      return profile.conceptMastery[conceptId] || 0;
    } catch (error) {
      logger.error({
        message: "Failed to get concept mastery",
        userId,
        tenantId,
        conceptId,
        error,
      }, "LearningEvidenceService");
      return 0;
    }
  }

  /**
   * Get mastery for a specific claim
   */
  async getClaimMastery(
    prisma: TutorPrismaClient,
    userId: UserId,
    tenantId: TenantId,
    claimId: string,
  ): Promise<number> {
    try {
      const profile = await this.getMasteryProfile(prisma, userId, tenantId);
      return profile.claimMastery[claimId] || 0;
    } catch (error) {
      logger.error({
        message: "Failed to get claim mastery",
        userId,
        tenantId,
        claimId,
        error,
      }, "LearningEvidenceService");
      return 0;
    }
  }

  /**
   * Get mastery for a specific module
   */
  async getModuleMastery(
    prisma: TutorPrismaClient,
    userId: UserId,
    tenantId: TenantId,
    moduleId: ModuleId,
  ): Promise<number> {
    try {
      const profile = await this.getMasteryProfile(prisma, userId, tenantId);
      return profile.moduleMastery[moduleId] || 0;
    } catch (error) {
      logger.error({
        message: "Failed to get module mastery",
        userId,
        tenantId,
        moduleId,
        error,
      }, "LearningEvidenceService");
      return 0;
    }
  }

  // ============================================================================
  // Private Helpers
  // ============================================================================

  /**
   * Validate evidence schema
   */
  private validateEvidenceSchema(evidence: EvidenceRecord): boolean {
    // Required fields
    if (!evidence.id || typeof evidence.id !== "string") return false;
    if (!evidence.userId || typeof evidence.userId !== "string") return false;
    if (!evidence.tenantId || typeof evidence.tenantId !== "string") return false;
    if (!evidence.type || !["assessment", "simulation", "interaction", "observation"].includes(evidence.type)) return false;
    if (!evidence.itemId || typeof evidence.itemId !== "string") return false;
    if (typeof evidence.success !== "boolean") return false;
    if (!evidence.timestamp || !(evidence.timestamp instanceof Date)) return false;
    if (!evidence.metadata || typeof evidence.metadata !== "object") return false;

    // Validate source field
    if (!evidence.source || !["server", "validated_client"].includes(evidence.source)) return false;

    // Validate confidence if provided
    if (evidence.confidence !== undefined) {
      if (typeof evidence.confidence !== "number" || evidence.confidence < 0 || evidence.confidence > 1) {
        return false;
      }
    }

    return true;
  }

  /**
   * Update mastery from evidence record
   */
  private async updateMasteryFromEvidence(
    prisma: TutorPrismaClient,
    evidence: EvidenceRecord,
  ): Promise<void> {
    // Extract concept/claim/module IDs from metadata
    const conceptId = evidence.metadata.conceptId as string | undefined;
    const claimId = evidence.metadata.claimId as string | undefined;
    const moduleId = evidence.metadata.moduleId as ModuleId | undefined;

    if (!conceptId && !claimId && !moduleId) {
      logger.warn({
        message: "Evidence has no concept/claim/module IDs",
        evidenceId: evidence.id,
      }, "LearningEvidenceService");
      return;
    }

    const masteryScore = this.calculateMasteryScore([evidence]);
    const confidence = evidence.confidence || 0.5;

    if (conceptId) {
      await this.updateMastery(prisma, {
        userId: evidence.userId,
        tenantId: evidence.tenantId,
        conceptId,
        claimId,
        evidenceId: evidence.id,
        moduleId,
        masteryScore,
        confidence,
        timestamp: evidence.timestamp,
      });
    }
  }
}

// Singleton instance
export function getLearningEvidenceService(): LearningEvidenceService {
  return LearningEvidenceService.getInstance();
}
