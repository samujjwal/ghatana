/**
 * Content Studio API Adapter
 *
 * Bridges the admin UI with the Content Studio backend via the canonical
 * `/api/content-studio/` route prefix. All mutations are persisted through
 * real backend endpoints — no mock or simulated responses on production paths.
 *
 * Auth: the adapter reads a module-level auth token set by `setAuthToken()`.
 * Call `setAuthToken(token)` from the `useAuth` hook after login so all outgoing
 * requests carry a valid `Authorization: Bearer <token>` header.
 */

import type {
  ContentAsset,
  ContentAssetRevision,
  EvaluationRecord,
  EvaluationScorecard,
  GenerationRequest,
  GenerationRequestWithJobs,
  GenerationReviewDecision,
  PublishResult,
  RegenerationCandidate,
  SubmitReviewDecisionInput,
} from "@tutorputor/contracts/v1/content-studio";

// ---------------------------------------------------------------------------
// Auth token store — set by useAuth on login / refresh
// ---------------------------------------------------------------------------
let _authToken: string | null = null;

/** Called by useAuth after a successful login or token refresh. */
export function setAuthToken(token: string | null): void {
  _authToken = token;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Resolve the API base from env or fall back to same-origin. */
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
const CS = `${BASE_URL}/api/content-studio`;

function authHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (_authToken) headers["Authorization"] = `Bearer ${_authToken}`;
  return headers;
}

async function csRequest<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const response = await fetch(`${CS}${path}`, {
    method,
    headers: authHeaders(),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    const err = await response
      .json()
      .catch(() => ({ message: response.statusText }));
    throw new Error(err.message ?? `HTTP ${response.status}`);
  }
  // 204 No Content
  if (response.status === 204) return undefined as unknown as T;
  return response.json();
}

// ---------------------------------------------------------------------------
// Admin-facing types (maps LearningExperience → admin UI shape)
// ---------------------------------------------------------------------------

export interface AdminExperience {
  id: string;
  title: string;
  description: string;
  status: "draft" | "review" | "published" | "archived";
  gradeLevel: string;
  subject: string;
  claims: AdminClaim[];
  createdAt: string;
  updatedAt: string;
  /** Detailed per-claim artifact counts from the progress endpoint */
  contentCounts?: {
    examples: number;
    simulations: number;
    animations: number;
  };
}

export interface AdminClaim {
  id: string;
  text: string;
  bloomLevel?: string;
  orderIndex: number;
  examples?: unknown[];
  simulations?: unknown[];
  animations?: unknown[];
}

export interface AdminValidationResult {
  status: "valid" | "warnings" | "invalid";
  canPublish: boolean;
  checks: Array<{
    checkId: string;
    pillar: string;
    name: string;
    passed: boolean;
    severity: "error" | "warning" | "info";
    message: string;
    suggestion?: string;
  }>;
  score: number;
  pillarScores: Record<string, number>;
  validatedAt: Date;
}

export interface AdminContentAsset {
  id: string;
  title: string;
  assetType: ContentAsset["assetType"];
  status: ContentAsset["status"];
  domain: string;
  targetGrades: string[];
  authorId: string;
  updatedAt: string;
  publishedAt?: string;
  currentVersion: number;
  qualityScore?: number;
  legacyExperienceId?: string;
}

export interface AdminContentAssetDetail {
  asset: AdminContentAsset;
  currentRevision: ContentAssetRevision | null;
}

export interface AdminExperienceTimelineEvent {
  id: string;
  type: string;
  actorId: string;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}

export interface AdminExperienceAnalytics {
  experienceId: string;
  viewCount?: number;
  completionCount?: number;
  completionRate?: number;
  avgTimeMinutes?: number;
  dropOffRate?: number;
  simulationStarts?: number;
  simulationAborts?: number;
  simulationErrors?: number;
  hasEngagementDrift?: boolean;
  hasQualityIssues?: boolean;
  trends7d?: {
    dates: string[];
    views: number[];
    completions: number[];
  };
  driftSignals?: Array<{
    type: string;
    severity: "low" | "medium" | "high";
    metric: string;
    currentValue: number;
    threshold: number;
    recommendation: string;
  }>;
  recommendedActions?: Array<{
    id: string;
    type: "regenerate" | "review" | "update" | "archive";
    priority: "low" | "medium" | "high";
    description: string;
  }>;
  latestValidation?: {
    status: string;
    validatedAt: string;
    accessibilityScore?: number;
    authorityScore?: number;
    accuracyScore?: number;
    usefulnessScore?: number;
    harmlessnessScore?: number;
    suggestions: string[];
  } | null;
  recentEvents: AdminExperienceTimelineEvent[];
}

export interface CreateExperienceInput {
  title: string;
  description?: string;
  gradeRange?: string;
  moduleId?: string;
}

export interface GenerationRequestListResult {
  items: GenerationRequest[];
  total: number;
}

export interface PublishGenerationRequestResult {
  published: number;
  skipped: number;
  results: PublishResult[];
}

// ---------------------------------------------------------------------------
// API Adapter
// ---------------------------------------------------------------------------

function mapExpToAdmin(exp: any): AdminExperience {
  return {
    id: exp.id,
    title: exp.title,
    description: exp.description ?? exp.intentProblem ?? "",
    status:
      exp.status === "published"
        ? "published"
        : exp.status === "archived"
          ? "archived"
          : exp.status === "review"
            ? "review"
            : "draft",
    gradeLevel: exp.gradeAdaptation?.gradeRange ?? "grade_6_8",
    subject: exp.domain ?? "TECH",
    claims: (exp.claims ?? []).map((c: any) => ({
      id: c.id,
      text: c.text ?? c.statement ?? "",
      bloomLevel: c.bloomLevel ?? c.bloom,
      orderIndex: c.orderIndex ?? 0,
      examples: c.examples ?? [],
      simulations: c.simulations ?? [],
      animations: c.animations ?? [],
    })),
    createdAt: exp.createdAt,
    updatedAt: exp.updatedAt,
  };
}

function mapValidationToAdmin(v: any): AdminValidationResult {
  const pillarScores: Record<string, number> = {
    Educational: v.pillarScores?.educational ?? v.score ?? 0,
    Experiential: v.pillarScores?.experiential ?? v.score ?? 0,
    Safety: v.pillarScores?.safety ?? 100,
    Technical: v.pillarScores?.technical ?? v.score ?? 0,
    Accessibility: v.pillarScores?.accessibility ?? v.score ?? 0,
  };

  const checks: AdminValidationResult["checks"] = (v.checks ?? []).map(
    (c: any) => ({
      checkId: c.checkId ?? c.id ?? "check",
      pillar: c.pillar ?? "General",
      name: c.name ?? c.description ?? "Check",
      passed: c.passed ?? c.status === "pass",
      severity: c.severity ?? (c.passed ? "info" : "warning"),
      message: c.message ?? "",
      suggestion: c.suggestion,
    }),
  );

  return {
    status:
      v.status === "valid"
        ? "valid"
        : v.status === "warnings"
          ? "warnings"
          : "invalid",
    canPublish: v.canPublish ?? false,
    checks,
    score: v.score ?? 0,
    pillarScores,
    validatedAt: v.validatedAt ? new Date(v.validatedAt) : new Date(),
  };
}

function mapAssetToAdmin(asset: ContentAsset): AdminContentAsset {
  return {
    id: asset.id,
    title: asset.title,
    assetType: asset.assetType,
    status: asset.status,
    domain: asset.domain,
    targetGrades: asset.targetGrades ?? [],
    authorId: asset.authorId,
    updatedAt: asset.updatedAt,
    publishedAt: asset.publishedAt,
    currentVersion: asset.currentVersion,
    qualityScore: asset.qualityScore,
    legacyExperienceId: asset.legacyExperienceId,
  };
}

export const contentStudioApi = {
  // ---------------------------------------------------------------------------
  // Canonical Asset Reads
  // ---------------------------------------------------------------------------

  /** List canonical content assets for admin library reads. */
  async getContentAssets(params?: {
    assetType?: ContentAsset["assetType"];
    status?: ContentAsset["status"];
    domain?: string;
    authorId?: string;
    search?: string;
    limit?: number;
    offset?: number;
  }): Promise<{ items: AdminContentAsset[]; total: number }> {
    const qs = new URLSearchParams();
    if (params?.assetType) qs.set("assetType", params.assetType);
    if (params?.status) qs.set("status", params.status);
    if (params?.domain) qs.set("domain", params.domain);
    if (params?.authorId) qs.set("authorId", params.authorId);
    if (params?.search) qs.set("search", params.search);
    if (params?.limit) qs.set("limit", String(params.limit));
    if (params?.offset) qs.set("offset", String(params.offset));
    const query = qs.toString() ? `?${qs}` : "";

    const result = await csRequest<{ data: ContentAsset[]; total: number }>(
      "GET",
      `/assets${query}`,
    );

    return {
      items: (result.data ?? []).map(mapAssetToAdmin),
      total: result.total ?? 0,
    };
  },

  /** Get canonical asset detail with current revision metadata. */
  async getContentAsset(
    assetId: string,
  ): Promise<AdminContentAssetDetail | null> {
    const result = await csRequest<{
      data: {
        asset: ContentAsset;
        currentRevision: ContentAssetRevision | null;
      };
    }>("GET", `/assets/${assetId}`).catch((error: unknown) => {
      if (
        error instanceof Error &&
        error.message.toLowerCase().includes("not found")
      ) {
        return null;
      }
      throw error;
    });

    if (!result?.data?.asset) {
      return null;
    }

    return {
      asset: mapAssetToAdmin(result.data.asset),
      currentRevision: result.data.currentRevision ?? null,
    };
  },

  // ---------------------------------------------------------------------------
  // CRUD
  // ---------------------------------------------------------------------------

  /** List all learning experiences for the current tenant. */
  async getExperiences(params?: {
    status?: string;
    limit?: number;
    offset?: number;
  }): Promise<AdminExperience[]> {
    const qs = new URLSearchParams();
    if (params?.status) qs.set("status", params.status);
    if (params?.limit) qs.set("limit", String(params.limit));
    if (params?.offset) qs.set("offset", String(params.offset));
    const query = qs.toString() ? `?${qs}` : "";
    const result = await csRequest<{ data: any[]; pagination: any }>(
      "GET",
      `/experiences${query}`,
    );
    return (result.data ?? []).map(mapExpToAdmin);
  },

  /** Get a single experience by ID, including all linked artifacts. */
  async getExperience(id: string): Promise<AdminExperience | null> {
    const result = await csRequest<{ data: any }>(
      "GET",
      `/experiences/${id}/comprehensive`,
    );
    if (!result?.data) return null;
    return mapExpToAdmin(result.data);
  },

  /** Create a new experience and enqueue claim generation. */
  async createExperience(
    input: CreateExperienceInput,
  ): Promise<AdminExperience> {
    const result = await csRequest<{ data: any }>(
      "POST",
      "/experiences",
      input,
    );
    return mapExpToAdmin(result.data?.experience ?? result.data);
  },

  /**
   * Generate content for an experience: creates + triggers claim generation.
   * Returns the created experience plus an initial validation result.
   */
  async generateContent(request: {
    title: string;
    description?: string;
    gradeRange?: string;
    maxClaims?: number;
  }): Promise<{
    experience: AdminExperience;
    validation: AdminValidationResult;
  }> {
    // 1. Create the experience
    const created = await contentStudioApi.createExperience({
      title: request.title,
      description: request.description,
      gradeRange: request.gradeRange ?? "grade_6_8",
    });

    // 2. Enqueue claim generation (fire-and-forget; progress tracked via /progress)
    await csRequest("POST", `/experiences/${created.id}/generate-claims`, {
      maxClaims: request.maxClaims ?? 5,
    }).catch(() => {
      // Generation already enqueued at creation time; ignore duplicate errors
    });

    // 3. Run initial validation pass
    const validation = await contentStudioApi.validateExperience(created.id);

    return { experience: created, validation };
  },

  /** Update an existing experience's metadata. */
  async updateExperience(
    id: string,
    updates: Partial<
      Pick<AdminExperience, "title" | "description" | "status" | "gradeLevel">
    >,
  ): Promise<AdminExperience> {
    const body: Record<string, unknown> = {};
    if (updates.title !== undefined) body.title = updates.title;
    if (updates.description !== undefined)
      body.description = updates.description;
    if (updates.status !== undefined) {
      body.status = updates.status;
    }
    if (updates.gradeLevel !== undefined) body.gradeRange = updates.gradeLevel;

    const result = await csRequest<{ data: any }>(
      "PUT",
      `/experiences/${id}`,
      body,
    );
    return mapExpToAdmin(result.data);
  },

  /** Delete an experience and all its artifacts. */
  async deleteExperience(id: string): Promise<void> {
    await csRequest("DELETE", `/experiences/${id}`);
  },

  // ---------------------------------------------------------------------------
  // Validation & Publishing
  // ---------------------------------------------------------------------------

  /** Run evidence-based validation for an experience. */
  async validateExperience(id: string): Promise<AdminValidationResult> {
    const result = await csRequest<{ data: any }>(
      "POST",
      `/experiences/${id}/validate`,
      {},
    );
    return mapValidationToAdmin(result.data);
  },

  /** Publish an experience (requires valid content). */
  async publishExperience(id: string): Promise<AdminExperience> {
    const result = await csRequest<{ data: any }>(
      "POST",
      `/experiences/${id}/publish`,
    );
    return mapExpToAdmin(result.data);
  },

  /** Return a published experience to review status. */
  async unpublishExperience(
    id: string,
    reason?: string,
  ): Promise<AdminExperience> {
    const result = await csRequest<{ data: any }>(
      "POST",
      `/experiences/${id}/unpublish`,
      reason ? { reason } : {},
    );
    return mapExpToAdmin(result.data);
  },

  /** Approve or reject an experience through canonical publish lifecycle routes. */
  async reviewDecision(
    id: string,
    decision: "approve" | "reject",
    reason?: string,
  ): Promise<void> {
    if (decision === "approve") {
      await contentStudioApi.publishExperience(id);
      return;
    }

    await contentStudioApi.unpublishExperience(id, reason);
  },

  // ---------------------------------------------------------------------------
  // Governed Generation Control Plane
  // ---------------------------------------------------------------------------

  /** List governed generation requests for the current tenant. */
  async getGenerationRequests(params?: {
    status?: string;
    limit?: number;
    offset?: number;
  }): Promise<GenerationRequestListResult> {
    const qs = new URLSearchParams();
    if (params?.status) qs.set("status", params.status);
    if (params?.limit) qs.set("limit", String(params.limit));
    if (params?.offset) qs.set("offset", String(params.offset));
    const query = qs.toString() ? `?${qs}` : "";

    return csRequest<GenerationRequestListResult>(
      "GET",
      `/generation/requests${query}`,
    );
  },

  /** Get a single generation request with all planned jobs. */
  async getGenerationRequest(
    requestId: string,
  ): Promise<GenerationRequestWithJobs | null> {
    const result = await csRequest<GenerationRequestWithJobs>(
      "GET",
      `/generation/requests/${requestId}`,
    ).catch((error: unknown) => {
      if (
        error instanceof Error &&
        error.message.toLowerCase().includes("not found")
      ) {
        return null;
      }
      throw error;
    });

    return result;
  },

  /** Run evaluation for a generation request. */
  async evaluateGenerationRequest(
    requestId: string,
  ): Promise<EvaluationScorecard> {
    return csRequest<EvaluationScorecard>(
      "POST",
      `/evaluation/requests/${requestId}/evaluate`,
    );
  },

  /** List evaluation records for a generation request. */
  async getGenerationEvaluations(
    requestId: string,
  ): Promise<EvaluationRecord[]> {
    const result = await csRequest<{ evaluations: EvaluationRecord[] }>(
      "GET",
      `/evaluation/requests/${requestId}/evaluations`,
    );
    return result.evaluations ?? [];
  },

  /** List review decisions for a generation request. */
  async getGenerationReviewDecisions(
    requestId: string,
  ): Promise<GenerationReviewDecision[]> {
    const result = await csRequest<{ decisions: GenerationReviewDecision[] }>(
      "GET",
      `/review/requests/${requestId}/decisions`,
    );
    return result.decisions ?? [];
  },

  /** Submit a canonical review decision for a generation request. */
  async submitGenerationReviewDecision(
    requestId: string,
    input: Omit<SubmitReviewDecisionInput, "requestId">,
  ): Promise<GenerationReviewDecision> {
    return csRequest<GenerationReviewDecision>(
      "POST",
      `/review/requests/${requestId}/decisions`,
      input,
    );
  },

  /** Bulk publish all approved assets emitted by a generation request. */
  async publishGenerationRequest(
    requestId: string,
  ): Promise<PublishGenerationRequestResult> {
    return csRequest<PublishGenerationRequestResult>(
      "POST",
      `/publish/requests/${requestId}/publish-all`,
      {},
    );
  },

  /** List open regeneration candidates. */
  async getRegenerationCandidates(filters?: {
    assetId?: string;
    trigger?: string;
  }): Promise<RegenerationCandidate[]> {
    const qs = new URLSearchParams();
    if (filters?.assetId) qs.set("assetId", filters.assetId);
    if (filters?.trigger) qs.set("trigger", filters.trigger);
    const query = qs.toString() ? `?${qs}` : "";

    const result = await csRequest<{ candidates: RegenerationCandidate[] }>(
      "GET",
      `/candidates${query}`,
    );
    return result.candidates ?? [];
  },

  /** Dismiss an open regeneration candidate. */
  async dismissRegenerationCandidate(
    candidateId: string,
  ): Promise<RegenerationCandidate> {
    return csRequest<RegenerationCandidate>(
      "POST",
      `/candidates/${candidateId}/dismiss`,
      {},
    );
  },

  /** Queue an open regeneration candidate onto a governed generation request. */
  async queueRegenerationCandidate(
    candidateId: string,
    generationRequestId: string,
  ): Promise<RegenerationCandidate> {
    return csRequest<RegenerationCandidate>(
      "POST",
      `/candidates/${candidateId}/queue`,
      { generationRequestId },
    );
  },

  /** Create regeneration candidates from recent learner feedback. */
  async detectRegenerationCandidates(): Promise<{ created: number }> {
    return csRequest<{ created: number }>(
      "POST",
      "/candidates/detect-from-feedback",
      {},
    );
  },

  // ---------------------------------------------------------------------------
  // Progress & Artifacts
  // ---------------------------------------------------------------------------

  /** Poll content generation progress for an experience. */
  async getGenerationProgress(experienceId: string): Promise<{
    status: string;
    percentComplete: number;
    totalClaims: number;
    claimsProcessed: number;
    isComplete: boolean;
    contentCounts: {
      examples: number;
      simulations: number;
      animations: number;
    };
  }> {
    return csRequest("GET", `/experiences/${experienceId}/progress`);
  },

  /** Get example artifacts linked to an experience's claims. */
  async getExamples(experienceId: string): Promise<unknown[]> {
    const result = await csRequest<{ data: unknown[] }>(
      "GET",
      `/experiences/${experienceId}/examples`,
    );
    return result.data ?? [];
  },

  /** Get simulation artifacts linked to an experience's claims. */
  async getSimulations(experienceId: string): Promise<unknown[]> {
    const result = await csRequest<{ data: unknown[] }>(
      "GET",
      `/experiences/${experienceId}/simulations`,
    );
    return result.data ?? [];
  },

  /** Get animation artifacts linked to an experience's claims. */
  async getAnimations(experienceId: string): Promise<unknown[]> {
    const result = await csRequest<{ data: unknown[] }>(
      "GET",
      `/experiences/${experienceId}/animations`,
    );
    return result.data ?? [];
  },

  /** Get analytics, latest validation, and recent authoring activity. */
  async getExperienceAnalytics(
    experienceId: string,
  ): Promise<AdminExperienceAnalytics> {
    const result = await csRequest<{ data: any }>(
      "GET",
      `/experiences/${experienceId}/analytics`,
    );

    return {
      experienceId,
      viewCount: result.data?.viewCount ?? 0,
      completionCount: result.data?.completionCount ?? 0,
      completionRate: result.data?.completionRate ?? 0,
      avgTimeMinutes: result.data?.avgTimeMinutes ?? 0,
      dropOffRate: result.data?.dropOffRate ?? 0,
      simulationStarts: result.data?.simulationStarts ?? 0,
      simulationAborts: result.data?.simulationAborts ?? 0,
      simulationErrors: result.data?.simulationErrors ?? 0,
      hasEngagementDrift: result.data?.hasEngagementDrift ?? false,
      hasQualityIssues: result.data?.hasQualityIssues ?? false,
      trends7d: result.data?.trends7d,
      driftSignals: result.data?.driftSignals ?? [],
      recommendedActions: result.data?.recommendedActions ?? [],
      latestValidation: result.data?.latestValidation ?? null,
      recentEvents: result.data?.recentEvents ?? [],
    };
  },

  // ---------------------------------------------------------------------------
  // Statistics (derived from experiences list)
  // ---------------------------------------------------------------------------

  /** Compute aggregate statistics from the experiences collection. */
  async getStatistics(): Promise<{
    totalExperiences: number;
    totalDraft: number;
    totalPublished: number;
    topSubjects: Array<{ subject: string; count: number }>;
  }> {
    const exps = await contentStudioApi.getExperiences({ limit: 500 });
    const subjectCounts: Record<string, number> = {};
    let totalDraft = 0;
    let totalPublished = 0;
    for (const exp of exps) {
      if (exp.status === "published") totalPublished++;
      else totalDraft++;
      subjectCounts[exp.subject] = (subjectCounts[exp.subject] ?? 0) + 1;
    }
    const topSubjects = Object.entries(subjectCounts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([subject, count]) => ({ subject, count }));

    return {
      totalExperiences: exps.length,
      totalDraft,
      totalPublished,
      topSubjects,
    };
  },
};
