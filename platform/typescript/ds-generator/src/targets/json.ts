/**
 * @fileoverview JSON token target for DesignSystemDocument.
 *
 * Emits the document's resolved tokens as a W3C Design Tokens Community Group
 * (DTCG) inspired JSON structure, enriched with schema version, semantic
 * alias resolution, and component variant definitions.
 *
 * @doc.type module
 * @doc.purpose DesignSystemDocument → JSON token emission
 * @doc.layer ds-generator
 * @doc.pattern Adapter
 */

import type { DesignSystemDocument } from '../model/design-system-document.js';
import {
  buildTokenGraph,
  flattenTokenRecord,
  graphToRecord,
} from '../tokens/token-graph.js';

export interface JsonTargetOptions {
  /** JSON indentation. Default: 2. */
  indent?: number;
  /** Whether to include metadata block. Default: true. */
  includeMetadata?: boolean;
  /** Whether to include component variant definitions. Default: true. */
  includeVariants?: boolean;
}

export interface JsonTokenOutput {
  /** Serialized JSON string. */
  readonly json: string;
  /** Parsed object (same data, ready for programmatic use). */
  readonly data: JsonTokenOutputData;
}

export interface JsonTokenOutputData {
  readonly schemaVersion: string;
  readonly documentId: string;
  readonly name: string;
  readonly basePresetId: string;
  readonly tokens: Record<string, unknown>;
  readonly semanticAliases?: Record<string, string>;
  readonly componentVariants?: unknown[];
  readonly metadata?: Record<string, unknown>;
}

/**
 * Emit a `DesignSystemDocument` as a structured JSON token file.
 */
export function emitJson(
  doc: DesignSystemDocument,
  options: JsonTargetOptions = {},
): JsonTokenOutput {
  const { indent = 2, includeMetadata = true, includeVariants = true } = options;

  // Resolve semantic aliases
  const flatBase = flattenTokenRecord(doc.resolvedTokens);
  const graphResult = buildTokenGraph(doc.semanticAliases, flatBase);
  const aliasRecord = graphToRecord(graphResult);

  const data: JsonTokenOutputData = {
    schemaVersion: doc.schemaVersion,
    documentId: doc.documentId,
    name: doc.name,
    basePresetId: doc.basePresetId,
    tokens: doc.resolvedTokens,
    ...(Object.keys(aliasRecord).length > 0 && { semanticAliases: aliasRecord }),
    ...(includeVariants && doc.componentVariants.length > 0 && { componentVariants: doc.componentVariants }),
    ...(includeMetadata && {
      metadata: {
        generatedAt: doc.generatedAt,
        generator: 'ghatana-ds-generator',
        ...doc.metadata,
      },
    }),
  };

  return {
    json: JSON.stringify(data, null, indent),
    data,
  };
}
