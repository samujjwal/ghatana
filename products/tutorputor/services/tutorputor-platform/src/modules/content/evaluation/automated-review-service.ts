/**
 * Automated Content Review Service
 *
 * Provides comprehensive automated review capabilities for generated content,
 * examples, animations, simulations, and discovery systems.
 *
 * @doc.type service
 * @doc.purpose Automated quality review and validation of content assets
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";

type ContentAssetType =
  | "learning_claim"
  | "content_example"
  | "simulation"
  | "animation";

type SimulationManifest = {
  parameters?: Array<{ type: string; min?: number; max?: number }>;
  controls?: unknown[];
  realTime?: boolean;
  safetyWarnings?: unknown;
  duration?: number;
  [key: string]: unknown;
};
type AnimationConfig = {
  id?: string;
  title?: string;
  duration?: number;
  keyframes?: Array<{ time?: number }>;
  assets?: string[];
  [key: string]: unknown;
};

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}

function asString(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

function asNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value)
    ? value
    : undefined;
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function extractText(value: unknown): string {
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean")
    return String(value);
  if (Array.isArray(value))
    return value.map(extractText).filter(Boolean).join(" ");
  const record = asRecord(value);
  if (record)
    return Object.values(record).map(extractText).filter(Boolean).join(" ");
  return "";
}

// ============================================================================
// Types
// ============================================================================

export interface ContentReviewResult {
  assetId: string;
  assetType: ContentAssetType;
  overallScore: number; // 0-100
  passed: boolean;
  issues: ReviewIssue[];
  recommendations: string[];
  reviewedAt: Date;
}

export interface ReviewIssue {
  severity: "error" | "warning" | "info";
  category: "completeness" | "quality" | "safety" | "technical" | "pedagogical";
  message: string;
  suggestion?: string;
}

export interface ClaimReviewResult extends ContentReviewResult {
  claimRef: string;
  claimText: string;
  modalities: {
    examples: number;
    simulations: number;
    animations: number;
  };
  completenessScore: number;
  qualityScore: number;
}

export interface ExampleReviewResult extends ContentReviewResult {
  exampleId: string;
  claimAlignment: number; // 0-100
  gradeAppropriateness: number; // 0-100
  uniqueness: number; // 0-100
  domainRelevance: number; // 0-100
}

export interface SimulationReviewResult extends ContentReviewResult {
  simulationId: string;
  manifestCompleteness: number; // 0-100
  schemaValidity: number; // 0-100
  interactivityScore: number; // 0-100
  safetyScore: number; // 0-100
}

export interface AnimationReviewResult extends ContentReviewResult {
  animationId: string;
  configCompleteness: number; // 0-100
  renderability: number; // 0-100
  assetDependencies: string[];
  previewCapability: boolean;
}

export interface DiscoveryReviewResult {
  searchRelevance: number; // 0-100
  recommendationQuality: number; // 0-100
  pathwayLogic: number; // 0-100
  autocompleteAccuracy: number; // 0-100
}

// ============================================================================
// Automated Content Review Service
// ============================================================================

export class AutomatedContentReviewService {
  constructor(private readonly prisma: PrismaClient) {}

  // ---------------------------------------------------------------------------
  // Claim Review
  // ---------------------------------------------------------------------------

  /**
   * Review a learning claim for completeness and quality
   */
  async reviewClaim(
    experienceId: string,
    claimRef: string,
  ): Promise<ClaimReviewResult> {
    const claim = await this.prisma.learningClaim.findFirst({
      where: { experienceId, claimRef },
      include: {
        examples: true,
        simulations: true,
        animations: true,
      },
    });

    if (!claim) {
      throw new Error(
        `Claim ${claimRef} not found in experience ${experienceId}`,
      );
    }

    const issues: ReviewIssue[] = [];
    let completenessScore = 0;
    let qualityScore = 0;

    // Check modality completeness
    const modalities = {
      examples: claim.examples?.length || 0,
      simulations: claim.simulations?.length || 0,
      animations: claim.animations?.length || 0,
    };

    // Completeness scoring
    if (modalities.examples === 0) {
      issues.push({
        severity: "error",
        category: "completeness",
        message: "Claim has no supporting examples",
        suggestion: "Add at least one concrete example to illustrate the claim",
      });
    } else {
      completenessScore += 30;
    }

    if (modalities.simulations === 0) {
      issues.push({
        severity: "warning",
        category: "completeness",
        message: "Claim has no interactive simulations",
        suggestion: "Consider adding a simulation to enhance learning",
      });
    } else {
      completenessScore += 40;
    }

    if (modalities.animations === 0) {
      issues.push({
        severity: "info",
        category: "completeness",
        message: "Claim has no explanatory animations",
        suggestion: "Add animations to visualize complex concepts",
      });
    } else {
      completenessScore += 30;
    }

    // Quality scoring
    qualityScore = await this.scoreClaimQuality(claim);

    const overallScore = (completenessScore + qualityScore) / 2;
    const passed =
      overallScore >= 70 &&
      issues.filter((i) => i.severity === "error").length === 0;

    return {
      assetId: claim.id,
      assetType: "learning_claim",
      overallScore,
      passed,
      issues,
      recommendations: this.generateClaimRecommendations(issues, modalities),
      reviewedAt: new Date(),
      claimRef,
      claimText: (claim as { claimText?: string }).claimText || claim.claimRef,
      modalities,
      completenessScore,
      qualityScore,
    };
  }

  // ---------------------------------------------------------------------------
  // Example Review
  // ---------------------------------------------------------------------------

  /**
   * Review a content example for quality and alignment
   */
  async reviewExample(exampleId: string): Promise<ExampleReviewResult> {
    const example = await this.prisma.claimExample.findUnique({
      where: { id: exampleId },
      include: { claim: true },
    });

    if (!example) {
      throw new Error(`Example ${exampleId} not found`);
    }

    const issues: ReviewIssue[] = [];
    const exampleText = [
      example.title,
      example.description,
      extractText(example.content),
    ]
      .filter(Boolean)
      .join(" ")
      .trim();

    // Claim alignment scoring
    const claimAlignment = await this.scoreClaimAlignment(example);

    // Grade appropriateness scoring
    const gradeAppropriateness = await this.scoreGradeAppropriateness(example);

    // Uniqueness scoring
    const uniqueness = await this.scoreUniqueness(example);

    // Domain relevance scoring
    const domainRelevance = await this.scoreDomainRelevance(example);

    // Quality checks
    if (exampleText.length < 50) {
      issues.push({
        severity: "error",
        category: "quality",
        message: "Example explanation is too short or missing",
        suggestion: "Provide a detailed explanation (at least 50 characters)",
      });
    }

    if (claimAlignment < 60) {
      issues.push({
        severity: "warning",
        category: "pedagogical",
        message: "Example may not align well with the claim",
        suggestion: "Review example content and strengthen connection to claim",
      });
    }

    const overallScore =
      (claimAlignment + gradeAppropriateness + uniqueness + domainRelevance) /
      4;
    const passed =
      overallScore >= 70 &&
      issues.filter((i) => i.severity === "error").length === 0;

    return {
      assetId: example.id,
      assetType: "content_example",
      overallScore,
      passed,
      issues,
      recommendations: this.generateExampleRecommendations(issues),
      reviewedAt: new Date(),
      exampleId,
      claimAlignment,
      gradeAppropriateness,
      uniqueness,
      domainRelevance,
    };
  }

  // ---------------------------------------------------------------------------
  // Simulation Review
  // ---------------------------------------------------------------------------

  /**
   * Review a simulation manifest for completeness and validity
   */
  async reviewSimulation(
    simulationId: string,
  ): Promise<SimulationReviewResult> {
    const simulation = await this.prisma.simulationManifest.findUnique({
      where: { id: simulationId },
    });

    if (!simulation) {
      throw new Error(`Simulation ${simulationId} not found`);
    }

    const issues: ReviewIssue[] = [];
    let manifestCompleteness = 0;
    let schemaValidity = 0;

    try {
      const manifest = (asRecord(simulation.manifest) ??
        {}) as SimulationManifest;

      // Check required fields
      const requiredFields = [
        "id",
        "title",
        "description",
        "parameters",
        "controls",
      ];
      for (const field of requiredFields) {
        if (!manifest[field as keyof SimulationManifest]) {
          issues.push({
            severity: "error",
            category: "completeness",
            message: `Missing required field: ${field}`,
            suggestion: `Add the ${field} field to the simulation manifest`,
          });
        } else {
          manifestCompleteness += 20;
        }
      }

      // Schema validation
      schemaValidity = await this.validateSimulationSchema(manifest);

      // Interactivity scoring
      const interactivityScore = this.scoreInteractivity(manifest);

      // Safety scoring
      const safetyScore = await this.scoreSimulationSafety(manifest);

      // Additional quality checks
      if (!manifest.parameters || manifest.parameters.length === 0) {
        issues.push({
          severity: "warning",
          category: "pedagogical",
          message: "Simulation has no adjustable parameters",
          suggestion: "Add parameters to allow exploration and learning",
        });
      }

      if ((asNumber(manifest.duration) ?? 0) > 600) {
        issues.push({
          severity: "info",
          category: "pedagogical",
          message: "Simulation duration is quite long",
          suggestion:
            "Consider breaking into shorter interactions or adding checkpoints",
        });
      }

      const overallScore =
        (manifestCompleteness +
          schemaValidity +
          interactivityScore +
          safetyScore) /
        4;
      const passed =
        overallScore >= 70 &&
        issues.filter((i) => i.severity === "error").length === 0;

      return {
        assetId: simulation.id,
        assetType: "simulation",
        overallScore,
        passed,
        issues,
        recommendations: this.generateSimulationRecommendations(issues),
        reviewedAt: new Date(),
        simulationId,
        manifestCompleteness,
        schemaValidity,
        interactivityScore,
        safetyScore,
      };
    } catch (_error) {
      issues.push({
        severity: "error",
        category: "technical",
        message: "Invalid JSON in simulation manifest",
        suggestion: "Fix JSON syntax and structure",
      });

      return {
        assetId: simulation.id,
        assetType: "simulation",
        overallScore: 0,
        passed: false,
        issues,
        recommendations: ["Fix JSON structure in simulation manifest"],
        reviewedAt: new Date(),
        simulationId,
        manifestCompleteness: 0,
        schemaValidity: 0,
        interactivityScore: 0,
        safetyScore: 0,
      };
    }
  }

  // ---------------------------------------------------------------------------
  // Animation Review
  // ---------------------------------------------------------------------------

  /**
   * Review an animation configuration for completeness and renderability
   */
  async reviewAnimation(animationId: string): Promise<AnimationReviewResult> {
    const animation = await this.prisma.claimAnimation.findUnique({
      where: { id: animationId },
    });

    if (!animation) {
      throw new Error(`Animation ${animationId} not found`);
    }

    const issues: ReviewIssue[] = [];
    let configCompleteness = 0;
    const assetDependencies: string[] = [];

    try {
      const config = (asRecord(animation.config) ?? {}) as AnimationConfig;

      // Check required fields
      const requiredChecks = [
        { field: "id", present: Boolean(config.id) },
        { field: "title", present: Boolean(config.title ?? animation.title) },
        {
          field: "duration",
          present: Boolean(config.duration ?? animation.duration),
        },
        { field: "keyframes", present: asArray(config.keyframes).length > 0 },
      ];
      for (const check of requiredChecks) {
        if (!check.present) {
          issues.push({
            severity: "error",
            category: "completeness",
            message: `Missing required field: ${check.field}`,
            suggestion: `Add the ${check.field} field to the animation configuration`,
          });
        } else {
          configCompleteness += 25;
        }
      }

      // Check asset dependencies
      assetDependencies.push(
        ...asArray(config.assets).filter(
          (asset): asset is string => typeof asset === "string",
        ),
      );

      // Renderability check
      const renderability = await this.checkAnimationRenderability(config);

      // Additional quality checks
      if ((asNumber(config.duration) ?? animation.duration) > 30) {
        issues.push({
          severity: "info",
          category: "pedagogical",
          message: "Animation duration is quite long",
          suggestion: "Consider shorter animations for better engagement",
        });
      }

      if (!config.keyframes || config.keyframes.length < 2) {
        issues.push({
          severity: "warning",
          category: "quality",
          message: "Animation has insufficient keyframes",
          suggestion: "Add more keyframes for smoother animation",
        });
      }

      const overallScore = (configCompleteness + renderability) / 2;
      const passed =
        overallScore >= 70 &&
        issues.filter((i) => i.severity === "error").length === 0;

      return {
        assetId: animation.id,
        assetType: "animation",
        overallScore,
        passed,
        issues,
        recommendations: this.generateAnimationRecommendations(issues),
        reviewedAt: new Date(),
        animationId,
        configCompleteness,
        renderability,
        assetDependencies,
        previewCapability: renderability > 70,
      };
    } catch (_error) {
      issues.push({
        severity: "error",
        category: "technical",
        message: "Invalid JSON in animation configuration",
        suggestion: "Fix JSON syntax and structure",
      });

      return {
        assetId: animation.id,
        assetType: "animation",
        overallScore: 0,
        passed: false,
        issues,
        recommendations: ["Fix JSON structure in animation configuration"],
        reviewedAt: new Date(),
        animationId,
        configCompleteness: 0,
        renderability: 0,
        assetDependencies,
        previewCapability: false,
      };
    }
  }

  // ---------------------------------------------------------------------------
  // Discovery System Review
  // ---------------------------------------------------------------------------

  /**
   * Review the entire discovery system (search, recommendations, pathways)
   */
  async reviewDiscoverySystem(): Promise<DiscoveryReviewResult> {
    const issues: ReviewIssue[] = [];

    const searchRelevance = await this.testSearchRelevance();
    const recommendationQuality = await this.testRecommendationQuality();
    const pathwayLogic = await this.testPathwayLogic();
    const autocompleteAccuracy = await this.testAutocompleteAccuracy();

    if (searchRelevance < 60) {
      issues.push({
        severity: "error",
        category: "quality",
        message: "Search relevance is below acceptable threshold",
        suggestion: "Improve search ranking algorithm and indexing",
      });
    }

    if (recommendationQuality < 60) {
      issues.push({
        severity: "warning",
        category: "quality",
        message: "Recommendation quality needs improvement",
        suggestion: "Enhance recommendation algorithm with more context",
      });
    }

    return {
      searchRelevance,
      recommendationQuality,
      pathwayLogic,
      autocompleteAccuracy,
    };
  }

  // ---------------------------------------------------------------------------
  // Batch Review Operations
  // ---------------------------------------------------------------------------

  /**
   * Review all content for an experience
   */
  async reviewExperience(experienceId: string): Promise<{
    claims: ClaimReviewResult[];
    examples: ExampleReviewResult[];
    simulations: SimulationReviewResult[];
    animations: AnimationReviewResult[];
    overallScore: number;
    passed: boolean;
  }> {
    const claims = await this.prisma.learningClaim.findMany({
      where: { experienceId },
    });

    const claimReviews = await Promise.all(
      claims.map((claim) => this.reviewClaim(experienceId, claim.claimRef)),
    );

    const examples = await this.prisma.claimExample.findMany({
      where: { experienceId },
    });

    const exampleReviews = await Promise.all(
      examples.map((example) => this.reviewExample(example.id)),
    );

    const simulations = await this.prisma.claimSimulation.findMany({
      where: { experienceId },
    });

    const simulationReviews = await Promise.all(
      simulations.map((sim) => this.reviewSimulation(sim.simulationManifestId)),
    );

    const animations = await this.prisma.claimAnimation.findMany({
      where: { experienceId },
    });

    const animationReviews = await Promise.all(
      animations.map((animation) => this.reviewAnimation(animation.id)),
    );

    const allReviews = [
      ...claimReviews,
      ...exampleReviews,
      ...simulationReviews,
      ...animationReviews,
    ];
    const overallScore =
      allReviews.reduce((sum: number, review) => sum + review.overallScore, 0) /
      allReviews.length;
    const passed =
      overallScore >= 70 && allReviews.every((review) => review.passed);

    return {
      claims: claimReviews,
      examples: exampleReviews,
      simulations: simulationReviews,
      animations: animationReviews,
      overallScore,
      passed,
    };
  }

  // ---------------------------------------------------------------------------
  // Helper Methods - Private
  // ---------------------------------------------------------------------------

  private async scoreClaimQuality(
    claim: Record<string, unknown>,
  ): Promise<number> {
    let score = 0;
    const claimText = asString(claim.text) ?? asString(claim.claimText) ?? "";
    const prerequisites = asArray(claim.prerequisites);
    const contentNeeds = asRecord(claim.contentNeeds);
    const bloomLevel = (asString(claim.bloomLevel) ?? "").toLowerCase();

    // Check claim text quality
    if (claimText.length >= 20) {
      score += 20;
    }

    // Check for prerequisite context
    if (prerequisites.length > 0) {
      score += 20;
    }

    // Check Bloom taxonomy signal
    if (
      [
        "remember",
        "understand",
        "apply",
        "analyze",
        "evaluate",
        "create",
      ].includes(bloomLevel)
    ) {
      score += 20;
    }

    // Check content-needs analysis presence
    if (contentNeeds && Object.keys(contentNeeds).length > 0) {
      score += 20;
    }

    // Check that the claim is substantial enough to guide downstream generation
    if (claimText.split(/\s+/).filter(Boolean).length >= 6) {
      score += 20;
    }

    return Math.min(score, 100);
  }

  private generateClaimRecommendations(
    issues: ReviewIssue[],
    modalities: { examples: number; simulations: number; animations: number },
  ): string[] {
    const recommendations: string[] = [];

    if (modalities.examples === 0) {
      recommendations.push("Add concrete examples to illustrate the claim");
    }

    if (modalities.simulations === 0) {
      recommendations.push(
        "Consider adding interactive simulations for hands-on learning",
      );
    }

    if (modalities.animations === 0) {
      recommendations.push("Add animations to help visualize complex concepts");
    }

    issues.forEach((issue) => {
      if (issue.suggestion) {
        recommendations.push(issue.suggestion);
      }
    });

    return recommendations;
  }

  private async scoreClaimAlignment(example: {
    content?: unknown;
    claim?: { text?: string } | null;
  }): Promise<number> {
    // Simple heuristic-based scoring
    let score = 50; // Base score

    // Check if example references the claim concepts
    const exampleText = extractText(example.content).toLowerCase();
    const claimText = (example.claim?.text ?? "").toLowerCase();
    if (exampleText && claimText) {
      const claimWords = claimText.split(/\s+/);
      const exampleWords = exampleText.split(/\s+/);
      const overlap = claimWords.filter((word: string) =>
        exampleWords.includes(word),
      ).length;
      score += Math.min(overlap * 5, 50);
    }

    return Math.min(score, 100);
  }

  private async scoreGradeAppropriateness(example: {
    content?: unknown;
  }): Promise<number> {
    // Simple heuristic based on complexity
    let score = 70; // Default to appropriate

    const text = extractText(example.content);
    const avgWordLength =
      text
        .split(" ")
        .reduce((sum: number, word: string) => sum + word.length, 0) /
      text.split(" ").length;

    // Adjust score based on complexity
    if (avgWordLength > 8) {
      score -= 20; // Too complex
    } else if (avgWordLength < 4) {
      score -= 10; // Too simple
    }

    return Math.max(0, Math.min(score, 100));
  }

  private async scoreUniqueness(
    _example: Record<string, unknown>,
  ): Promise<number> {
    // Simplified uniqueness scoring - return default score
    // In real implementation would check against other examples
    return 85;
  }

  private async scoreDomainRelevance(example: {
    content?: unknown;
    claim?: { text?: string } | null;
  }): Promise<number> {
    const text = extractText(example.content).toLowerCase();
    const claimTerms = (example.claim?.text ?? "")
      .toLowerCase()
      .split(/\s+/)
      .filter((term) => term.length > 4);

    if (claimTerms.length === 0 || text.length === 0) {
      return 60;
    }

    const keywordMatches = claimTerms.filter((keyword) =>
      text.includes(keyword),
    ).length;
    const relevanceScore = Math.min(
      (keywordMatches / claimTerms.length) * 100,
      100,
    );
    return Math.max(relevanceScore, 60);
  }

  private generateExampleRecommendations(issues: ReviewIssue[]): string[] {
    return issues
      .filter((issue) => issue.suggestion)
      .map((issue) => issue.suggestion!);
  }

  private async validateSimulationSchema(
    manifest: SimulationManifest,
  ): Promise<number> {
    let score = 100;

    // Check parameter definitions
    if (!manifest.parameters || manifest.parameters.length === 0) {
      score -= 30;
    }

    // Check control definitions
    if (!manifest.controls || manifest.controls.length === 0) {
      score -= 30;
    }

    // Check for valid types
    manifest.parameters?.forEach((param) => {
      if (
        !param.type ||
        !["number", "string", "boolean", "range"].includes(param.type)
      ) {
        score -= 10;
      }
    });

    return Math.max(0, score);
  }

  private scoreInteractivity(manifest: SimulationManifest): number {
    let score = 0;

    // Base score for having parameters
    if (manifest.parameters && manifest.parameters.length > 0) {
      score += 30;
    }

    // Bonus for different parameter types
    const paramTypes = new Set(manifest.parameters?.map((p) => p.type));
    score += paramTypes.size * 10;

    // Bonus for controls
    if (manifest.controls && manifest.controls.length > 0) {
      score += 20;
    }

    // Bonus for real-time updates
    if (manifest.realTime) {
      score += 20;
    }

    return Math.min(score, 100);
  }

  private async scoreSimulationSafety(
    manifest: SimulationManifest,
  ): Promise<number> {
    let score = 100;

    // Check for potentially unsafe parameter ranges
    manifest.parameters?.forEach((param) => {
      if (param.type === "range") {
        if (param.min !== undefined && param.max !== undefined) {
          // Check for reasonable ranges
          if (param.min < -1000 || param.max > 1000) {
            score -= 10;
          }
        }
      }
    });

    // Check for safety warnings
    if (
      !manifest.safetyWarnings &&
      manifest.parameters?.some((p) => p.type === "range")
    ) {
      score -= 20;
    }

    return Math.max(0, score);
  }

  private generateSimulationRecommendations(issues: ReviewIssue[]): string[] {
    return issues
      .filter((issue) => issue.suggestion)
      .map((issue) => issue.suggestion!);
  }

  private async checkAnimationRenderability(
    config: AnimationConfig,
  ): Promise<number> {
    let score = 100;

    // Check keyframe structure
    if (!config.keyframes || config.keyframes.length < 2) {
      score -= 40;
    }

    // Check for valid timing
    config.keyframes?.forEach((keyframe) => {
      if (typeof keyframe.time !== "number" || keyframe.time < 0) {
        score -= 10;
      }
    });

    // Check for asset dependencies
    if (config.assets && config.assets.length > 0) {
      // Assume assets exist (would need to check file system in real implementation)
      score += 10;
    }

    return Math.max(0, score);
  }

  private generateAnimationRecommendations(issues: ReviewIssue[]): string[] {
    return issues
      .filter((issue) => issue.suggestion)
      .map((issue) => issue.suggestion!);
  }

  private async testSearchRelevance(): Promise<number> {
    // Mock implementation - would test actual search functionality
    return 65; // Below target
  }

  private async testRecommendationQuality(): Promise<number> {
    // Mock implementation - would test actual recommendation algorithm
    return 60; // Below target
  }

  private async testPathwayLogic(): Promise<number> {
    // Mock implementation - would test actual pathway logic
    return 55; // Below target
  }

  private async testAutocompleteAccuracy(): Promise<number> {
    // Mock implementation - would test actual autocomplete
    return 70; // Meets minimum
  }
}
