import {
  deserializeDocument,
  type BuilderDocument,
  type RoundTripFidelity,
  type SerializedDocument,
} from '@ghatana/ui-builder';

import {
  createEmptyBuilderDocument,
  createPageArtifactDocument,
  type PageArtifactDocument,
} from './pageArtifactDocument';
import { isBuilderDocument } from './builder-document-adapter';

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

  return pages.map((page, index) => {
    const artifactId = page.id ?? `${model.id ?? 'artifact'}-page-${index + 1}`;
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
    } satisfies PageArtifactDocument;
  });
}

export function importPageArtifactsFromCode(
  serializedSemanticModel: string,
  createdBy: string,
): readonly PageArtifactDocument[] {
  const parsed = JSON.parse(serializedSemanticModel) as SemanticProductModelLike;
  return compileSemanticModelToPageArtifacts(parsed, createdBy);
}
