/**
 * Canonical route helpers for AEP.
 *
 * Single source of truth for all application route generation.
 * All page-level navigation must use these helpers instead of
 * hardcoded strings to prevent route drift and broken handoffs.
 *
 * @doc.type utility
 * @doc.purpose Typed canonical route generation for AEP navigation
 * @doc.layer frontend
 */

// ─── Route constants ─────────────────────────────────────────────────

const OPERATE = "/operate";
const OPERATE_COSTS = "/operate/costs";
const OPERATE_REVIEWS = "/operate/reviews";
const BUILD_PIPELINES = "/build/pipelines";
const BUILD_NEW_PIPELINE = "/build/pipelines/new";
const BUILD_PATTERNS = "/build/patterns";
const LEARN_EPISODES = "/learn/episodes";
const LEARN_MEMORY = "/learn/memory";
const GOVERN = "/govern";
const GOVERN_PRIVACY = "/govern/privacy";
const CATALOG_AGENTS = "/catalog/agents";
const CATALOG_MARKETPLACE = "/catalog/marketplace";
const CATALOG_WORKFLOWS = "/catalog/workflows";
const LOGIN = "/login";

// ─── Public helpers ──────────────────────────────────────────────────

/** Monitoring dashboard (runs & alerts). */
export function getOperateUrl(): string {
  return OPERATE;
}

/** Cost dashboard. */
export function getCostDashboardUrl(): string {
  return OPERATE_COSTS;
}

/** HITL review queue. */
export function getReviewQueueUrl(): string {
  return OPERATE_REVIEWS;
}

/** Run detail page. */
export function getRunDetailUrl(runId: string): string {
  return `/operate/runs/${encodeURIComponent(runId)}`;
}

/** Pipeline list page. */
export function getPipelineListUrl(): string {
  return BUILD_PIPELINES;
}

/** New pipeline builder (blank canvas). */
export function getNewPipelineUrl(): string {
  return BUILD_NEW_PIPELINE;
}

/** Edit existing pipeline in builder. */
export function getEditPipelineUrl(id: string): string {
  return `/build/pipelines/${encodeURIComponent(id)}/edit`;
}

/** Pattern studio. */
export function getPatternStudioUrl(): string {
  return BUILD_PATTERNS;
}

/** Learning episodes page. */
export function getLearningEpisodesUrl(): string {
  return LEARN_EPISODES;
}

/** Memory explorer. */
export function getMemoryExplorerUrl(): string {
  return LEARN_MEMORY;
}

/** Governance page. */
export function getGovernanceUrl(): string {
  return GOVERN;
}

/** Privacy request workbench. */
export function getPrivacyRequestsUrl(): string {
  return GOVERN_PRIVACY;
}

/** Agent registry. */
export function getAgentRegistryUrl(): string {
  return CATALOG_AGENTS;
}

/** Agent detail page. */
export function getAgentDetailUrl(id: string): string {
  return `/catalog/agents/${encodeURIComponent(id)}`;
}

/** Agent marketplace. */
export function getMarketplaceUrl(): string {
  return CATALOG_MARKETPLACE;
}

/** Workflow catalog. */
export function getWorkflowCatalogUrl(): string {
  return CATALOG_WORKFLOWS;
}

/** Login page. */
export function getLoginUrl(): string {
  return LOGIN;
}

// ─── Route matching helpers ───────────────────────────────────────────

/** Check if a path is the pipeline builder (new or edit). */
export function isPipelineBuilderPath(path: string): boolean {
  return path === BUILD_NEW_PIPELINE || /^\/build\/pipelines\/[^/]+\/edit$/.test(path);
}

/** Extract pipeline ID from builder edit path. */
export function extractPipelineIdFromBuilderPath(path: string): string | null {
  const match = /^\/build\/pipelines\/([^/]+)\/edit$/.exec(path);
  return match ? decodeURIComponent(match[1]) : null;
}

/** Check if a path needs live SSE data. */
export function isSseRelevantPath(path: string): boolean {
  return (
    path === OPERATE ||
    path.startsWith("/operate/runs/") ||
    path === OPERATE_REVIEWS ||
    isPipelineBuilderPath(path)
  );
}

/** Routes that are considered "operational" and need live data. */
export const OPERATIONAL_PATHS: readonly string[] = [
  OPERATE,
  OPERATE_REVIEWS,
  OPERATE_COSTS,
];
