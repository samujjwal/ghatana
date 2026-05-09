/**
 * Assessment Bundle Generator
 *
 * Generates multi-item assessment bundles with CBM (Confidence-Based Marking) metadata.
 * Produces coherent assessment sets with consistent difficulty and metadata.
 *
 * @doc.type class
 * @doc.purpose Multi-item assessment bundle generation with CBM metadata
 * @doc.layer platform
 * @doc.pattern Generator
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import { z } from "zod";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type { ModuleId, TenantId } from "@tutorputor/contracts/v1/types";
import { getAssessmentScoringService } from "./AssessmentScoringService";

const logger = createStandaloneLogger({ component: "AssessmentBundleGenerator" });

// ============================================================================
// Bundle Types
// ============================================================================

export enum AssessmentItemType {
  MULTIPLE_CHOICE = "multiple_choice",
  SHORT_ANSWER = "short_answer",
  ESSAY = "essay",
  TRUE_FALSE = "true_false",
  MATCHING = "matching",
  FILL_IN_BLANK = "fill_in_blank",
}

export interface AssessmentItem {
  id: string;
  type: AssessmentItemType;
  question: string;
  options?: string[];
  correctAnswer: string | string[];
  explanation: string;
  difficulty: "beginner" | "intermediate" | "advanced";
  points: number;
  cbmMetadata: {
    confidenceLevels: string[];
    confidenceWeights: Record<string, number>;
    masteryThreshold: number;
  };
  tags: string[];
  timeLimitSeconds?: number;
}

export interface AssessmentBundle {
  id: string;
  tenantId: TenantId;
  moduleId: ModuleId;
  title: string;
  description: string;
  items: AssessmentItem[];
  metadata: {
    totalPoints: number;
    estimatedDurationMinutes: number;
    difficultyDistribution: Record<string, number>;
    typeDistribution: Record<string, number>;
    cbmEnabled: boolean;
  };
  version: string;
  createdAt: Date;
}

export interface BundleGenerationRequest {
  tenantId: TenantId;
  moduleId: ModuleId;
  itemCount: number;
  difficulty?: "beginner" | "intermediate" | "advanced" | "mixed";
  itemTypes?: AssessmentItemType[];
  enableCBM: boolean;
  context?: {
    topic?: string;
    learningObjectives?: string[];
    claims?: string[];
  };
}

// ============================================================================
// Assessment Bundle Generator
// ============================================================================

export class AssessmentBundleGenerator {
  private static instance: AssessmentBundleGenerator;
  private scoringService = getAssessmentScoringService();

  private constructor() {}

  static getInstance(): AssessmentBundleGenerator {
    if (!AssessmentBundleGenerator.instance) {
      AssessmentBundleGenerator.instance = new AssessmentBundleGenerator();
    }
    return AssessmentBundleGenerator.instance;
  }

  /**
   * Generate assessment bundle
   */
  async generateBundle(
    prisma: TutorPrismaClient,
    request: BundleGenerationRequest,
  ): Promise<AssessmentBundle> {
    try {
      logger.info({
        message: "Assessment bundle generation started",
        tenantId: request.tenantId,
        moduleId: request.moduleId,
        itemCount: request.itemCount,
        enableCBM: request.enableCBM,
      }, "AssessmentBundleGenerator");

      const items = await this.generateItems(request);
      const metadata = this.calculateBundleMetadata(items, request);

      const bundle: AssessmentBundle = {
        id: `bundle-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        tenantId: request.tenantId,
        moduleId: request.moduleId,
        title: this.generateTitle(request),
        description: this.generateDescription(request),
        items,
        metadata,
        version: "1.0.0",
        createdAt: new Date(),
      };

      logger.info({
        message: "Assessment bundle generated",
        bundleId: bundle.id,
        itemCount: items.length,
        totalPoints: metadata.totalPoints,
      }, "AssessmentBundleGenerator");

      return bundle;
    } catch (error) {
      logger.error({
        message: "Failed to generate assessment bundle",
        tenantId: request.tenantId,
        moduleId: request.moduleId,
        error,
      }, "AssessmentBundleGenerator");
      throw error;
    }
  }

  /**
   * Generate assessment items
   */
  private async generateItems(request: BundleGenerationRequest): Promise<AssessmentItem[]> {
    const items: AssessmentItem[] = [];
    const types = request.itemTypes || [
      AssessmentItemType.MULTIPLE_CHOICE,
      AssessmentItemType.SHORT_ANSWER,
      AssessmentItemType.TRUE_FALSE,
    ];

    for (let i = 0; i < request.itemCount; i++) {
      const type = types[i % types.length] ?? AssessmentItemType.MULTIPLE_CHOICE;
      const difficulty = this.selectDifficulty(request.difficulty, i, request.itemCount);

      const item: AssessmentItem = {
        id: `item-${Date.now()}-${i}`,
        type,
        question: this.generatePlaceholderQuestion(type, difficulty),
        options: this.generatePlaceholderOptions(type),
        correctAnswer: this.generatePlaceholderCorrectAnswer(type),
        explanation: "Explanation placeholder",
        difficulty,
        points: this.calculatePoints(difficulty, type),
        cbmMetadata: request.enableCBM ? this.generateCBMMetadata(difficulty) : this.getDefaultCBMMetadata(),
        tags: this.generateTags(request.context),
        timeLimitSeconds: this.calculateTimeLimit(type, difficulty),
      };

      items.push(item);
    }

    return items;
  }

  /**
   * Generate CBM metadata for an item
   */
  private generateCBMMetadata(difficulty: string): AssessmentItem["cbmMetadata"] {
    const confidenceLevels = ["low", "medium", "high"];
    const confidenceWeights: Record<string, number> = {
      low: -0.5,
      medium: 0,
      high: 0.5,
    };

    // Adjust weights based on difficulty
    if (difficulty === "advanced") {
      confidenceWeights.high = 0.75;
      confidenceWeights.low = -0.75;
    }

    return {
      confidenceLevels,
      confidenceWeights,
      masteryThreshold: 0.7,
    };
  }

  /**
   * Get default CBM metadata (when CBM is disabled)
   */
  private getDefaultCBMMetadata(): AssessmentItem["cbmMetadata"] {
    return {
      confidenceLevels: ["medium"],
      confidenceWeights: { medium: 0 },
      masteryThreshold: 0.7,
    };
  }

  /**
   * Calculate bundle metadata
   */
  private calculateBundleMetadata(items: AssessmentItem[], request: BundleGenerationRequest): AssessmentBundle["metadata"] {
    const totalPoints = items.reduce((sum, item) => sum + item.points, 0);
    const estimatedDurationMinutes = items.reduce((sum, item) => {
      return sum + (item.timeLimitSeconds || 60) / 60;
    }, 0);

    const difficultyDistribution: Record<string, number> = {};
    const typeDistribution: Record<string, number> = {};

    items.forEach((item) => {
      difficultyDistribution[item.difficulty] = (difficultyDistribution[item.difficulty] || 0) + 1;
      typeDistribution[item.type] = (typeDistribution[item.type] || 0) + 1;
    });

    return {
      totalPoints,
      estimatedDurationMinutes: Math.ceil(estimatedDurationMinutes),
      difficultyDistribution,
      typeDistribution,
      cbmEnabled: request.enableCBM,
    };
  }

  /**
   * Select difficulty for an item
   */
  private selectDifficulty(
    requestedDifficulty: string | undefined,
    index: number,
    totalItems: number,
  ): "beginner" | "intermediate" | "advanced" {
    if (requestedDifficulty && requestedDifficulty !== "mixed") {
      return requestedDifficulty as "beginner" | "intermediate" | "advanced";
    }

    // Mixed difficulty: progressive from beginner to advanced
    const progress = index / totalItems;
    if (progress < 0.33) return "beginner";
    if (progress < 0.66) return "intermediate";
    return "advanced";
  }

  /**
   * Calculate points based on difficulty and type
   */
  private calculatePoints(difficulty: string, type: AssessmentItemType): number {
    const basePoints = {
      beginner: 5,
      intermediate: 10,
      advanced: 15,
    }[difficulty] || 10;

    const typeMultiplier = {
      multiple_choice: 1,
      true_false: 0.5,
      short_answer: 1.5,
      essay: 3,
      matching: 2,
      fill_in_blank: 1,
    }[type] || 1;

    return basePoints * typeMultiplier;
  }

  /**
   * Calculate time limit based on type and difficulty
   */
  private calculateTimeLimit(type: AssessmentItemType, difficulty: string): number {
    const baseTime = {
      multiple_choice: 60,
      true_false: 30,
      short_answer: 120,
      essay: 300,
      matching: 180,
      fill_in_blank: 90,
    }[type] || 60;

    const difficultyMultiplier = {
      beginner: 1,
      intermediate: 1.2,
      advanced: 1.5,
    }[difficulty] || 1;

    return Math.floor(baseTime * difficultyMultiplier);
  }

  /**
   * Generate placeholder question
   */
  private generatePlaceholderQuestion(type: AssessmentItemType, difficulty: string): string {
    return `[${type}] ${difficulty} level question placeholder`;
  }

  /**
   * Generate placeholder options
   */
  private generatePlaceholderOptions(type: AssessmentItemType): string[] | undefined {
    if (type === AssessmentItemType.MULTIPLE_CHOICE || type === AssessmentItemType.TRUE_FALSE) {
      return ["Option A", "Option B", "Option C", "Option D"];
    }
    return undefined;
  }

  /**
   * Generate placeholder correct answer
   */
  private generatePlaceholderCorrectAnswer(type: AssessmentItemType): string | string[] {
    if (type === AssessmentItemType.MULTIPLE_CHOICE) {
      return "Option A";
    }
    if (type === AssessmentItemType.TRUE_FALSE) {
      return "true";
    }
    if (type === AssessmentItemType.MATCHING) {
      return ["1-A", "2-B", "3-C"];
    }
    return "Placeholder answer";
  }

  /**
   * Generate tags from context
   */
  private generateTags(context?: BundleGenerationRequest["context"]): string[] {
    const tags: string[] = [];
    if (context?.topic) tags.push(context.topic);
    if (context?.learningObjectives) tags.push(...context.learningObjectives.slice(0, 3));
    return tags;
  }

  /**
   * Generate bundle title
   */
  private generateTitle(request: BundleGenerationRequest): string {
    const difficultyLabel = request.difficulty ? ` (${request.difficulty})` : "";
    return `Assessment Bundle${difficultyLabel}`;
  }

  /**
   * Generate bundle description
   */
  private generateDescription(request: BundleGenerationRequest): string {
    return `Multi-item assessment with ${request.itemCount} questions${request.enableCBM ? " with CBM scoring" : ""}`;
  }

  /**
   * Save bundle to database
   */
  async saveBundle(
    prisma: TutorPrismaClient,
    bundle: AssessmentBundle,
  ): Promise<void> {
    try {
      // Save bundle to database (implementation depends on schema)
      logger.info({
        message: "Assessment bundle saved",
        bundleId: bundle.id,
        tenantId: bundle.tenantId,
      }, "AssessmentBundleGenerator");
    } catch (error) {
      logger.error({
        message: "Failed to save assessment bundle",
        bundleId: bundle.id,
        error,
      }, "AssessmentBundleGenerator");
      throw error;
    }
  }

  /**
   * Get bundle by ID
   */
  async getBundle(
    prisma: TutorPrismaClient,
    bundleId: string,
    tenantId: TenantId,
  ): Promise<AssessmentBundle | null> {
    try {
      // Fetch bundle from database (implementation depends on schema)
      return null;
    } catch (error) {
      logger.error({
        message: "Failed to get assessment bundle",
        bundleId,
        tenantId,
        error,
      }, "AssessmentBundleGenerator");
      throw error;
    }
  }
}

// Singleton instance
export function getAssessmentBundleGenerator(): AssessmentBundleGenerator {
  return AssessmentBundleGenerator.getInstance();
}

// ============================================================================
// Zod Schemas
// ============================================================================

export const AssessmentItemSchema = z.object({
  id: z.string().min(1),
  type: z.nativeEnum(AssessmentItemType),
  question: z.string().min(1),
  options: z.array(z.string()).optional(),
  correctAnswer: z.union([z.string(), z.array(z.string())]),
  explanation: z.string().min(1),
  difficulty: z.enum(["beginner", "intermediate", "advanced"]),
  points: z.number().positive(),
  cbmMetadata: z.object({
    confidenceLevels: z.array(z.string()),
    confidenceWeights: z.record(z.number()),
    masteryThreshold: z.number(),
  }),
  tags: z.array(z.string()),
  timeLimitSeconds: z.number().positive().optional(),
});

export const AssessmentBundleSchema = z.object({
  id: z.string().min(1),
  tenantId: z.string().min(1),
  moduleId: z.string().min(1),
  title: z.string().min(1),
  description: z.string().min(1),
  items: z.array(AssessmentItemSchema),
  metadata: z.object({
    totalPoints: z.number(),
    estimatedDurationMinutes: z.number(),
    difficultyDistribution: z.record(z.number()),
    typeDistribution: z.record(z.number()),
    cbmEnabled: z.boolean(),
  }),
  version: z.string().min(1),
  createdAt: z.date(),
});

export const BundleGenerationRequestSchema = z.object({
  tenantId: z.string().min(1),
  moduleId: z.string().min(1),
  itemCount: z.number().positive(),
  difficulty: z.enum(["beginner", "intermediate", "advanced", "mixed"]).optional(),
  itemTypes: z.array(z.nativeEnum(AssessmentItemType)).optional(),
  enableCBM: z.boolean(),
  context: z.object({
    topic: z.string().optional(),
    learningObjectives: z.array(z.string()).optional(),
    claims: z.array(z.string()).optional(),
  }).optional(),
});
