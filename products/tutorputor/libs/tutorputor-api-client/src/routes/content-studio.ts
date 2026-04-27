/**
 * @tutorputor/api-client — Content Studio routes
 *
 * Typed wrappers for `/api/content-studio` endpoints used by the admin authoring app.
 *
 * @doc.type module
 * @doc.purpose Content Studio API route client
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type { BoundApiRequest } from "../client.js";

// ---------------------------------------------------------------------------
// Types (sourced from contracts)
// ---------------------------------------------------------------------------

export interface LearningExperienceInput {
  title: string;
  description?: string;
  domain: string;
  difficulty: "INTRO" | "INTERMEDIATE" | "ADVANCED";
  tags?: string[];
}

export interface LearningExperienceSummary {
  id: string;
  title: string;
  description?: string;
  domain: string;
  difficulty: "INTRO" | "INTERMEDIATE" | "ADVANCED";
  tags: string[];
  status: "DRAFT" | "PENDING_REVIEW" | "PUBLISHED" | "REJECTED";
  publishedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PublishGateResult {
  canPublish: boolean;
  trustScore: number;
  blockers: string[];
  warnings: string[];
}

export interface ClaimInput {
  statement: string;
  evidence?: string[];
  sourceUrls?: string[];
}

export interface Claim {
  id: string;
  statement: string;
  evidence: string[];
  sourceUrls: string[];
  trustScore: number;
  validatedAt?: string;
}

// ---------------------------------------------------------------------------
// Route client
// ---------------------------------------------------------------------------

/**
 * Content Studio API client. Bind to a `BoundApiRequest` created from `createBoundRequest()`.
 */
export class ContentStudioApiClient {
  constructor(private readonly request: BoundApiRequest) {}

  /**
   * GET /api/content-studio/experiences
   * Lists all learning experiences for the tenant.
   */
  async listExperiences(): Promise<LearningExperienceSummary[]> {
    return this.request<LearningExperienceSummary[]>("/api/content-studio/experiences");
  }

  /**
   * POST /api/content-studio/experiences
   * Creates a new draft learning experience.
   */
  async createExperience(input: LearningExperienceInput): Promise<LearningExperienceSummary> {
    return this.request<LearningExperienceSummary>("/api/content-studio/experiences", {
      method: "POST",
      body: input,
    });
  }

  /**
   * GET /api/content-studio/experiences/:id
   * Returns a full experience with all artifacts.
   */
  async getExperience(id: string): Promise<LearningExperienceSummary> {
    return this.request<LearningExperienceSummary>(
      `/api/content-studio/experiences/${encodeURIComponent(id)}`,
    );
  }

  /**
   * GET /api/content-studio/experiences/:id/publish-gate
   * Returns the publish gate evaluation for a draft experience.
   */
  async getPublishGate(experienceId: string): Promise<PublishGateResult> {
    return this.request<PublishGateResult>(
      `/api/content-studio/experiences/${encodeURIComponent(experienceId)}/publish-gate`,
    );
  }

  /**
   * POST /api/content-studio/experiences/:id/publish
   * Attempts to publish the experience (fails if publish gate blocks).
   */
  async publishExperience(experienceId: string): Promise<LearningExperienceSummary> {
    return this.request<LearningExperienceSummary>(
      `/api/content-studio/experiences/${encodeURIComponent(experienceId)}/publish`,
      { method: "POST" },
    );
  }

  /**
   * GET /api/content-studio/experiences/:id/claims
   * Lists claims associated with an experience.
   */
  async listClaims(experienceId: string): Promise<Claim[]> {
    return this.request<Claim[]>(
      `/api/content-studio/experiences/${encodeURIComponent(experienceId)}/claims`,
    );
  }

  /**
   * POST /api/content-studio/experiences/:id/claims
   * Creates a new claim on an experience.
   */
  async createClaim(experienceId: string, input: ClaimInput): Promise<Claim> {
    return this.request<Claim>(
      `/api/content-studio/experiences/${encodeURIComponent(experienceId)}/claims`,
      { method: "POST", body: input },
    );
  }
}
