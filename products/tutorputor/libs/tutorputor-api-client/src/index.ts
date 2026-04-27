/**
 * @tutorputor/api-client
 *
 * Typed TutorPutor API client derived from @tutorputor/contracts.
 * Replaces hand-rolled per-app fetch wrappers across web, admin, and mobile.
 *
 * Usage:
 * ```ts
 * import { createTutorPutorApiClient } from "@tutorputor/api-client";
 *
 * const api = createTutorPutorApiClient({
 *   baseUrl: import.meta.env.VITE_API_BASE_URL,
 *   getAccessToken: () => storage.getItem("access_token"),
 * });
 *
 * const dashboard = await api.learning.getDashboard();
 * ```
 *
 * @doc.type module
 * @doc.purpose Barrel export for @tutorputor/api-client
 * @doc.layer product
 * @doc.pattern Facade
 */

export * from "./errors.js";
export * from "./client.js";
export { AuthApiClient } from "./routes/auth.js";
export { LearningApiClient } from "./routes/learning.js";
export { ContentStudioApiClient } from "./routes/content-studio.js";
export { AnalyticsApiClient } from "./routes/analytics.js";

// ---------------------------------------------------------------------------
// Convenience factory
// ---------------------------------------------------------------------------

import { createBoundRequest, type TutorPutorClientConfig } from "./client.js";
import { AuthApiClient } from "./routes/auth.js";
import { LearningApiClient } from "./routes/learning.js";
import { ContentStudioApiClient } from "./routes/content-studio.js";
import { AnalyticsApiClient } from "./routes/analytics.js";

export interface TutorPutorApiClient {
  auth: AuthApiClient;
  learning: LearningApiClient;
  contentStudio: ContentStudioApiClient;
  analytics: AnalyticsApiClient;
}

/**
 * Creates a fully typed TutorPutor API client.
 *
 * All route sub-clients share the same underlying request function,
 * which handles auth headers, 401 retry, timeouts, and error mapping.
 */
export function createTutorPutorApiClient(
  config: TutorPutorClientConfig,
): TutorPutorApiClient {
  const request = createBoundRequest(config);

  return {
    auth: new AuthApiClient(request),
    learning: new LearningApiClient(request),
    contentStudio: new ContentStudioApiClient(request),
    analytics: new AnalyticsApiClient(request),
  };
}
