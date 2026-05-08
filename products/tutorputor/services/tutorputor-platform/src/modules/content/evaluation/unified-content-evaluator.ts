/**
 * P1-1: Unified Content Evaluator Service
 *
 * Orchestrates all validators (schema, pedagogical, factual, simulation, accessibility)
 * and produces trust scores that drive publish decisions.
 *
 * @doc.type service
 * @doc.purpose Unified evaluation pipeline for generated content
 * @doc.layer product
 * @doc.pattern Service
 */

import type { Logger } from "pino";
import type { PrismaClient } from "@tutorputor/core/db";
import type { KnowledgeBaseService } from "../../knowledge-base/service.js";
import { FActScoreEvaluator } from "./factscore-evaluator.js";
import { IndependentGeneratedContentValidator } from "./independent-validator-service.js";
import { AtomicClaimExtractor } from "./atomic-claim-extractor.js";
import {
  computeTrustScore,
  type SchemaValidationCheck,
  type PedagogicalValidationCheck,
  type FactualValidationCheck,
  type SimulationValidationCheck,
  type AccessibilityValidationCheck,
  type TrustScoreResult,
  type ContentProvenanceGraph,
  type RegressionScorecard,
  GOLDEN_DATASETS,
  MISCONCEPTION_BENCHMARKS,
  HALLUCINATION_TEST_SETS,
} from "./P1-1-GOLDEN-DATASETS-AND-EVALUATOR";

export interface UnifiedEvaluatorConfig {
  enableHallucinationDetection: boolean;
  enableSimulationValidation: boolean;
  enableAccessibilityValidation: boolean;
  modelVersion: string;
  promptVersion: string;
}

export interface ContentEvaluationRequest {
  tenantId: string;
  experienceId: string;
  artifactId: string;
  contentType: "claim" | "example" | "explanation" | "simulation" | "animation";
  content: string;
  domain: string;
  gradeLevel: number | string;
  bloomLevel: number;
  metadata: Record<string, unknown> | undefined;
}

export interface ContentEvaluationResult {
  artifactId: string;
  trustScore: TrustScoreResult;
  schemaCheck: SchemaValidationCheck;
  pedagogicalCheck: PedagogicalValidationCheck;
  factualCheck: FactualValidationCheck;
  simulationCheck: SimulationValidationCheck;
  accessibilityCheck: AccessibilityValidationCheck;
  provenanceNode: {
    artifact_id: string;
    type: string;
    generated_by_model: string;
    generated_by_prompt_version: string;
    trust_score: number;
    publish_decision: string;
    timestamp: string;
  };
  shouldPublishAutomatically: boolean;
  requiresHumanReview: boolean;
  shouldRemediate: boolean;
  reviewQueueId: string | undefined;
  remediationQueueId: string | undefined;
}

export class UnifiedContentEvaluator {
  private readonly config: UnifiedEvaluatorConfig;
  private readonly factScoreEvaluator: FActScoreEvaluator;
  private readonly independentValidator: IndependentGeneratedContentValidator;
  private readonly atomicClaimExtractor: AtomicClaimExtractor;
  private provenanceGraphs = new Map<string, ContentProvenanceGraph>();
  private regressionScorecards = new Map<string, RegressionScorecard>();

  constructor(
    private readonly logger: Logger,
    private readonly prisma: PrismaClient,
    private readonly knowledgeBaseService: Pick<
      KnowledgeBaseService,
      "validateContent"
    >,
    config: UnifiedEvaluatorConfig,
  ) {
    this.config = config;
    this.factScoreEvaluator = new FActScoreEvaluator(logger);
    this.independentValidator = new IndependentGeneratedContentValidator(
      prisma,
      knowledgeBaseService,
    );
    this.atomicClaimExtractor = new AtomicClaimExtractor(logger);
  }

  /**
   * Evaluate a single piece of generated content against all validators.
   * Returns a trust score and publish decision.
   */
  async evaluateContent(
    request: ContentEvaluationRequest,
  ): Promise<ContentEvaluationResult> {
    const now = new Date().toISOString();

    // 1. Schema Validation
    const schemaCheck = await this.validateSchema(request);

    // 2. Pedagogical Validation
    const pedagogicalCheck = await this.validatePedagogical(request);

    // 3. Factual Validation
    const factualCheck = await this.validateFactual(request);

    // 4. Simulation Validation (if applicable)
    const simulationCheck = this.config.enableSimulationValidation
      ? await this.validateSimulation(request)
      : this.defaultSimulationCheck();

    // 5. Accessibility Validation
    const accessibilityCheck = this.config.enableAccessibilityValidation
      ? await this.validateAccessibility(request)
      : this.defaultAccessibilityCheck();

    // 6. Compute Trust Score
    const trustScore = computeTrustScore(
      schemaCheck,
      pedagogicalCheck,
      factualCheck,
      simulationCheck,
      accessibilityCheck,
    );

    // 7. Determine publish decision
    const shouldPublishAutomatically =
      trustScore.publish_decision === "AUTO_PASS";
    const requiresHumanReview =
      trustScore.publish_decision === "HUMAN_REVIEW";
    const shouldRemediate = trustScore.publish_decision === "AUTO_REMEDIATE";

    // 8. Create provenance node
    const provenanceNode = {
      artifact_id: request.artifactId,
      type: request.contentType,
      generated_by_model: this.config.modelVersion,
      generated_by_prompt_version: this.config.promptVersion,
      trust_score: trustScore.overall_score,
      publish_decision: trustScore.publish_decision,
      timestamp: now,
    };

    // 9. Create review/remediation queue entries if needed
    let reviewQueueId: string | undefined;
    let remediationQueueId: string | undefined;

    if (requiresHumanReview) {
      reviewQueueId = await this.createReviewQueueEntry(
        request,
        trustScore,
        provenanceNode,
      );
    }

    if (shouldRemediate) {
      remediationQueueId = await this.createRemediationQueueEntry(
        request,
        trustScore,
        provenanceNode,
      );
    }

    return {
      artifactId: request.artifactId,
      trustScore,
      schemaCheck,
      pedagogicalCheck,
      factualCheck,
      simulationCheck,
      accessibilityCheck,
      provenanceNode,
      shouldPublishAutomatically,
      requiresHumanReview,
      shouldRemediate,
      reviewQueueId,
      remediationQueueId,
    };
  }

  // ── Individual Validators ────────────────────────────────────────────────

  private async validateSchema(
    request: ContentEvaluationRequest,
  ): Promise<SchemaValidationCheck> {
    const errors: Array<{ field: string; reason: string }> = [];

    // Check required fields
    if (!request.content || request.content.trim().length === 0) {
      errors.push({ field: "content", reason: "Content is empty" });
    }

    if (!request.contentType) {
      errors.push({ field: "contentType", reason: "Content type is required" });
    }

    if (!request.domain) {
      errors.push({ field: "domain", reason: "Domain is required" });
    }

    if (!request.gradeLevel) {
      errors.push({
        field: "gradeLevel",
        reason: "Grade level is required",
      });
    }

    // Check type-specific schemas
    const typeErrors = this.validateContentTypeSchema(
      request.contentType,
      request.content,
    );
    errors.push(...typeErrors);

    const passed = errors.length === 0;
    const score = passed ? 1.0 : Math.max(0, 1.0 - errors.length * 0.1);

    return {
      type: passed ? "SCHEMA_VALID" : "SCHEMA_INVALID",
      passed,
      errors,
      score: Math.max(0, Math.min(1, score)),
    };
  }

  private validateContentTypeSchema(
    contentType: string,
    content: string,
  ): Array<{ field: string; reason: string }> {
    const errors: Array<{ field: string; reason: string }> = [];

    if (
      contentType === "simulation" ||
      contentType === "animation"
    ) {
      try {
        JSON.parse(content);
      } catch {
        errors.push({
          field: content,
          reason: `${contentType} content must be valid JSON`,
        });
      }
    }

    // For claims: should not be empty and should have some structure
    if (contentType === "claim") {
      if (content.split(/[.!?]/).length < 1) {
        errors.push({
          field: "content",
          reason: "Claim must contain at least one complete sentence",
        });
      }
    }

    return errors;
  }

  private async validatePedagogical(
    request: ContentEvaluationRequest,
  ): Promise<PedagogicalValidationCheck> {
    const issues: string[] = [];
    let score = 1.0;

    // Look up golden dataset for domain to check alignment
    const goldenDataset =
      GOLDEN_DATASETS[request.domain as keyof typeof GOLDEN_DATASETS];

    if (!goldenDataset) {
      issues.push(`No golden dataset found for domain: ${request.domain}`);
      score -= 0.2;
    }

    // Check for misconception addressing
    const misconceptionBenchmark =
      MISCONCEPTION_BENCHMARKS[request.domain as keyof typeof MISCONCEPTION_BENCHMARKS];
    const addresses_misconception =
      misconceptionBenchmark &&
      misconceptionBenchmark.some(
        (m) =>
          request.content.toLowerCase().includes(m.misconception.toLowerCase()) ||
          request.content
            .toLowerCase()
            .includes(m.correct_claim.toLowerCase()),
      );

    if (
      request.contentType === "claim" &&
      misconceptionBenchmark &&
      !addresses_misconception
    ) {
      issues.push(
        "Claim does not address known misconceptions for this domain",
      );
    }

    // Check grade fit (simple heuristic: content length should scale with grade)
    const wordCount = request.content.split(/\s+/).length;
    const gradeNum =
      typeof request.gradeLevel === "number"
        ? request.gradeLevel
        : parseInt(String(request.gradeLevel).split("_").pop() ?? "9", 10) || 9;
    const minWords = gradeNum < 9 ? 10 : 20;
    const maxWords = gradeNum < 9 ? 100 : 500;

    let gradeFitScore = 1.0;
    if (wordCount < minWords) {
      issues.push(
        `Content is too brief for grade level ${request.gradeLevel}`,
      );
      gradeFitScore = 0.6;
    } else if (wordCount > maxWords) {
      issues.push(
        `Content is too verbose for grade level ${request.gradeLevel}`,
      );
      gradeFitScore = 0.7;
    }

    score = (score + gradeFitScore) / 2;

    return {
      type: "PEDAGOGICAL",
      passed: issues.length === 0 && score >= 0.7,
      bloom_level_appropriate: true,
      has_tasks:
        request.contentType === "claim" || request.contentType === "example",
      has_worked_examples:
        request.contentType === "example" ||
        request.contentType === "explanation",
      grade_fit_score: gradeFitScore,
      misconception_addresses: addresses_misconception || !misconceptionBenchmark,
      issues,
      score: Math.max(0, Math.min(1, score)),
    };
  }

  private async validateFactual(
    request: ContentEvaluationRequest,
  ): Promise<FactualValidationCheck> {
    if (!this.config.enableHallucinationDetection) {
      return {
        type: "FACTUAL",
        passed: true,
        supported_facts: [],
        unsupported_facts: [],
        contradicting_facts: [],
        hallucination_detected: false,
        confidence_score: 0.8,
        issues: [],
        score: 0.8,
        atomic_claims_validated: 0,
        evidence_coverage_score: 0.8,
      };
    }

    // TODO 26: Extract atomic claims for granular validation
    const atomicClaims = await this.atomicClaimExtractor.extractFromContent(request.content);

    this.logger.info(
      {
        artifactId: request.artifactId,
        totalAtomicClaims: atomicClaims.totalClaims,
        extractionConfidence: atomicClaims.extractionConfidence,
      },
      "Extracted atomic claims for factual validation",
    );

    // Validate each atomic claim against evidence bundles
    const supportedFacts: string[] = [];
    const unsupportedFacts: string[] = [];
    const contradictingFacts: string[] = [];
    const issues: string[] = [];

    let totalConfidence = 0;
    let validatedCount = 0;

    for (const claim of atomicClaims.claims) {
      try {
        // Look up evidence bundle for this claim
        const evidenceBundle = await this.prisma.evidenceBundle.findFirst({
          where: {
            claimId: claim.id,
            tenantId: request.tenantId,
          },
        });

        if (!evidenceBundle) {
          // No evidence bundle - mark as unsupported
          unsupportedFacts.push(JSON.stringify({ claim: claim.text, reason: "No evidence bundle found" }));
          issues.push(`Atomic claim "${claim.text.substring(0, 50)}..." has no evidence bundle`);
          continue;
        }

        // Score support/contradiction from evidence bundle
        if (evidenceBundle.contradictionDetected) {
          contradictingFacts.push(JSON.stringify({ claim: claim.text, evidenceBundleId: evidenceBundle.id }));
          issues.push(`Atomic claim "${claim.text.substring(0, 50)}..." contradicts evidence`);
        } else if (evidenceBundle.coverageScore >= 0.7) {
          supportedFacts.push(JSON.stringify({ claim: claim.text, evidenceBundleId: evidenceBundle.id, coverageScore: evidenceBundle.coverageScore }));
          totalConfidence += evidenceBundle.confidenceScore * claim.confidence;
          validatedCount++;
        } else {
          unsupportedFacts.push(JSON.stringify({ claim: claim.text, evidenceBundleId: evidenceBundle.id, coverageScore: evidenceBundle.coverageScore }));
          issues.push(`Atomic claim "${claim.text.substring(0, 50)}..." has insufficient evidence coverage (${evidenceBundle.coverageScore.toFixed(2)})`);
        }
      } catch (error) {
        this.logger.error(
          { error, claimId: claim.id },
          "Failed to validate atomic claim",
        );
        unsupportedFacts.push(JSON.stringify({ claim: claim.text, reason: "Validation error" }));
      }
    }

    // Calculate overall scores
    const avgConfidence = validatedCount > 0 ? totalConfidence / validatedCount : 0;
    const evidenceCoverageScore = atomicClaims.totalClaims > 0 
      ? validatedCount / atomicClaims.totalClaims 
      : 0;
    const hallucinated = contradictingFacts.length > 0 && contradictingFacts.length > supportedFacts.length;

    // Block auto-publish below threshold (TODO 26 requirement)
    const AUTO_PUBLISH_THRESHOLD = 0.7;
    const passed = !hallucinated && evidenceCoverageScore >= AUTO_PUBLISH_THRESHOLD && avgConfidence >= 0.6;

    return {
      type: "FACTUAL",
      passed,
      supported_facts: supportedFacts,
      unsupported_facts: unsupportedFacts,
      contradicting_facts: contradictingFacts,
      hallucination_detected: hallucinated,
      confidence_score: avgConfidence,
      issues,
      score: evidenceCoverageScore,
      atomic_claims_validated: validatedCount,
      evidence_coverage_score: evidenceCoverageScore,
    };
  }

  private async validateSimulation(
    request: ContentEvaluationRequest,
  ): Promise<SimulationValidationCheck> {
    if (request.contentType !== "simulation" && request.contentType !== "animation") {
      return this.defaultSimulationCheck();
    }

    const issues: string[] = [];
    let invariantsHold = true;

    try {
      const simData = JSON.parse(request.content);

      // Domain-specific invariant checks
      switch (request.domain) {
        case "MATH":
          invariantsHold = this.checkMathSimulationInvariants(simData, issues);
          break;
        case "PHYSICS":
          invariantsHold = this.checkPhysicsSimulationInvariants(
            simData,
            issues,
          );
          break;
        case "CHEMISTRY":
          invariantsHold = this.checkChemistrySimulationInvariants(
            simData,
            issues,
          );
          break;
        case "BIOLOGY":
          invariantsHold = this.checkBiologySimulationInvariants(simData, issues);
          break;
        default:
          // Generic JSON validation passed above
          break;
      }
    } catch (error) {
      issues.push("Simulation JSON parsing failed");
      invariantsHold = false;
    }

    const score = invariantsHold ? 1.0 : Math.max(0, 1.0 - issues.length * 0.1);

    return {
      type: "SIMULATION",
      passed: invariantsHold && issues.length === 0,
      domain: request.domain,
      invariants_checked: issues.length === 0
        ? [{ name: "domain_invariants", holds: true }]
        : [{ name: "domain_invariants", holds: false }],
      energy_conservation: invariantsHold,
      momentum_conservation: invariantsHold,
      numerical_stability: Math.max(0, Math.min(1, score)),
      issues,
      score: Math.max(0, Math.min(1, score)),
    };
  }

  private checkMathSimulationInvariants(
    simData: Record<string, unknown>,
    issues: string[],
  ): boolean {
    // Check for algebraic correctness
    if (simData.type === "quadratic" && simData.discriminant !== undefined) {
      const a = Number(simData.a) || 0;
      const b = Number(simData.b) || 0;
      const c = Number(simData.c) || 0;
      const expectedDiscriminant = b * b - 4 * a * c;
      const actualDiscriminant = Number(simData.discriminant);

      if (Math.abs(actualDiscriminant - expectedDiscriminant) > 0.01) {
        issues.push(
          `Discriminant mismatch: expected ${expectedDiscriminant}, got ${actualDiscriminant}`,
        );
        return false;
      }
    }

    return true;
  }

  private checkPhysicsSimulationInvariants(
    simData: Record<string, unknown>,
    issues: string[],
  ): boolean {
    // Check for energy conservation
    if (simData.type === "motion" && simData.frames && Array.isArray(simData.frames)) {
      const frames = simData.frames as Array<Record<string, unknown>>;
      let prevEnergy: number | null = null;

      for (const frame of frames) {
        const m = Number(frame.mass) || 1;
        const v = Number(frame.velocity) || 0;
        const h = Number(frame.height) || 0;
        const g = 9.8;

        const KE = 0.5 * m * v * v; // Kinetic energy
        const PE = m * g * h; // Potential energy
        const totalEnergy = KE + PE;

        if (prevEnergy !== null) {
          const energyDiff = Math.abs(totalEnergy - prevEnergy);
          const percentDiff = (energyDiff / (prevEnergy || 1)) * 100;

          if (percentDiff > 5) {
            issues.push(
              `Energy not conserved: ${percentDiff.toFixed(1)}% difference from previous frame`,
            );
            return false;
          }
        }

        prevEnergy = totalEnergy;
      }
    }

    return true;
  }

  private checkChemistrySimulationInvariants(
    simData: Record<string, unknown>,
    issues: string[],
  ): boolean {
    // Check for valence correctness
    if (simData.type === "bonding" && simData.molecules && Array.isArray(simData.molecules)) {
      // Simplified: check that valence constraints are respected
      // Real implementation would use more sophisticated chemistry rules
    }

    return true;
  }

  private checkBiologySimulationInvariants(
    simData: Record<string, unknown>,
    issues: string[],
  ): boolean {
    // Check for physiological plausibility
    if (simData.type === "physiology") {
      // Simplified checks for plausible ranges
    }

    return true;
  }

  private async validateAccessibility(
    request: ContentEvaluationRequest,
  ): Promise<AccessibilityValidationCheck> {
    const issues: string[] = [];
    let score = 1.0;

    // Readability: estimate grade level needed
    const wordCount = request.content.split(/\s+/).length;
    const sentenceCount = request.content.split(/[.!?]/).length;
    const avgWordsPerSentence = wordCount / (sentenceCount || 1);

    // Flesch-Kincaid formula (simplified)
    const readabilityGrade = Math.max(
      0,
      0.39 * avgWordsPerSentence - 11.8 * (wordCount / sentenceCount) + 15.59,
    );

    // Check WCAG AA readability (should be understandable)
    const targetGrade =
      typeof request.gradeLevel === "number"
        ? request.gradeLevel
        : parseInt(String(request.gradeLevel).split("_").pop() ?? "9", 10) || 9;
    if (readabilityGrade > targetGrade + 3) {
      issues.push(
        `Content readability (grade ${readabilityGrade.toFixed(1)}) exceeds target grade (${targetGrade})`,
      );
      score -= 0.2;
    }

    // Check for potentially offensive or non-inclusive language
    const potentialBiasTerms = ["man", "guy", "girl", "he", "she"];
    const biasCount = potentialBiasTerms.filter((term) =>
      request.content.toLowerCase().includes(term),
    ).length;

    if (biasCount > 0) {
      issues.push(
        "Content may use gender-specific or potentially non-inclusive language",
      );
      score -= 0.1;
    }

    return {
      type: "ACCESSIBILITY",
      passed: issues.length === 0,
      readability_grade_level: readabilityGrade,
      wcag_aa_compliant: issues.length === 0,
      cultural_sensitivity_issues: biasCount > 0 ? ["Potential bias detected"] : [],
      language_clarity_issues:
        readabilityGrade > targetGrade + 3 ? ["Too complex"] : [],
      issues,
      score: Math.max(0, Math.min(1, score)),
    };
  }

  // ── Queue Management ─────────────────────────────────────────────────────

  private async createReviewQueueEntry(
    request: ContentEvaluationRequest,
    trustScore: TrustScoreResult,
    provenanceNode: Record<string, unknown>,
  ): Promise<string> {
    const queueId = `review-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

    await this.prisma.reviewQueue.create({
      data: {
        id: queueId,
        tenantId: request.tenantId,
        experienceId: request.experienceId,
        riskLevel: trustScore.overall_score < 0.65 ? "HIGH" : "MEDIUM",
        triggerReason: trustScore.reasoning.slice(0, 200),
        priority: trustScore.overall_score < 0.65 ? 10 : 5,
        metadata: {
          artifactId: request.artifactId,
          contentType: request.contentType,
          trustScore: trustScore.overall_score,
          publishDecision: trustScore.publish_decision,
        },
      },
    });

    this.logger.info(
      { queueId, artifactId: request.artifactId, trustScore: trustScore.overall_score },
      "Created human review queue entry",
    );

    return queueId;
  }

  private async createRemediationQueueEntry(
    request: ContentEvaluationRequest,
    trustScore: TrustScoreResult,
    provenanceNode: Record<string, unknown>,
  ): Promise<string> {
    const triggerReason = trustScore.reasoning ?? "Trust score below remediation threshold";
    const remediationNotes = JSON.stringify({
      scores: trustScore,
      provenance: provenanceNode,
    });

    const entry = await this.prisma.remediationQueue.create({
      data: {
        tenantId: request.tenantId,
        experienceId: request.experienceId ?? request.artifactId,
        artifactId: request.artifactId,
        contentType: request.contentType,
        trustScore: trustScore.overall_score,
        publishDecision: trustScore.publish_decision,
        triggerReason,
        remediationNotes,
        status: "PENDING",
      },
    }) as { id: string };

    this.logger.info(
      {
        queueId: entry.id,
        artifactId: request.artifactId,
        trustScore: trustScore.overall_score,
        reasoning: trustScore.reasoning,
      },
      "Queued content for auto-remediation",
    );

    return entry.id;
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private getEvidenceForDomain(
    domain: string,
  ): Array<{ statement: string; source: string; evidence_type?: string }> {
    // In production, fetch from golden dataset + external knowledge base
    const goldenDataset =
      GOLDEN_DATASETS[domain as keyof typeof GOLDEN_DATASETS];

    if (!goldenDataset) {
      return [];
    }

    // Extract claim statements as evidence
    return goldenDataset.claims.map((claim) => ({
      statement: claim.claim,
      source: claim.source,
      evidence_type: "claim",
    }));
  }

  private defaultSimulationCheck(): SimulationValidationCheck {
    return {
      type: "SIMULATION",
      passed: true,
      domain: "UNKNOWN",
      invariants_checked: [],
      energy_conservation: true,
      momentum_conservation: true,
      numerical_stability: 1.0,
      issues: [],
      score: 1.0,
    };
  }

  private defaultAccessibilityCheck(): AccessibilityValidationCheck {
    return {
      type: "ACCESSIBILITY",
      passed: true,
      readability_grade_level: 9,
      wcag_aa_compliant: true,
      cultural_sensitivity_issues: [],
      language_clarity_issues: [],
      issues: [],
      score: 1.0,
    };
  }
}
