/**
 * Evaluation Service
 *
 * Evaluates generated content against quality dimensions before publish.
 * Produces EvaluationRecord scorecards that drive publish recommendations.
 *
 * Dimensions:
 *   - Coherence: claim-evidence-task alignment
 *   - Completeness: all required artifact types present
 *   - Safety: no harmful/inappropriate content detected
 *   - Accessibility: grade-level language appropriateness
 *   - ManifestValidity: simulation/animation/assessment manifests are valid
 *
 * @doc.type class
 * @doc.purpose Evaluate generated content and produce scorecards
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";

type PublishRecommendation = "block" | "manual_review" | "auto_publish";

interface EvaluationIssue {
  dimension: string;
  severity: "warning" | "error";
  message: string;
  detail?: string;
}

interface EvaluationRecord {
  id: string;
  tenantId: string;
  assetId?: string;
  generationJobId?: string;
  generationRequestId?: string;
  coherenceScore?: number;
  completenessScore?: number;
  safetyScore?: number;
  accessibilityScore?: number;
  manifestValidityScore?: number;
  overallScore?: number;
  status: "passed" | "failed";
  recommendation: PublishRecommendation;
  issues?: EvaluationIssue[];
  diagnostics?: Record<string, unknown>;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

interface EvaluationScorecard {
  evaluationId: string;
  generationRequestId: string;
  overallScore: number;
  recommendation: PublishRecommendation;
  dimensions: {
    coherence: number;
    completeness: number;
    safety: number;
    accessibility: number;
    manifestValidity: number;
  };
  issues: EvaluationIssue[];
  blockedReasons: string[];
}

// ---------------------------------------------------------------------------
// Internal scorer helpers
// ---------------------------------------------------------------------------

function scoreCoherence(outputData: Record<string, unknown>): {
  score: number;
  issues: EvaluationIssue[];
} {
  const issues: EvaluationIssue[] = [];
  const claims = (outputData["claims"] as any[] | undefined) ?? [];
  const examples = (outputData["examples"] as any[] | undefined) ?? [];

  // Each example and assessment should map to at least one claim
  if (claims.length === 0) {
    issues.push({
      dimension: "coherence",
      severity: "warning",
      message: "No claims found",
      detail: "Content lacks foundational claim coverage",
    });
  }
  if (examples.length > 0 && claims.length === 0) {
    issues.push({
      dimension: "coherence",
      severity: "error",
      message: "Examples exist without supporting claims",
    });
  }

  const score = issues.some((i) => i.severity === "error")
    ? 0.3
    : claims.length > 0
      ? 0.9
      : 0.6;
  return { score, issues };
}

function scoreCompleteness(
  outputData: Record<string, unknown>,
  jobType: string,
): { score: number; issues: EvaluationIssue[] } {
  const issues: EvaluationIssue[] = [];
  const requiredKeys: Record<string, string[]> = {
    claim: ["claims", "count"],
    explainer: ["examples", "count"],
    worked_example: ["examples", "count"],
    simulation: ["simulations"],
    animation: ["animations"],
    assessment: ["assessments"],
    evaluation: ["evaluationStatus"],
  };

  const keys = requiredKeys[jobType] ?? [];
  const missing = keys.filter((k) => !(k in outputData));

  missing.forEach((k) =>
    issues.push({
      dimension: "completeness",
      severity: "warning",
      message: `Missing output key: ${k}`,
    }),
  );

  const score = missing.length === 0 ? 1.0 : 1.0 - missing.length * 0.2;
  return { score: Math.max(0, score), issues };
}

function scoreSafety(outputData: Record<string, unknown>): {
  score: number;
  issues: EvaluationIssue[];
} {
  // Lightweight safety check on text fields
  const HIGH_RISK = /\b(hate|violence|illegal|explicit|profan)/i;
  const text = JSON.stringify(outputData);

  const issues: EvaluationIssue[] = [];
  if (HIGH_RISK.test(text)) {
    issues.push({
      dimension: "safety",
      severity: "error",
      message: "Potentially harmful language detected",
    });
  }

  return { score: issues.length === 0 ? 1.0 : 0.1, issues };
}

function scoreAccessibility(
  outputData: Record<string, unknown>,
  gradeLevel?: string,
): { score: number; issues: EvaluationIssue[] } {
  const issues: EvaluationIssue[] = [];

  // Very large documents may be inaccessible
  const len = JSON.stringify(outputData).length;
  if (len > 20_000) {
    issues.push({
      dimension: "accessibility",
      severity: "warning",
      message: "Output is very long; may overwhelm learners",
      detail: `${len} characters`,
    });
  }

  if (gradeLevel && (gradeLevel.includes("4") || gradeLevel.includes("5"))) {
    // For young learners, flag anything that looks technically dense
    if (len > 8_000) {
      issues.push({
        dimension: "accessibility",
        severity: "warning",
        message: "Content may be too dense for young learners",
      });
    }
  }

  return { score: issues.length === 0 ? 0.95 : 0.7, issues };
}

function scoreManifestValidity(
  outputData: Record<string, unknown>,
  jobType: string,
): { score: number; issues: EvaluationIssue[] } {
  const issues: EvaluationIssue[] = [];

  const MANIFEST_TYPES = ["simulation", "animation"];
  if (!MANIFEST_TYPES.includes(jobType)) {
    return { score: 1.0, issues };
  }

  const manifests =
    (outputData["simulations"] as any[]) ??
    (outputData["animations"] as any[]) ??
    [];

  manifests.forEach((m, idx) => {
    if (!m.id) {
      issues.push({
        dimension: "manifest_validity",
        severity: "error",
        message: `Manifest[${idx}] missing id`,
      });
    }
    if (!m.title) {
      issues.push({
        dimension: "manifest_validity",
        severity: "warning",
        message: `Manifest[${idx}] missing title`,
      });
    }
  });

  const score = issues.some((i) => i.severity === "error") ? 0.3 : 1.0;
  return { score, issues };
}

function deriveRecommendation(
  overallScore: number,
  issues: EvaluationIssue[],
): PublishRecommendation {
  const hasBlocker = issues.some((i) => i.severity === "error");
  if (hasBlocker || overallScore < 0.5) return "block";
  if (overallScore < 0.75) return "manual_review";
  return "auto_publish";
}

// ---------------------------------------------------------------------------
// Mapper
// ---------------------------------------------------------------------------

function mapRecord(row: Record<string, unknown>): EvaluationRecord {
  return {
    id: row.id,
    tenantId: row.tenantId,
    assetId: row.assetId ?? undefined,
    generationJobId: row.generationJobId ?? undefined,
    generationRequestId: row.generationRequestId ?? undefined,
    coherenceScore: row.coherenceScore ?? undefined,
    completenessScore: row.completenessScore ?? undefined,
    safetyScore: row.safetyScore ?? undefined,
    accessibilityScore: row.accessibilityScore ?? undefined,
    manifestValidityScore: row.manifestValidityScore ?? undefined,
    overallScore: row.overallScore ?? undefined,
    status: (row.status as string).toLowerCase() as EvaluationRecord["status"],
    recommendation: (row.recommendation as string)
      .toLowerCase()
      .replace(/_/g, "_") as PublishRecommendation,
    issues: row.issues ?? undefined,
    diagnostics: row.diagnostics ?? undefined,
    errorMessage: row.errorMessage ?? undefined,
    createdAt: (row.createdAt as Date).toISOString(),
    updatedAt: (row.updatedAt as Date).toISOString(),
  };
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class EvaluationService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Evaluate all completed jobs in a generation request.
   * Creates one EvaluationRecord per job.
   */
  async evaluateGenerationRequest(
    tenantId: string,
    generationRequestId: string,
  ): Promise<EvaluationScorecard> {
    // Fetch the request and all COMPLETED jobs
    const request = await (this.prisma as any).generationRequest.findFirst({
      where: { id: generationRequestId, tenantId },
      include: { jobs: { where: { status: "COMPLETED" } } },
    });

    if (!request) {
      throw new Error(`Generation request ${generationRequestId} not found`);
    }

    // Delete any previous evaluation records for this request
    await (this.prisma as any).evaluationRecord.deleteMany({
      where: { generationRequestId },
    });

    const allIssues: EvaluationIssue[] = [];
    const jobRecords: EvaluationRecord[] = [];

    for (const job of request.jobs) {
      const record = await this.evaluateJob(tenantId, job, generationRequestId);
      jobRecords.push(record);
      allIssues.push(...(record.issues ?? []));
    }

    const scores = jobRecords
      .filter((r) => r.overallScore !== undefined)
      .map((r) => r.overallScore!);

    const avgScore =
      scores.length > 0 ? scores.reduce((a, b) => a + b, 0) / scores.length : 0;

    const recommendation = deriveRecommendation(avgScore, allIssues);
    const blockedReasons = allIssues
      .filter((i) => i.severity === "error")
      .map((i) => i.message);

    return {
      evaluationId: `eval-${generationRequestId}`,
      generationRequestId,
      overallScore: avgScore,
      recommendation,
      dimensions: {
        coherence: avg(
          jobRecords
            .map((r) => r.coherenceScore)
            .filter((s): s is number => s !== undefined),
        ),
        completeness: avg(
          jobRecords
            .map((r) => r.completenessScore)
            .filter((s): s is number => s !== undefined),
        ),
        safety: avg(
          jobRecords
            .map((r) => r.safetyScore)
            .filter((s): s is number => s !== undefined),
        ),
        accessibility: avg(
          jobRecords
            .map((r) => r.accessibilityScore)
            .filter((s): s is number => s !== undefined),
        ),
        manifestValidity: avg(
          jobRecords
            .map((r) => r.manifestValidityScore)
            .filter((s): s is number => s !== undefined),
        ),
      },
      issues: allIssues,
      blockedReasons,
    };
  }

  /**
   * Evaluate a single generation job and persist an EvaluationRecord.
   */
  async evaluateJob(
    tenantId: string,
    job: any,
    generationRequestId?: string,
  ): Promise<EvaluationRecord> {
    const outputData = (job.outputData as Record<string, unknown> | null) ?? {};
    const jobType = (job.jobType as string).toLowerCase();
    const gradeLevel = (job.parameters as any)?.gradeLevel as
      | string
      | undefined;

    const coherence = scoreCoherence(outputData);
    const completeness = scoreCompleteness(outputData, jobType);
    const safety = scoreSafety(outputData);
    const accessibility = scoreAccessibility(outputData, gradeLevel);
    const manifestValidity = scoreManifestValidity(outputData, jobType);

    const allIssues = [
      ...coherence.issues,
      ...completeness.issues,
      ...safety.issues,
      ...accessibility.issues,
      ...manifestValidity.issues,
    ];

    const overall =
      coherence.score * 0.25 +
      completeness.score * 0.25 +
      safety.score * 0.3 +
      accessibility.score * 0.1 +
      manifestValidity.score * 0.1;

    const recommendation = deriveRecommendation(overall, allIssues);
    const status: "passed" | "failed" =
      recommendation === "block" ? "failed" : "passed";
    const assetId = resolveAssetId(job);

    const created = await (this.prisma as any).evaluationRecord.create({
      data: {
        tenantId,
        assetId,
        generationJobId: job.id,
        generationRequestId: generationRequestId ?? null,
        coherenceScore: coherence.score,
        completenessScore: completeness.score,
        safetyScore: safety.score,
        accessibilityScore: accessibility.score,
        manifestValidityScore: manifestValidity.score,
        overallScore: overall,
        status: status.toUpperCase(),
        recommendation: recommendation.toUpperCase().replace(/-/g, "_"),
        issues: allIssues.length > 0 ? allIssues : null,
        diagnostics: { jobType, jobId: job.id },
      },
    });

    if (assetId) {
      await (this.prisma as any).contentAsset.update({
        where: { id: assetId },
        data: {
          qualityScore: overall,
          reviewState: JSON.stringify({
            source: "evaluation",
            status,
            recommendation,
            overallScore: overall,
            updatedAt: new Date().toISOString(),
          }),
        },
      });
    }

    return mapRecord(created);
  }

  /**
   * Get evaluation records for a generation request.
   */
  async getEvaluationsByRequest(
    tenantId: string,
    generationRequestId: string,
  ): Promise<EvaluationRecord[]> {
    const records = await (this.prisma as any).evaluationRecord.findMany({
      where: { tenantId, generationRequestId },
      orderBy: { createdAt: "desc" },
    });
    return records.map(mapRecord);
  }

  /**
   * Get a single evaluation record.
   */
  async getEvaluation(
    tenantId: string,
    evaluationId: string,
  ): Promise<EvaluationRecord | null> {
    const record = await (this.prisma as any).evaluationRecord.findFirst({
      where: { id: evaluationId, tenantId },
    });
    return record ? mapRecord(record) : null;
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function avg(values: number[]): number {
  if (values.length === 0) return 0;
  return values.reduce((a, b) => a + b, 0) / values.length;
}

function resolveAssetId(job: {
  outputAssetId?: string | null;
  outputData?: Record<string, unknown> | null;
}): string | null {
  if (typeof job.outputAssetId === "string" && job.outputAssetId.length > 0) {
    return job.outputAssetId;
  }

  const outputData = job.outputData;
  if (
    outputData &&
    typeof outputData["assetId"] === "string" &&
    outputData["assetId"].length > 0
  ) {
    return outputData["assetId"] as string;
  }

  return null;
}
