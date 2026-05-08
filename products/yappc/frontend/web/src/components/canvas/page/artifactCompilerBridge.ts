import {
  deserializeDocument,
  insertNode,
  type BuilderDocument,
  type NodeId,
  type RoundTripFidelity,
  type SerializedDocument,
} from '@ghatana/ui-builder';

import { isBuilderDocument } from './builder-document-adapter';
import {
  createEmptyBuilderDocument,
  createPageArtifactDocument,
  type PageArtifactDocument,
  type PageArtifactGraphSnapshot,
} from './pageArtifactDocument';

export interface ImportedSourceArtifactInput {
  readonly projectId: string;
  readonly componentName?: string;
  readonly source: string;
  readonly sourceType: 'tsx' | 'route' | 'storybook' | 'artifact' | 'zip';
  readonly importedAt: string;
  /**
   * Extracted component AST data from yappc-artifact-compiler.
   * When present, the bridge builds a populated BuilderDocument with
   * real canvas nodes instead of an empty document.
   */
  readonly extractedComponents?: ReadonlyArray<{
    readonly name: string;
    readonly isDefaultExport?: boolean;
    readonly jsxUsage: readonly string[];
    readonly props: ReadonlyArray<{ readonly name: string; readonly type: string; readonly required: boolean; readonly defaultValue?: unknown }>;
    readonly slots: ReadonlyArray<{ readonly name: string; readonly multiple: boolean; readonly required: boolean }>;
    readonly hooksUsed?: readonly string[];
    readonly accessibility?: unknown;
    readonly sourceLocation?: {
      readonly filePath: string;
      readonly startLine: number;
      readonly startColumn: number;
      readonly endLine: number;
      readonly endColumn: number;
    };
  }>;
}

type ImportedExtractedComponent = NonNullable<ImportedSourceArtifactInput['extractedComponents']>[number];

interface SemanticPageLike {
  readonly id?: string;
  readonly name?: string;
  readonly builderDocument?: unknown;
  readonly serializedBuilderDocument?: unknown;
  readonly residualIslands?: readonly { readonly id: string }[];
  readonly confidence?: number;
  readonly canRoundTrip?: boolean;
}

interface SemanticProductModelLike {
  readonly id?: string;
  readonly name?: string;
  readonly pages?: readonly SemanticPageLike[];
}

interface SemanticPageGraphSummary {
  readonly artifactId: string;
  readonly name: string;
  readonly index: number;
}

function asSerializedDocument(value: unknown): SerializedDocument | null {
  if (!value || typeof value !== 'object') {
    return null;
  }

  const record = value as Record<string, unknown>;
  if (
    typeof record.id !== 'string' ||
    typeof record.version !== 'string' ||
    typeof record.name !== 'string' ||
    typeof record.designSystem !== 'object' ||
    !Array.isArray(record.rootNodes) ||
    typeof record.nodes !== 'object' ||
    record.nodes === null
  ) {
    return null;
  }

  return record as unknown as SerializedDocument;
}

function toRoundTripFidelity(page: SemanticPageLike): RoundTripFidelity {
  return {
    canRoundTrip: page.canRoundTrip ?? true,
    confidence: typeof page.confidence === 'number' ? page.confidence : 0.9,
    lossPoints: [],
  };
}

function resolveBuilderDocument(page: SemanticPageLike, createdBy: string): BuilderDocument {
  if (isBuilderDocument(page.builderDocument)) {
    return page.builderDocument;
  }

  const serialized = asSerializedDocument(page.serializedBuilderDocument);
  if (serialized) {
    return deserializeDocument(serialized);
  }

  return createEmptyBuilderDocument(page.name ?? 'Generated Page', createdBy);
}

export function compileSemanticModelToPageArtifacts(
  model: SemanticProductModelLike,
  createdBy: string,
): readonly PageArtifactDocument[] {
  const pages = model.pages ?? [];
  if (pages.length === 0) {
    return [
      createPageArtifactDocument({
        artifactId: model.id ?? 'generated-page-artifact',
        name: model.name ?? 'Generated Page',
        createdBy,
        source: 'generated',
      }),
    ];
  }

  const productId = model.id ?? 'semantic-model';
  const productName = model.name ?? productId;
  const graphId = `${productId}:graph`;
  const importedAt = new Date().toISOString();
  const pageSummaries: readonly SemanticPageGraphSummary[] = pages.map((page, index) => ({
    artifactId: page.id ?? `${productId}-page-${index + 1}`,
    name: page.name ?? `Page ${index + 1}`,
    index,
  }));

  return pages.map((page, index) => {
    const pageSummary = pageSummaries[index];
    const artifactId = pageSummary?.artifactId ?? `${productId}-page-${index + 1}`;
    const document = resolveBuilderDocument(page, createdBy);
    const residualIslandIds = (page.residualIslands ?? []).map((island) => island.id);

    return {
      ...createPageArtifactDocument({
        artifactId,
        document,
        name: page.name ?? document.name,
        createdBy,
        source: 'decompiled',
      }),
      roundTripFidelity: toRoundTripFidelity(page),
      residualIslandIds,
      artifactGraph: buildSemanticModelGraphSnapshot({
        artifactId,
        graphId,
        projectId: productId,
        productId,
        productName,
        source: productId,
        importedAt,
        pageName: page.name ?? document.name,
        pageIndex: pageSummary?.index ?? index,
        pages: pageSummaries,
        residualIslandIds,
        createdBy,
        confidence: typeof page.confidence === 'number' ? page.confidence : 0.9,
      }),
    } satisfies PageArtifactDocument;
  });
}

export function importPageArtifactsFromCode(
  serializedSemanticModel: string,
  createdBy: string,
): readonly PageArtifactDocument[] {
  try {
    const parsed = JSON.parse(serializedSemanticModel) as SemanticProductModelLike;
    return compileSemanticModelToPageArtifacts(parsed, createdBy);
  } catch (error: unknown) {
    if (error instanceof SyntaxError) {
      throw new Error('Invalid JSON - could not parse semantic model.', { cause: error });
    }

    throw error;
  }
}

export function compileImportedSourceToPageArtifacts(
  imported: ImportedSourceArtifactInput,
  createdBy: string,
): readonly PageArtifactDocument[] {
  const normalizedName = (imported.componentName ?? inferNameFromSource(imported.source) ?? 'ImportedPage').trim();
  const artifactId = `${imported.projectId}-${slugifyForArtifactId(normalizedName)}`;

  // Build a populated BuilderDocument when extracted component data is available.
  // Each extracted component becomes the root node; its JSX usages (child elements)
  // become child nodes in the default 'children' slot, giving the canvas a real
  // starting graph rather than an empty document.
  const builderDocument = buildDocumentFromExtractedComponents(
    imported.extractedComponents ?? [],
    normalizedName,
    createdBy,
  );

  return [
    {
      ...createPageArtifactDocument({
        artifactId,
        document: builderDocument,
        name: normalizedName,
        createdBy,
        source: 'imported',
      }),
      validationSummary: {
        valid: true,
        errorCount: 0,
        warningCount: 0,
      },
      artifactGraph: buildImportedSourceGraphSnapshot({
        artifactId,
        projectId: imported.projectId,
        sourceType: imported.sourceType,
        source: imported.source,
        importedAt: imported.importedAt,
        createdBy,
        componentName: normalizedName,
        extractedComponents: imported.extractedComponents ?? [],
      }),
      updatedAt: imported.importedAt,
    },
  ];
}

function buildImportedSourceGraphSnapshot(params: {
  readonly artifactId: string;
  readonly projectId: string;
  readonly sourceType: ImportedSourceArtifactInput['sourceType'];
  readonly source: string;
  readonly importedAt: string;
  readonly createdBy: string;
  readonly componentName: string;
  readonly extractedComponents: readonly ImportedExtractedComponent[];
}): PageArtifactGraphSnapshot {
  const sourceNodeId = `${params.artifactId}:source`;
  const pageNodeId = `${params.artifactId}:page`;
  const componentNodes = params.extractedComponents.map((component) => ({
    id: `${params.artifactId}:component:${component.name}`,
    kind: 'component' as const,
    label: component.name,
    sourceLocation: component.sourceLocation,
    metadata: {
      defaultExport: component.isDefaultExport ?? false,
      propCount: component.props.length,
      slotCount: component.slots.length,
      hookCount: component.hooksUsed?.length ?? 0,
      jsxUsageCount: component.jsxUsage.length,
      hasAccessibilityMetadata: component.accessibility != null,
    },
  }));

  const uniqueReferences = new Set<string>();
  params.extractedComponents.forEach((component) => {
    component.jsxUsage.forEach((usage) => uniqueReferences.add(usage));
  });

  const referenceNodes = [...uniqueReferences]
    .filter((usage) => !params.extractedComponents.some((component) => component.name === usage))
    .map((usage) => ({
      id: `${params.artifactId}:component-ref:${usage}`,
      kind: 'component' as const,
      label: usage,
      metadata: {
        inferredFromJsxUsage: true,
      },
    }));

  const nodes: PageArtifactGraphSnapshot['nodes'] = [
    {
      id: sourceNodeId,
      kind: 'source',
      label: params.source,
      metadata: {
        sourceType: params.sourceType,
      },
    },
    {
      id: pageNodeId,
      kind: 'page',
      label: params.componentName,
    },
    ...componentNodes,
    ...referenceNodes,
  ];

  const edges: PageArtifactGraphSnapshot['edges'] = [
    {
      id: `${params.artifactId}:page-derived-from-source`,
      from: pageNodeId,
      to: sourceNodeId,
      kind: 'derived-from',
    },
    ...componentNodes.map((node) => ({
      id: `${params.artifactId}:page-contains:${node.label}`,
      from: pageNodeId,
      to: node.id,
      kind: 'contains' as const,
    })),
    ...referenceNodes.map((node) => ({
      id: `${params.artifactId}:page-references:${node.label}`,
      from: pageNodeId,
      to: node.id,
      kind: 'references' as const,
    })),
  ];

  const confidence = params.extractedComponents.length > 0
    ? Math.min(0.95, 0.7 + params.extractedComponents.length * 0.05)
    : 0.55;

  return {
    graphId: `${params.artifactId}:graph`,
    projectId: params.projectId,
    sourceType: params.sourceType,
    source: params.source,
    importedAt: params.importedAt,
    nodes,
    edges,
    provenance: {
      createdBy: params.createdBy,
      compiler: 'yappc-artifact-compiler',
      confidence,
      residualIslandIds: [],
    },
  };
}

function buildSemanticModelGraphSnapshot(params: {
  readonly artifactId: string;
  readonly graphId: string;
  readonly projectId: string;
  readonly productId: string;
  readonly productName: string;
  readonly source: string;
  readonly importedAt: string;
  readonly pageName: string;
  readonly pageIndex: number;
  readonly pages: readonly SemanticPageGraphSummary[];
  readonly residualIslandIds: readonly string[];
  readonly createdBy: string;
  readonly confidence: number;
}): PageArtifactGraphSnapshot {
  const productNodeId = `${params.productId}:product`;
  const pageNodeId = `${params.artifactId}:page`;
  const peerPageNodes = params.pages
    .filter((page) => page.artifactId !== params.artifactId)
    .map((page) => ({
      id: `${page.artifactId}:page`,
      kind: 'page' as const,
      label: page.name,
      metadata: {
        pageIndex: page.index,
        peerPage: true,
      },
    }));
  const residualNodes = params.residualIslandIds.map((residualId) => ({
    id: `${params.artifactId}:residual:${residualId}`,
    kind: 'residual' as const,
    label: residualId,
  }));

  return {
    graphId: params.graphId,
    projectId: params.projectId,
    sourceType: 'semantic-model',
    source: params.source,
    importedAt: params.importedAt,
    nodes: [
      {
        id: productNodeId,
        kind: 'product',
        label: params.productName,
        metadata: {
          pageCount: params.pages.length,
        },
      },
      {
        id: pageNodeId,
        kind: 'page',
        label: params.pageName,
        metadata: {
          pageIndex: params.pageIndex,
          currentArtifact: true,
        },
      },
      ...peerPageNodes,
      ...residualNodes,
    ],
    edges: [
      {
        id: `${params.artifactId}:page-part-of-product`,
        from: pageNodeId,
        to: productNodeId,
        kind: 'part-of',
      },
      ...peerPageNodes.map((node) => ({
        id: `${node.id}-part-of-product`,
        from: node.id,
        to: productNodeId,
        kind: 'part-of' as const,
      })),
      ...residualNodes.map((node) => ({
        id: `${params.artifactId}:page-residual:${node.label}`,
        from: node.id,
        to: pageNodeId,
        kind: 'residual-of' as const,
      })),
    ],
    provenance: {
      createdBy: params.createdBy,
      compiler: 'yappc-artifact-compiler',
      confidence: params.confidence,
      residualIslandIds: params.residualIslandIds,
    },
  };
}

/**
 * Build a BuilderDocument from extracted component AST data.
 *
 * Strategy:
 * - The first extracted component becomes the root canvas node.
 * - Each unique JSX usage within that component becomes a child node
 *   in the root's 'children' slot (flat layout — JSX nesting is not
 *   preserved because ExtractedComponent does not expose the tree).
 * - Default prop values from the extracted PropSchema are applied.
 */
function buildDocumentFromExtractedComponents(
  extractedComponents: ReadonlyArray<{
    readonly name: string;
    readonly jsxUsage: readonly string[];
    readonly props: ReadonlyArray<{ readonly name: string; readonly type: string; readonly required: boolean; readonly defaultValue?: unknown }>;
    readonly slots: ReadonlyArray<{ readonly name: string; readonly multiple: boolean; readonly required: boolean }>;
  }>,
  fallbackName: string,
  createdBy: string,
): BuilderDocument {
  if (extractedComponents.length === 0) {
    return createEmptyBuilderDocument(fallbackName, createdBy);
  }

  const rootExtracted = extractedComponents[0];
  if (!rootExtracted) {
    return createEmptyBuilderDocument(fallbackName, createdBy);
  }

  // Build default props from PropSchema default values
  const defaultProps: Record<string, unknown> = {};
  for (const prop of rootExtracted.props) {
    if (prop.defaultValue !== undefined) {
      defaultProps[prop.name] = prop.defaultValue;
    }
  }

  // Determine slot names from the extracted slot schema
  const slotNames = rootExtracted.slots.map((s) => s.name);
  const primarySlot = slotNames[0] ?? 'children';
  const emptySlots: Record<string, readonly NodeId[]> = {};
  for (const slotName of slotNames) {
    emptySlots[slotName] = [];
  }
  if (!emptySlots[primarySlot]) {
    emptySlots[primarySlot] = [];
  }

  // Insert the root node
  let document = createEmptyBuilderDocument(rootExtracted.name, createdBy);
  document = insertNode(
    document,
    {
      contractName: rootExtracted.name,
      props: defaultProps,
      slots: emptySlots,
      bindings: [],
      metadata: {
        name: rootExtracted.name,
        layout: {
          resizable: true,
          positionable: true,
        },
      },
    },
  );

  const rootNodeId = document.rootNodes[0];
  if (!rootNodeId) {
    return document;
  }

  // Insert each unique JSX usage as a child of the root node in the primary slot
  const seenJsxNames = new Set<string>();
  for (const jsxName of rootExtracted.jsxUsage) {
    if (seenJsxNames.has(jsxName)) {
      continue;
    }
    seenJsxNames.add(jsxName);

    document = insertNode(
      document,
      {
        contractName: jsxName,
        props: {},
        slots: {},
        bindings: [],
        metadata: {
          name: jsxName,
          layout: {
            resizable: true,
            positionable: true,
          },
        },
      },
      rootNodeId,
      primarySlot,
    );
  }

  return document;
}

function inferNameFromSource(source: string): string | null {
  const fileName = source.split('/').pop() ?? source;
  const name = fileName.replace(/\.[^.]+$/, '');
  return name.length > 0 ? name : null;
}

function slugifyForArtifactId(value: string): string {
  const normalized = value
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase();

  return normalized.length > 0 ? normalized : 'imported-page';
}
