import type {
  PageArtifactDocument,
  PageArtifactGraphEdge,
  PageArtifactGraphNode,
  PageArtifactGraphSnapshot,
} from './pageArtifactDocument';

export interface PageArtifactDocumentPersistenceAdapter {
  save(document: PageArtifactDocument): Promise<void>;
  load(artifactId: string): Promise<PageArtifactDocument | null>;
}

export interface PageArtifactScope {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
}

export type PageArtifactPersistenceErrorKind =
  | 'missing-scope'
  | 'unauthenticated'
  | 'forbidden'
  | 'validation'
  | 'artifact-graph'
  | 'http';

export class PageArtifactPersistenceError extends Error {
  constructor(
    readonly kind: PageArtifactPersistenceErrorKind,
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = 'PageArtifactPersistenceError';
  }
}

// ---------------------------------------------------------------------------
// Conflict detection
// ---------------------------------------------------------------------------

/** Thrown when a save detects a remote version newer than the document being saved. */
export class PageArtifactConflictError extends Error {
  readonly kind = 'conflict' as const;
  readonly remoteVersion: string;

  constructor(artifactId: string, remoteVersion: string) {
    super(`Conflict saving artifact "${artifactId}": remote version is "${remoteVersion}". Reload and re-apply changes.`);
    this.name = 'PageArtifactConflictError';
    this.remoteVersion = remoteVersion;
  }
}

export function isConflictError(err: unknown): err is PageArtifactConflictError {
  return err instanceof PageArtifactConflictError;
}

export function isNonFallbackPersistenceError(err: unknown): boolean {
  return (
    err instanceof PageArtifactPersistenceError &&
    (err.kind === 'missing-scope' ||
      err.kind === 'unauthenticated' ||
      err.kind === 'forbidden' ||
      err.kind === 'artifact-graph' ||
      err.kind === 'validation')
  );
}

interface HttpPersistenceOptions {
  readonly baseUrl?: string;
  readonly artifactGraphBaseUrl?: string;
  readonly fetchImpl?: typeof fetch;
  readonly scope?: PageArtifactScope;
  readonly scopeProvider?: () => PageArtifactScope | null;
}

interface ArtifactGraphIngestNodeDto {
  readonly id: string;
  readonly type: string;
  readonly name: string;
  readonly filePath: string;
  readonly content: string;
  readonly properties: Record<string, string | number | boolean | null>;
  readonly tags: readonly string[];
  readonly tenantId: string;
  readonly projectId: string;
}

interface ArtifactGraphIngestEdgeDto {
  readonly sourceNodeId: string;
  readonly targetNodeId: string;
  readonly relationshipType: string;
  readonly properties: Record<string, string | number | boolean | null>;
}

interface ArtifactGraphIngestRequest {
  readonly productId: string;
  readonly tenantId: string;
  readonly nodes: readonly ArtifactGraphIngestNodeDto[];
  readonly edges: readonly ArtifactGraphIngestEdgeDto[];
}

export interface LocalDraftPolicy {
  readonly allowClassifications: ReadonlySet<string>;
  /** Maximum age for locally stored drafts. Defaults to 24 hours. */
  readonly ttlMs?: number;
}

const DEFAULT_STORAGE_PREFIX = '@ghatana/yappc:page-artifact:';
const DEFAULT_LOCAL_DRAFT_POLICY: LocalDraftPolicy = {
  allowClassifications: new Set(['PUBLIC', 'INTERNAL']),
  ttlMs: 24 * 60 * 60 * 1000,
};

interface LocalDraftEnvelope {
  readonly schemaVersion: 1;
  readonly savedAt: string;
  readonly expiresAt: string | null;
  readonly document: PageArtifactDocument;
}

function normalizeClassification(classification: string): string {
  return classification.trim().toUpperCase();
}

function isLocalDraftEnvelope(value: unknown): value is LocalDraftEnvelope {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const candidate = value as Record<string, unknown>;
  return (
    candidate.schemaVersion === 1 &&
    typeof candidate.savedAt === 'string' &&
    (typeof candidate.expiresAt === 'string' || candidate.expiresAt === null) &&
    typeof candidate.document === 'object' &&
    candidate.document !== null
  );
}

function isPageArtifactDocument(value: unknown): value is PageArtifactDocument {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.artifactId === 'string' &&
    typeof candidate.documentId === 'string' &&
    typeof candidate.dataClassification === 'string' &&
    typeof candidate.syncStatus === 'string' &&
    typeof candidate.serializedBuilderDocument === 'object' &&
    candidate.serializedBuilderDocument !== null
  );
}

function buildLocalDraftEnvelope(
  document: PageArtifactDocument,
  policy: LocalDraftPolicy,
): LocalDraftEnvelope {
  const savedAtDate = new Date();
  const ttlMs = policy.ttlMs ?? DEFAULT_LOCAL_DRAFT_POLICY.ttlMs;
  return {
    schemaVersion: 1,
    savedAt: savedAtDate.toISOString(),
    expiresAt: typeof ttlMs === 'number' && ttlMs > 0
      ? new Date(savedAtDate.getTime() + ttlMs).toISOString()
      : null,
    document,
  };
}

function isLocalDraftExpired(envelope: LocalDraftEnvelope): boolean {
  if (envelope.expiresAt === null) {
    return false;
  }

  const expiresAt = Date.parse(envelope.expiresAt);
  return Number.isNaN(expiresAt) || expiresAt <= Date.now();
}

function isLocalDraftAllowed(
  document: PageArtifactDocument,
  policy: LocalDraftPolicy,
): boolean {
  return policy.allowClassifications.has(normalizeClassification(document.dataClassification));
}

function validateScope(scope: PageArtifactScope | null | undefined): PageArtifactScope {
  if (!scope?.tenantId || !scope.workspaceId || !scope.projectId) {
    throw new PageArtifactPersistenceError(
      'missing-scope',
      'Page artifact persistence requires tenant, workspace, and project scope.',
      422,
    );
  }

  return scope;
}

async function readErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const text = await response.text();
    return text ? `${fallback}: ${text.slice(0, 300)}` : fallback;
  } catch {
    return fallback;
  }
}

function buildGraphNodeProperties(
  graph: PageArtifactGraphSnapshot,
  node: PageArtifactGraphNode,
  artifactId: string,
): Record<string, string | number | boolean | null> {
  return {
    artifactId,
    graphId: graph.graphId,
    sourceType: graph.sourceType,
    importedAt: graph.importedAt,
    sourceLocationFilePath: node.sourceLocation?.filePath ?? null,
    sourceLocationStartLine: node.sourceLocation?.startLine ?? null,
    sourceLocationStartColumn: node.sourceLocation?.startColumn ?? null,
    sourceLocationEndLine: node.sourceLocation?.endLine ?? null,
    sourceLocationEndColumn: node.sourceLocation?.endColumn ?? null,
    confidence: graph.provenance.confidence,
    residualIslandCount: graph.provenance.residualIslandIds.length,
    ...node.metadata,
  };
}

function buildGraphEdgeProperties(
  graph: PageArtifactGraphSnapshot,
  edge: PageArtifactGraphEdge,
  artifactId: string,
): Record<string, string | number | boolean | null> {
  return {
    artifactId,
    edgeId: edge.id,
    graphId: graph.graphId,
    projectId: graph.projectId,
    importedAt: graph.importedAt,
  };
}

function buildArtifactGraphIngestRequest(
  document: PageArtifactDocument,
  graph: PageArtifactGraphSnapshot,
  scope: PageArtifactScope,
): ArtifactGraphIngestRequest {
  return {
    productId: graph.graphId,
    tenantId: scope.tenantId,
    nodes: graph.nodes.map((node) => ({
      id: node.id,
      type: node.kind,
      name: node.label,
      filePath: node.sourceLocation?.filePath ?? graph.source,
      content: JSON.stringify({
        source: graph.source,
        sourceType: graph.sourceType,
        nodeKind: node.kind,
      }),
      properties: buildGraphNodeProperties(graph, node, document.artifactId),
      tags: [node.kind, graph.sourceType, graph.provenance.compiler],
      tenantId: scope.tenantId,
      projectId: graph.projectId,
    })),
    edges: graph.edges.map((edge) => ({
      sourceNodeId: edge.from,
      targetNodeId: edge.to,
      relationshipType: edge.kind,
      properties: buildGraphEdgeProperties(graph, edge, document.artifactId),
    })),
  };
}

export class LocalStoragePageArtifactPersistenceAdapter
  implements PageArtifactDocumentPersistenceAdapter {
  private readonly prefix: string;
  private readonly policy: LocalDraftPolicy;

  constructor(
    prefix: string = DEFAULT_STORAGE_PREFIX,
    policy: LocalDraftPolicy = DEFAULT_LOCAL_DRAFT_POLICY,
  ) {
    this.prefix = prefix;
    this.policy = policy;
  }

  async save(document: PageArtifactDocument): Promise<void> {
    const classification = normalizeClassification(document.dataClassification);
    if (!this.policy.allowClassifications.has(classification)) {
      throw new PageArtifactPersistenceError(
        'forbidden',
        `Local drafts are disabled for ${classification.toLowerCase()} page artifacts.`,
        403,
      );
    }

    localStorage.setItem(
      this.prefix + document.artifactId,
      JSON.stringify(buildLocalDraftEnvelope(document, this.policy)),
    );
  }

  async load(artifactId: string): Promise<PageArtifactDocument | null> {
    const storageKey = this.prefix + artifactId;
    const raw = localStorage.getItem(storageKey);
    if (!raw) {
      return null;
    }

    try {
      const parsed: unknown = JSON.parse(raw);

      if (isLocalDraftEnvelope(parsed)) {
        if (isLocalDraftExpired(parsed) || !isPageArtifactDocument(parsed.document)) {
          localStorage.removeItem(storageKey);
          return null;
        }

        if (!isLocalDraftAllowed(parsed.document, this.policy)) {
          localStorage.removeItem(storageKey);
          return null;
        }

        return parsed.document;
      }

      if (isPageArtifactDocument(parsed)) {
        if (!isLocalDraftAllowed(parsed, this.policy)) {
          localStorage.removeItem(storageKey);
          return null;
        }

        return parsed;
      }

      localStorage.removeItem(storageKey);
      return null;
    } catch {
      localStorage.removeItem(storageKey);
      return null;
    }
  }
}

export class HttpPageArtifactPersistenceAdapter
  implements PageArtifactDocumentPersistenceAdapter {
  private readonly baseUrl: string;
  private readonly artifactGraphBaseUrl: string;
  private readonly fetchImpl: typeof fetch;
  private readonly scope?: PageArtifactScope;
  private readonly scopeProvider?: () => PageArtifactScope | null;

  constructor(options: HttpPersistenceOptions = {}) {
    this.baseUrl = options.baseUrl ?? '/api/v1/page-artifacts';
    this.artifactGraphBaseUrl = options.artifactGraphBaseUrl ?? '/api/v1/yappc/artifact/graph';
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.scope = options.scope;
    this.scopeProvider = options.scopeProvider;
  }

  private getScope(): PageArtifactScope {
    return validateScope(this.scopeProvider?.() ?? this.scope);
  }

  private buildHeaders(extra?: HeadersInit): HeadersInit {
    const scope = this.getScope();
    return {
      Accept: 'application/json',
      'X-Tenant-ID': scope.tenantId,
      'X-Workspace-ID': scope.workspaceId,
      'X-Project-ID': scope.projectId,
      ...extra,
    };
  }

  private async throwForResponse(response: Response, action: string): Promise<never> {
    if (response.status === 401) {
      throw new PageArtifactPersistenceError('unauthenticated', 'Sign in again to sync this page artifact.', 401);
    }
    if (response.status === 403) {
      throw new PageArtifactPersistenceError('forbidden', 'You do not have permission to sync this page artifact.', 403);
    }
    if (response.status === 422) {
      const message = await readErrorMessage(response, 'The page artifact is missing required scope or has invalid content');
      throw new PageArtifactPersistenceError('validation', message, 422);
    }

    const message = await readErrorMessage(response, `Failed to ${action} page artifact (${response.status})`);
    throw new PageArtifactPersistenceError('http', message, response.status);
  }

  private async ingestArtifactGraph(document: PageArtifactDocument): Promise<void> {
    if (!document.artifactGraph) {
      return;
    }

    const scope = this.getScope();
    const response = await this.fetchImpl(`${this.artifactGraphBaseUrl}/ingest`, {
      method: 'POST',
      headers: this.buildHeaders({
        'Content-Type': 'application/json',
      }),
      credentials: 'include',
      body: JSON.stringify(buildArtifactGraphIngestRequest(document, document.artifactGraph, scope)),
    });

    if (!response.ok) {
      const message = await readErrorMessage(
        response,
        `Failed to ingest artifact graph for page artifact "${document.artifactId}" (${response.status})`,
      );
      throw new PageArtifactPersistenceError('artifact-graph', message, response.status);
    }
  }

  async save(document: PageArtifactDocument): Promise<void> {
    const response = await this.fetchImpl(
      `${this.baseUrl}/${encodeURIComponent(document.artifactId)}/document`,
      {
        method: 'PUT',
        headers: this.buildHeaders({
          'Content-Type': 'application/json',
          // Optimistic concurrency: pass the current documentId as the ETag.
          // The server SHOULD return 409 with the current version in the
          // `X-Current-Version` response header when the precondition fails.
          'If-Match': document.documentId,
        }),
        credentials: 'include',
        body: JSON.stringify(document),
      },
    );

    if (response.status === 409) {
      const remoteVersion = response.headers.get('X-Current-Version') ?? 'unknown';
      throw new PageArtifactConflictError(document.artifactId, remoteVersion);
    }

    if (!response.ok) {
      await this.throwForResponse(response, 'save');
    }

    await this.ingestArtifactGraph(document);
  }

  async load(artifactId: string): Promise<PageArtifactDocument | null> {
    const response = await this.fetchImpl(
      `${this.baseUrl}/${encodeURIComponent(artifactId)}/document`,
      {
        method: 'GET',
        headers: this.buildHeaders(),
        credentials: 'include',
      },
    );

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      await this.throwForResponse(response, 'load');
    }

    return (await response.json()) as PageArtifactDocument;
  }
}

export class ResilientPageArtifactPersistenceAdapter
  implements PageArtifactDocumentPersistenceAdapter {
  private readonly primary: PageArtifactDocumentPersistenceAdapter;
  private readonly fallback: PageArtifactDocumentPersistenceAdapter;

  constructor(
    primary: PageArtifactDocumentPersistenceAdapter,
    fallback: PageArtifactDocumentPersistenceAdapter,
  ) {
    this.primary = primary;
    this.fallback = fallback;
  }

  async save(document: PageArtifactDocument): Promise<void> {
    try {
      await this.primary.save(document);
      await this.fallback.save(document);
    } catch (err) {
      // Re-throw conflict errors — do not swallow them via fallback,
      // as the user must resolve the conflict explicitly.
      if (isConflictError(err)) {
        throw err;
      }
      if (isNonFallbackPersistenceError(err)) {
        throw err;
      }
      await this.fallback.save(document);
    }
  }

  async load(artifactId: string): Promise<PageArtifactDocument | null> {
    try {
      const loaded = await this.primary.load(artifactId);
      if (loaded) {
        return loaded;
      }
    } catch (err) {
      if (isNonFallbackPersistenceError(err)) {
        throw err;
      }
      // Fall through to local recovery.
    }

    return this.fallback.load(artifactId);
  }
}
