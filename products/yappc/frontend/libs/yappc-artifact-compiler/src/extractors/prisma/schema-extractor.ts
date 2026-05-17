/**
 * @fileoverview Prisma Schema Extractor.
 *
 * Parses Prisma schema files to extract DataModel entities, relations,
 * indexes, constraints, and unsupported feature warnings.
 */

import type { ArtifactRecord } from '../../inventory/types';
import type { GraphNode, GraphEdge, GraphNodeKind } from '../../graph/types';
import type { DataModel, EntityField, EntityRelation, EntityIndex } from '../../model/types';
import type { ExtractionResult, ExtractionContext } from '../types';

export const EXTRACTOR_ID = 'prisma-schema';
export const EXTRACTOR_VERSION = '0.1.0';

// ============================================================================
// Prisma Schema Parsing
// ============================================================================

export interface ExtractedPrismaModel {
  readonly name: string;
  readonly fields: readonly EntityField[];
  readonly relations: readonly EntityRelation[];
  readonly indexes: readonly EntityIndex[];
  readonly unsupportedFeatures: ReadonlyArray<{
    readonly feature: string;
    readonly reason: string;
    readonly line: number;
  }>;
}

export function parsePrismaSchema(content: string, _filePath: string): ExtractedPrismaModel[] {
  const models: ExtractedPrismaModel[] = [];
  const lines = content.split('\n');

  let currentModel: {
    name: string;
    fields: EntityField[];
    relations: EntityRelation[];
    indexes: EntityIndex[];
    unsupportedFeatures: Array<{ feature: string; reason: string; line: number }>;
  } | null = null;

  let inModel = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]!.trim();
    const lineNum = i + 1;

    // Skip comments and empty lines
    if (line.startsWith('//') || line.startsWith('/*') || line === '' || line.startsWith('*')) {
      continue;
    }

    // Model start
    if (line.startsWith('model ')) {
      const match = line.match(/model\s+(\w+)/);
      if (match && match[1] !== undefined) {
        currentModel = {
          name: match[1],
          fields: [],
          relations: [],
          indexes: [],
          unsupportedFeatures: [],
        };
        inModel = true;
      }
      continue;
    }

    // Model end
    if (inModel && line === '}') {
      if (currentModel) {
        models.push(currentModel);
      }
      currentModel = null;
      inModel = false;
      continue;
    }

    if (!inModel || !currentModel) continue;

    // Field definition
    const fieldMatch = line.match(new RegExp('^(\\w+)\\s+(\\w+(\\?)?(\\[\\])?)(\\s+@id)?(\\s+@unique)?(\\s+@default\\(.+\\))?'));
    if (fieldMatch && fieldMatch[1] !== undefined && fieldMatch[2] !== undefined) {
      const name = fieldMatch[1];
      const typeStr = fieldMatch[2];
      const isOptional = !!fieldMatch[3];
      const isArray = !!fieldMatch[4];
      const isPrimaryKey = !!fieldMatch[5];
      const isUnique = !!fieldMatch[6];
      const defaultMatch = line.match(new RegExp('@default\\((.+)\\)'));
      const defaultValue = defaultMatch && defaultMatch[1] !== undefined ? parseDefaultValue(defaultMatch[1]) : undefined;

      // Check if this is a relation field (type is another model name)
      const isRelation = typeStr && !isScalarType(typeStr.replace(/\?$/, '').replace(/\[\]$/, ''));

      if (isRelation) {
        const targetModel = typeStr.replace(/\?$/, '').replace(/\[\]$/, '');
        const relationAttrs = line.match(new RegExp('@relation\\([^]*\\)'));
        let kind: EntityRelation['kind'] = 'many-to-one';
        if (isArray) kind = 'one-to-many';
        if (isOptional) kind = 'many-to-one';

        // Try to infer from relation attribute
        if (relationAttrs) {
          const relContent = relationAttrs[0];
          if (relContent.includes('fields:') && relContent.includes('references:')) {
            kind = 'many-to-one';
          }
        }

        currentModel.relations.push({
          targetEntityId: targetModel, // Will be resolved to actual UUIDs later
          kind,
          fieldName: name,
          optional: isOptional,
          onDelete: undefined,
        });
      } else {
        currentModel.fields.push({
          name,
          type: isArray ? `${typeStr.replace(/\[\]$/, '')}[]` : typeStr.replace(/\?$/, ''),
          required: !isOptional,
          unique: isUnique,
          defaultValue,
          isPrimaryKey,
          isForeignKey: false,
          description: undefined,
        });
      }
      continue;
    }

    // Index/unique index
    const indexMatch = line.match(/@@index\(\[([^\]]+)\]\)/);
    if (indexMatch && indexMatch[1] !== undefined) {
      const fields = indexMatch[1].split(',').map(f => f.trim());
      currentModel.indexes.push({
        name: `idx_${currentModel.name}_${fields.join('_')}`,
        fields,
        unique: false,
        type: 'btree',
      });
      continue;
    }

    const uniqueIndexMatch = line.match(/@@unique\(\[([^\]]+)\]\)/);
    if (uniqueIndexMatch && uniqueIndexMatch[1] !== undefined) {
      const fields = uniqueIndexMatch[1].split(',').map(f => f.trim());
      currentModel.indexes.push({
        name: `unique_${currentModel.name}_${fields.join('_')}`,
        fields,
        unique: true,
        type: 'btree',
      });
      continue;
    }

    // Unsupported features
    if (line.includes('@db.') || line.includes('@@fulltext') || line.includes('@@map') ||
        line.includes('@@schema') || line.includes('@@ignore') || line.includes('NativeType')) {
      currentModel.unsupportedFeatures.push({
        feature: line.split(' ')[0] ?? line.substring(0, 30),
        reason: `Prisma-specific attribute: ${line.substring(0, 100)}`,
        line: lineNum,
      });
    }
  }

  return models;
}

function isScalarType(type: string): boolean {
  const scalars = ['String', 'Int', 'BigInt', 'Float', 'Decimal', 'DateTime',
    'Json', 'Bytes', 'Boolean', 'UUID', 'Cuid', 'String?', 'Int?', 'Boolean?'];
  return scalars.includes(type) || scalars.includes(type.replace(/\?$/, ''));
}

function parseDefaultValue(value: string): unknown {
  const trimmed = value.trim();
  if (trimmed.startsWith('"') && trimmed.endsWith('"')) return trimmed.slice(1, -1);
  if (trimmed.startsWith('\'') && trimmed.endsWith('\'')) return trimmed.slice(1, -1);
  if (trimmed === 'true') return true;
  if (trimmed === 'false') return false;
  if (trimmed === 'null') return null;
  if (trimmed === 'autoincrement()') return { prismaDefault: 'autoincrement' };
  if (trimmed === 'uuid()') return { prismaDefault: 'uuid' };
  if (trimmed === 'cuid()') return { prismaDefault: 'cuid' };
  if (trimmed === 'now()') return { prismaDefault: 'now' };
  if (trimmed.startsWith('dbgenerated(')) return { prismaDefault: 'dbgenerated', value: trimmed };
  if (!isNaN(Number(trimmed))) return Number(trimmed);
  return { raw: trimmed };
}

// ============================================================================
// Artifact Extractor Implementation
// ============================================================================

export async function extractPrismaSchemaArtifact(
  record: ArtifactRecord,
  context: ExtractionContext,
): Promise<ExtractionResult> {
  const startTime = Date.now();

  try {
    const content = await context.readFile(record.relativePath);
    const models = parsePrismaSchema(content, record.relativePath);

    const nodes: GraphNode[] = [];
    const edges: GraphEdge[] = [];
    const modelElements: DataModel[] = [];
    const warnings: Array<ExtractionResult['warnings'][number]> = [];
    const errors: ExtractionResult['errors'] = [];
    const now = new Date().toISOString();

    // Build model name -> ID map for relation resolution
    const modelNameToId = new Map<string, string>();
    for (const model of models) {
      modelNameToId.set(model.name, crypto.randomUUID());
    }

    for (const model of models) {
      const modelId = modelNameToId.get(model.name)!;

      nodes.push({
        id: modelId,
        type: 'entity' as GraphNodeKind, // P0: Canonical field name 'type', not legacy 'kind'
        label: model.name,
        sourceLocation: {
          filePath: record.relativePath,
          startLine: 1,
          startColumn: 1,
          endLine: content.split('\n').length,
          endColumn: 1,
        },
        extractorId: EXTRACTOR_ID,
        extractorVersion: EXTRACTOR_VERSION,
        confidence: 0.94,
        provenance: 'exact',
        privacySecurityFlags: [],
        residualFragmentIds: [],
        metadata: {
          fieldCount: model.fields.length,
          relationCount: model.relations.length,
          indexCount: model.indexes.length,
        },
      });

      // Create edges for relations
      for (const relation of model.relations) {
        const targetId = modelNameToId.get(relation.targetEntityId);
        if (targetId) {
          edges.push({
            id: crypto.randomUUID(),
            sourceId: modelId,
            targetId,
            relationshipType: 'DEPENDS_ON', // P0: Canonical field name 'relationshipType', not legacy 'kind'
            confidence: 0.9,
            bidirectional: relation.kind === 'many-to-many',
            metadata: {
              relationKind: relation.kind,
              fieldName: relation.fieldName,
            },
          });
        }
      }

      // Resolve relation target IDs
      const resolvedRelations = model.relations.map(r => ({
        ...r,
        targetEntityId: modelNameToId.get(r.targetEntityId) ?? r.targetEntityId,
      }));

      modelElements.push({
        id: modelId,
        kind: 'data-entity',
        name: model.name,
        description: `Prisma model ${model.name}`,
        confidence: 0.94,
        provenance: {
          extractorId: EXTRACTOR_ID,
          extractorVersion: EXTRACTOR_VERSION,
          sourcePaths: [record.relativePath],
          kind: 'exact',
          extractedAt: now,
        },
        securityFlags: [],
        privacyFlags: [],
        tags: [],
        tableName: model.name,
        fields: [...model.fields],
        relations: [...resolvedRelations],
        indexes: [...model.indexes],
        constraints: [],
        unsupportedFeatures: model.unsupportedFeatures.map(f => ({
          feature: f.feature,
          reason: f.reason,
          originalSql: undefined,
        })),
        migrationLineage: [],
      });

      if (model.unsupportedFeatures.length > 0) {
        warnings.push({
          message: `Model ${model.name} has ${model.unsupportedFeatures.length} unsupported features that could not be fully modeled`,
          category: 'unsupported-feature',
        });
      }
    }

    return {
      extractorId: EXTRACTOR_ID,
      extractorVersion: EXTRACTOR_VERSION,
      artifact: record,
      nodes,
      edges,
      modelElements,
      residualIslands: [],
      errors,
      warnings,
      durationMs: Date.now() - startTime,
    };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return {
      extractorId: EXTRACTOR_ID,
      extractorVersion: EXTRACTOR_VERSION,
      artifact: record,
      nodes: [],
      edges: [],
      modelElements: [],
      residualIslands: [],
      errors: [{ message, recoverable: false }],
      warnings: [],
      durationMs: Date.now() - startTime,
    };
  }
}
