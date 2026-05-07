import type { PageArtifactDocument } from './pageArtifactDocument';

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
      err.kind === 'validation')
  );
}

interface HttpPersistenceOptions {
  readonly baseUrl?: string;
  readonly fetchImpl?: typeof fetch;
  readonly scope?: PageArtifactScope;
  readonly scopeProvider?: () => PageArtifactScope | null;
}

export interface LocalDraftPolicy {
  readonly allowClassifications: ReadonlySet<string>;
}

const DEFAULT_STORAGE_PREFIX = '@ghatana/yappc:page-artifact:';
const DEFAULT_LOCAL_DRAFT_POLICY: LocalDraftPolicy = {
  allowClassifications: new Set(['PUBLIC', 'INTERNAL']),
};

function normalizeClassification(classification: string): string {
  return classification.trim().toUpperCase();
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

    localStorage.setItem(this.prefix + document.artifactId, JSON.stringify(document));
  }

  async load(artifactId: string): Promise<PageArtifactDocument | null> {
    const raw = localStorage.getItem(this.prefix + artifactId);
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as PageArtifactDocument;
    } catch {
      return null;
    }
  }
}

export class HttpPageArtifactPersistenceAdapter
  implements PageArtifactDocumentPersistenceAdapter {
  private readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;
  private readonly scope?: PageArtifactScope;
  private readonly scopeProvider?: () => PageArtifactScope | null;

  constructor(options: HttpPersistenceOptions = {}) {
    this.baseUrl = options.baseUrl ?? '/api/v1/page-artifacts';
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
