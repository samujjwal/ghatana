/**
 * @fileoverview State Management Extractor.
 *
 * Extracts state store patterns from Redux, Zustand, Jotai, Context, and XState
 * configurations in TypeScript/JavaScript files.
 */

import * as ts from 'typescript';
import type { ArtifactRecord } from '../../inventory/types';
import type { GraphNode, GraphNodeKind } from '../../graph/types';
import type { StateStoreModel } from '../../model/types';
import type { ExtractionResult, ExtractionContext } from '../types';

export const EXTRACTOR_ID = 'state-store';
export const EXTRACTOR_VERSION = '0.1.0';

// ============================================================================
// Extracted State Store Data
// ============================================================================

export interface ExtractedStateStore {
  readonly name: string;
  readonly storeType: StateStoreModel['storeType'];
  readonly stateKeys: ReadonlyArray<string>;
  readonly actionTypes: ReadonlyArray<{ name: string; payloadType: string | undefined }>;
  readonly reducers: ReadonlyArray<{ name: string; handledActions: ReadonlyArray<string> }>;
  readonly selectors: ReadonlyArray<{ name: string; inputPaths: ReadonlyArray<string>; outputType: string | undefined }>;
  readonly connectedComponents: ReadonlyArray<string>;
  readonly sourceLocation: {
    readonly filePath: string;
    readonly startLine: number;
    readonly startColumn: number;
    readonly endLine: number;
    readonly endColumn: number;
  };
}

// ============================================================================
// State Store Detection and Extraction
// ============================================================================

export function extractStateStoresFromSource(content: string, filePath: string): ExtractedStateStore[] {
  const sourceFile = ts.createSourceFile(
    filePath,
    content,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX,
  );

  const stores: ExtractedStateStore[] = [];

  // Detect Zustand: create(...)
  const zustandPattern = /create(<[^>]*>)?\s*\(/;
  if (zustandPattern.test(content)) {
    const match = content.match(/(?:export\s+const|const)\s+(\w+)\s+=\s*create/);
    const storeName = match && match[1] !== undefined ? match[1] : 'zustandStore';

    const stateKeys: string[] = [];
    const actions: Array<{ name: string; payloadType: string | undefined }> = [];

    ts.forEachChild(sourceFile, (node) => {
      if (ts.isVariableStatement(node)) {
        for (const decl of node.declarationList.declarations) {
          if (ts.isIdentifier(decl.name)) {
            const init = decl.initializer;
            if (init && ts.isCallExpression(init) && ts.isIdentifier(init.expression) && init.expression.text === 'create') {
              // Extract state from create(set => ({ ... }))
              if (init.arguments.length > 0) {
                const arg = init.arguments[0]!;
                if (ts.isArrowFunction(arg) || ts.isFunctionExpression(arg)) {
                  const body = arg.body;
                  if (body && ts.isObjectLiteralExpression(body)) {
                    for (const prop of body.properties) {
                      if (ts.isPropertyAssignment(prop) && ts.isIdentifier(prop.name)) {
                        const key = prop.name.text;
                        if (ts.isArrowFunction(prop.initializer) || ts.isFunctionExpression(prop.initializer)) {
                          actions.push({ name: key, payloadType: undefined });
                        } else {
                          stateKeys.push(key);
                        }
                      }
                      if (ts.isShorthandPropertyAssignment(prop) && ts.isIdentifier(prop.name)) {
                        stateKeys.push(prop.name.text);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    });

    if (stateKeys.length > 0 || actions.length > 0) {
      stores.push({
        name: storeName,
        storeType: 'zustand',
        stateKeys,
        actionTypes: actions,
        reducers: [],
        selectors: [],
        connectedComponents: [],
        sourceLocation: {
          filePath,
          startLine: 1,
          startColumn: 1,
          endLine: sourceFile.getLineAndCharacterOfPosition(sourceFile.getEnd()).line + 1,
          endColumn: 1,
        },
      });
    }
  }

  // Detect Redux Toolkit: createSlice(...)
  const reduxPattern = /createSlice\s*\(/;
  if (reduxPattern.test(content)) {
    const sliceMatch = content.match(/(?:export\s+const|const)\s+(\w+)\s+=\s*createSlice/);
    const sliceName = sliceMatch && sliceMatch[1] !== undefined ? sliceMatch[1] : 'slice';

    const stateKeys: string[] = [];
    const actions: Array<{ name: string; payloadType: string | undefined }> = [];
    const reducers: Array<{ name: string; handledActions: readonly string[] }> = [];

    ts.forEachChild(sourceFile, (node) => {
      if (ts.isVariableStatement(node)) {
        for (const decl of node.declarationList.declarations) {
          if (ts.isIdentifier(decl.name)) {
            const init = decl.initializer;
            if (init && ts.isCallExpression(init) && ts.isIdentifier(init.expression) && init.expression.text === 'createSlice') {
              const configArg = init.arguments[0];
              if (configArg && ts.isObjectLiteralExpression(configArg)) {
                for (const prop of configArg.properties) {
                  if (ts.isPropertyAssignment(prop) && ts.isIdentifier(prop.name)) {
                    const key = prop.name.text;
                    if (key === 'name' && ts.isStringLiteral(prop.initializer)) {
                      // Slice name
                    }
                    if (key === 'initialState' && ts.isObjectLiteralExpression(prop.initializer)) {
                      for (const stateProp of prop.initializer.properties) {
                        if (ts.isPropertyAssignment(stateProp) && ts.isIdentifier(stateProp.name)) {
                          stateKeys.push(stateProp.name.text);
                        }
                        if (ts.isShorthandPropertyAssignment(stateProp) && ts.isIdentifier(stateProp.name)) {
                          stateKeys.push(stateProp.name.text);
                        }
                      }
                    }
                    if (key === 'reducers' && ts.isObjectLiteralExpression(prop.initializer)) {
                      const reducerNames: string[] = [];
                      for (const reducerProp of prop.initializer.properties) {
                        if (ts.isPropertyAssignment(reducerProp) && ts.isIdentifier(reducerProp.name)) {
                          reducerNames.push(reducerProp.name.text);
                          actions.push({ name: reducerProp.name.text, payloadType: undefined });
                        }
                        if (ts.isShorthandPropertyAssignment(reducerProp) && ts.isIdentifier(reducerProp.name)) {
                          reducerNames.push(reducerProp.name.text);
                        }
                      }
                      reducers.push({ name: sliceName, handledActions: reducerNames });
                    }
                  }
                }
              }
            }
          }
        }
      }
    });

    if (stateKeys.length > 0 || actions.length > 0) {
      stores.push({
        name: sliceName,
        storeType: 'redux',
        stateKeys,
        actionTypes: actions,
        reducers,
        selectors: [],
        connectedComponents: [],
        sourceLocation: {
          filePath,
          startLine: 1,
          startColumn: 1,
          endLine: sourceFile.getLineAndCharacterOfPosition(sourceFile.getEnd()).line + 1,
          endColumn: 1,
        },
      });
    }
  }

  // Detect React Context: createContext(...)
  const contextPattern = /createContext\s*\(/;
  if (contextPattern.test(content)) {
    const contextMatch = content.match(/(?:export\s+const|const)\s+(\w+)\s+=\s*createContext/);
    const contextName = contextMatch && contextMatch[1] !== undefined ? contextMatch[1] : 'context';

    stores.push({
      name: contextName,
      storeType: 'context',
      stateKeys: [],
      actionTypes: [],
      reducers: [],
      selectors: [],
      connectedComponents: [],
      sourceLocation: {
        filePath,
        startLine: 1,
        startColumn: 1,
        endLine: sourceFile.getLineAndCharacterOfPosition(sourceFile.getEnd()).line + 1,
        endColumn: 1,
      },
    });
  }

  // Detect Jotai: atom(...)
  const jotaiPattern = /atom\s*\(/;
  if (jotaiPattern.test(content) && content.includes('jotai')) {
    const atoms: string[] = [];

    ts.forEachChild(sourceFile, (node) => {
      if (ts.isVariableStatement(node)) {
        for (const decl of node.declarationList.declarations) {
          if (ts.isIdentifier(decl.name)) {
            const init = decl.initializer;
            if (init && ts.isCallExpression(init) && ts.isIdentifier(init.expression) && init.expression.text === 'atom') {
              atoms.push(decl.name.text);
            }
          }
        }
      }
    });

    if (atoms.length > 0) {
      stores.push({
        name: 'jotaiAtoms',
        storeType: 'jotai',
        stateKeys: atoms,
        actionTypes: [],
        reducers: [],
        selectors: [],
        connectedComponents: [],
        sourceLocation: {
          filePath,
          startLine: 1,
          startColumn: 1,
          endLine: sourceFile.getLineAndCharacterOfPosition(sourceFile.getEnd()).line + 1,
          endColumn: 1,
        },
      });
    }
  }

  return stores;
}

// ============================================================================
// Artifact Extractor Implementation
// ============================================================================

export async function extractStateStoreArtifact(
  record: ArtifactRecord,
  context: ExtractionContext,
): Promise<ExtractionResult> {
  const startTime = Date.now();

  try {
    const content = await context.readFile(record.relativePath);
    const stores = extractStateStoresFromSource(content, record.relativePath);

    const nodes: GraphNode[] = [];
    const modelElements: StateStoreModel[] = [];
    const warnings: Array<ExtractionResult['warnings'][number]> = [];
    const errors: ExtractionResult['errors'] = [];
    const now = new Date().toISOString();

    for (const store of stores) {
      const storeId = crypto.randomUUID();

      nodes.push({
        id: storeId,
        kind: 'state-store' as GraphNodeKind,
        label: store.name,
        sourceLocation: store.sourceLocation,
        extractorId: EXTRACTOR_ID,
        extractorVersion: EXTRACTOR_VERSION,
        confidence: 0.82,
        provenance: 'inferred',
        privacySecurityFlags: [],
        residualFragmentIds: [],
        metadata: {
          storeType: store.storeType,
          stateKeyCount: store.stateKeys.length,
          actionCount: store.actionTypes.length,
        },
      });

      modelElements.push({
        id: storeId,
        kind: 'state-store',
        name: store.name,
        description: `${store.storeType} state store`,
        confidence: 0.82,
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
        storeType: store.storeType,
        stateTree: Object.fromEntries(store.stateKeys.map(k => [k, undefined])),
        actionTypes: store.actionTypes.map(a => ({
          name: a.name,
          payloadType: a.payloadType,
          description: undefined,
        })),
        reducers: store.reducers.map(r => ({
          name: r.name,
          handledActions: [...r.handledActions],
        })),
        selectors: store.selectors.map(s => ({
          name: s.name,
          inputPaths: [...s.inputPaths],
          outputType: s.outputType,
        })),
        connectedComponentIds: [],
      });
    }

    if (stores.length === 0) {
      warnings.push({
        message: `No recognizable state management patterns found in ${record.relativePath}`,
        category: 'pattern-ambiguous',
      });
    }

    return {
      extractorId: EXTRACTOR_ID,
      extractorVersion: EXTRACTOR_VERSION,
      artifact: record,
      nodes,
      edges: [],
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
