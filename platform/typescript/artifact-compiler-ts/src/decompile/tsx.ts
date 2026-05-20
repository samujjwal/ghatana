/**
 * @fileoverview TSX decompiler — TSX source → LogicalArtifactModel.
 *
 * Uses the TypeScript compiler API to parse TSX/TS files and extract
 * the logical structure into ArtifactNode and ArtifactEdge records.
 *
 * @doc.type module
 * @doc.purpose TSX/TS source decompiler for the artifact pipeline
 * @doc.layer platform
 * @doc.pattern Adapter
 */

import * as ts from "typescript";
import {
  type ArtifactNode,
  type ArtifactEdge,
  type ArtifactKind,
  type ArtifactDependencyKind,
  createLogicalArtifactModel,
  type LogicalArtifactModel,
} from "@ghatana/artifact-contracts";
import type { FidelityReport, LossPoint } from "@ghatana/artifact-contracts";
import { computeFidelityReport } from "@ghatana/artifact-contracts";

// ============================================================================
// PROTECTED REGION PARSING
// ============================================================================

/**
 * A parsed protected region from source code.
 */
export interface ProtectedRegion {
  /** The region ID from the marker. */
  readonly regionId: string;
  /** The owner kind annotation (user-authored, generated, or protected). */
  readonly ownerKind: string;
  /** 1-based start line of the region content (after begin marker). */
  readonly startLine: number;
  /** 1-based end line of the region content (before end marker). */
  readonly endLine: number;
  /** The content lines between the markers (excluding markers themselves). */
  readonly contentLines: readonly string[];
}

/**
 * Parse @ghatana-region markers from a source file.
 *
 * Extracts regions marked with:
 * ```ts
 * // @ghatana-region: begin <regionId> owner=<ownerKind>
 * // ... content ...
 * // @ghatana-region: end <regionId>
 * ```
 *
 * @param sourceFile - The TypeScript source file to scan.
 * @returns Array of parsed protected regions in source order.
 */
export function parseProtectedRegions(sourceFile: ts.SourceFile): ProtectedRegion[] {
  const regions: ProtectedRegion[] = [];
  const lines = sourceFile.text.split('\n');
  const pendingBegins = new Map<string, { startLine: number; ownerKind: string }>();

  const REGION_BEGIN_REGEX = /^\s*\/\/\s*@ghatana-region:\s+begin\s+(\S+)\s+owner=(\S+)/;
  const REGION_END_REGEX = /^\s*\/\/\s*@ghatana-region:\s+end\s+(\S+)/;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]!;
    const beginMatch = line.match(REGION_BEGIN_REGEX);
    const endMatch = line.match(REGION_END_REGEX);

    if (beginMatch) {
      const regionId = beginMatch[1]!;
      const ownerKind = beginMatch[2]!;
      pendingBegins.set(regionId, { startLine: i + 1, ownerKind });
    } else if (endMatch) {
      const regionId = endMatch[1]!;
      const pending = pendingBegins.get(regionId);
      if (pending) {
        const contentLines = lines.slice(pending.startLine, i);
        regions.push({
          regionId,
          ownerKind: pending.ownerKind,
          startLine: pending.startLine,
          endLine: i,
          contentLines,
        });
        pendingBegins.delete(regionId);
      }
    }
  }

  // Any unmatched begin markers are ignored (malformed regions)
  return regions;
}

// ============================================================================
// INPUT CONTRACT
// ============================================================================

/**
 * A single source file to decompile.
 */
export interface DecompileSourceFile {
  /** Relative path within the workspace (used as node ID seed). */
  readonly relativePath: string;
  /** Raw source content. */
  readonly content: string;
}

/**
 * Input to the TSX decompiler.
 */
export interface DecompileTsxInput {
  /** Human-readable label for the resulting model (e.g. repo name). */
  readonly label: string;
  /** Unique model ID (UUID). */
  readonly modelId: string;
  /** Source files to decompile. */
  readonly files: readonly DecompileSourceFile[];
  /**
   * Set of known design-system component names. Used to classify component
   * usage accurately and produce correct `usesDesignSystem` metadata.
   */
  readonly designSystemComponentNames?: ReadonlySet<string>;
}

// ============================================================================
// OUTPUT CONTRACT
// ============================================================================

/**
 * Result of a TSX decompile run.
 */
export interface DecompileTsxResult {
  readonly model: LogicalArtifactModel;
  readonly fidelityReport: FidelityReport;
  /**
   * Per-file fidelity reports keyed by relativePath.
   */
  readonly perFileFidelity: ReadonlyMap<string, FidelityReport>;
}

// ============================================================================
// INTERNAL HELPERS
// ============================================================================

/**
 * Infer ArtifactKind from a file's path and top-level exported symbol names.
 */
function inferKindFromPath(relativePath: string): ArtifactKind {
  const lower = relativePath.toLowerCase();
  if (lower.includes(".test.") || lower.includes(".spec.")) return "test";
  if (lower.includes(".stories.")) return "story";
  if (lower.endsWith(".css") || lower.endsWith(".scss")) return "style";
  if (lower.endsWith(".json")) return "schema";
  if (lower.endsWith(".config.ts") || lower.endsWith(".config.js")) return "config";
  if (/\/(pages?|routes?|views?)\//i.test(lower)) return "page";
  if (/\/layouts?\//i.test(lower)) return "layout";
  if (/\/hooks?\//i.test(lower)) return "hook";
  if (/\/store\//i.test(lower) || lower.includes(".atom.")) return "store";
  if (/\/services?\//i.test(lower) || lower.includes(".service.")) return "service";
  if (/\/(utils?|helpers?|lib)\//i.test(lower)) return "utility";
  if (/\/types?\//i.test(lower) || lower.endsWith(".types.ts") || lower.endsWith(".d.ts")) return "type";
  // Default: assume component for TSX files
  if (lower.endsWith(".tsx")) return "component";
  return "unknown";
}

/**
 * Extract the display name from a file path (file stem).
 */
function displayNameFromPath(relativePath: string): string {
  const parts = relativePath.split("/");
  const filename = parts[parts.length - 1] ?? relativePath;
  return filename.replace(/\.(tsx?|jsx?|css|scss|json)$/, "");
}

/**
 * Convert a file's relative path into a stable node ID.
 */
function nodeIdFromPath(relativePath: string): string {
  // Normalise path separators and strip leading ./
  return relativePath.replace(/\\/g, "/").replace(/^\.\//, "");
}

/**
 * Determine the dependency kind from a TypeScript import declaration.
 */
function dependencyKindFromImport(
  decl: ts.ImportDeclaration,
): ArtifactDependencyKind {
  // type-only import
  if (decl.importClause?.isTypeOnly === true) return "type-only";
  return "import";
}

/**
 * Detect whether a source file uses JSX that references design-system components.
 */
function detectDesignSystemUsage(
  sourceFile: ts.SourceFile,
  dsComponentNames: ReadonlySet<string>,
): boolean {
  let found = false;
  function visit(node: ts.Node): void {
    if (found) return;
    if (ts.isJsxOpeningLikeElement(node)) {
      const tagName = node.tagName.getText(sourceFile);
      if (dsComponentNames.has(tagName)) {
        found = true;
      }
    }
    ts.forEachChild(node, visit);
  }
  ts.forEachChild(sourceFile, visit);
  return found;
}

// ============================================================================
// JSX TREE EXTRACTION
// ============================================================================

/**
 * A lightweight node in the extracted JSX element tree.
 */
export interface JsxTreeNode {
  /** JSX tag name (e.g. "Button", "div", "Route"). */
  readonly tagName: string;
  /** Whether the tag is a HTML intrinsic (lower-case first letter). */
  readonly isIntrinsic: boolean;
  /** Child JSX tree nodes (direct children). */
  readonly children: readonly JsxTreeNode[];
  /** 1-based start line in the source file. */
  readonly startLine: number;
  /** 1-based end line in the source file. */
  readonly endLine: number;
}

/**
 * Extract the JSX element tree from all JSX-rendering function bodies in a
 * source file. Returns a flat list of root JSX trees (one per render function).
 */
export function extractJsxTree(sourceFile: ts.SourceFile): JsxTreeNode[] {
  const roots: JsxTreeNode[] = [];

  function visitJsx(node: ts.JsxElement | ts.JsxSelfClosingElement | ts.JsxFragment): JsxTreeNode {
    if (ts.isJsxSelfClosingElement(node)) {
      const tagName = node.tagName.getText(sourceFile);
      const { line: startLine } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
      const { line: endLine } = sourceFile.getLineAndCharacterOfPosition(node.getEnd());
      return { tagName, isIntrinsic: /^[a-z]/.test(tagName), children: [], startLine: startLine + 1, endLine: endLine + 1 };
    }

    if (ts.isJsxFragment(node)) {
      const children: JsxTreeNode[] = [];
      for (const child of node.children) {
        if (ts.isJsxElement(child) || ts.isJsxSelfClosingElement(child) || ts.isJsxFragment(child)) {
          children.push(visitJsx(child));
        }
      }
      const { line: startLine } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
      const { line: endLine } = sourceFile.getLineAndCharacterOfPosition(node.getEnd());
      return { tagName: '<>', isIntrinsic: true, children, startLine: startLine + 1, endLine: endLine + 1 };
    }

    // ts.JsxElement
    const tagName = node.openingElement.tagName.getText(sourceFile);
    const { line: startLine } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
    const { line: endLine } = sourceFile.getLineAndCharacterOfPosition(node.getEnd());
    const children: JsxTreeNode[] = [];
    for (const child of node.children) {
      if (ts.isJsxElement(child) || ts.isJsxSelfClosingElement(child) || ts.isJsxFragment(child)) {
        children.push(visitJsx(child));
      }
    }
    return { tagName, isIntrinsic: /^[a-z]/.test(tagName), children, startLine: startLine + 1, endLine: endLine + 1 };
  }

  function visitNode(node: ts.Node): void {
    // Collect top-level JSX returns from arrow functions and function declarations
    if (
      ts.isReturnStatement(node) &&
      node.expression &&
      (ts.isJsxElement(node.expression) ||
        ts.isJsxSelfClosingElement(node.expression) ||
        ts.isJsxFragment(node.expression))
    ) {
      roots.push(visitJsx(node.expression));
    } else if (
      ts.isParenthesizedExpression(node) &&
      (ts.isJsxElement(node.expression) ||
        ts.isJsxSelfClosingElement(node.expression) ||
        ts.isJsxFragment(node.expression)) &&
      node.parent &&
      ts.isReturnStatement(node.parent)
    ) {
      roots.push(visitJsx(node.expression));
    }
    ts.forEachChild(node, visitNode);
  }

  ts.forEachChild(sourceFile, visitNode);
  return roots;
}

// ============================================================================
// ROUTE GRAPH DETECTION
// ============================================================================

/**
 * A detected route declaration.
 */
export interface DetectedRoute {
  /** The `path` prop value (e.g. "/dashboard/:id"). */
  readonly path: string;
  /** The component element name bound to this route. */
  readonly componentName: string;
  /** Whether this is an index route (no `path` prop). */
  readonly isIndex: boolean;
  /** 1-based source line where the route element appears. */
  readonly sourceLine: number;
}

/**
 * Extract React Router v6 route declarations from a source file.
 *
 * Handles `<Route path="..." element={<Component />}>` patterns used by
 * `react-router-dom` v6. Does not handle legacy v5 `component` props or
 * dynamic route config objects.
 */
export function extractRouteGraph(sourceFile: ts.SourceFile): DetectedRoute[] {
  const routes: DetectedRoute[] = [];

  function getJsxAttributeValue(attrs: ts.JsxAttributes, name: string): string | null {
    for (const attr of attrs.properties) {
      if (!ts.isJsxAttribute(attr)) continue;
      if (attr.name.getText(sourceFile) !== name) continue;
      if (!attr.initializer) return null;
      if (ts.isStringLiteral(attr.initializer)) return attr.initializer.text;
      if (ts.isJsxExpression(attr.initializer) && attr.initializer.expression) {
        // Try to inline-evaluate string literals inside {}
        if (ts.isStringLiteral(attr.initializer.expression)) {
          return attr.initializer.expression.text;
        }
        // Return the raw text as a reference for non-literal expressions
        return `{${attr.initializer.expression.getText(sourceFile)}}`;
      }
    }
    return null;
  }

  function extractComponentNameFromElement(node: ts.JsxExpression | ts.StringLiteral | ts.JsxAttribute | undefined): string | null {
    if (!node) return null;
    if (ts.isJsxAttribute(node) && node.initializer) {
      if (ts.isJsxExpression(node.initializer) && node.initializer.expression) {
        const expr = node.initializer.expression;
        if (ts.isJsxElement(expr) || ts.isJsxSelfClosingElement(expr)) {
          const tagName = ts.isJsxElement(expr)
            ? expr.openingElement.tagName.getText(sourceFile)
            : expr.tagName.getText(sourceFile);
          return tagName;
        }
        return expr.getText(sourceFile);
      }
    }
    return null;
  }

  function visitNode(node: ts.Node): void {
    if (ts.isJsxOpeningLikeElement(node)) {
      const tagName = node.tagName.getText(sourceFile);
      if (tagName === 'Route') {
        const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
        const path = getJsxAttributeValue(node.attributes, 'path') ?? '';
        const isIndex = getJsxAttributeValue(node.attributes, 'index') !== null;

        // Extract component name from `element` prop
        let componentName = 'Unknown';
        for (const attr of node.attributes.properties) {
          if (ts.isJsxAttribute(attr) && attr.name.getText(sourceFile) === 'element') {
            const extracted = extractComponentNameFromElement(attr);
            if (extracted) {
              componentName = extracted;
            }
          }
        }

        routes.push({ path, componentName, isIndex, sourceLine: line + 1 });
      }
    }
    ts.forEachChild(node, visitNode);
  }

  ts.forEachChild(sourceFile, visitNode);
  return routes;
}

// ============================================================================
// COMPONENT USAGE GRAPH
// ============================================================================

/**
 * A record of a component usage (JSX element reference) in a file.
 */
export interface ComponentUsageRecord {
  /** The component tag name used in JSX. */
  readonly tagName: string;
  /** Whether this refers to a known design-system component. */
  readonly isDesignSystem: boolean;
  /** 1-based source line of the JSX element. */
  readonly sourceLine: number;
  /** The import specifier this tag was resolved from (if any). */
  readonly importedFrom: string | null;
}

/**
 * Extract all component usages (non-intrinsic JSX tags) from a source file.
 * Returns an array of usage records that can be linked to the component graph.
 */
export function extractComponentUsages(
  sourceFile: ts.SourceFile,
  dsComponentNames: ReadonlySet<string>,
  importSpecifierByName: ReadonlyMap<string, string>,
): ComponentUsageRecord[] {
  const seen = new Set<string>();
  const usages: ComponentUsageRecord[] = [];

  function visitNode(node: ts.Node): void {
    if (ts.isJsxOpeningLikeElement(node)) {
      const tagName = node.tagName.getText(sourceFile);
      // Only collect custom components (not HTML intrinsics)
      if (/^[A-Z]/.test(tagName)) {
        const key = `${tagName}:${sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile)).line}`;
        if (!seen.has(key)) {
          seen.add(key);
          const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
          usages.push({
            tagName,
            isDesignSystem: dsComponentNames.has(tagName),
            sourceLine: line + 1,
            importedFrom: importSpecifierByName.get(tagName) ?? null,
          });
        }
      }
    }
    ts.forEachChild(node, visitNode);
  }

  ts.forEachChild(sourceFile, visitNode);
  return usages;
}

/**
 * Collect imported module specifiers and the names of exported symbols.
 */
function collectImportsAndExports(sourceFile: ts.SourceFile): {
  imports: Array<{ specifier: string; isTypeOnly: boolean; decl: ts.ImportDeclaration }>;
  exportedSymbols: string[];
  hasDynamicImport: boolean;
} {
  const imports: Array<{ specifier: string; isTypeOnly: boolean; decl: ts.ImportDeclaration }> = [];
  const exportedSymbols: string[] = [];
  let hasDynamicImport = false;

  function visit(node: ts.Node): void {
    // Static imports
    if (ts.isImportDeclaration(node)) {
      const specifier = (node.moduleSpecifier as ts.StringLiteral).text;
      const isTypeOnly = node.importClause?.isTypeOnly === true;
      imports.push({ specifier, isTypeOnly, decl: node });
    }

    // Dynamic import()
    if (ts.isCallExpression(node)) {
      const expr = node.expression;
      if (
        expr.kind === ts.SyntaxKind.ImportKeyword ||
        (ts.isIdentifier(expr) && expr.text === "import")
      ) {
        hasDynamicImport = true;
      }
    }

    // Export declarations: export { ... } or export default ...
    if (ts.isExportDeclaration(node)) {
      if (node.exportClause && ts.isNamedExports(node.exportClause)) {
        for (const el of node.exportClause.elements) {
          exportedSymbols.push(el.name.text);
        }
      }
    }

    // export function / export class / export const / export default
    if (
      ts.isFunctionDeclaration(node) ||
      ts.isClassDeclaration(node) ||
      ts.isVariableStatement(node) ||
      ts.isTypeAliasDeclaration(node) ||
      ts.isInterfaceDeclaration(node)
    ) {
      const mods = node.modifiers;
      const hasExport = mods?.some(
        (m) => m.kind === ts.SyntaxKind.ExportKeyword,
      );
      if (hasExport) {
        if (ts.isFunctionDeclaration(node) && node.name) {
          exportedSymbols.push(node.name.text);
        } else if (ts.isClassDeclaration(node) && node.name) {
          exportedSymbols.push(node.name.text);
        } else if (ts.isVariableStatement(node)) {
          for (const decl of node.declarationList.declarations) {
            if (ts.isIdentifier(decl.name)) {
              exportedSymbols.push(decl.name.text);
            }
          }
        } else if (ts.isTypeAliasDeclaration(node)) {
          exportedSymbols.push(node.name.text);
        } else if (ts.isInterfaceDeclaration(node)) {
          exportedSymbols.push(node.name.text);
        }
      }
    }

    ts.forEachChild(node, visit);
  }

  ts.forEachChild(sourceFile, visit);
  return { imports, exportedSymbols, hasDynamicImport };
}

/**
 * Infer prop names from an interface or type alias in a TSX file.
 * Looks for the first Props-like interface and collects member names.
 */
function inferPropsFromSourceFile(sourceFile: ts.SourceFile): Record<string, string> {
  const props: Record<string, string> = {};

  function visit(node: ts.Node): void {
    if (
      ts.isInterfaceDeclaration(node) &&
      /[Pp]rops/.test(node.name.text)
    ) {
      for (const member of node.members) {
        if (ts.isPropertySignature(member) && ts.isIdentifier(member.name)) {
          const typeName = member.type
            ? member.type.getText(sourceFile)
            : "unknown";
          props[member.name.text] = typeName;
        }
      }
    }
    ts.forEachChild(node, visit);
  }

  ts.forEachChild(sourceFile, visit);
  return props;
}

// ============================================================================
// MAIN DECOMPILER
// ============================================================================

/**
 * Decompile a set of TSX/TS source files into a LogicalArtifactModel.
 *
 * Parsing uses the TypeScript compiler API for accurate AST analysis.
 * The resulting model can be projected into BuilderDocument, CanvasDocument,
 * or a DS generator config via the corresponding projection modules.
 *
 * @doc.type function
 * @doc.purpose Entrypoint for TSX → LogicalArtifactModel decompilation
 * @doc.layer platform
 * @doc.pattern Adapter
 */
export function decompileTsx(input: DecompileTsxInput): DecompileTsxResult {
  const dsNames: ReadonlySet<string> =
    input.designSystemComponentNames ?? new Set<string>();

  const model = createLogicalArtifactModel(input.modelId, input.label);
  const mutableNodes: Record<string, ArtifactNode> = {};
  const mutableEdges: ArtifactEdge[] = [];
  const edgeIdSet = new Set<string>();
  const perFileFidelity = new Map<string, FidelityReport>();
  const pipelineLossPoints: LossPoint[] = [];

  for (const file of input.files) {
    const fileLossPoints: LossPoint[] = [];
    const nodeId = nodeIdFromPath(file.relativePath);
    const kind = inferKindFromPath(file.relativePath);

    // Parse source file
    const sourceFile = ts.createSourceFile(
      file.relativePath,
      file.content,
      ts.ScriptTarget.Latest,
      /* setParentNodes */ true,
      // Choose TSX if the extension suggests it
      file.relativePath.endsWith(".tsx") || file.relativePath.endsWith(".jsx")
        ? ts.ScriptKind.TSX
        : ts.ScriptKind.TS,
    );

    const { imports, exportedSymbols, hasDynamicImport } =
      collectImportsAndExports(sourceFile);

    // Build import name → specifier map for component usage resolution
    const importSpecifierByName = new Map<string, string>();
    for (const { specifier, decl } of imports) {
      if (decl.importClause) {
        const { name, namedBindings } = decl.importClause;
        if (name) {
          importSpecifierByName.set(name.text, specifier);
        }
        if (namedBindings && ts.isNamedImports(namedBindings)) {
          for (const el of namedBindings.elements) {
            importSpecifierByName.set(el.name.text, specifier);
          }
        }
      }
    }

    if (hasDynamicImport) {
      fileLossPoints.push({
        code: "dynamic-import",
        description: "Dynamic import() detected — dependency may not be fully captured.",
        severity: "info",
        confidenceImpact: 0.05,
      });
    }

    const usesDesignSystem = detectDesignSystemUsage(sourceFile, dsNames);
    const inferredProps =
      kind === "component" || kind === "page" || kind === "layout"
        ? inferPropsFromSourceFile(sourceFile)
        : {};

    // Extract JSX tree, route graph, and component usage graph for TSX files
    const isTsxFile = file.relativePath.endsWith(".tsx") || file.relativePath.endsWith(".jsx");
    const jsxTree = isTsxFile ? extractJsxTree(sourceFile) : [];
    const detectedRoutes = isTsxFile ? extractRouteGraph(sourceFile) : [];
    const componentUsages = isTsxFile
      ? extractComponentUsages(sourceFile, dsNames, importSpecifierByName)
      : [];

    // Parse protected regions to preserve user-authored content spans
    const protectedRegions = parseProtectedRegions(sourceFile);

    // Compute real source span
    const totalLines = sourceFile.getLineAndCharacterOfPosition(sourceFile.end).line + 1;

    // Build the ArtifactNode
    const node: ArtifactNode = {
      id: nodeId,
      displayName: displayNameFromPath(file.relativePath),
      kind,
      sourceRef: {
        repositoryUri: "local://",
        commitRef: "working-tree",
        file: {
          relativePath: file.relativePath,
          contentType: "text/typescript",
          kind: "unknown",
        },
        span: {
          startOffset: 0,
          endOffset: sourceFile.end,
          startLine: 1,
          endLine: totalLines,
        },
      },
      exportedSymbols,
      inferredProps,
      usesDesignSystem,
      classificationConfidence: fileLossPoints.length === 0 ? 1 : 0.8,
      metadata: {
        jsxTreeRoots: jsxTree.length,
        detectedRouteCount: detectedRoutes.length,
        componentUsageCount: componentUsages.length,
        jsxTree: jsxTree as unknown as Record<string, unknown>[],
        detectedRoutes: detectedRoutes as unknown as Record<string, unknown>[],
        componentUsages: componentUsages as unknown as Record<string, unknown>[],
        protectedRegions: protectedRegions as unknown as Record<string, unknown>[],
      },
    };

    mutableNodes[nodeId] = node;

    // Build edges from imports
    for (const { specifier, isTypeOnly, decl } of imports) {
      // Only create edges to other files in the model (relative imports)
      if (!specifier.startsWith(".")) continue;
      // Normalise specifier relative to file directory
      const dir = file.relativePath.split("/").slice(0, -1).join("/");
      const resolvedPath = `${dir}/${specifier}`
        .replace(/\/\.\//g, "/")
        .replace(/\\/g, "/");
      // Strip extension-less paths — we'll match by prefix
      const edgeId = `${nodeId}→${resolvedPath}`;
      if (!edgeIdSet.has(edgeId)) {
        edgeIdSet.add(edgeId);
        const kind: ArtifactDependencyKind = isTypeOnly
          ? "type-only"
          : dependencyKindFromImport(decl);
        mutableEdges.push({
          id: edgeId,
          fromId: nodeId,
          toId: resolvedPath,
          kind,
          importSpecifier: specifier,
        });
      }
    }

    // Compute per-file fidelity
    const fileReport = computeFidelityReport(fileLossPoints, nodeId, "file");
    perFileFidelity.set(file.relativePath, fileReport);
    pipelineLossPoints.push(...fileLossPoints);
  }

  // Assemble final model
  const finalModel: LogicalArtifactModel = {
    ...model,
    nodes: mutableNodes,
    edges: mutableEdges,
  };

  const pipelineReport = computeFidelityReport(
    pipelineLossPoints,
    input.modelId,
    "pipeline",
  );

  return {
    model: finalModel,
    fidelityReport: pipelineReport,
    perFileFidelity,
  };
}
