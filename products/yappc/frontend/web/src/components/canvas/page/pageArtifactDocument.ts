import {
  createDocumentId,
  deserializeDocument,
  serializeDocument,
  type BuilderDocument,
  type RoundTripFidelity,
  type SerializedDocument,
} from '@ghatana/ui-builder';
type DataClassification = NonNullable<BuilderDocument['metadata']['dataClassification']>;
type TrustLevel = NonNullable<BuilderDocument['metadata']['trustLevel']>;

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
  readonly validationSummary?: {
    readonly valid: boolean;
    readonly errorCount: number;
    readonly warningCount: number;
  };
}

export function createEmptyBuilderDocument(name: string, author: string): BuilderDocument {
  const now = new Date().toISOString();

  return {
    id: createDocumentId(),
    version: '1',
    name,
    designSystem: {
      id: 'ghatana-ds-v1',
      name: 'Ghatana Design System',
      version: '1.0.0',
      tokenSetIds: [],
      componentContracts: [],
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

export function getSerializedNodeCount(
  pageDocument: PageArtifactDocument | undefined,
): number {
  if (!pageDocument) {
    return 0;
  }

  return Object.keys(pageDocument.serializedBuilderDocument.nodes).length;
}
