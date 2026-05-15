/**
 * @fileoverview Storybook CSF (Component Story Format) Extractor.
 *
 * Parses Storybook CSF files using the TypeScript Compiler API to extract:
 * - Component metadata from default export
 * - Story definitions and their args/decorators
 * - Variant matrices and visual states
 * - Canonical prop presets from story args
 *
 * Operates entirely on local source files — no external Storybook service calls.
 */

import * as ts from 'typescript';
import type { ArtifactRecord } from '../../inventory/types';
import type { GraphNode, GraphEdge, GraphNodeKind } from '../../graph/types';
import type { ComponentModel, ComponentVariant } from '../../model/types';
import type { ExtractionResult, ExtractionContext } from '../types';

export const EXTRACTOR_ID = 'storybook-csf';
export const EXTRACTOR_VERSION = '0.1.0';

// ============================================================================
// Extracted Story Data
// ============================================================================

export interface ExtractedStory {
  readonly name: string;
  readonly exportName: string;
  readonly args: Record<string, unknown>;
  readonly decorators: readonly string[];
  readonly parameters: Record<string, unknown>;
  readonly sourceLocation: {
    readonly filePath: string;
    readonly startLine: number;
    readonly startColumn: number;
    readonly endLine: number;
    readonly endColumn: number;
  };
}

export interface ExtractedMeta {
  readonly title: string | undefined;
  readonly componentName: string | undefined;
  readonly componentImport: string | undefined;
  readonly tags: readonly string[];
  readonly parameters: Record<string, unknown>;
  readonly sourceLocation: {
    readonly filePath: string;
    readonly startLine: number;
    readonly startColumn: number;
    readonly endLine: number;
    readonly endColumn: number;
  };
}

export interface ExtractedCsfData {
  readonly meta: ExtractedMeta;
  readonly stories: readonly ExtractedStory[];
  readonly componentFilePath: string | undefined;
}

// ============================================================================
// CSF Parsing
// ============================================================================

export function parseCsfSource(content: string, filePath: string): ExtractedCsfData | null {
  const sourceFile = ts.createSourceFile(
    filePath,
    content,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX,
  );

  let meta: ExtractedMeta | undefined;
  const stories: ExtractedStory[] = [];

  ts.forEachChild(sourceFile, (node) => {
    // Default export: export default { title: '...', component: Button, ... }
    if (ts.isExportAssignment(node) && !node.isExportEquals) {
      const metaObj = extractObjectLiteral(node.expression, sourceFile);
      if (metaObj) {
        const title = typeof metaObj['title'] === 'string' ? metaObj['title'] : undefined;
        const componentVal = metaObj['component'];
        let componentName: string | undefined;
        let componentImport: string | undefined;

        if (typeof componentVal === 'string') {
          componentName = componentVal;
        } else if (componentVal && typeof componentVal === 'object' && 'identifier' in componentVal) {
          componentName = String(componentVal['identifier']);
          componentImport = String(componentVal['identifier']);
        }

        const tags: string[] = [];
        if (Array.isArray(metaObj['tags'])) {
          tags.push(...metaObj['tags'].filter((t): t is string => typeof t === 'string'));
        }

        meta = {
          title,
          componentName,
          componentImport,
          tags,
          parameters: typeof metaObj['parameters'] === 'object' && metaObj['parameters'] !== null
            ? metaObj['parameters'] as Record<string, unknown>
            : {},
          sourceLocation: getSourceLocation(node, filePath),
        };
      }
    }

    // Named story exports: export const Primary = { args: { ... } }
    if (ts.isVariableStatement(node)) {
      const isExport = node.modifiers?.some(m => m.kind === ts.SyntaxKind.ExportKeyword);
      if (isExport) {
        for (const decl of node.declarationList.declarations) {
          if (ts.isIdentifier(decl.name) && decl.initializer) {
            const storyName = decl.name.text;
            // Skip 'default' and meta exports
            if (storyName === 'default') continue;

            const storyObj = extractObjectLiteral(decl.initializer, sourceFile);
            if (storyObj) {
              const args = typeof storyObj['args'] === 'object' && storyObj['args'] !== null
                ? storyObj['args'] as Record<string, unknown>
                : {};
              const parameters = typeof storyObj['parameters'] === 'object' && storyObj['parameters'] !== null
                ? storyObj['parameters'] as Record<string, unknown>
                : {};

              const decorators: string[] = [];
              if (Array.isArray(storyObj['decorators'])) {
                for (const d of storyObj['decorators']) {
                  if (typeof d === 'string') decorators.push(d);
                  if (d && typeof d === 'object' && 'function' in d) {
                    decorators.push(String(d['function']));
                  }
                }
              }

              stories.push({
                name: storyName,
                exportName: storyName,
                args,
                decorators,
                parameters,
                sourceLocation: getSourceLocation(decl, filePath),
              });
            } else if (ts.isArrowFunction(decl.initializer) || ts.isFunctionExpression(decl.initializer)) {
              // CSF 3 function stories: export const Primary = () => <Button ... />
              stories.push({
                name: storyName,
                exportName: storyName,
                args: {},
                decorators: [],
                parameters: {},
                sourceLocation: getSourceLocation(decl, filePath),
              });
            }
          }
        }
      }
    }
  });

  if (!meta && stories.length === 0) {
    return null;
  }

  // Try to resolve component file path from import
  let componentFilePath: string | undefined;
  if (meta?.componentImport) {
    ts.forEachChild(sourceFile, (node) => {
      if (ts.isImportDeclaration(node) && node.importClause && node.moduleSpecifier && ts.isStringLiteral(node.moduleSpecifier)) {
        const bindings = node.importClause.namedBindings;
        if (bindings && ts.isNamedImports(bindings)) {
          for (const elem of bindings.elements) {
            if (elem.name.text === meta!.componentImport) {
              componentFilePath = node.moduleSpecifier.text;
            }
          }
        }
        if (node.importClause.name && node.importClause.name.text === meta!.componentImport) {
          componentFilePath = node.moduleSpecifier.text;
        }
      }
    });
  }

  return {
    meta: meta ?? {
      title: undefined,
      componentName: undefined,
      componentImport: undefined,
      tags: [],
      parameters: {},
      sourceLocation: { filePath, startLine: 1, startColumn: 1, endLine: 1, endColumn: 1 },
    },
    stories,
    componentFilePath,
  };
}

// ============================================================================
// Object Literal Extraction
// ============================================================================

function extractObjectLiteral(node: ts.Expression, sourceFile: ts.SourceFile): Record<string, unknown> | null {
  if (!ts.isObjectLiteralExpression(node)) {
    return null;
  }

  const result: Record<string, unknown> = {};

  for (const prop of node.properties) {
    if (ts.isPropertyAssignment(prop) && ts.isIdentifier(prop.name)) {
      const key = prop.name.text;
      result[key] = extractExpressionValue(prop.initializer, sourceFile);
    }
    if (ts.isShorthandPropertyAssignment(prop) && ts.isIdentifier(prop.name)) {
      result[prop.name.text] = prop.name.text;
    }
  }

  return result;
}

function extractExpressionValue(node: ts.Expression, sourceFile: ts.SourceFile): unknown {
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
    return node.text;
  }
  if (ts.isNumericLiteral(node)) {
    return Number(node.text);
  }
  if (node.kind === ts.SyntaxKind.TrueKeyword) return true;
  if (node.kind === ts.SyntaxKind.FalseKeyword) return false;
  if (node.kind === ts.SyntaxKind.NullKeyword) return null;
  if (ts.isArrayLiteralExpression(node)) {
    return node.elements.map(e => extractExpressionValue(e, sourceFile));
  }
  if (ts.isObjectLiteralExpression(node)) {
    return extractObjectLiteral(node, sourceFile);
  }
  if (ts.isIdentifier(node)) {
    return { identifier: node.text };
  }
  if (ts.isArrowFunction(node) || ts.isFunctionExpression(node)) {
    return { function: node.getText(sourceFile) };
  }
  return node.getText(sourceFile);
}

// ============================================================================
// Source Location Helper
// ============================================================================

function getSourceLocation(node: ts.Node, filePath: string) {
  const sourceFile = node.getSourceFile();
  const start = sourceFile.getLineAndCharacterOfPosition(node.getStart());
  const end = sourceFile.getLineAndCharacterOfPosition(node.getEnd());

  return {
    filePath,
    startLine: start.line + 1,
    startColumn: start.character + 1,
    endLine: end.line + 1,
    endColumn: end.character + 1,
  };
}

// ============================================================================
// Artifact Extractor Implementation
// ============================================================================

export async function extractStorybookCsf(
  record: ArtifactRecord,
  context: ExtractionContext,
): Promise<ExtractionResult> {
  const startTime = Date.now();

  try {
    const content = await context.readFile(record.relativePath);
    const extracted = parseCsfSource(content, record.relativePath);

    if (!extracted) {
      return {
        extractorId: EXTRACTOR_ID,
        extractorVersion: EXTRACTOR_VERSION,
        artifact: record,
        nodes: [],
        edges: [],
        modelElements: [],
        residualIslands: [],
        errors: [{ message: 'File does not appear to be a valid CSF story file', recoverable: true }],
        warnings: [],
        durationMs: Date.now() - startTime,
      };
    }

    const nodes: GraphNode[] = [];
    const edges: GraphEdge[] = [];
    const modelElements: ComponentModel[] = [];
    const warnings: Array<ExtractionResult['warnings'][number]> = [];
    const errors: ExtractionResult['errors'] = [];
    const now = new Date().toISOString();

    const storyNodeIds: string[] = [];

    // Create story graph nodes
    for (const story of extracted.stories) {
      const storyId = crypto.randomUUID();
      storyNodeIds.push(storyId);

      nodes.push({
        id: storyId,
        kind: 'story' as GraphNodeKind,
        label: story.name,
        sourceLocation: story.sourceLocation,
        extractorId: EXTRACTOR_ID,
        extractorVersion: EXTRACTOR_VERSION,
        confidence: 0.95,
        provenance: 'exact',
        privacySecurityFlags: [],
        residualFragmentIds: [],
        metadata: {
          args: story.args,
          decorators: story.decorators,
          parameters: story.parameters,
        },
      });
    }

    // Build component model from stories if component name is known
    const componentName = extracted.meta.componentName;
    if (componentName) {
      const componentId = crypto.randomUUID();

      // Build variants from story args
      const variants: ComponentVariant[] = extracted.stories.map(story => ({
        name: story.name,
        propOverrides: story.args,
        description: story.parameters['docs'] && typeof story.parameters['docs'] === 'object'
          ? String((story.parameters['docs'] as Record<string, unknown>)['description'] ?? '')
          : undefined,
      }));

      nodes.push({
        id: componentId,
        kind: 'component' as GraphNodeKind,
        label: componentName,
        sourceLocation: extracted.meta.sourceLocation,
        extractorId: EXTRACTOR_ID,
        extractorVersion: EXTRACTOR_VERSION,
        confidence: 0.88,
        provenance: 'inferred',
        privacySecurityFlags: [],
        residualFragmentIds: [],
        metadata: {
          storyCount: extracted.stories.length,
          title: extracted.meta.title,
          tags: extracted.meta.tags,
        },
      });

      // Link stories to component
      for (const storyId of storyNodeIds) {
        edges.push({
          id: crypto.randomUUID(),
          sourceId: storyId,
          targetId: componentId,
          kind: 'story-for',
          confidence: 0.95,
          bidirectional: false,
          metadata: {},
        });
      }

      // Model element for the component with inferred props from story args
      const allArgKeys = new Set<string>();
      for (const story of extracted.stories) {
        for (const key of Object.keys(story.args)) {
          allArgKeys.add(key);
        }
      }

      modelElements.push({
        id: componentId,
        kind: 'component',
        name: componentName,
        description: extracted.meta.title,
        confidence: 0.88,
        provenance: {
          extractorId: EXTRACTOR_ID,
          extractorVersion: EXTRACTOR_VERSION,
          sourcePaths: [record.relativePath],
          kind: 'inferred',
          extractedAt: now,
        },
        securityFlags: [],
        privacyFlags: [],
        tags: [],
        contractName: componentName,
        props: Array.from(allArgKeys).map(key => ({
          name: key,
          type: 'unknown',
          required: false,
          defaultValue: undefined,
          description: undefined,
          examples: extracted.stories.map(s => s.args[key]).filter(v => v !== undefined),
        })),
        slots: [],
        events: [],
        variants,
        stateConnections: [],
        dataDependencies: [],
        styleDependencies: [],
        accessibility: undefined,
        storyIds: storyNodeIds,
        builderCanvasHints: {},
      });
    } else {
      warnings.push({
        message: 'Could not determine component name from CSF meta export; story-to-component linking skipped',
        category: 'partial-extraction',
      });
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
