/**
 * Centralized TanStack Query key definitions for YAPPC.
 *
 * All query keys must be defined here to prevent:
 * - Cache collisions from mismatched key structures
 * - Scattered `invalidateQueries` calls referencing wrong keys
 * - Inconsistent naming between hook files
 *
 * @doc.type module
 * @doc.purpose Single source of truth for all TanStack Query cache keys
 * @doc.layer product
 * @doc.pattern Constants
 */

// ─── Workspace ────────────────────────────────────────────────────────────────

export const workspaceQueryKeys = {
  all: ['workspaces'] as const,
  lists: () => [...workspaceQueryKeys.all, 'list'] as const,
  list: (filters?: Record<string, unknown>) =>
    [...workspaceQueryKeys.lists(), filters] as const,
  details: () => [...workspaceQueryKeys.all, 'detail'] as const,
  detail: (id: string) => [...workspaceQueryKeys.details(), id] as const,
  members: (workspaceId: string) =>
    [...workspaceQueryKeys.all, 'members', workspaceId] as const,
  settings: (workspaceId: string) =>
    [...workspaceQueryKeys.all, 'settings', workspaceId] as const,
  teams: (workspaceId: string) =>
    [...workspaceQueryKeys.all, 'teams', workspaceId] as const,
};

// ─── Project ──────────────────────────────────────────────────────────────────

export const projectQueryKeys = {
  all: ['projects'] as const,
  lists: () => [...projectQueryKeys.all, 'list'] as const,
  list: (workspaceId: string) =>
    [...projectQueryKeys.lists(), workspaceId] as const,
  details: () => [...projectQueryKeys.all, 'detail'] as const,
  detail: (id: string) => [...projectQueryKeys.details(), id] as const,
  available: (workspaceId: string) =>
    [...projectQueryKeys.all, 'available', workspaceId] as const,
};

// ─── Requirements ─────────────────────────────────────────────────────────────

export const requirementQueryKeys = {
  all: ['requirements'] as const,
  list: (params?: Record<string, unknown>) =>
    ['requirements', 'list', params] as const,
  detail: (id: string) => ['requirements', 'detail', id] as const,
  funnel: (projectId: string) => ['requirements', 'funnel', projectId] as const,
  quality: (id: string) => ['requirements', 'quality', id] as const,
};

// ─── AI Suggestions ───────────────────────────────────────────────────────────

export const aiQueryKeys = {
  all: ['ai'] as const,
  inbox: () => ['ai', 'inbox'] as const,
  suggestion: (id: string) => ['ai', 'suggestion', id] as const,
  pending: (resourceId?: string) => ['ai', 'pending', resourceId] as const,
};

// ─── Audit ────────────────────────────────────────────────────────────────────

export const auditQueryKeys = {
  all: ['audit'] as const,
  events: (params?: Record<string, unknown>) =>
    ['audit', 'events', params] as const,
  event: (id: string) => ['audit', 'event', id] as const,
  summary: (params?: Record<string, unknown>) =>
    ['audit', 'summary', params] as const,
  resourceEvents: (resourceId: string, resourceType: string) =>
    ['audit', 'resource', resourceType, resourceId] as const,
};

// ─── Version ──────────────────────────────────────────────────────────────────

export const versionQueryKeys = {
  all: ['version'] as const,
  history: (resourceId: string, resourceType: string) =>
    ['version', 'history', resourceType, resourceId] as const,
  detail: (id: string) => ['version', 'detail', id] as const,
  compare: (v1: string, v2: string) => ['version', 'compare', v1, v2] as const,
};

// ─── Authorization ────────────────────────────────────────────────────────────

export const authQueryKeys = {
  all: ['auth'] as const,
  userPermissions: (userId: string) => ['auth', 'user', userId] as const,
  myPermissions: () => ['auth', 'me'] as const,
  personas: () => ['auth', 'personas'] as const,
  personaPermissions: (persona: string) =>
    ['auth', 'persona', persona] as const,
};

// ─── Architecture ─────────────────────────────────────────────────────────────

export const architectureQueryKeys = {
  all: ['architecture'] as const,
  impact: (resourceId: string) =>
    ['architecture', 'impact', resourceId] as const,
  dependencies: (resourceId: string) =>
    ['architecture', 'dependencies', resourceId] as const,
  techDebt: (projectId?: string) =>
    ['architecture', 'tech-debt', projectId] as const,
  patterns: (projectId?: string) =>
    ['architecture', 'patterns', projectId] as const,
};
