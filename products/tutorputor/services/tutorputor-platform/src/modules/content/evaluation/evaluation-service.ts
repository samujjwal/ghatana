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
import { Prisma } from "@tutorputor/core/db";

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

interface ApprovedArtifactBaseline {
  assetId: string;
  currentVersion: number;
  promptHash?: string;
  qualityScore?: number;
  reviewState?: Record<string, unknown>;
  latestEvaluationId?: string;
  latestEvaluationScore?: number;
  latestEvaluationRecommendation?: string;
  textLength: number;
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function asString(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function parseJsonRecord(value: unknown): Record<string, unknown> | undefined {
  if (typeof value !== "string" || value.trim().length === 0) {
    return undefined;
  }

  try {
    return asRecord(JSON.parse(value));
  } catch {
    return undefined;
  }
}

function readRequiredFlag(value: unknown): boolean {
  const record = asRecord(value);
  return record?.["required"] === true;
}

function countEvidenceSignals(value: unknown): number {
  const output = asRecord(value);
  if (!output) {
    return 0;
  }

  const topLevelEvidence = [
    ...asArray(output["evidence"]),
    ...asArray(output["evidenceRefs"]),
    ...asArray(output["tasks"]),
    ...asArray(output["assessments"]),
  ].length;

  const claimEvidence = asArray(output["claims"]).reduce<number>((count, claim) => {
    const claimRecord = asRecord(claim);
    if (!claimRecord) {
      return count;
    }

    const embeddedEvidence = [
      ...asArray(claimRecord["evidence"]),
      ...asArray(claimRecord["evidenceRefs"]),
      ...asArray(claimRecord["tasks"]),
      ...asArray(claimRecord["assessments"]),
    ].length;

    return count + embeddedEvidence;
  }, 0);

  return topLevelEvidence + claimEvidence;
}

function deriveRequestCoverageIssues(
  jobs: Array<{ outputData: unknown }>,
): EvaluationIssue[] {
  const issues: EvaluationIssue[] = [];

  const coverage = jobs.reduce(
    (state, job) => {
      const output = asRecord(job.outputData);
      if (!output) {
        return state;
      }

      state.examples += asArray(output["examples"]).length;
      state.simulations += asArray(output["simulations"]).length;
      state.animations += asArray(output["animations"]).length;
      state.evidence += countEvidenceSignals(output);

      for (const claim of asArray(output["claims"])) {
        const claimRecord = asRecord(claim);
        if (!claimRecord) {
          continue;
        }

        state.claims += 1;
        const contentNeeds =
          asRecord(claimRecord["contentNeeds"]) ??
          asRecord(claimRecord["content_needs"]);

        if (readRequiredFlag(contentNeeds?.["examples"])) {
          state.requiredExamples += 1;
        }
        if (readRequiredFlag(contentNeeds?.["simulation"])) {
          state.requiredSimulations += 1;
        }
        if (readRequiredFlag(contentNeeds?.["animation"])) {
          state.requiredAnimations += 1;
        }
      }

      return state;
    },
    {
      claims: 0,
      examples: 0,
      simulations: 0,
      animations: 0,
      evidence: 0,
      requiredExamples: 0,
      requiredSimulations: 0,
      requiredAnimations: 0,
    },
  );

  if (coverage.claims === 0) {
    return issues;
  }

  if (coverage.examples === 0) {
    issues.push({
      dimension: "coverage",
      severity: "error",
      message: "Claims do not yet have any concrete examples",
      detail:
        coverage.requiredExamples > 0
          ? `${coverage.requiredExamples} claim${coverage.requiredExamples === 1 ? "" : "s"} explicitly require example coverage.`
          : "At least one example should ground the generated claim set before review.",
    });
  }

  if (coverage.evidence === 0) {
    issues.push({
      dimension: "evidence",
      severity: "error",
      message: "Generated content has no evidence-producing task or assessment coverage",
      detail:
        "Add linked tasks, assessments, or explicit evidence references before manual review.",
    });
  }

  if (coverage.requiredSimulations > 0 && coverage.simulations === 0) {
    issues.push({
      dimension: "coverage",
      severity: "warning",
      message: "Claims requiring simulations are missing interactive modality coverage",
      detail: `${coverage.requiredSimulations} claim${coverage.requiredSimulations === 1 ? "" : "s"} requested simulation support.`,
    });
  }

  if (coverage.requiredAnimations > 0 && coverage.animations === 0) {
    issues.push({
      dimension: "coverage",
      severity: "warning",
      message: "Claims requiring animations are missing visual modality coverage",
      detail: `${coverage.requiredAnimations} claim${coverage.requiredAnimations === 1 ? "" : "s"} requested animation support.`,
    });
  }

  return issues;
}

function extractSnapshotTextLength(snapshot: unknown): number {
  const record = asRecord(snapshot);
  if (!record) {
    return 0;
  }

  const blocks = asArray(record["blocks"]);
  const blockText = blocks
    .map((block) => JSON.stringify(block))
    .join(" ")
    .trim().length;

  return Math.max(JSON.stringify(record).length, blockText);
}

function isApprovedBaseline(baseline: ApprovedArtifactBaseline): boolean {
  const reviewStatus = asString(baseline.reviewState?.["status"]);
  const reviewRecommendation = asString(baseline.reviewState?.["recommendation"]);
  const latestRecommendation = baseline.latestEvaluationRecommendation;

  return (
    reviewStatus === "approved" ||
    reviewRecommendation === "auto_publish" ||
    latestRecommendation === "AUTO_PUBLISH" ||
    baseline.qualityScore !== undefined
  );
}

function deriveApprovedBaselineIssues(input: {
  outputData: Record<string, unknown>;
  overallScore: number;
  baseline?: ApprovedArtifactBaseline;
}): EvaluationIssue[] {
  const baseline = input.baseline;
  if (!baseline || !isApprovedBaseline(baseline)) {
    return [];
  }

  const issues: EvaluationIssue[] = [];
  const baselineScoreCandidates = [
    baseline.latestEvaluationScore,
    baseline.qualityScore,
  ].filter((score): score is number => typeof score === "number");
  const baselineScore =
    baselineScoreCandidates.length > 0
      ? Math.max(...baselineScoreCandidates)
      : undefined;

  if (
    baselineScore !== undefined &&
    input.overallScore < baselineScore - 0.1
  ) {
    issues.push({
      dimension: "regression",
      severity: "error",
      message: "Regenerated artifact quality regressed below the approved baseline",
      detail: `Approved baseline score ${baselineScore.toFixed(2)} is materially higher than regenerated score ${input.overallScore.toFixed(2)}.`,
    });
  }

  const regeneratedTextLength = JSON.stringify(input.outputData).length;
  if (
    baseline.textLength > 0 &&
    regeneratedTextLength > 0 &&
    regeneratedTextLength < baseline.textLength * 0.6
  ) {
    issues.push({
      dimension: "regression",
      severity: "error",
      message: "Regenerated artifact is materially thinner than the approved baseline",
      detail: `Approved baseline content footprint ${baseline.textLength} shrank to ${regeneratedTextLength}.`,
    });
  }

  return issues;
}

function collectTextSegments(value: unknown): string[] {
  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed.length > 0 ? [trimmed] : [];
  }

  if (Array.isArray(value)) {
    return value.flatMap((entry) => collectTextSegments(entry));
  }

  const record = asRecord(value);
  if (!record) {
    return [];
  }

  return Object.values(record).flatMap((entry) => collectTextSegments(entry));
}

function hasStructuredSignal(
  value: unknown,
  matcher: (key: string, entry: unknown) => boolean,
): boolean {
  if (Array.isArray(value)) {
    return value.some((entry) => hasStructuredSignal(entry, matcher));
  }

  const record = asRecord(value);
  if (!record) {
    return false;
  }

  return Object.entries(record).some(
    ([key, entry]) => matcher(key, entry) || hasStructuredSignal(entry, matcher),
  );
}

function extractAssessmentItems(
  outputData: Record<string, unknown>,
): Array<Record<string, unknown>> {
  return asArray(outputData["assessments"]).flatMap((entry) => {
    const record = asRecord(entry);
    if (!record) {
      return [];
    }

    const nestedItems = asArray(record["items"])
      .map((item) => asRecord(item))
      .filter((item): item is Record<string, unknown> => Boolean(item));

    return nestedItems.length > 0 ? nestedItems : [record];
  });
}

function normalizeGradeLevel(
  gradeLevel?: string,
): "young" | "middle" | "older" | "advanced" | "unknown" {
  const normalized = gradeLevel?.toLowerCase().trim();
  if (!normalized) {
    return "unknown";
  }

  if (normalized.includes("k_2") || normalized.includes("grade_3_5")) {
    return "young";
  }
  if (normalized.includes("grade_6_8")) {
    return "middle";
  }
  if (normalized.includes("grade_9_12")) {
    return "older";
  }
  if (normalized.includes("undergraduate") || normalized.includes("graduate")) {
    return "advanced";
  }
  return "unknown";
}

function scorePedagogy(
  outputData: Record<string, unknown>,
  jobType: string,
  gradeLevel?: string,
): { score: number; issues: EvaluationIssue[] } {
  const issues: EvaluationIssue[] = [];
  const textSegments = collectTextSegments(outputData);
  const combinedText = textSegments.join(" ");
  const sentences = combinedText
    .split(/[.!?]+/)
    .map((sentence) => sentence.trim())
    .filter((sentence) => sentence.length > 0);
  const words = combinedText
    .split(/\s+/)
    .map((word) => word.replace(/[^a-zA-Z0-9'-]/g, ""))
    .filter((word) => word.length > 0);
  const averageSentenceLength =
    sentences.length > 0
      ? sentences.reduce(
          (total, sentence) =>
            total + sentence.split(/\s+/).filter(Boolean).length,
          0,
        ) / sentences.length
      : 0;
  const complexWordCount = words.filter((word) => word.length >= 13).length;
  const gradeBand = normalizeGradeLevel(gradeLevel);
  const scaffoldPattern =
    /\b(first|next|then|finally|step\s*\d+|hint|check your work|worked example|watch for|try this)\b/i;
  const misconceptionPattern =
    /\b(common mistake|misconception|pitfall|do not confuse|watch for this error|incorrectly assume)\b/i;
  const hasScaffoldingSignal =
    hasStructuredSignal(outputData, (key, entry) => {
      const lowered = key.toLowerCase();
      return (
        [
          "steps",
          "hint",
          "hints",
          "guidance",
          "scaffolding",
          "workedsteps",
          "checkpoints",
          "instructions",
        ].includes(lowered) &&
        ((Array.isArray(entry) && entry.length > 0) || typeof entry === "string")
      );
    }) || scaffoldPattern.test(combinedText);
  const hasMisconceptionSignal =
    hasStructuredSignal(outputData, (key, entry) => {
      const lowered = key.toLowerCase();
      return (
        [
          "misconception",
          "misconceptions",
          "misconceptiontarget",
          "misconceptionnote",
          "commonmistake",
          "commonmistakes",
          "pitfall",
          "pitfalls",
          "distractors",
        ].includes(lowered) &&
        ((Array.isArray(entry) && entry.length > 0) || typeof entry === "string")
      );
    }) || misconceptionPattern.test(combinedText);

  if (
    (gradeBand === "young" && (averageSentenceLength > 16 || complexWordCount >= 6)) ||
    (gradeBand === "middle" && averageSentenceLength > 24)
  ) {
    issues.push({
      dimension: "pedagogy",
      severity: "warning",
      message: "Difficulty appears too dense for the target grade range",
      detail: `Average sentence length ${averageSentenceLength.toFixed(1)} words with ${complexWordCount} very long words.`,
    });
  }

  if (
    ["claim", "explainer", "worked_example", "assessment"].includes(jobType) &&
    !hasScaffoldingSignal
  ) {
    issues.push({
      dimension: "pedagogy",
      severity: jobType === "assessment" ? "error" : "warning",
      message: "Content lacks explicit scaffolding for the learner",
      detail:
        "Add ordered steps, hints, or guided checkpoints so learners can progress through the idea deliberately.",
    });
  }

  if (
    ["claim", "explainer", "worked_example", "assessment"].includes(jobType) &&
    !hasMisconceptionSignal
  ) {
    issues.push({
      dimension: "pedagogy",
      severity: "warning",
      message: "Content does not anticipate likely learner misconceptions",
      detail:
        "Call out a common mistake, distractor rationale, or misconception target before review.",
    });
  }

  if (jobType === "assessment") {
    const assessmentItems = extractAssessmentItems(outputData);

    if (assessmentItems.length === 0) {
      issues.push({
        dimension: "pedagogy",
        severity: "error",
        message: "Assessment output is missing assessment items",
      });
    }

    assessmentItems.forEach((item, index) => {
      const prompt =
        asString(item["prompt"]) ??
        asString(item["question"]) ??
        asString(item["stem"]);
      const answer =
        asString(item["correctAnswer"]) ??
        asString(item["answer"]) ??
        asString(item["expectedResponse"]) ??
        asString(item["rubric"]);
      const rationale =
        asString(item["explanation"]) ??
        asString(item["feedback"]) ??
        asString(item["rationale"]) ??
        asString(item["workedSolution"]);

      if (!prompt) {
        issues.push({
          dimension: "pedagogy",
          severity: "error",
          message: `Assessment item ${index + 1} is missing a prompt`,
        });
      }

      if (!answer) {
        issues.push({
          dimension: "pedagogy",
          severity: "error",
          message: `Assessment item ${index + 1} is missing the expected answer or rubric`,
        });
      }

      if (!rationale) {
        issues.push({
          dimension: "pedagogy",
          severity: "error",
          message: `Assessment item ${index + 1} is missing answer rationale or feedback`,
        });
      }
    });
  }

  const warningCount = issues.filter((issue) => issue.severity === "warning").length;
  const errorCount = issues.filter((issue) => issue.severity === "error").length;
  const score = Math.max(0, 1 - warningCount * 0.12 - errorCount * 0.35);

  return { score, issues };
}

// ---------------------------------------------------------------------------
// Internal scorer helpers
// ---------------------------------------------------------------------------

function scoreCoherence(
  outputData: Record<string, unknown>,
  jobType: string,
): {
  score: number;
  issues: EvaluationIssue[];
} {
  const issues: EvaluationIssue[] = [];
  const claims = (outputData["claims"] as any[] | undefined) ?? [];
  const examples = (outputData["examples"] as any[] | undefined) ?? [];
  const jobTypeSupportsStandaloneArtifacts = [
    "explainer",
    "worked_example",
    "simulation",
    "animation",
    "assessment",
  ].includes(jobType);

  // Each example and assessment should map to at least one claim
  if (claims.length === 0 && !jobTypeSupportsStandaloneArtifacts) {
    issues.push({
      dimension: "coherence",
      severity: "warning",
      message: "No claims found",
      detail: "Content lacks foundational claim coverage",
    });
  }
  if (
    examples.length > 0 &&
    claims.length === 0 &&
    !jobTypeSupportsStandaloneArtifacts
  ) {
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
  const createdAt =
    row.createdAt instanceof Date
      ? row.createdAt
      : new Date(String(row.createdAt));
  const updatedAt =
    row.updatedAt instanceof Date
      ? row.updatedAt
      : new Date(String(row.updatedAt));
  return {
    id: String(row.id),
    tenantId: String(row.tenantId),
    ...(typeof row.assetId === "string" ? { assetId: row.assetId } : {}),
    ...(typeof row.generationJobId === "string"
      ? { generationJobId: row.generationJobId }
      : {}),
    ...(typeof row.generationRequestId === "string"
      ? { generationRequestId: row.generationRequestId }
      : {}),
    ...(typeof row.coherenceScore === "number"
      ? { coherenceScore: row.coherenceScore }
      : {}),
    ...(typeof row.completenessScore === "number"
      ? { completenessScore: row.completenessScore }
      : {}),
    ...(typeof row.safetyScore === "number"
      ? { safetyScore: row.safetyScore }
      : {}),
    ...(typeof row.accessibilityScore === "number"
      ? { accessibilityScore: row.accessibilityScore }
      : {}),
    ...(typeof row.manifestValidityScore === "number"
      ? { manifestValidityScore: row.manifestValidityScore }
      : {}),
    ...(typeof row.overallScore === "number"
      ? { overallScore: row.overallScore }
      : {}),
    status: (row.status as string).toLowerCase() as EvaluationRecord["status"],
    recommendation: (row.recommendation as string)
      .toLowerCase()
      .replace(/_/g, "_") as PublishRecommendation,
    ...(Array.isArray(row.issues)
      ? { issues: row.issues as EvaluationIssue[] }
      : {}),
    ...(row.diagnostics && typeof row.diagnostics === "object"
      ? { diagnostics: row.diagnostics as Record<string, unknown> }
      : {}),
    ...(typeof row.errorMessage === "string"
      ? { errorMessage: row.errorMessage }
      : {}),
    createdAt: createdAt.toISOString(),
    updatedAt: updatedAt.toISOString(),
  };
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class EvaluationService {
  constructor(private readonly prisma: PrismaClient) {}

  private async loadApprovedBaseline(
    tenantId: string,
    assetId: string,
    generationRequestId?: string,
  ): Promise<ApprovedArtifactBaseline | undefined> {
    const [asset, latestEvaluation] = await Promise.all([
      this.prisma.contentAsset.findFirst({
        where: { id: assetId, tenantId },
        select: {
          id: true,
          currentVersion: true,
          promptHash: true,
          qualityScore: true,
          reviewState: true,
          searchableText: true,
          revisions: {
            orderBy: { version: "desc" },
            take: 1,
            select: {
              version: true,
              snapshot: true,
              qualityScore: true,
            },
          },
        },
      }),
      this.prisma.evaluationRecord.findFirst({
        where: {
          tenantId,
          assetId,
          ...(generationRequestId
            ? { NOT: { generationRequestId } }
            : {}),
        },
        orderBy: { createdAt: "desc" },
      }),
    ]);

    if (!asset) {
      return undefined;
    }

    const latestRevision = asset.revisions[0];
    const reviewState = parseJsonRecord(asset.reviewState);
    const searchableTextLength = asset.searchableText?.trim().length ?? 0;
    const revisionTextLength = extractSnapshotTextLength(latestRevision?.snapshot);

    return {
      assetId: asset.id,
      currentVersion: latestRevision?.version ?? asset.currentVersion,
      ...(asset.promptHash ? { promptHash: asset.promptHash } : {}),
      ...(typeof latestRevision?.qualityScore === "number"
        ? { qualityScore: latestRevision.qualityScore }
        : typeof asset.qualityScore === "number"
          ? { qualityScore: asset.qualityScore }
          : {}),
      ...(reviewState ? { reviewState } : {}),
      ...(latestEvaluation?.id ? { latestEvaluationId: latestEvaluation.id } : {}),
      ...(typeof latestEvaluation?.overallScore === "number"
        ? { latestEvaluationScore: latestEvaluation.overallScore }
        : {}),
      ...(typeof latestEvaluation?.recommendation === "string"
        ? { latestEvaluationRecommendation: latestEvaluation.recommendation }
        : {}),
      textLength: Math.max(searchableTextLength, revisionTextLength),
    };
  }

  /**
   * Evaluate all completed jobs in a generation request.
   * Creates one EvaluationRecord per job.
   */
  async evaluateGenerationRequest(
    tenantId: string,
    generationRequestId: string,
  ): Promise<EvaluationScorecard> {
    // Fetch the request and all COMPLETED jobs
    const request = await this.prisma.generationRequest.findFirst({
      where: { id: generationRequestId, tenantId },
      include: { jobs: { where: { status: "COMPLETED" } } },
    });

    if (!request) {
      throw new Error(`Generation request ${generationRequestId} not found`);
    }

    // Delete any previous evaluation records for this request
    await this.prisma.evaluationRecord.deleteMany({
      where: { generationRequestId },
    });

    const allIssues: EvaluationIssue[] = [];
    const jobRecords: EvaluationRecord[] = [];

    for (const job of request.jobs) {
      const record = await this.evaluateJob(tenantId, job, generationRequestId);
      jobRecords.push(record);
      allIssues.push(...(record.issues ?? []));
    }

    allIssues.push(
      ...deriveRequestCoverageIssues(
        request.jobs.map((job) => ({ outputData: job.outputData })),
      ),
    );

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
    job: {
      id: string;
      outputAssetId?: string | null;
      outputData: unknown;
      jobType: string;
      parameters: unknown;
    },
    generationRequestId?: string,
  ): Promise<EvaluationRecord> {
    const outputData = (job.outputData as Record<string, unknown> | null) ?? {};
    const jobType = (job.jobType as string).toLowerCase();
    const assetId = resolveAssetId({
      ...(job.outputAssetId !== undefined
        ? { outputAssetId: job.outputAssetId }
        : {}),
      outputData,
    });
    const gradeLevel = (
      job.parameters as Record<string, unknown> | null | undefined
    )?.gradeLevel as string | undefined;
    const promptHash = asString(
      (job.parameters as Record<string, unknown> | null | undefined)?.["promptHash"],
    );
    const approvedBaseline = assetId
      ? await this.loadApprovedBaseline(tenantId, assetId, generationRequestId)
      : undefined;

    const coherence = scoreCoherence(outputData, jobType);
    const completeness = scoreCompleteness(outputData, jobType);
    const safety = scoreSafety(outputData);
    const accessibility = scoreAccessibility(outputData, gradeLevel);
    const manifestValidity = scoreManifestValidity(outputData, jobType);
    const pedagogy = scorePedagogy(outputData, jobType, gradeLevel);

    const allIssues = [
      ...coherence.issues,
      ...completeness.issues,
      ...safety.issues,
      ...accessibility.issues,
      ...manifestValidity.issues,
      ...pedagogy.issues,
    ];

    const overall =
      coherence.score * 0.22 +
      completeness.score * 0.22 +
      safety.score * 0.26 +
      accessibility.score * 0.1 +
      manifestValidity.score * 0.1 +
      pedagogy.score * 0.1;

    const regressionIssues = deriveApprovedBaselineIssues({
      outputData,
      overallScore: overall,
      ...(approvedBaseline ? { baseline: approvedBaseline } : {}),
    });
    allIssues.push(...regressionIssues);

    const recommendation = deriveRecommendation(overall, allIssues);
    const status: "passed" | "failed" =
      recommendation === "block" ? "failed" : "passed";

    const created = await this.prisma.evaluationRecord.create({
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
        status: status.toUpperCase() as
          | "PENDING"
          | "RUNNING"
          | "PASSED"
          | "FAILED"
          | "SKIPPED",
        recommendation: recommendation.toUpperCase().replace(/-/g, "_") as
          | "AUTO_PUBLISH"
          | "MANUAL_REVIEW"
          | "BLOCK",
        issues:
          allIssues.length > 0
            ? (allIssues as unknown as Prisma.InputJsonValue)
            : Prisma.JsonNull,
        diagnostics: {
          jobType,
          jobId: job.id,
          pedagogy: {
            score: Number(pedagogy.score.toFixed(4)),
            issueCount: pedagogy.issues.length,
            ...(gradeLevel ? { gradeLevel } : {}),
          },
          ...(promptHash ? { promptHash } : {}),
          ...(approvedBaseline
            ? {
                regressionBaseline: {
                  assetId: approvedBaseline.assetId,
                  version: approvedBaseline.currentVersion,
                  ...(approvedBaseline.promptHash
                    ? { promptHash: approvedBaseline.promptHash }
                    : {}),
                  ...(approvedBaseline.latestEvaluationId
                    ? { evaluationId: approvedBaseline.latestEvaluationId }
                    : {}),
                  ...(typeof approvedBaseline.latestEvaluationScore === "number"
                    ? { evaluationScore: approvedBaseline.latestEvaluationScore }
                    : typeof approvedBaseline.qualityScore === "number"
                      ? { evaluationScore: approvedBaseline.qualityScore }
                      : {}),
                  deltaOverallScore:
                    typeof approvedBaseline.latestEvaluationScore === "number"
                      ? Number(
                          (overall - approvedBaseline.latestEvaluationScore).toFixed(4),
                        )
                      : typeof approvedBaseline.qualityScore === "number"
                        ? Number((overall - approvedBaseline.qualityScore).toFixed(4))
                        : null,
                },
              }
            : {}),
        },
      },
    });

    if (assetId) {
      await this.prisma.contentAsset.update({
        where: { id: assetId },
        data: {
          qualityScore: overall,
          reviewState: JSON.stringify({
            source: "evaluation",
            status,
            recommendation,
            overallScore: overall,
            ...(approvedBaseline
              ? {
                  regressionBaseline: {
                    version: approvedBaseline.currentVersion,
                    ...(approvedBaseline.promptHash
                      ? { promptHash: approvedBaseline.promptHash }
                      : {}),
                    ...(approvedBaseline.latestEvaluationId
                      ? { evaluationId: approvedBaseline.latestEvaluationId }
                      : {}),
                  },
                }
              : {}),
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
    const records = await this.prisma.evaluationRecord.findMany({
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
    const record = await this.prisma.evaluationRecord.findFirst({
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
