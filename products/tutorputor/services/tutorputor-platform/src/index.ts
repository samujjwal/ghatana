/**
 * TutorPutor Platform Entry Point
 *
 * Exports all consolidated modules for use in the API Gateway or standalone server.
 */

// Core Modules
export { contentModule } from "./modules/content/index.js";
export { learningModule } from "./modules/learning/index.js";
export { userModule } from "./modules/user/index.js";
export { collaborationModule } from "./modules/collaboration/index.js";
export { engagementModule } from "./modules/engagement/index.js";
export { integrationModule } from "./modules/integration/index.js";
export { tenantModule } from "./modules/tenant/index.js";
export { aiModule } from "./modules/ai/index.js";

// Core Utilities & Observability
export { setupPlatform } from "./setup.js";
export {
  setupMetrics,
  setupHealthChecks,
} from "./core/observability/metrics.js";
export { setupErrorTracking } from "./core/observability/error-tracking.js";
export { setupRateLimit } from "./core/middleware/rate-limit.js";

// Types
export * from "./types/fastify.js";
