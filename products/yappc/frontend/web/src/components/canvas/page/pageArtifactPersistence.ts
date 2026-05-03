import type { PageArtifactDocument } from './pageArtifactDocument';

export interface PageArtifactDocumentPersistenceAdapter {
  save(document: PageArtifactDocument): Promise<void>;
  load(artifactId: string): Promise<PageArtifactDocument | null>;
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

interface HttpPersistenceOptions {
  readonly baseUrl?: string;
  readonly fetchImpl?: typeof fetch;
}

const DEFAULT_STORAGE_PREFIX = '@ghatana/yappc:page-artifact:';

export class LocalStoragePageArtifactPersistenceAdapter
  implements PageArtifactDocumentPersistenceAdapter {
  private readonly prefix: string;

  constructor(prefix: string = DEFAULT_STORAGE_PREFIX) {
    this.prefix = prefix;
  }

  async save(document: PageArtifactDocument): Promise<void> {
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

  constructor(options: HttpPersistenceOptions = {}) {
    this.baseUrl = options.baseUrl ?? '/api/v1/page-artifacts';
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  async save(document: PageArtifactDocument): Promise<void> {
    const response = await this.fetchImpl(
      `${this.baseUrl}/${encodeURIComponent(document.artifactId)}/document`,
      {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          // Optimistic concurrency: pass the current documentId as the ETag.
          // The server SHOULD return 409 with the current version in the
          // `X-Current-Version` response header when the precondition fails.
          'If-Match': document.documentId,
        },
        body: JSON.stringify(document),
      },
    );

    if (response.status === 409) {
      const remoteVersion = response.headers.get('X-Current-Version') ?? 'unknown';
      throw new PageArtifactConflictError(document.artifactId, remoteVersion);
    }

    if (!response.ok) {
      throw new Error(`Failed to save page artifact (${response.status})`);
    }
  }

  async load(artifactId: string): Promise<PageArtifactDocument | null> {
    const response = await this.fetchImpl(
      `${this.baseUrl}/${encodeURIComponent(artifactId)}/document`,
      {
        method: 'GET',
        headers: {
          Accept: 'application/json',
        },
      },
    );

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      throw new Error(`Failed to load page artifact (${response.status})`);
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
      await this.fallback.save(document);
    }
  }

  async load(artifactId: string): Promise<PageArtifactDocument | null> {
    try {
      const loaded = await this.primary.load(artifactId);
      if (loaded) {
        return loaded;
      }
    } catch {
      // Fall through to local recovery.
    }

    return this.fallback.load(artifactId);
  }
}
