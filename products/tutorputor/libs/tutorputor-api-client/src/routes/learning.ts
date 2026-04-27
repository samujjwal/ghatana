/**
 * @tutorputor/api-client — Learning routes
 *
 * Typed wrappers for `/api/v1/learning`, `/api/v1/modules`,
 * `/api/v1/pathways`, and `/api/v1/assessments` endpoints.
 *
 * @doc.type module
 * @doc.purpose Learning API route client
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type {
  DashboardSummary,
  ModuleSummary,
  ModuleDetail,
  Enrollment,
  Assessment,
  AssessmentSummary,
  AssessmentAttempt,
  LearningPath,
  LearningEventInput,
} from "@tutorputor/contracts/v1/types";
import type { BoundApiRequest } from "../client.js";

// ---------------------------------------------------------------------------
// Route client
// ---------------------------------------------------------------------------

/**
 * Learning API client. Bind to a `BoundApiRequest` created from `createBoundRequest()`.
 */
export class LearningApiClient {
  constructor(private readonly request: BoundApiRequest) {}

  // -------------------------------------------------------------------------
  // Dashboard
  // -------------------------------------------------------------------------

  /**
   * GET /api/v1/learning/dashboard
   * Returns the learner's personalized dashboard summary.
   */
  async getDashboard(): Promise<DashboardSummary> {
    return this.request<DashboardSummary>("/api/v1/learning/dashboard");
  }

  // -------------------------------------------------------------------------
  // Modules
  // -------------------------------------------------------------------------

  /**
   * GET /api/v1/modules
   * Lists published modules, optionally filtered.
   */
  async listModules(params?: {
    domain?: string;
    query?: string;
    cursor?: string;
    limit?: number;
  }): Promise<{ items: ModuleSummary[]; nextCursor?: string | null }> {
    const qs = new URLSearchParams();
    if (params?.domain) qs.set("domain", params.domain);
    if (params?.query) qs.set("query", params.query);
    if (params?.cursor) qs.set("cursor", params.cursor);
    if (params?.limit) qs.set("limit", String(params.limit));

    const query = qs.toString() ? `?${qs.toString()}` : "";
    return this.request<{ items: ModuleSummary[]; nextCursor?: string | null }>(
      `/api/v1/modules${query}`,
    );
  }

  /**
   * GET /api/v1/modules/:slug
   * Returns a full module detail with optional enrollment data.
   */
  async getModule(slug: string): Promise<{ module: ModuleDetail; enrollment?: Enrollment }> {
    return this.request<{ module: ModuleDetail; enrollment?: Enrollment }>(
      `/api/v1/modules/${encodeURIComponent(slug)}`,
    );
  }

  // -------------------------------------------------------------------------
  // Enrollments
  // -------------------------------------------------------------------------

  /**
   * POST /api/v1/enrollments
   * Enrols the authenticated user in a module.
   */
  async enroll(moduleId: string): Promise<Enrollment> {
    return this.request<Enrollment>("/api/v1/enrollments", {
      method: "POST",
      body: { moduleId },
    });
  }

  /**
   * GET /api/v1/enrollments/:id
   * Returns a specific enrollment.
   */
  async getEnrollment(enrollmentId: string): Promise<Enrollment> {
    return this.request<Enrollment>(
      `/api/v1/enrollments/${encodeURIComponent(enrollmentId)}`,
    );
  }

  // -------------------------------------------------------------------------
  // Learning paths / pathways
  // -------------------------------------------------------------------------

  /**
   * GET /api/v1/pathways
   * Returns available learning paths for the tenant.
   */
  async listPathways(): Promise<LearningPath[]> {
    return this.request<LearningPath[]>("/api/v1/pathways");
  }

  // -------------------------------------------------------------------------
  // Assessments
  // -------------------------------------------------------------------------

  /**
   * GET /api/v1/assessments
   * Lists assessments for the authenticated learner.
   */
  async listAssessments(): Promise<AssessmentSummary[]> {
    return this.request<AssessmentSummary[]>("/api/v1/assessments");
  }

  /**
   * GET /api/v1/assessments/:id
   * Returns a specific assessment with items.
   */
  async getAssessment(assessmentId: string): Promise<Assessment> {
    return this.request<Assessment>(
      `/api/v1/assessments/${encodeURIComponent(assessmentId)}`,
    );
  }

  /**
   * POST /api/v1/assessments/:id/attempts
   * Submits an assessment attempt.
   */
  async submitAttempt(
    assessmentId: string,
    attempt: { answers: Array<{ itemId: string; value: unknown }> },
  ): Promise<AssessmentAttempt> {
    return this.request<AssessmentAttempt>(
      `/api/v1/assessments/${encodeURIComponent(assessmentId)}/attempts`,
      { method: "POST", body: attempt },
    );
  }

  // -------------------------------------------------------------------------
  // Telemetry
  // -------------------------------------------------------------------------

  /**
   * POST /api/v1/learning/events
   * Tracks a learning event (fire-and-forget, errors are swallowed by design).
   */
  async trackEvent(event: LearningEventInput): Promise<void> {
    try {
      await this.request<void>("/api/v1/learning/events", {
        method: "POST",
        body: event,
      });
    } catch {
      // Learning event tracking must never break the learner flow.
    }
  }
}
