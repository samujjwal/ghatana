/**
 * @fileoverview BuilderDocument persistence, autosave, version restore, and session recovery.
 *
 * Provides a persistence adapter interface and a concrete localStorage-backed
 * implementation, plus autosave orchestration with debouncing and session recovery.
 */

import type { BuilderDocument, DocumentId, NodeId, ComponentInstance } from './types.js';

// ============================================================================
// Serialization helpers
// ============================================================================

/** Serialise a BuilderDocument to a plain JSON-safe object. */
export function serializeDocument(doc: BuilderDocument): SerializedDocument {
  return {
    ...doc,
    nodes: Object.fromEntries(doc.nodes),
  };
}

/** Deserialise a plain object back to a BuilderDocument. */
export function deserializeDocument(raw: SerializedDocument): BuilderDocument {
  return {
    ...raw,
    id: raw.id as DocumentId,
    nodes: new Map(
      Object.entries(raw.nodes).map(([k, v]) => [k as NodeId, v as ComponentInstance])
    ) as unknown as ReadonlyMap<NodeId, ComponentInstance>,
  };
}

export interface SerializedDocument {
  readonly id: string;
  readonly version: string;
  readonly name: string;
  readonly designSystem: BuilderDocument['designSystem'];
  readonly rootNodes: BuilderDocument['rootNodes'];
  readonly nodes: Record<string, BuilderDocument['nodes'] extends ReadonlyMap<infer _K, infer V> ? V : never>;
  readonly metadata: BuilderDocument['metadata'];
}

// ============================================================================
// Version entry
// ============================================================================

export interface DocumentVersion {
  readonly versionId: string;
  readonly documentId: string;
  readonly savedAt: number;
  readonly label: string;
  readonly document: SerializedDocument;
}

// ============================================================================
// Persistence Adapter Interface
// ============================================================================

/** Pluggable persistence adapter — implement to support any backend. */
export interface PersistenceAdapter {
  /** Save the current document state. Returns the version ID. */
  save(doc: BuilderDocument, label: string): Promise<string>;

  /** Load the latest saved document for the given ID. */
  load(documentId: string): Promise<BuilderDocument | null>;

  /** List all saved versions for a document, newest first. */
  listVersions(documentId: string): Promise<readonly DocumentVersion[]>;

  /** Restore a specific version. */
  restoreVersion(documentId: string, versionId: string): Promise<BuilderDocument | null>;

  /** Delete a specific version. */
  deleteVersion(documentId: string, versionId: string): Promise<void>;

  /** Clear all data for a document. */
  clearDocument(documentId: string): Promise<void>;
}

// ============================================================================
// localStorage Adapter
// ============================================================================

const LS_KEY_PREFIX = '@ghatana/ui-builder:doc:';
const LS_VERSIONS_SUFFIX = ':versions';
const MAX_VERSIONS = 50;

/** localStorage-backed persistence adapter. Safe to use in browser environments. */
export class LocalStoragePersistenceAdapter implements PersistenceAdapter {
  private readonly prefix: string;

  constructor(prefix: string = LS_KEY_PREFIX) {
    this.prefix = prefix;
  }

  async save(doc: BuilderDocument, label: string): Promise<string> {
    const versionId = `v-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    const entry: DocumentVersion = {
      versionId,
      documentId: doc.id,
      savedAt: Date.now(),
      label,
      document: serializeDocument(doc),
    };

    // Save latest
    localStorage.setItem(this.prefix + doc.id, JSON.stringify(entry.document));

    // Append to version list
    const versionsKey = this.prefix + doc.id + LS_VERSIONS_SUFFIX;
    const existing: DocumentVersion[] = this.readVersionList(versionsKey);
    existing.unshift(entry);
    if (existing.length > MAX_VERSIONS) {
      existing.splice(MAX_VERSIONS);
    }
    localStorage.setItem(versionsKey, JSON.stringify(existing));

    return versionId;
  }

  async load(documentId: string): Promise<BuilderDocument | null> {
    const raw = localStorage.getItem(this.prefix + documentId);
    if (!raw) return null;
    try {
      return deserializeDocument(JSON.parse(raw) as SerializedDocument);
    } catch {
      return null;
    }
  }

  async listVersions(documentId: string): Promise<readonly DocumentVersion[]> {
    return this.readVersionList(this.prefix + documentId + LS_VERSIONS_SUFFIX);
  }

  async restoreVersion(documentId: string, versionId: string): Promise<BuilderDocument | null> {
    const versions = await this.listVersions(documentId);
    const entry = versions.find((v) => v.versionId === versionId);
    if (!entry) return null;
    return deserializeDocument(entry.document);
  }

  async deleteVersion(documentId: string, versionId: string): Promise<void> {
    const versionsKey = this.prefix + documentId + LS_VERSIONS_SUFFIX;
    const versions = this.readVersionList(versionsKey);
    const updated = versions.filter((v) => v.versionId !== versionId);
    localStorage.setItem(versionsKey, JSON.stringify(updated));
  }

  async clearDocument(documentId: string): Promise<void> {
    localStorage.removeItem(this.prefix + documentId);
    localStorage.removeItem(this.prefix + documentId + LS_VERSIONS_SUFFIX);
  }

  private readVersionList(key: string): DocumentVersion[] {
    const raw = localStorage.getItem(key);
    if (!raw) return [];
    try {
      return JSON.parse(raw) as DocumentVersion[];
    } catch {
      return [];
    }
  }
}

// ============================================================================
// In-Memory Adapter (tests / SSR)
// ============================================================================

/** In-memory persistence adapter. Useful for testing and server environments. */
export class InMemoryPersistenceAdapter implements PersistenceAdapter {
  private readonly docs = new Map<string, SerializedDocument>();
  private readonly versions = new Map<string, DocumentVersion[]>();

  async save(doc: BuilderDocument, label: string): Promise<string> {
    const versionId = `v-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    const entry: DocumentVersion = {
      versionId,
      documentId: doc.id,
      savedAt: Date.now(),
      label,
      document: serializeDocument(doc),
    };
    this.docs.set(doc.id, entry.document);
    const list = this.versions.get(doc.id) ?? [];
    list.unshift(entry);
    if (list.length > MAX_VERSIONS) list.splice(MAX_VERSIONS);
    this.versions.set(doc.id, list);
    return versionId;
  }

  async load(documentId: string): Promise<BuilderDocument | null> {
    const raw = this.docs.get(documentId);
    return raw ? deserializeDocument(raw) : null;
  }

  async listVersions(documentId: string): Promise<readonly DocumentVersion[]> {
    return this.versions.get(documentId) ?? [];
  }

  async restoreVersion(documentId: string, versionId: string): Promise<BuilderDocument | null> {
    const entry = (this.versions.get(documentId) ?? []).find((v) => v.versionId === versionId);
    return entry ? deserializeDocument(entry.document) : null;
  }

  async deleteVersion(documentId: string, versionId: string): Promise<void> {
    const list = this.versions.get(documentId) ?? [];
    this.versions.set(documentId, list.filter((v) => v.versionId !== versionId));
  }

  async clearDocument(documentId: string): Promise<void> {
    this.docs.delete(documentId);
    this.versions.delete(documentId);
  }
}

// ============================================================================
// Autosave Orchestrator
// ============================================================================

export interface AutosaveConfig {
  /** Debounce delay in ms. Default: 2000 */
  readonly debounceMs: number;
  /** Label generator for autosave versions. */
  readonly labelFn: (doc: BuilderDocument) => string;
  /** Called after each successful save with the version ID. */
  readonly onSaved?: (versionId: string) => void;
  /** Called on save error. */
  readonly onError?: (err: unknown) => void;
}

const DEFAULT_AUTOSAVE_CONFIG: AutosaveConfig = {
  debounceMs: 2000,
  labelFn: (doc) => `Autosave – ${new Date().toISOString()} (${doc.metadata.updatedAt})`,
};

/**
 * Autosave orchestrator. Call `schedule(doc)` whenever the document changes;
 * the actual save is debounced. Call `flush()` to force an immediate save
 * (e.g. before navigation). Call `dispose()` to cancel pending timers.
 */
export class AutosaveOrchestrator {
  private readonly adapter: PersistenceAdapter;
  private readonly config: AutosaveConfig;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private pending: BuilderDocument | null = null;
  private saving = false;

  constructor(adapter: PersistenceAdapter, config: Partial<AutosaveConfig> = {}) {
    this.adapter = adapter;
    this.config = { ...DEFAULT_AUTOSAVE_CONFIG, ...config };
  }

  /** Schedule an autosave. Resets debounce timer if called again before it fires. */
  schedule(doc: BuilderDocument): void {
    this.pending = doc;
    if (this.timer !== null) {
      clearTimeout(this.timer);
    }
    this.timer = setTimeout(() => {
      void this.executeSave();
    }, this.config.debounceMs);
  }

  /** Force save immediately. Safe to call before page unload. */
  async flush(): Promise<string | null> {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    return this.executeSave();
  }

  /** Cancel any pending autosave. Does not save. */
  dispose(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.pending = null;
  }

  private async executeSave(): Promise<string | null> {
    if (!this.pending || this.saving) return null;
    this.saving = true;
    const doc = this.pending;
    this.pending = null;
    try {
      const label = this.config.labelFn(doc);
      const versionId = await this.adapter.save(doc, label);
      this.config.onSaved?.(versionId);
      return versionId;
    } catch (err: unknown) {
      this.config.onError?.(err);
      return null;
    } finally {
      this.saving = false;
    }
  }
}

// ============================================================================
// Session Recovery
// ============================================================================

/** Attempt to recover the most recent session document from the adapter. */
export async function recoverSession(
  documentId: string,
  adapter: PersistenceAdapter,
): Promise<BuilderDocument | null> {
  return adapter.load(documentId);
}
