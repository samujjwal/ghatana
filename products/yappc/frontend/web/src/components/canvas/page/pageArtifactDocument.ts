import {
  createDocumentId,
  deserializeDocument,
  serializeDocument,
  type BuilderDocument,
  type RoundTripFidelity,
  type SerializedDocument,
  type AIActionLineage,
  type AIHookKind,
  type AIReviewState,
  AIActionLineageTracker,
  createLineageEntry,
} from '@ghatana/ui-builder';
import { getContractMap } from './registry';
type DataClassification = NonNullable<BuilderDocument['metadata']['dataClassification']>;
type TrustLevel = NonNullable<BuilderDocument['metadata']['trustLevel']>;

// ---------------------------------------------------------------------------
// AI Change Record
// ---------------------------------------------------------------------------

/**
 * Product-layer record for an AI-originated change applied to a page artifact.
 * Wraps the platform `AIActionLineage` type and adds artifact-scoped context.
 *
 * @doc.type interface
 * @doc.purpose Product-scoped AI change record for page artifact governance
 * @doc.layer product
 */
export interface PageArtifactAIChangeRecord {
  /** The underlying platform lineage entry. */
  readonly lineage: AIActionLineage;
  /** The artifact this change was applied to. */
  readonly artifactId: string;
  /** The document version at the time the change was applied. */
  readonly documentId: string;
}

/**
 * Creates a new `AIChangeRecord` for a given artifact using the platform
 * `createLineageEntry` factory.
 */
export function createAIChangeRecord(
  artifactId: string,
  documentId: string,
  hookKind: AIHookKind,
  reason: string,
  confidence: number,
  affectedNodeIds: readonly string[],
  options?: {
    readonly reversible?: boolean;
    readonly reviewState?: AIReviewState;
    readonly correlationId?: string;
    readonly evidence?: readonly string[];
  },
): PageArtifactAIChangeRecord {
  const lineage = createLineageEntry(hookKind, reason, confidence, affectedNodeIds, options);
  return { lineage, artifactId, documentId };
}

/** Re-export the platform tracker for use in product code. */
export { AIActionLineageTracker };
export type { AIActionLineage, AIHookKind, AIReviewState };

export type PageArtifactSource =
  | 'created-in-builder'
  | 'decompiled'
  | 'imported'
  | 'generated';

export type PageArtifactSyncStatus =
  | 'dirty'
  | 'saving'
  | 'synced'
  | 'error'
  | 'offline';

export interface PageArtifactDocument {
  readonly artifactId: string;
  readonly documentId: string;
  readonly serializedBuilderDocument: SerializedDocument;
  readonly source: PageArtifactSource;
  readonly roundTripFidelity?: RoundTripFidelity;
  readonly residualIslandIds?: readonly string[];
  readonly syncStatus: PageArtifactSyncStatus;
  readonly trustLevel: TrustLevel;
  readonly dataClassification: DataClassification;
  readonly createdBy: string;
  readonly updatedBy: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly aiChangeRecords?: readonly PageArtifactAIChangeRecord[];
  readonly validationSummary?: {
    readonly valid: boolean;
    readonly errorCount: number;
    readonly warningCount: number;
  };
}

export function createEmptyBuilderDocument(name: string, author: string): BuilderDocument {
  const now = new Date().toISOString();
  const contractMap = getContractMap();
  const componentContracts = Array.from(contractMap.values());

  return {
    id: createDocumentId(),
    version: '1',
    name,
    designSystem: {
      id: 'ghatana-ds-v1',
      name: 'Ghatana Design System',
      version: '1.0.0',
      tokenSetIds: [],
      componentContracts,
      themeId: 'default',
    },
    rootNodes: [],
    nodes: new Map(),
    metadata: {
      createdAt: now,
      updatedAt: now,
      author,
      dataClassification: 'INTERNAL',
      trustLevel: 'GENERATED_TRUSTED',
    },
  };
}

export function createPageArtifactDocument(args: {
  artifactId: string;
  document?: BuilderDocument;
  name: string;
  createdBy: string;
  source?: PageArtifactSource;
}): PageArtifactDocument {
  const now = new Date().toISOString();
  const document = args.document ?? createEmptyBuilderDocument(args.name, args.createdBy);

  return {
    artifactId: args.artifactId,
    documentId: document.id,
    serializedBuilderDocument: serializeDocument(document),
    source: args.source ?? 'created-in-builder',
    syncStatus: 'dirty',
    trustLevel: document.metadata.trustLevel ?? 'GENERATED_TRUSTED',
    dataClassification: document.metadata.dataClassification ?? 'INTERNAL',
    createdBy: args.createdBy,
    updatedBy: args.createdBy,
    createdAt: now,
    updatedAt: now,
  };
}

export function getBuilderDocument(pageDocument: PageArtifactDocument | undefined): BuilderDocument | undefined {
  if (!pageDocument) {
    return undefined;
  }

  return deserializeDocument(pageDocument.serializedBuilderDocument);
}

export function updatePageArtifactDocument(
  pageDocument: PageArtifactDocument,
  document: BuilderDocument,
  updatedBy: string,
  syncStatus: PageArtifactSyncStatus,
  validationSummary?: PageArtifactDocument['validationSummary'],
): PageArtifactDocument {
  const now = new Date().toISOString();

  return {
    ...pageDocument,
    documentId: document.id,
    serializedBuilderDocument: serializeDocument(document),
    syncStatus,
    trustLevel: document.metadata.trustLevel ?? pageDocument.trustLevel,
    dataClassification:
      document.metadata.dataClassification ?? pageDocument.dataClassification,
    updatedBy,
    updatedAt: now,
    validationSummary,
  };
}

export function appendAIChangeRecord(
  pageDocument: PageArtifactDocument,
  record: PageArtifactAIChangeRecord,
): PageArtifactDocument {
  return {
    ...pageDocument,
    aiChangeRecords: [...(pageDocument.aiChangeRecords ?? []), record],
    updatedAt: new Date().toISOString(),
  };
}

export function getSerializedNodeCount(
  pageDocument: PageArtifactDocument | undefined,
): number {
  if (!pageDocument) {
    return 0;
  }

  return Object.keys(pageDocument.serializedBuilderDocument.nodes).length;
}
