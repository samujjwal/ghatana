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
          endOffset: 0,
          startLine: 1,
          endLine: sourceFile.getLineAndCharacterOfPosition(sourceFile.end).line + 1,
        },
      },
      exportedSymbols,
      inferredProps,
      usesDesignSystem,
      classificationConfidence: fileLossPoints.length === 0 ? 1 : 0.8,
      metadata: {},
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
