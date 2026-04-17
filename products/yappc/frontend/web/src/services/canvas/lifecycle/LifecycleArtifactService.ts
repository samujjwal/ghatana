/**
 * Lifecycle Artifact Service
 *
 * Service for managing lifecycle artifacts throughout the 7-phase lifecycle model.
 * Provides CRUD operations, status tracking, and AI-assisted artifact generation.
 *
 * @doc.type service
 * @doc.purpose Lifecycle artifact management
 * @doc.layer product
 * @doc.pattern Service
 */

import type {
  LifecycleArtifactKind,
  LifecycleArtifactMetadata,
  ArtifactPlacement,
} from '@/shared/types/lifecycle-artifacts';
import {
  LIFECYCLE_ARTIFACT_CATALOG,
  createArtifactTag,
} from '@/shared/types/lifecycle-artifacts';
import { LifecyclePhase } from '@/types/lifecycle';

// ============================================================================
// Types
// ============================================================================

/**
 * Artifact status tracking.
 */
export type ArtifactStatus = 'draft' | 'complete' | 'validated' | 'archived';

/**
 * Stored lifecycle artifact with metadata.
 */
export interface LifecycleArtifact {
  /** Unique artifact ID */
  id: string;
  /** Project this artifact belongs to */
  projectId: string;
  /** Artifact kind from the taxonomy */
  kind: LifecycleArtifactKind;
  /** Human-readable title */
  title: string;
  /** Artifact content payload (kind-specific) */
  payload: Record<string, unknown>;
  /** Current status */
  status: ArtifactStatus;
  /** Associated tags */
  tags: string[];
  /** Version number */
  version: number;
  /** Created timestamp */
  createdAt: string;
  /** Last updated timestamp */
  updatedAt: string;
  /** User who created the artifact */
  createdBy: string;
  /** User who last updated the artifact */
  updatedBy: string;
  /** Validation errors, if any */
  validationErrors?: string[];
  /** AI generation metadata */
  aiGeneration?: {
    model: string;
    prompt?: string;
    confidence: number;
    generatedAt: string;
  };
}

/**
 * Create artifact request.
 */
export interface CreateArtifactRequest {
  projectId: string;
  kind: LifecycleArtifactKind;
  title?: string;
  payload?: Record<string, unknown>;
  status?: ArtifactStatus;
}

/**
 * Update artifact request.
 */
export interface UpdateArtifactRequest {
  title?: string;
  payload?: Record<string, unknown>;
  status?: ArtifactStatus;
}

/**
 * Query filter for artifacts.
 */
export interface ArtifactFilter {
  projectId?: string;
  kinds?: LifecycleArtifactKind[];
  phases?: LifecyclePhase[];
  status?: ArtifactStatus[];
  search?: string;
}

/**
 * Artifact summary for listings.
 */
export interface ArtifactSummary {
  id: string;
  kind: LifecycleArtifactKind;
  title: string;
  status: ArtifactStatus;
  phase: LifecyclePhase;
  updatedAt: string;
}

/**
 * Artifact dependency graph node.
 */
export interface ArtifactNode {
  id: string;
  kind: LifecycleArtifactKind;
  title: string;
  status: ArtifactStatus;
  phase: LifecyclePhase;
}

/**
 * Artifact dependency graph edge.
 */
export interface ArtifactEdge {
  source: string;
  target: string;
  type: 'depends_on' | 'informs' | 'validates';
}

/**
 * Artifact dependency graph.
 */
export interface ArtifactGraph {
  nodes: ArtifactNode[];
  edges: ArtifactEdge[];
}

// ============================================================================
// Repository Interface
// ============================================================================

/**
 * Repository interface for lifecycle artifact persistence.
 */
export interface ILifecycleArtifactRepository {
  save(artifact: LifecycleArtifact): Promise<void>;
  findById(id: string): Promise<LifecycleArtifact | null>;
  findByProjectAndKind(
    projectId: string,
    kind: LifecycleArtifactKind
  ): Promise<LifecycleArtifact[]>;
  findByProject(projectId: string): Promise<LifecycleArtifact[]>;
  findByFilter(filter: ArtifactFilter): Promise<LifecycleArtifact[]>;
  update(artifact: LifecycleArtifact): Promise<void>;
  delete(id: string): Promise<boolean>;
}

function canUseStorage(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';
}

function artifactStorageKey(projectId: string): string {
  return `yappc.lifecycle.artifacts.${projectId}`;
}

const LIFECYCLE_API_BASE = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
  : '/api';

type ApiLifecycleArtifact = {
  id: string;
  projectId: string;
  title: string;
  type: string;
  description?: string | null;
  content?: string | null;
  status: string;
  phase?: LifecyclePhase;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  metadata?: Record<string, unknown>;
};

function getAuthToken(): string | null {
  if (!canUseStorage()) {
    return null;
  }

  const rawSession = window.localStorage.getItem('auth-session');
  if (!rawSession) {
    return null;
  }

  try {
    const parsed = JSON.parse(rawSession) as { token?: string };
    return parsed.token ?? null;
  } catch {
    return null;
  }
}

function mapStatusToApi(status: ArtifactStatus): string {
  if (status === 'validated') {
    return 'approved';
  }
  if (status === 'complete') {
    return 'review';
  }
  if (status === 'archived') {
    return 'approved';
  }
  return 'draft';
}

function mapStatusFromApi(status: string): ArtifactStatus {
  if (status === 'approved') {
    return 'validated';
  }
  if (status === 'review') {
    return 'complete';
  }
  if (status === 'archived') {
    return 'archived';
  }
  return 'draft';
}

function getFallbackKindForPhase(phase?: LifecyclePhase): LifecycleArtifactKind {
  const firstMatch = (
    Object.values(LIFECYCLE_ARTIFACT_CATALOG) as LifecycleArtifactMetadata[]
  ).find((metadata) => metadata.phase === phase);

  return firstMatch?.kind ?? 'idea_brief';
}

function resolveKindFromApiArtifact(
  artifact: ApiLifecycleArtifact
): LifecycleArtifactKind {
  const explicitKind = artifact.metadata?.kind;
  if (
    typeof explicitKind === 'string' &&
    explicitKind in LIFECYCLE_ARTIFACT_CATALOG
  ) {
    return explicitKind as LifecycleArtifactKind;
  }

  const byType = (
    Object.values(LIFECYCLE_ARTIFACT_CATALOG) as LifecycleArtifactMetadata[]
  ).find((metadata) => {
    return (
      metadata.label.toLowerCase() === artifact.type.toLowerCase() ||
      metadata.kind.toLowerCase() === artifact.type.toLowerCase()
    );
  });

  return byType?.kind ?? getFallbackKindForPhase(artifact.phase);
}

function parsePayload(content?: string | null): Record<string, unknown> {
  if (!content) {
    return {};
  }

  try {
    return JSON.parse(content) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function mapApiArtifactToLifecycleArtifact(
  artifact: ApiLifecycleArtifact
): LifecycleArtifact {
  const kind = resolveKindFromApiArtifact(artifact);
  const metadata = artifact.metadata ?? {};

  return {
    id: artifact.id,
    projectId: artifact.projectId,
    kind,
    title: artifact.title,
    payload: parsePayload(artifact.content),
    status: mapStatusFromApi(artifact.status),
    tags: Array.isArray(metadata.tags)
      ? metadata.tags.filter((tag): tag is string => typeof tag === 'string')
      : [createArtifactTag(kind)],
    version: typeof metadata.version === 'number' ? metadata.version : 1,
    createdAt: artifact.createdAt ?? new Date().toISOString(),
    updatedAt: artifact.updatedAt ?? new Date().toISOString(),
    createdBy: artifact.createdBy ?? 'system',
    updatedBy:
      typeof metadata.updatedBy === 'string' ? metadata.updatedBy : artifact.createdBy ?? 'system',
    aiGeneration:
      metadata.aiGeneration && typeof metadata.aiGeneration === 'object'
        ? (metadata.aiGeneration as LifecycleArtifact['aiGeneration'])
        : undefined,
  };
}

/**
 * API-backed artifact repository with local fallback.
 */
export class ApiBackedLifecycleArtifactRepository
  implements ILifecycleArtifactRepository
{
  constructor(
    private readonly fallback: ILifecycleArtifactRepository =
      new LocalStorageLifecycleArtifactRepository()
  ) {}

  async save(artifact: LifecycleArtifact): Promise<void> {
    await this.fallback.save(artifact);

    try {
      await this.request('/artifacts', {
        method: 'POST',
        body: JSON.stringify({
          id: artifact.id,
          projectId: artifact.projectId,
          title: artifact.title,
          type: LIFECYCLE_ARTIFACT_CATALOG[artifact.kind].label,
          content: JSON.stringify(artifact.payload ?? {}),
          status: mapStatusToApi(artifact.status),
          phase: LIFECYCLE_ARTIFACT_CATALOG[artifact.kind].phase,
          createdBy: artifact.createdBy,
          metadata: {
            kind: artifact.kind,
            version: artifact.version,
            tags: artifact.tags,
            updatedBy: artifact.updatedBy,
            aiGeneration: artifact.aiGeneration,
          },
        }),
      });
    } catch {
      // Keep local copy to preserve UX when backend is unavailable.
    }
  }

  async findById(id: string): Promise<LifecycleArtifact | null> {
    try {
      const artifact = await this.request<ApiLifecycleArtifact>(
        `/artifacts/${encodeURIComponent(id)}`
      );
      const mapped = mapApiArtifactToLifecycleArtifact(artifact);
      await this.fallback.save(mapped);
      return mapped;
    } catch {
      return this.fallback.findById(id);
    }
  }

  async findByProjectAndKind(
    projectId: string,
    kind: LifecycleArtifactKind
  ): Promise<LifecycleArtifact[]> {
    const artifacts = await this.findByProject(projectId);
    return artifacts.filter((artifact) => artifact.kind === kind);
  }

  async findByProject(projectId: string): Promise<LifecycleArtifact[]> {
    try {
      const artifacts = await this.request<ApiLifecycleArtifact[]>(
        `/projects/${encodeURIComponent(projectId)}/artifacts`
      );

      const mapped = artifacts.map(mapApiArtifactToLifecycleArtifact);
      for (const artifact of mapped) {
        await this.fallback.save(artifact);
      }

      return mapped;
    } catch {
      return this.fallback.findByProject(projectId);
    }
  }

  async findByFilter(filter: ArtifactFilter): Promise<LifecycleArtifact[]> {
    const artifacts = filter.projectId
      ? await this.findByProject(filter.projectId)
      : await this.fallback.findByFilter(filter);

    return artifacts.filter((artifact) => {
      if (filter.kinds && !filter.kinds.includes(artifact.kind)) {
        return false;
      }
      if (filter.phases) {
        const metadata = LIFECYCLE_ARTIFACT_CATALOG[artifact.kind];
        if (!filter.phases.includes(metadata.phase)) {
          return false;
        }
      }
      if (filter.status && !filter.status.includes(artifact.status)) {
        return false;
      }
      if (filter.search) {
        const search = filter.search.toLowerCase();
        if (!artifact.title.toLowerCase().includes(search)) {
          return false;
        }
      }
      return true;
    });
  }

  async update(artifact: LifecycleArtifact): Promise<void> {
    await this.fallback.update(artifact);

    try {
      await this.request(`/artifacts/${encodeURIComponent(artifact.id)}`, {
        method: 'PATCH',
        body: JSON.stringify({
          title: artifact.title,
          content: JSON.stringify(artifact.payload ?? {}),
          status: mapStatusToApi(artifact.status),
          metadata: {
            kind: artifact.kind,
            version: artifact.version,
            tags: artifact.tags,
            updatedBy: artifact.updatedBy,
            aiGeneration: artifact.aiGeneration,
          },
        }),
      });
    } catch {
      // Keep local update even if remote persistence fails.
    }
  }

  async delete(id: string): Promise<boolean> {
    const deleted = await this.fallback.delete(id);

    try {
      await this.request(`/artifacts/${encodeURIComponent(id)}`, {
        method: 'DELETE',
      });
      return true;
    } catch {
      return deleted;
    }
  }

  private async request<T = unknown>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const token = getAuthToken();
    const response = await fetch(`${LIFECYCLE_API_BASE}${endpoint}`, {
      ...options,
      headers: {
        Accept: 'application/json',
        ...(options.body
          ? {
              'Content-Type': 'application/json',
            }
          : {}),
        ...(token
          ? {
              Authorization: `Bearer ${token}`,
            }
          : {}),
        ...options.headers,
      },
    });

    if (!response.ok) {
      throw new Error(`Lifecycle API request failed (${response.status})`);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return (await response.json()) as T;
  }
}

// ============================================================================
// In-Memory Repository
// ============================================================================

/**
 * In-memory repository implementation for development/testing.
 */
export class InMemoryLifecycleArtifactRepository implements ILifecycleArtifactRepository {
  private artifacts: Map<string, LifecycleArtifact> = new Map();

  async save(artifact: LifecycleArtifact): Promise<void> {
    this.artifacts.set(artifact.id, { ...artifact });
  }

  async findById(id: string): Promise<LifecycleArtifact | null> {
    const artifact = this.artifacts.get(id);
    return artifact ? { ...artifact } : null;
  }

  async findByProjectAndKind(
    projectId: string,
    kind: LifecycleArtifactKind
  ): Promise<LifecycleArtifact[]> {
    return Array.from(this.artifacts.values())
      .filter((a) => a.projectId === projectId && a.kind === kind)
      .map((a) => ({ ...a }));
  }

  async findByProject(projectId: string): Promise<LifecycleArtifact[]> {
    return Array.from(this.artifacts.values())
      .filter((a) => a.projectId === projectId)
      .map((a) => ({ ...a }));
  }

  async findByFilter(filter: ArtifactFilter): Promise<LifecycleArtifact[]> {
    return Array.from(this.artifacts.values())
      .filter((artifact) => {
        if (filter.projectId && artifact.projectId !== filter.projectId) {
          return false;
        }
        if (filter.kinds && !filter.kinds.includes(artifact.kind)) {
          return false;
        }
        if (filter.phases) {
          const metadata = LIFECYCLE_ARTIFACT_CATALOG[artifact.kind];
          if (!filter.phases.includes(metadata.phase)) {
            return false;
          }
        }
        if (filter.status && !filter.status.includes(artifact.status)) {
          return false;
        }
        if (filter.search) {
          const search = filter.search.toLowerCase();
          if (!artifact.title.toLowerCase().includes(search)) {
            return false;
          }
        }
        return true;
      })
      .map((a) => ({ ...a }));
  }

  async update(artifact: LifecycleArtifact): Promise<void> {
    if (!this.artifacts.has(artifact.id)) {
      throw new Error(`Artifact not found: ${artifact.id}`);
    }
    this.artifacts.set(artifact.id, { ...artifact });
  }

  async delete(id: string): Promise<boolean> {
    return this.artifacts.delete(id);
  }
}

/**
 * Browser-backed repository that persists artifacts across reloads.
 */
export class LocalStorageLifecycleArtifactRepository
  implements ILifecycleArtifactRepository
{
  private fallback = new InMemoryLifecycleArtifactRepository();

  private readAll(): LifecycleArtifact[] {
    if (!canUseStorage()) {
      return [];
    }

    const artifacts: LifecycleArtifact[] = [];
    for (let index = 0; index < window.localStorage.length; index += 1) {
      const key = window.localStorage.key(index);
      if (!key?.startsWith('yappc.lifecycle.artifacts.')) {
        continue;
      }

      const raw = window.localStorage.getItem(key);
      if (!raw) {
        continue;
      }

      try {
        const parsed = JSON.parse(raw) as LifecycleArtifact[];
        artifacts.push(...parsed);
      } catch {
        continue;
      }
    }

    return artifacts;
  }

  private readProject(projectId: string): LifecycleArtifact[] {
    if (!canUseStorage()) {
      return [];
    }

    const raw = window.localStorage.getItem(artifactStorageKey(projectId));
    if (!raw) {
      return [];
    }

    try {
      return JSON.parse(raw) as LifecycleArtifact[];
    } catch {
      return [];
    }
  }

  private writeProject(projectId: string, artifacts: LifecycleArtifact[]): void {
    if (!canUseStorage()) {
      return;
    }

    window.localStorage.setItem(
      artifactStorageKey(projectId),
      JSON.stringify(artifacts)
    );
  }

  async save(artifact: LifecycleArtifact): Promise<void> {
    if (!canUseStorage()) {
      return this.fallback.save(artifact);
    }

    const artifacts = this.readProject(artifact.projectId).filter(
      (existing) => existing.id !== artifact.id
    );
    artifacts.push({ ...artifact });
    this.writeProject(artifact.projectId, artifacts);
  }

  async findById(id: string): Promise<LifecycleArtifact | null> {
    if (!canUseStorage()) {
      return this.fallback.findById(id);
    }

    const artifact = this.readAll().find((candidate) => candidate.id === id);
    return artifact ? { ...artifact } : null;
  }

  async findByProjectAndKind(
    projectId: string,
    kind: LifecycleArtifactKind
  ): Promise<LifecycleArtifact[]> {
    const artifacts = canUseStorage()
      ? this.readProject(projectId)
      : await this.fallback.findByProject(projectId);

    return artifacts
      .filter((artifact) => artifact.kind === kind)
      .map((artifact) => ({ ...artifact }));
  }

  async findByProject(projectId: string): Promise<LifecycleArtifact[]> {
    if (!canUseStorage()) {
      return this.fallback.findByProject(projectId);
    }

    return this.readProject(projectId).map((artifact) => ({ ...artifact }));
  }

  async findByFilter(filter: ArtifactFilter): Promise<LifecycleArtifact[]> {
    const artifacts = canUseStorage()
      ? this.readAll()
      : await this.fallback.findByFilter(filter);

    return artifacts
      .filter((artifact) => {
        if (filter.projectId && artifact.projectId !== filter.projectId) {
          return false;
        }
        if (filter.kinds && !filter.kinds.includes(artifact.kind)) {
          return false;
        }
        if (filter.phases) {
          const metadata = LIFECYCLE_ARTIFACT_CATALOG[artifact.kind];
          if (!filter.phases.includes(metadata.phase)) {
            return false;
          }
        }
        if (filter.status && !filter.status.includes(artifact.status)) {
          return false;
        }
        if (filter.search) {
          const search = filter.search.toLowerCase();
          if (!artifact.title.toLowerCase().includes(search)) {
            return false;
          }
        }
        return true;
      })
      .map((artifact) => ({ ...artifact }));
  }

  async update(artifact: LifecycleArtifact): Promise<void> {
    if (!canUseStorage()) {
      return this.fallback.update(artifact);
    }

    const artifacts = this.readProject(artifact.projectId);
    const index = artifacts.findIndex((existing) => existing.id === artifact.id);
    if (index === -1) {
      throw new Error(`Artifact not found: ${artifact.id}`);
    }

    artifacts[index] = { ...artifact };
    this.writeProject(artifact.projectId, artifacts);
  }

  async delete(id: string): Promise<boolean> {
    if (!canUseStorage()) {
      return this.fallback.delete(id);
    }

    const artifact = await this.findById(id);
    if (!artifact) {
      return false;
    }

    const artifacts = this.readProject(artifact.projectId).filter(
      (existing) => existing.id !== id
    );
    this.writeProject(artifact.projectId, artifacts);
    return true;
  }
}

// ============================================================================
// Service Implementation
// ============================================================================

/**
 * Lifecycle Artifact Service.
 *
 * Manages lifecycle artifacts with CRUD operations, status tracking,
 * and dependency graph generation.
 */
export class LifecycleArtifactService {
  constructor(private readonly repository: ILifecycleArtifactRepository) {}

  // -------------------------------------------------------------------------
  // CRUD Operations
  // -------------------------------------------------------------------------

  /**
   * Create a new lifecycle artifact.
   */
  async createArtifact(
    request: CreateArtifactRequest,
    userId: string
  ): Promise<LifecycleArtifact> {
    const metadata = LIFECYCLE_ARTIFACT_CATALOG[request.kind];
    const now = new Date().toISOString();

    const artifact: LifecycleArtifact = {
      id: this.generateId(),
      projectId: request.projectId,
      kind: request.kind,
      title: request.title || metadata.label,
      payload: request.payload || this.getDefaultPayload(request.kind),
      status: request.status || 'draft',
      tags: [createArtifactTag(request.kind)],
      version: 1,
      createdAt: now,
      updatedAt: now,
      createdBy: userId,
      updatedBy: userId,
    };

    await this.repository.save(artifact);
    return artifact;
  }

  /**
   * Get artifact by ID.
   */
  async getArtifact(id: string): Promise<LifecycleArtifact | null> {
    return this.repository.findById(id);
  }

  /**
   * Get artifact by project and kind.
   * Returns the most recent artifact of that kind.
   */
  async getArtifactByKind(
    projectId: string,
    kind: LifecycleArtifactKind
  ): Promise<LifecycleArtifact | null> {
    const artifacts = await this.repository.findByProjectAndKind(
      projectId,
      kind
    );
    if (artifacts.length === 0) {
      return null;
    }
    // Return the most recently updated one
    return artifacts.sort(
      (a, b) =>
        new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
    )[0];
  }

  /**
   * List artifacts for a project with optional filtering.
   */
  async listArtifacts(filter: ArtifactFilter): Promise<ArtifactSummary[]> {
    const artifacts = await this.repository.findByFilter(filter);
    return artifacts.map((a) => ({
      id: a.id,
      kind: a.kind,
      title: a.title,
      status: a.status,
      phase: LIFECYCLE_ARTIFACT_CATALOG[a.kind].phase,
      updatedAt: a.updatedAt,
    }));
  }

  /**
   * Update an existing artifact.
   */
  async updateArtifact(
    id: string,
    request: UpdateArtifactRequest,
    userId: string
  ): Promise<LifecycleArtifact> {
    const existing = await this.repository.findById(id);
    if (!existing) {
      throw new Error(`Artifact not found: ${id}`);
    }

    const updated: LifecycleArtifact = {
      ...existing,
      title: request.title ?? existing.title,
      payload: request.payload ?? existing.payload,
      status: request.status ?? existing.status,
      version: existing.version + 1,
      updatedAt: new Date().toISOString(),
      updatedBy: userId,
    };

    await this.repository.update(updated);
    return updated;
  }

  /**
   * Delete an artifact.
   */
  async deleteArtifact(id: string): Promise<boolean> {
    return this.repository.delete(id);
  }

  // -------------------------------------------------------------------------
  // Status Operations
  // -------------------------------------------------------------------------

  /**
   * Mark artifact as complete.
   */
  async markComplete(id: string, userId: string): Promise<LifecycleArtifact> {
    return this.updateArtifact(id, { status: 'complete' }, userId);
  }

  /**
   * Mark artifact as validated.
   */
  async markValidated(id: string, userId: string): Promise<LifecycleArtifact> {
    return this.updateArtifact(id, { status: 'validated' }, userId);
  }

  /**
   * Archive an artifact.
   */
  async archiveArtifact(
    id: string,
    userId: string
  ): Promise<LifecycleArtifact> {
    return this.updateArtifact(id, { status: 'archived' }, userId);
  }

  // -------------------------------------------------------------------------
  // Dependency Graph
  // -------------------------------------------------------------------------

  /**
   * Generate artifact dependency graph for a project.
   */
  async getArtifactGraph(projectId: string): Promise<ArtifactGraph> {
    const artifacts = await this.repository.findByProject(projectId);
    const nodes: ArtifactNode[] = [];
    const edges: ArtifactEdge[] = [];

    // Build nodes from existing artifacts
    const artifactByKind = new Map<LifecycleArtifactKind, LifecycleArtifact>();
    for (const artifact of artifacts) {
      artifactByKind.set(artifact.kind, artifact);
      nodes.push({
        id: artifact.id,
        kind: artifact.kind,
        title: artifact.title,
        status: artifact.status,
        phase: LIFECYCLE_ARTIFACT_CATALOG[artifact.kind].phase,
      });
    }

    // Build edges from required upstream
    for (const artifact of artifacts) {
      const metadata = LIFECYCLE_ARTIFACT_CATALOG[artifact.kind];
      for (const upstreamKind of metadata.requiredUpstream) {
        const upstreamArtifact = artifactByKind.get(upstreamKind);
        if (upstreamArtifact) {
          edges.push({
            source: upstreamArtifact.id,
            target: artifact.id,
            type: 'depends_on',
          });
        }
      }
    }

    return { nodes, edges };
  }

  /**
   * Get missing upstream artifacts for a given kind.
   */
  async getMissingUpstream(
    projectId: string,
    kind: LifecycleArtifactKind
  ): Promise<LifecycleArtifactKind[]> {
    const metadata = LIFECYCLE_ARTIFACT_CATALOG[kind];
    const missing: LifecycleArtifactKind[] = [];

    for (const upstreamKind of metadata.requiredUpstream) {
      const artifact = await this.getArtifactByKind(projectId, upstreamKind);
      if (!artifact) {
        missing.push(upstreamKind);
      }
    }

    return missing;
  }

  // -------------------------------------------------------------------------
  // Phase Operations
  // -------------------------------------------------------------------------

  /**
   * Get all artifacts for a specific phase.
   */
  async getArtifactsByPhase(
    projectId: string,
    phase: LifecyclePhase
  ): Promise<LifecycleArtifact[]> {
    return this.repository.findByFilter({
      projectId,
      phases: [phase],
    });
  }

  /**
   * Check if all required artifacts for a phase are complete.
   */
  async isPhaseComplete(
    projectId: string,
    phase: LifecyclePhase
  ): Promise<boolean> {
    const requiredKinds = (
      Object.values(LIFECYCLE_ARTIFACT_CATALOG) as LifecycleArtifactMetadata[]
    )
      .filter((m) => m.phase === phase)
      .map((m) => m.kind);

    for (const kind of requiredKinds) {
      const artifact = await this.getArtifactByKind(projectId, kind);
      if (!artifact || artifact.status === 'draft') {
        return false;
      }
    }

    return true;
  }

  // -------------------------------------------------------------------------
  // Metadata & Utilities
  // -------------------------------------------------------------------------

  /**
   * Get artifact metadata by kind.
   */
  getMetadata(kind: LifecycleArtifactKind): LifecycleArtifactMetadata {
    return LIFECYCLE_ARTIFACT_CATALOG[kind];
  }

  /**
   * Get all artifact kinds for a phase.
   */
  getKindsForPhase(phase: LifecyclePhase): LifecycleArtifactKind[] {
    return (
      Object.values(LIFECYCLE_ARTIFACT_CATALOG) as LifecycleArtifactMetadata[]
    )
      .filter((m) => m.phase === phase)
      .map((m) => m.kind);
  }

  /**
   * Get placement info for an artifact kind.
   */
  getPlacement(kind: LifecycleArtifactKind): ArtifactPlacement {
    return LIFECYCLE_ARTIFACT_CATALOG[kind].placement;
  }

  /**
   * Get URL for navigating to an artifact.
   */
  getArtifactUrl(projectId: string, kind: LifecycleArtifactKind): string {
    const placement = this.getPlacement(kind);
    const base = `/p/${projectId}`;

    switch (placement.surface) {
      case 'app':
        return `${base}?${placement.paramType}=${placement.param}`;
      case 'canvas':
        return `${base}/canvas?${placement.paramType}=${placement.param}`;
      case 'preview':
        return `${base}/preview?${placement.paramType}=${placement.param}`;
      case 'deploy':
        return `${base}/deploy?${placement.paramType}=${placement.param}`;
      default:
        return base;
    }
  }

  // -------------------------------------------------------------------------
  // Private Methods
  // -------------------------------------------------------------------------

  /**
   * Generate a unique artifact ID.
   */
  private generateId(): string {
    return `art_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Get default payload for an artifact kind.
   */
  private getDefaultPayload(
    kind: LifecycleArtifactKind
  ): Record<string, unknown> {
    // Return empty default payloads based on kind
    switch (kind) {
      case 'idea_brief':
        return {
          title: '',
          oneLiner: '',
          targetUsers: [],
          businessValue: '',
          constraints: [],
          assumptions: [],
        };
      case 'research_pack':
        return {
          sources: [],
          marketNotes: '',
          userInsights: [],
          risks: [],
          openQuestions: [],
        };
      case 'problem_statement':
        return {
          problem: '',
          who: '',
          when: '',
          whyNow: '',
          successMetrics: [],
          nonGoals: [],
        };
      case 'requirements':
        return { epics: [] };
      case 'adr':
        return {
          context: '',
          decision: '',
          options: [],
          consequences: '',
          status: 'proposed',
        };
      case 'ux_spec':
        return {
          primaryFlows: [],
          iaNotes: '',
          a11yNotes: '',
          contentNotes: '',
          edgeCases: [],
        };
      case 'threat_model':
        return {
          assets: [],
          actors: [],
          threats: [],
          mitigations: [],
          residualRisk: '',
        };
      case 'validation_report':
        return {
          coverageSummary: { total: 0, passed: 0, failed: 0, skipped: 0 },
          a11yFindings: [],
          perfFindings: [],
          riskFindings: [],
          recommendations: [],
        };
      case 'simulation_results':
        return {
          scenarios: [],
          outcomes: [],
          edgeCases: [],
          confidenceScore: 0,
        };
      case 'delivery_plan':
        return {
          milestones: [],
          workItems: [],
        };
      case 'release_strategy':
        return {
          releaseType: 'rolling',
          environments: [],
          rolloutSteps: [],
          featureFlags: [],
          rollbackPlan: '',
        };
      case 'evidence_pack':
        return {
          testResults: [],
          securityScans: [],
          buildArtifacts: [],
          complianceChecks: [],
        };
      case 'release_packet':
        return {
          releaseNotes: '',
          faq: [],
          runbook: '',
          contacts: [],
        };
      case 'ops_baseline':
        return {
          sloTargets: [],
          baselineMetrics: [],
          dashboards: [],
          alerts: [],
        };
      case 'incident_report':
        return {
          timeline: [],
          rootCause: '',
          impact: '',
          mitigations: [],
          postMortemUrl: '',
        };
      case 'enhancement_requests':
        return {
          requests: [],
          prioritization: [],
        };
      case 'learning_record':
        return {
          retrospectives: [],
          insights: [],
          recommendations: [],
        };
      default:
        return {};
    }
  }
}

// ============================================================================
// React Hook
// ============================================================================

import React from 'react';

/**
 * React hook for lifecycle artifact operations.
 */
export function useLifecycleArtifacts(projectId: string) {
  const [service] = React.useState(
    () =>
      new LifecycleArtifactService(new ApiBackedLifecycleArtifactRepository())
  );
  const [artifacts, setArtifacts] = React.useState<ArtifactSummary[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<Error | null>(null);

  const refresh = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await service.listArtifacts({ projectId });
      setArtifacts(list);
    } catch (err) {
      setError(
        err instanceof Error ? err : new Error('Failed to load artifacts')
      );
    } finally {
      setLoading(false);
    }
  }, [service, projectId]);

  React.useEffect(() => {
    refresh();
  }, [refresh]);

  const createArtifact = React.useCallback(
    async (kind: LifecycleArtifactKind, userId: string) => {
      const artifact = await service.createArtifact(
        { projectId, kind },
        userId
      );
      await refresh();
      return artifact;
    },
    [service, projectId, refresh]
  );

  const updateArtifact = React.useCallback(
    async (id: string, request: UpdateArtifactRequest, userId: string) => {
      const artifact = await service.updateArtifact(id, request, userId);
      await refresh();
      return artifact;
    },
    [service, refresh]
  );

  const deleteArtifact = React.useCallback(
    async (id: string) => {
      await service.deleteArtifact(id);
      await refresh();
    },
    [service, refresh]
  );

  const getGraph = React.useCallback(async () => {
    return service.getArtifactGraph(projectId);
  }, [service, projectId]);

  return {
    artifacts,
    loading,
    error,
    refresh,
    createArtifact,
    updateArtifact,
    deleteArtifact,
    getGraph,
    service,
  };
}
