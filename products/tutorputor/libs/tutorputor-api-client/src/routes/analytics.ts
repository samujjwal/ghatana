/**
 * @tutorputor/api-client — Analytics routes
 *
 * Typed wrappers for `/api/v1/analytics` endpoints.
 *
 * @doc.type module
 * @doc.purpose Analytics API route client
 * @doc.layer product
 * @doc.pattern Adapter
 */

import type { BoundApiRequest } from "../client.js";
import type { AnalyticsSummary, AdvancedAnalyticsSummary } from "@tutorputor/contracts/v1/types";

// ---------------------------------------------------------------------------
// Route client
// ---------------------------------------------------------------------------

export class AnalyticsApiClient {
  constructor(private readonly request: BoundApiRequest) {}

  /**
   * GET /api/v1/analytics/summary
   * Returns a summary analytics view for the tenant.
   */
  async getSummary(): Promise<AnalyticsSummary> {
    return this.request<AnalyticsSummary>("/api/v1/analytics/summary");
  }

  /**
   * GET /api/v1/analytics/advanced
   * Returns advanced analytics including risk indicators and difficulty heatmaps.
   */
  async getAdvanced(): Promise<AdvancedAnalyticsSummary> {
    return this.request<AdvancedAnalyticsSummary>("/api/v1/analytics/advanced");
  }
}
