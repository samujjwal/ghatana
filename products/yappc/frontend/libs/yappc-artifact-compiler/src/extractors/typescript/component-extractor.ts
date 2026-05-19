/**
 * @fileoverview TypeScript/TSX Component Extractor.
 *
 * Uses the TypeScript Compiler API (not regex) to extract component contracts,
 * props, slots, events, variants, and accessibility metadata from TSX source files.
 *
 * This extractor replaces the heuristic regex-based TSX import in ui-builder
 * with a proper AST-backed, type-aware extraction pipeline.
 */

import * as ts from 'typescript';
import type {
  ArtifactRecord,
} from '../../inventory/types';
import {
  buildDeterministicNodeId,
} from '../../graph/types';
import type {
  GraphNode,
  GraphEdge,
  GraphNodeKind,
  UnresolvedGraphEdge,
  SnapshotRef,
} from '../../graph/types';
import type {
  ComponentModel,
  PropSchema,
  SlotSchema,
  EventSchema,
  ComponentVariant,
  AccessibilityMetadata,
} from '../../model/types';
import type {
  ResidualIsland,
} from '../../residual/types';
import type {
  ExtractionResult,
  ExtractionContext,
} from '../types';

// ============================================================================
// Extractor Identity
// ============================================================================

export const EXTRACTOR_ID = 'typescript-component';
export const EXTRACTOR_VERSION = '0.1.0';

// ============================================================================
// Component Extraction
// ============================================================================

export interface ExtractedComponent {
  readonly name: string;
  readonly isDefaultExport: boolean;
  readonly props: readonly PropSchema[];
  readonly slots: readonly SlotSchema[];
  readonly events: readonly EventSchema[];
  readonly variants: readonly ComponentVariant[];
  readonly accessibility: AccessibilityMetadata | undefined;
  readonly sourceLocation: {
    readonly filePath: string;
    readonly startLine: number;
    readonly startColumn: number;
    readonly endLine: number;
    readonly endColumn: number;
  };
  readonly jsxUsage: readonly string[];
  readonly hooksUsed: readonly string[];
}

// ============================================================================
// TypeScript Program Setup
// ============================================================================

function createCompilerHost(
  fileContent: string,
  fileName: string,
): ts.CompilerHost {
  const defaultHost = ts.createCompilerHost({});

  return {
    ...defaultHost,
    getSourceFile: (requestedFileName, languageVersion, onError, shouldCreateNewSourceFile) => {
      if (requestedFileName === fileName) {
        return ts.createSourceFile(fileName, fileContent, languageVersion, true, ts.ScriptKind.TSX);
      }
      return defaultHost.getSourceFile(requestedFileName, languageVersion, onError, shouldCreateNewSourceFile);
    },
    readFile: (requestedFileName) => {
      if (requestedFileName === fileName) return fileContent;
      return defaultHost.readFile(requestedFileName);
    },
    fileExists: (requestedFileName) => {
      if (requestedFileName === fileName) return true;
      return defaultHost.fileExists(requestedFileName);
    },
  };
}

// ============================================================================
// AST Traversal Helpers
// ============================================================================

function findExportedComponents(sourceFile: ts.SourceFile): ts.Node[] {
  const components: ts.Node[] = [];

  function visit(node: ts.Node) {
    // Function declaration: export function Button(props: ButtonProps) { ... }
    if (ts.isFunctionDeclaration(node) && node.name) {
      const hasExport = node.modifiers?.some(m => m.kind === ts.SyntaxKind.ExportKeyword);
      const isDefault = node.modifiers?.some(m => m.kind === ts.SyntaxKind.DefaultKeyword);
      if (hasExport || isDefault) {
        if (returnsJSX(node)) {
          components.push(node);
        }
      }
    }

    // Arrow function: export const Button = (props) => { ... }
    if (ts.isVariableStatement(node)) {
      const hasExport = node.modifiers?.some(m => m.kind === ts.SyntaxKind.ExportKeyword);
      if (hasExport) {
        for (const decl of node.declarationList.declarations) {
          if (ts.isIdentifier(decl.name)) {
            if (decl.initializer && (ts.isArrowFunction(decl.initializer) || ts.isFunctionExpression(decl.initializer))) {
              if (returnsJSX(decl.initializer)) {
                components.push(decl);
              }
            }
          }
        }
      }
    }

    // Class component: export class Button extends React.Component { ... }
    if (ts.isClassDeclaration(node) && node.name) {
      const hasExport = node.modifiers?.some(m => m.kind === ts.SyntaxKind.ExportKeyword);
      if (hasExport) {
        const hasRender = node.members.some(m => ts.isMethodDeclaration(m) && m.name && ts.isIdentifier(m.name) && m.name.text === 'render');
        const extendsReact = node.heritageClauses?.some(clause =>
          clause.types.some(t => ts.isExpressionWithTypeArguments(t) &&
            ts.isPropertyAccessExpression(t.expression) &&
            t.expression.expression.getText(sourceFile).includes('React') &&
            t.expression.name.text === 'Component'
          )
        );
        if (hasRender || extendsReact) {
          components.push(node);
        }
      }
    }

    ts.forEachChild(node, visit);
  }

  visit(sourceFile);
  return components;
}

function returnsJSX(node: ts.FunctionDeclaration | ts.ArrowFunction | ts.FunctionExpression): boolean {
  let hasJsx = false;

  function visit(n: ts.Node) {
    if (ts.isJsxElement(n) || ts.isJsxSelfClosingElement(n) || ts.isJsxFragment(n)) {
      hasJsx = true;
      return;
    }
    ts.forEachChild(n, visit);
  }

  visit(node);
  return hasJsx;
}

// ============================================================================
// Props Extraction
// ============================================================================

function extractPropsFromType(
  typeNode: ts.TypeNode | undefined,
  sourceFile: ts.SourceFile,
  typeChecker: ts.TypeChecker | undefined,
): PropSchema[] {
  if (!typeNode) return [];

  const props: PropSchema[] = [];

  // Direct type literal: { foo: string; bar: number }
  if (ts.isTypeLiteralNode(typeNode)) {
    for (const member of typeNode.members) {
      if (ts.isPropertySignature(member) && ts.isIdentifier(member.name)) {
        const name = member.name.text;
        const type = typeChecker
          ? typeChecker.typeToString(typeChecker.getTypeAtLocation(member))
          : member.type?.getText(sourceFile) ?? 'unknown';
        const required = !member.questionToken;
        const description = extractJsDocDescription(member);

        props.push({
          name,
          type,
          required,
          defaultValue: undefined,
          description,
          examples: [],
        });
      }
    }
  }

  // Type reference: ButtonProps - try to resolve
  if (ts.isTypeReferenceNode(typeNode) && typeChecker) {
    const type = typeChecker.getTypeAtLocation(typeNode);
    const properties = type.getProperties();
    for (const prop of properties) {
      const declaration = prop.valueDeclaration;
      if (declaration) {
        const typeString = typeChecker.typeToString(
          typeChecker.getTypeOfSymbolAtLocation(prop, declaration)
        );
        props.push({
          name: prop.name,
          type: typeString,
          required: !prop.declarations?.some(d => (d as ts.ParameterDeclaration).questionToken !== undefined),
          defaultValue: undefined,
          description: undefined,
          examples: [],
        });
      }
    }
  }

  // Intersection type: Props & { extra: string }
  if (ts.isIntersectionTypeNode(typeNode)) {
    for (const type of typeNode.types) {
      props.push(...extractPropsFromType(type, sourceFile, typeChecker));
    }
  }

  return props;
}

function extractPropsFromDestructuring(
  params: ts.NodeArray<ts.ParameterDeclaration>,
  sourceFile: ts.SourceFile,
): PropSchema[] {
  const props: PropSchema[] = [];

  for (const param of params) {
    // Destructured parameter: ({ foo, bar }: Props)
    if (ts.isObjectBindingPattern(param.name)) {
      for (const element of param.name.elements) {
        if (ts.isBindingElement(element) && ts.isIdentifier(element.name)) {
          const name = element.name.text;
          const hasDefault = !!element.initializer;
          props.push({
            name,
            type: 'unknown',
            required: !element.dotDotDotToken && !hasDefault,
            defaultValue: element.initializer ? extractLiteralValue(element.initializer) : undefined,
            description: undefined,
            examples: [],
          });
        }
      }
    }

    // Single props parameter: (props: ButtonProps)
    if (ts.isIdentifier(param.name) && param.type) {
      // We can't resolve the type without a type checker, but we can note it
      props.push({
        name: param.name.text,
        type: param.type.getText(sourceFile),
        required: !param.questionToken,
        defaultValue: param.initializer ? extractLiteralValue(param.initializer) : undefined,
        description: undefined,
        examples: [],
      });
    }
  }

  return props;
}

function extractDefaultValuesFromDestructuring(
  param: ts.ParameterDeclaration,
): Map<string, unknown> {
  const defaults = new Map<string, unknown>();

  if (!ts.isObjectBindingPattern(param.name)) {
    return defaults;
  }

  for (const element of param.name.elements) {
    if (ts.isBindingElement(element) && ts.isIdentifier(element.name) && element.initializer) {
      defaults.set(element.name.text, extractLiteralValue(element.initializer));
    }
  }

  return defaults;
}

function applyPropDefaults(
  props: PropSchema[],
  defaults: ReadonlyMap<string, unknown>,
): PropSchema[] {
  if (defaults.size === 0) return props;

  return props.map(prop => (
    defaults.has(prop.name)
      ? { ...prop, defaultValue: defaults.get(prop.name), required: false }
      : prop
  ));
}

function extractLiteralValue(node: ts.Expression): unknown {
  if (ts.isStringLiteral(node)) return node.text;
  if (ts.isNumericLiteral(node)) return Number(node.text);
  if (node.kind === ts.SyntaxKind.TrueKeyword) return true;
  if (node.kind === ts.SyntaxKind.FalseKeyword) return false;
  if (node.kind === ts.SyntaxKind.NullKeyword) return null;
  if (ts.isArrayLiteralExpression(node)) return node.elements.map(extractLiteralValue);
  if (ts.isObjectLiteralExpression(node)) {
    const obj: Record<string, unknown> = {};
    for (const prop of node.properties) {
      if (ts.isPropertyAssignment(prop) && ts.isIdentifier(prop.name)) {
        obj[prop.name.text] = extractLiteralValue(prop.initializer);
      }
    }
    return obj;
  }
  return undefined;
}

function extractClassProps(
  node: ts.ClassDeclaration,
  sourceFile: ts.SourceFile,
  typeChecker: ts.TypeChecker | undefined,
): PropSchema[] {
  for (const clause of node.heritageClauses ?? []) {
    for (const type of clause.types) {
      const propsType = type.typeArguments?.[0];
      if (propsType) {
        return extractPropsFromType(propsType, sourceFile, typeChecker);
      }
    }
  }

  return [];
}

// ============================================================================
// JSDoc Description Extraction
// ============================================================================

function extractJsDocDescription(node: ts.Node): string | undefined {
  const jsDoc = (node as unknown as { jsDoc?: ts.JSDoc[] }).jsDoc;
  if (jsDoc && jsDoc.length > 0) {
    const firstDoc = jsDoc[0]!;
    const comment = firstDoc.comment;
    if (comment === undefined) return undefined;
    if (typeof comment === 'string') return comment;
    if (Array.isArray(comment)) {
      return comment.map((c: ts.Node) => ('text' in c && typeof (c as unknown as { text: string }).text === 'string' ? (c as unknown as { text: string }).text : '')).join(' ').trim();
    }
  }
  return undefined;
}

// ============================================================================
// Slots Extraction (from JSX children patterns)
// ============================================================================

function extractSlots(node: ts.FunctionDeclaration | ts.ArrowFunction | ts.FunctionExpression | ts.ClassDeclaration | ts.MethodDeclaration): SlotSchema[] {
  const slots: SlotSchema[] = [];
  const seen = new Set<string>();

  function visit(n: ts.Node) {
    // Look for {props.children} or {children} usage
    if (ts.isJsxExpression(n) && n.expression) {
      const expr = n.expression;
      if (ts.isPropertyAccessExpression(expr) && expr.name.text === 'children') {
        const base = expr.expression.getText(expr.getSourceFile());
        const slotName = base === 'props' ? 'children' : base;
        if (!seen.has(slotName)) {
          seen.add(slotName);
          slots.push({ name: slotName, multiple: false, required: false });
        }
      }
      if (ts.isIdentifier(expr) && expr.text === 'children') {
        if (!seen.has('children')) {
          seen.add('children');
          slots.push({ name: 'children', multiple: false, required: false });
        }
      }
    }

    // Look for named slot props like <div>{props.header}</div>
    if (ts.isPropertyAccessExpression(n) && ts.isIdentifier(n.expression)) {
      const name = n.name.text;
      if (['header', 'footer', 'sidebar', 'content', 'body', 'title', 'actions'].includes(name)) {
        if (!seen.has(name)) {
          seen.add(name);
          slots.push({ name, multiple: false, required: false });
        }
      }
    }

    ts.forEachChild(n, visit);
  }

  visit(node);
  return slots;
}

// ============================================================================
// Events Extraction (from on* handler props)
// ============================================================================

function extractEvents(props: PropSchema[]): EventSchema[] {
  return props
    .filter(p => p.name.startsWith('on') && p.type.includes('=>'))
    .map(p => ({
      name: p.name,
      payloadType: p.type,
      description: p.description,
    }));
}

// ============================================================================
// Hooks Extraction
// ============================================================================

function extractHooksUsed(node: ts.Node): string[] {
  const hooks = new Set<string>();

  function visit(n: ts.Node) {
    if (ts.isCallExpression(n) && ts.isIdentifier(n.expression)) {
      const name = n.expression.text;
      if (name.startsWith('use')) {
        hooks.add(name);
      }
    }
    ts.forEachChild(n, visit);
  }

  visit(node);
  return Array.from(hooks);
}

// ============================================================================
// JSX Usage Extraction (which components this component renders)
// ============================================================================

function extractJsxUsage(node: ts.Node): string[] {
  const usage = new Set<string>();

  function visit(n: ts.Node) {
    if (ts.isJsxOpeningLikeElement(n) && ts.isIdentifier(n.tagName)) {
      const name = n.tagName.text;
      if (name.length > 0 && name.charAt(0) === name.charAt(0).toUpperCase()) {
        usage.add(name);
      }
    }
    ts.forEachChild(n, visit);
  }

  visit(node);
  return Array.from(usage);
}

// ============================================================================
// Accessibility Extraction
// ============================================================================

function extractAccessibility(node: ts.Node, _sourceFile: ts.SourceFile): AccessibilityMetadata | undefined {
  let ariaRole: string | undefined;
  let keyboardNavigation = false;
  let focusable = false;
  let screenReaderLabel: string | undefined;

  function visit(n: ts.Node) {
    // JSX attributes
    if (ts.isJsxAttribute(n)) {
      const attrName = ts.isIdentifier(n.name) ? n.name.text : '';
      if (attrName === 'role' && n.initializer && ts.isStringLiteral(n.initializer)) {
        ariaRole = n.initializer.text;
      }
      if (attrName === 'tabIndex') {
        focusable = true;
      }
      if (attrName === 'aria-label' && n.initializer) {
        if (ts.isStringLiteral(n.initializer)) {
          screenReaderLabel = n.initializer.text;
        } else if (
          ts.isJsxExpression(n.initializer) &&
          n.initializer.expression
        ) {
          screenReaderLabel = n.initializer.expression.getText(_sourceFile);
        }
      }
      if (attrName === 'onKeyDown' || attrName === 'onKeyUp' || attrName === 'onKeyPress') {
        keyboardNavigation = true;
      }
    }

    ts.forEachChild(n, visit);
  }

  visit(node);

  if (ariaRole || keyboardNavigation || focusable || screenReaderLabel) {
    return { ariaRole, keyboardNavigation, focusable, screenReaderLabel };
  }

  return undefined;
}

// ============================================================================
// Component Name Extraction
// ============================================================================

function getComponentName(node: ts.Node, _sourceFile: ts.SourceFile): string {
  if (ts.isFunctionDeclaration(node) && node.name) {
    return node.name.text;
  }
  if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name)) {
    return node.name.text;
  }
  if (ts.isClassDeclaration(node) && node.name) {
    return node.name.text;
  }
  return 'UnknownComponent';
}

// ============================================================================
// Source Location
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
// Main Extraction
// ============================================================================

export function extractComponentsFromSource(
  content: string,
  filePath: string,
): ExtractedComponent[] {
  const parsedSourceFile = ts.createSourceFile(
    filePath,
    content,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX,
  );

  const host = createCompilerHost(content, filePath);
  const program = ts.createProgram([filePath], {
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ESNext,
    jsx: ts.JsxEmit.ReactJSX,
    noEmit: true,
    skipLibCheck: true,
  }, host);

  const typeChecker = program.getTypeChecker();
  const sourceFile = program.getSourceFile(filePath) ?? parsedSourceFile;
  const components = findExportedComponents(sourceFile);

  return components.map(node => {
    const name = getComponentName(node, sourceFile);
    const location = getSourceLocation(node, filePath);

    // Determine if default export
    let isDefaultExport = false;
    if (ts.isFunctionDeclaration(node)) {
      isDefaultExport = node.modifiers?.some(m => m.kind === ts.SyntaxKind.DefaultKeyword) ?? false;
    } else if (ts.isVariableDeclaration(node)) {
      const parent = node.parent.parent; // VariableStatement
      if (ts.isVariableStatement(parent)) {
        isDefaultExport = parent.modifiers?.some(m => m.kind === ts.SyntaxKind.DefaultKeyword) ?? false;
      }
    } else if (ts.isClassDeclaration(node)) {
      isDefaultExport = node.modifiers?.some(m => m.kind === ts.SyntaxKind.DefaultKeyword) ?? false;
    }

    // Extract props
    let props: PropSchema[] = [];
    if (ts.isFunctionDeclaration(node) || ts.isArrowFunction(node) || ts.isFunctionExpression(node)) {
      const params = ts.isFunctionDeclaration(node)
        ? node.parameters
        : (node as ts.ArrowFunction | ts.FunctionExpression).parameters;

      if (params.length > 0 && params[0] !== undefined) {
        const firstParam = params[0];
        if (firstParam.type) {
          props = applyPropDefaults(
            extractPropsFromType(firstParam.type, sourceFile, typeChecker),
            extractDefaultValuesFromDestructuring(firstParam),
          );
        } else if (ts.isObjectBindingPattern(firstParam.name)) {
          props = extractPropsFromDestructuring(params, sourceFile);
        }
      }
    } else if (ts.isVariableDeclaration(node) && node.initializer) {
      if (ts.isArrowFunction(node.initializer) || ts.isFunctionExpression(node.initializer)) {
        const params = node.initializer.parameters;
        if (params.length > 0 && params[0] !== undefined) {
          const firstParam = params[0];
          if (firstParam.type) {
            props = applyPropDefaults(
              extractPropsFromType(firstParam.type, sourceFile, typeChecker),
              extractDefaultValuesFromDestructuring(firstParam),
            );
          } else if (ts.isObjectBindingPattern(firstParam.name)) {
            props = extractPropsFromDestructuring(params, sourceFile);
          }
        }
      }
    } else if (ts.isClassDeclaration(node)) {
      props = extractClassProps(node, sourceFile, typeChecker);
    }

    // Extract slots
    let slots: SlotSchema[] = [];
    let jsxUsage: string[] = [];
    let hooksUsed: string[] = [];
    let accessibility: AccessibilityMetadata | undefined;

    if (ts.isFunctionDeclaration(node) || ts.isArrowFunction(node) || ts.isFunctionExpression(node)) {
      slots = extractSlots(node);
      jsxUsage = extractJsxUsage(node);
      hooksUsed = extractHooksUsed(node);
      accessibility = extractAccessibility(node, sourceFile);
    } else if (ts.isClassDeclaration(node)) {
      const renderMethod = node.members.find(m =>
        ts.isMethodDeclaration(m) && m.name && ts.isIdentifier(m.name) && m.name.text === 'render'
      );
      if (renderMethod && ts.isMethodDeclaration(renderMethod)) {
        slots = extractSlots(renderMethod);
        jsxUsage = extractJsxUsage(renderMethod);
        hooksUsed = extractHooksUsed(renderMethod);
        accessibility = extractAccessibility(renderMethod, sourceFile);
      }
    } else if (ts.isVariableDeclaration(node) && node.initializer) {
      if (ts.isArrowFunction(node.initializer) || ts.isFunctionExpression(node.initializer)) {
        slots = extractSlots(node.initializer);
        jsxUsage = extractJsxUsage(node.initializer);
        hooksUsed = extractHooksUsed(node.initializer);
        accessibility = extractAccessibility(node.initializer, sourceFile);
      }
    }

    // Extract events from props
    const events = extractEvents(props);

    return {
      name,
      isDefaultExport,
      props,
      slots,
      events,
      variants: [], // Variants are better extracted from stories or prop defaults
      accessibility,
      sourceLocation: location,
      jsxUsage,
      hooksUsed,
    };
  });
}

// ============================================================================
// Artifact Extractor Implementation
// ============================================================================

export async function extractComponentArtifact(
  record: ArtifactRecord,
  context: ExtractionContext,
): Promise<ExtractionResult> {
  const startTime = Date.now();

  try {
    const content = await context.readFile(record.relativePath);
    const extracted = extractComponentsFromSource(content, record.relativePath);

    const nodes: GraphNode[] = [];
    const edges: GraphEdge[] = [];
    const unresolvedEdges: UnresolvedGraphEdge[] = [];
    const modelElements: ComponentModel[] = [];
    const residualIslands: ResidualIsland[] = [];
    const errors: ExtractionResult['errors'] = [];
    const warnings: Array<ExtractionResult['warnings'][number]> = [];

    const snapshotRef: SnapshotRef | undefined = context.snapshotRef;
    const now = new Date().toISOString();

    for (const comp of extracted) {
      // Phase 1: build deterministic node ID from snapshot ref + relative path + symbol
      const componentId = buildDeterministicNodeId(
        snapshotRef,
        record.relativePath,
        'component',
        comp.name,
      );

      const symbolRef = `${record.relativePath}#component:${comp.name}`;

      // Graph node with stable sourceRef and symbolRef
      nodes.push({
        id: componentId,
        type: 'component' as GraphNodeKind, // P0: Canonical field name 'type', not legacy 'kind'
        label: comp.name,
        sourceRef: snapshotRef ? componentId : undefined,
        symbolRef,
        sourceLocation: comp.sourceLocation,
        extractorId: EXTRACTOR_ID,
        extractorVersion: EXTRACTOR_VERSION,
        confidence: 0.92,
        provenance: 'exact',
        privacySecurityFlags: [],
        residualFragmentIds: [],
        metadata: {
          isDefaultExport: comp.isDefaultExport,
          propsCount: comp.props.length,
          slotsCount: comp.slots.length,
          eventsCount: comp.events.length,
          hooksUsed: comp.hooksUsed,
          props: comp.props,
          tags: [],
        },
      });

      // Phase 1: emit UnresolvedGraphEdge for JSX usage — NEVER fake targetId
      for (const usage of comp.jsxUsage) {
        unresolvedEdges.push({
          sourceId: componentId,
          targetRef: usage,          // component name string — resolved in Phase 2
          targetKindHint: 'component',
          relationshipType: 'renders', // P0: Canonical lowercase value, not 'RENDERS'
          sourceLocation: comp.sourceLocation,
          confidence: 0.85,
          metadata: { targetComponentName: usage, extractedFrom: record.relativePath },
        });
      }

      // Model element
      modelElements.push({
        id: componentId,
        kind: 'component',
        name: comp.name,
        description: `React component ${comp.name}`,
        confidence: 0.92,
        provenance: {
          extractorId: EXTRACTOR_ID,
          extractorVersion: EXTRACTOR_VERSION,
          sourcePaths: [record.relativePath],
          kind: 'exact',
          extractedAt: now,
        },
        graphNodeIds: [componentId],
        sourceRefs: snapshotRef ? [componentId] : [],
        residualIslandIds: [],
        securityFlags: [],
        privacyFlags: [],
        tags: [],
        contractName: comp.name,
        props: comp.props.map(p => ({
          name: p.name,
          type: p.type || 'unknown',
          required: p.required,
          examples: [],
          defaultValue: p.defaultValue,
          description: undefined,
        })),
        slots: [],
        events: [],
        variants: [],
        stateConnections: [],
        dataDependencies: [],
        styleDependencies: [],
        accessibility: {
          keyboardNavigation: false,
          focusable: false,
        },
        storyIds: [],
        builderCanvasHints: {},
      });
    }

    if (content.includes('useEffect') && content.includes('setTimeout')) {
      warnings.push({
        message: `File ${record.relativePath} contains useEffect with setTimeout - side effects may not be fully modeled`,
        category: 'partial-extraction',
      });
    }

    return {
      extractorId: EXTRACTOR_ID,
      extractorVersion: EXTRACTOR_VERSION,
      artifact: record,
      nodes,
      edges,
      unresolvedEdges,
      modelElements,
      residualIslands,
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
      unresolvedEdges: [],
      modelElements: [],
      residualIslands: [],
      errors: [{ message, recoverable: false }],
      warnings: [],
      durationMs: Date.now() - startTime,
    };
  }
}
