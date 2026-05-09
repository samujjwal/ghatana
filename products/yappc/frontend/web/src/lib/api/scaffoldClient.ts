/**
 * YAPPC Scaffold API Client
 *
 * Domain-scoped client for scaffold packs, projects, templates, and dependencies.
 *
 * @doc.type module
 * @doc.purpose Domain-scoped REST client for YAPPC scaffold APIs
 * @doc.layer product
 * @doc.pattern API Client
 */

import { parseJsonResponse, readErrorResponse } from '@/lib/http';

// ─────────────────────────────────────────────────────────────────────────────
// Domain types
// ─────────────────────────────────────────────────────────────────────────────

export interface ApiError {
  readonly status: number;
  readonly message: string;
  readonly type?: string;
}

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly type?: string,
  ) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal fetch helpers
// ─────────────────────────────────────────────────────────────────────────────

async function handleError(response: Response, context: string): Promise<never> {
  const message = await readErrorResponse(response, `${context} failed (${response.status})`);
  let type: string | undefined;
  try {
    const ct = response.headers.get('content-type') ?? '';
    if (ct.includes('problem+json')) {
      const body = JSON.parse(message) as { type?: unknown };
      if (typeof body.type === 'string') type = body.type;
    }
  } catch {
    // non-parseable, ignore
  }
  throw new ApiRequestError(response.status, message, type);
}

async function get<T>(path: string, context: string): Promise<T> {
  const response = await fetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<T>(response, context);
}

async function post<TRequest, TResponse>(path: string, body: TRequest, context: string): Promise<TResponse> {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<TResponse>(response, context);
}

// ─────────────────────────────────────────────────────────────────────────────
// Packs
// ─────────────────────────────────────────────────────────────────────────────

export interface Pack {
  readonly name: string;
  readonly description: string;
  readonly languages: readonly string[];
  readonly categories: readonly string[];
  readonly platforms: readonly string[];
}

export const packs = {
  list: () => get<Pack[]>('/api/v1/packs', 'packs.list'),
  languages: () => get<string[]>('/api/v1/packs/languages', 'packs.languages'),
  categories: () => get<string[]>('/api/v1/packs/categories', 'packs.categories'),
  platforms: () => get<string[]>('/api/v1/packs/platforms', 'packs.platforms'),
  refresh: () => post<Record<string, never>, { success: boolean }>('/api/v1/packs/refresh', {}, 'packs.refresh'),
  get: (name: string) => get<Pack>(`/api/v1/packs/${encodeURIComponent(name)}`, 'packs.get'),
  validate: (name: string) => get<{ valid: boolean; errors: string[] }>(`/api/v1/packs/${encodeURIComponent(name)}/validate`, 'packs.validate'),
  templates: (name: string) => get<string[]>(`/api/v1/packs/${encodeURIComponent(name)}/templates`, 'packs.templates'),
  variables: (name: string) => get<Record<string, string>>(`/api/v1/packs/${encodeURIComponent(name)}/variables`, 'packs.variables'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Scaffold Projects
// ─────────────────────────────────────────────────────────────────────────────

export interface ScaffoldProject {
  readonly id: string;
  readonly name: string;
  readonly pack: string;
  readonly status: string;
  readonly createdAt: string;
}

export const scaffoldProjects = {
  create: (body: { name: string; pack: string; options?: Record<string, unknown> }) =>
    post<typeof body, ScaffoldProject>('/api/v1/scaffold/projects', body, 'scaffoldProjects.create'),
  addFeature: (body: { projectId: string; feature: string }) =>
    post<typeof body, { success: boolean }>('/api/v1/scaffold/projects/add-feature', body, 'scaffoldProjects.addFeature'),
  update: (body: { projectId: string; updates: Record<string, unknown> }) =>
    post<typeof body, ScaffoldProject>('/api/v1/scaffold/projects/update', body, 'scaffoldProjects.update'),
  info: (projectId: string) =>
    get<ScaffoldProject>(`/api/v1/scaffold/projects/info${encodeQuery({ projectId })}`, 'scaffoldProjects.info'),
  state: (projectId: string) =>
    get<Record<string, unknown>>(`/api/v1/scaffold/projects/state${encodeQuery({ projectId })}`, 'scaffoldProjects.state'),
  validate: (projectId: string) =>
    get<{ valid: boolean; errors: string[] }>(`/api/v1/scaffold/projects/validate${encodeQuery({ projectId })}`, 'scaffoldProjects.validate'),
  checkUpdates: (projectId: string) =>
    get<{ hasUpdates: boolean; version: string }>(`/api/v1/scaffold/projects/check-updates${encodeQuery({ projectId })}`, 'scaffoldProjects.checkUpdates'),
  previewUpdate: (body: { projectId: string }) =>
    post<typeof body, { changes: unknown[] }>('/api/v1/scaffold/projects/preview-update', body, 'scaffoldProjects.previewUpdate'),
  features: (projectId: string) =>
    get<string[]>(`/api/v1/scaffold/projects/features${encodeQuery({ projectId })}`, 'scaffoldProjects.features'),
  export: (body: { projectId: string }) =>
    post<typeof body, { archive: string }>('/api/v1/scaffold/projects/export', body, 'scaffoldProjects.export'),
  import: (body: { archive: string }) =>
    post<typeof body, ScaffoldProject>('/api/v1/scaffold/projects/import', body, 'scaffoldProjects.import'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Templates
// ─────────────────────────────────────────────────────────────────────────────

export const templates = {
  render: (body: { template: string; variables: Record<string, unknown> }) =>
    post<typeof body, { rendered: string }>('/api/v1/templates/render', body, 'templates.render'),
  helpers: () => get<string[]>('/api/v1/templates/helpers', 'templates.helpers'),
  validate: (body: { template: string }) =>
    post<typeof body, { valid: boolean; errors: string[] }>('/api/v1/templates/validate', body, 'templates.validate'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Dependencies
// ─────────────────────────────────────────────────────────────────────────────

export const dependencies = {
  analyzePack: (packName: string) =>
    get<{ dependencies: string[] }>(`/api/v1/dependencies/analyze/pack/${encodeURIComponent(packName)}`, 'dependencies.analyzePack'),
  analyzeProject: (body: { projectPath: string }) =>
    post<typeof body, { dependencies: string[]; conflicts: string[] }>('/api/v1/dependencies/analyze/project', body, 'dependencies.analyzeProject'),
  conflicts: (body: { dependencies: string[] }) =>
    post<typeof body, { conflicts: string[] }>('/api/v1/dependencies/conflicts', body, 'dependencies.conflicts'),
  addConflicts: (body: { conflicts: string[] }) =>
    post<typeof body, { success: boolean }>('/api/v1/dependencies/add-conflicts', body, 'dependencies.addConflicts'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Helper
// ─────────────────────────────────────────────────────────────────────────────

function encodeQuery(params: Record<string, string | number | boolean | undefined>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) {
      query.set(key, String(value));
    }
  }
  const queryString = query.toString();
  return queryString ? `?${queryString}` : '';
}

// ─────────────────────────────────────────────────────────────────────────────
// Export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * YAPPC Scaffold API client
 * Groups all scaffold-related APIs: packs, scaffoldProjects, templates, dependencies
 */
export const scaffoldClient = {
  packs,
  scaffoldProjects,
  templates,
  dependencies,
} as const;
