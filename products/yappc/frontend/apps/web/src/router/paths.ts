/**
 * Route Constants & Helpers
 *
 * @description Type-safe route path constants, URL builders, and navigation
 * helpers for all YAPPC application routes.
 *
 * @doc.type routing
 * @doc.purpose Route path constants and navigation utilities
 * @doc.layer infrastructure
 */

// =============================================================================
// Route Path Constants
// =============================================================================

export const ROUTES = {
  // ── Public ──────────────────────────────────────────────────────────────
  HOME: '/',
  SSO_CALLBACK: '/sso/callback',

  // ── App-level ───────────────────────────────────────────────────────────
  DASHBOARD: '/dashboard',
  PROJECTS: '/projects',
  TEMPLATES: '/templates',
  SETTINGS: '/settings',
  PROFILE: '/profile',

  // ── Project scope ──────────────────────────────────────────────────────
  project: (projectId: string) => `/project/${projectId}`,
  unified: (projectId: string) => `/project/${projectId}/unified`,
  unifiedPhase: (projectId: string, phase: string) =>
    `/project/${projectId}/unified/${phase}`,

  // ── Phase 1: Bootstrapping ─────────────────────────────────────────────
  bootstrap: {
    root: (projectId: string) => `/project/${projectId}/bootstrap`,
    session: (projectId: string, sessionId: string) =>
      `/project/${projectId}/bootstrap/session/${sessionId}`,
    upload: (projectId: string) => `/project/${projectId}/bootstrap/upload`,
    collaborate: (projectId: string) =>
      `/project/${projectId}/bootstrap/collaborate`,
    resume: (projectId: string) => `/project/${projectId}/bootstrap/resume`,
    complete: (projectId: string) =>
      `/project/${projectId}/bootstrap/complete`,
    preview: (projectId: string) => `/project/${projectId}/bootstrap/preview`,
  },

  // ── Phase 2: Setup / Initialization ────────────────────────────────────
  setup: {
    root: (projectId: string) => `/project/${projectId}/setup`,
    infrastructure: (projectId: string) =>
      `/project/${projectId}/setup/infrastructure`,
    environments: (projectId: string) =>
      `/project/${projectId}/setup/environments`,
    team: (projectId: string) => `/project/${projectId}/setup/team`,
    progress: (projectId: string) => `/project/${projectId}/setup/progress`,
    presets: (projectId: string) => `/project/${projectId}/setup/presets`,
    complete: (projectId: string) => `/project/${projectId}/setup/complete`,
  },

  // ── Phase 3: Development ───────────────────────────────────────────────
  development: {
    root: (projectId: string) => `/project/${projectId}/development`,
    board: (projectId: string, sprintId?: string) =>
      sprintId
        ? `/project/${projectId}/development/board/${sprintId}`
        : `/project/${projectId}/development/board`,
    backlog: (projectId: string) =>
      `/project/${projectId}/development/backlog`,
    story: (projectId: string, storyId: string) =>
      `/project/${projectId}/development/stories/${storyId}`,
    epics: (projectId: string) => `/project/${projectId}/development/epics`,
    prs: (projectId: string, prId?: string) =>
      prId
        ? `/project/${projectId}/development/prs/${prId}`
        : `/project/${projectId}/development/prs`,
    review: (projectId: string, prId: string) =>
      `/project/${projectId}/development/review/${prId}`,
    reviewDashboard: (projectId: string) =>
      `/project/${projectId}/development/review-dashboard`,
    reviewDetail: (projectId: string, id: string) =>
      `/project/${projectId}/development/review-detail/${id}`,
    flags: (projectId: string) => `/project/${projectId}/development/flags`,
    deployments: (projectId: string) =>
      `/project/${projectId}/development/deployments`,
    velocity: (projectId: string) =>
      `/project/${projectId}/development/velocity`,
    planning: (projectId: string) =>
      `/project/${projectId}/development/planning`,
  },

  // ── Phase 4: Operations ────────────────────────────────────────────────
  operations: {
    root: (projectId: string) => `/project/${projectId}/operations`,
    incidents: (projectId: string, incidentId?: string) =>
      incidentId
        ? `/project/${projectId}/operations/incidents/${incidentId}`
        : `/project/${projectId}/operations/incidents`,
    warroom: (projectId: string, incidentId: string) =>
      `/project/${projectId}/operations/incidents/${incidentId}/warroom`,
    alerts: (projectId: string) =>
      `/project/${projectId}/operations/alerts`,
    dashboards: (projectId: string, dashboardId?: string) =>
      dashboardId
        ? `/project/${projectId}/operations/dashboards/${dashboardId}`
        : `/project/${projectId}/operations/dashboards`,
    logs: (projectId: string) => `/project/${projectId}/operations/logs`,
    metrics: (projectId: string) =>
      `/project/${projectId}/operations/metrics`,
    runbooks: (projectId: string, runbookId?: string) =>
      runbookId
        ? `/project/${projectId}/operations/runbooks/${runbookId}`
        : `/project/${projectId}/operations/runbooks`,
    oncall: (projectId: string) => `/project/${projectId}/operations/oncall`,
    services: (projectId: string) =>
      `/project/${projectId}/operations/services`,
    postmortems: (projectId: string) =>
      `/project/${projectId}/operations/postmortems`,
  },

  // ── Phase 5: Collaboration ─────────────────────────────────────────────
  team: {
    root: (projectId: string) => `/project/${projectId}/team`,
    calendar: (projectId: string) => `/project/${projectId}/team/calendar`,
    knowledge: (projectId: string) =>
      `/project/${projectId}/team/knowledge`,
    article: (projectId: string, articleId: string) =>
      `/project/${projectId}/team/knowledge/${articleId}`,
    articleEdit: (projectId: string, articleId: string) =>
      `/project/${projectId}/team/knowledge/${articleId}/edit`,
    articleNew: (projectId: string) =>
      `/project/${projectId}/team/knowledge/new`,
    standups: (projectId: string) => `/project/${projectId}/team/standups`,
    retros: (projectId: string) => `/project/${projectId}/team/retros`,
    messages: (projectId: string) => `/project/${projectId}/team/messages`,
    channel: (projectId: string, channelId: string) =>
      `/project/${projectId}/team/messages/channel/${channelId}`,
    dm: (projectId: string, userId: string) =>
      `/project/${projectId}/team/messages/dm/${userId}`,
    goals: (projectId: string) => `/project/${projectId}/team/goals`,
    activity: (projectId: string) => `/project/${projectId}/team/activity`,
  },

  // ── Phase 6: Security ──────────────────────────────────────────────────
  security: {
    root: (projectId: string) => `/project/${projectId}/security`,
    vulnerabilities: (projectId: string, vulnId?: string) =>
      vulnId
        ? `/project/${projectId}/security/vulnerabilities/${vulnId}`
        : `/project/${projectId}/security/vulnerabilities`,
    scans: (projectId: string, scanId?: string) =>
      scanId
        ? `/project/${projectId}/security/scans/${scanId}`
        : `/project/${projectId}/security/scans`,
    compliance: (projectId: string, frameworkId?: string) =>
      frameworkId
        ? `/project/${projectId}/security/compliance/${frameworkId}`
        : `/project/${projectId}/security/compliance`,
    secrets: (projectId: string) =>
      `/project/${projectId}/security/secrets`,
    policies: (projectId: string, policyId?: string) =>
      policyId
        ? `/project/${projectId}/security/policies/${policyId}`
        : `/project/${projectId}/security/policies`,
    alerts: (projectId: string) =>
      `/project/${projectId}/security/alerts`,
    audit: (projectId: string) =>
      `/project/${projectId}/security/audit`,
    threatModel: (projectId: string) =>
      `/project/${projectId}/security/threat-model`,
  },

  // ── Admin ──────────────────────────────────────────────────────────────
  admin: {
    root: '/admin',
    teams: '/admin/teams',
    billing: '/admin/billing',
  },

  // ── Errors ─────────────────────────────────────────────────────────────
  UNAUTHORIZED: '/unauthorized',
  ERROR: '/error',
  NOT_FOUND: '/404',
} as const;

// =============================================================================
// Route Types
// =============================================================================

export type RouteKey = keyof typeof ROUTES;

export interface BreadcrumbItem {
  label: string;
  path?: string;
  icon?: string;
}

// =============================================================================
// URL Builder
// =============================================================================

/**
 * Build a URL with optional query parameters.
 */
export function buildUrl(
  path: string,
  params?: Record<string, string | number | boolean | null | undefined>
): string {
  if (!params) return path;

  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value != null && value !== '') {
      searchParams.set(key, String(value));
    }
  }

  const query = searchParams.toString();
  return query ? `${path}?${query}` : path;
}

// =============================================================================
// Route Matching
// =============================================================================

/**
 * Match a URL pathname against a route pattern (supports :param segments).
 */
export function matchRoute(
  pathname: string,
  pattern: string
): Record<string, string> | null {
  const pathParts = pathname.split('/').filter(Boolean);
  const patternParts = pattern.split('/').filter(Boolean);

  if (pathParts.length !== patternParts.length) return null;

  const params: Record<string, string> = {};

  for (let i = 0; i < patternParts.length; i++) {
    if (patternParts[i].startsWith(':')) {
      params[patternParts[i].slice(1)] = pathParts[i];
    } else if (patternParts[i] !== pathParts[i]) {
      return null;
    }
  }

  return params;
}

/**
 * Extract route params from the current pathname.
 */
export function extractRouteParams(
  pathname: string
): Record<string, string> {
  const segments = pathname.split('/').filter(Boolean);
  const params: Record<string, string> = {};

  // Known positional param patterns
  if (segments[0] === 'project' && segments[1]) {
    params.projectId = segments[1];
  }
  if (segments[2] === 'bootstrap' && segments[3] === 'session' && segments[4]) {
    params.sessionId = segments[4];
  }
  if (segments[2] === 'development' && segments[3] === 'prs' && segments[4]) {
    params.prId = segments[4];
  }
  if (segments[2] === 'operations' && segments[3] === 'incidents' && segments[4]) {
    params.incidentId = segments[4];
  }
  if (segments[2] === 'operations' && segments[3] === 'dashboards' && segments[4]) {
    params.dashboardId = segments[4];
  }
  if (segments[2] === 'operations' && segments[3] === 'runbooks' && segments[4]) {
    params.runbookId = segments[4];
  }
  if (segments[2] === 'team' && segments[3] === 'knowledge' && segments[4]) {
    params.articleId = segments[4];
  }
  if (segments[2] === 'security' && segments[3] === 'vulnerabilities' && segments[4]) {
    params.vulnId = segments[4];
  }
  if (segments[2] === 'security' && segments[3] === 'scans' && segments[4]) {
    params.scanId = segments[4];
  }
  if (segments[2] === 'security' && segments[3] === 'policies' && segments[4]) {
    params.policyId = segments[4];
  }

  return params;
}

// =============================================================================
// Phase Helpers
// =============================================================================

const PHASE_SEGMENTS = new Set([
  'bootstrap',
  'setup',
  'development',
  'operations',
  'team',
  'security',
]);

/**
 * Determine the current project phase from a pathname.
 * Returns the phase segment name or null if not in a project phase.
 */
export function getPhaseFromPath(pathname: string): string | null {
  const segments = pathname.split('/').filter(Boolean);
  // Pattern: /project/:id/:phase/...
  if (segments[0] === 'project' && segments[2]) {
    const phase = segments[2];
    if (PHASE_SEGMENTS.has(phase)) return phase;
  }
  return null;
}

// =============================================================================
// Query String Helpers
// =============================================================================

/**
 * Parse query string into a Record.
 */
export function parseQueryParams(search: string): Record<string, string> {
  const params = new URLSearchParams(search);
  const result: Record<string, string> = {};
  params.forEach((value, key) => {
    result[key] = value;
  });
  return result;
}

// =============================================================================
// Breadcrumbs
// =============================================================================

/**
 * Generate breadcrumb trail from current path.
 */
export function generateBreadcrumbs(
  pathname: string,
  projectId?: string,
  projectName?: string
): BreadcrumbItem[] {
  const crumbs: BreadcrumbItem[] = [];
  const segments = pathname.split('/').filter(Boolean);

  if (segments.length === 0) {
    return [{ label: 'Home' }];
  }

  let currentPath = '';

  for (let i = 0; i < segments.length; i++) {
    const segment = segments[i];
    currentPath += `/${segment}`;

    // Skip 'project' segment and projectId
    if (segment === 'project') continue;
    if (projectId && segment === projectId) {
      crumbs.push({
        label: projectName ?? 'Project',
        path: currentPath,
      });
      continue;
    }

    crumbs.push({
      label: formatSegmentLabel(segment),
      path: i < segments.length - 1 ? currentPath : undefined,
    });
  }

  return crumbs;
}

function formatSegmentLabel(segment: string): string {
  return segment
    .split('-')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}
