/**
 * @fileoverview Residual island detector for the artifact compiler pipeline.
 *
 * Analyses a LogicalArtifactModel (and optionally a set of TSX SourceFiles)
 * to produce a ResidualIslandReport identifying source constructs that the
 * pipeline cannot faithfully represent in the logical model.
 *
 * @doc.type module
 * @doc.purpose Residual island detection for the artifact compiler pipeline
 * @doc.layer platform
 * @doc.pattern Utility
 */

import * as ts from "typescript";
import type {
  LogicalArtifactModel,
  ArtifactNode,
  ResidualIsland,
  SourceRef,
} from "@ghatana/artifact-contracts";
import {
  createResidualIslandReport,
  type ResidualIslandReport,
} from "@ghatana/artifact-contracts";

// ============================================================================
// HELPERS
// ============================================================================

/**
 * Build a best-effort SourceRef for an AST-level location.
 * Uses sentinel values for VCS fields (repositoryUri, commitRef) that are
 * not available inside the local static analysis context.
 */
function makeLocalSourceRef(
  relativePath: string,
  startLine: number,
  endLine: number,
): SourceRef {
  return {
    repositoryUri: "local://",
    commitRef: "working-tree",
    file: {
      relativePath,
      contentType: "text/typescript",
      kind: "component",
    },
    span: {
      startOffset: 0,
      endOffset: 0,
      startLine,
      endLine,
    },
  };
}

// ============================================================================
// DETECTION INPUT
// ============================================================================

/**
 * A parsed source file pair used by the residual detector.
 * Pass these in addition to the model for AST-level detection.
 */
export interface ParsedSourceFile {
  readonly relativePath: string;
  readonly sourceFile: ts.SourceFile;
}

// ============================================================================
// AST-LEVEL DETECTORS
// ============================================================================

/**
 * Detect use of `eval()` or `new Function()` — runtime dynamic constructs.
 */
function detectEvalUsage(
  sourceFile: ts.SourceFile,
  relativePath: string,
): ResidualIsland[] {
  const islands: ResidualIsland[] = [];

  function visit(node: ts.Node): void {
    if (ts.isCallExpression(node)) {
      const expr = node.expression;
      const text = expr.getText(sourceFile);
      if (text === "eval") {
        const pos = sourceFile.getLineAndCharacterOfPosition(node.getStart());
        islands.push({
          id: `${relativePath}:eval:${pos.line}`,
          kind: "runtime-dynamic",
          description: `eval() usage at line ${pos.line + 1} — runtime dynamic code cannot be statically modelled.`,
          sourceRef: makeLocalSourceRef(relativePath, pos.line + 1, pos.line + 1),
          remediation: "Replace eval() with a well-typed function or lookup table.",
          severity: "blocking",
        });
      }
    }
    if (
      ts.isNewExpression(node) &&
      ts.isIdentifier(node.expression) &&
      node.expression.text === "Function"
    ) {
      const pos = sourceFile.getLineAndCharacterOfPosition(node.getStart());
      islands.push({
        id: `${relativePath}:new-function:${pos.line}`,
        kind: "runtime-dynamic",
        description: `new Function() usage at line ${pos.line + 1} — dynamic function construction cannot be statically modelled.`,
        sourceRef: makeLocalSourceRef(relativePath, pos.line + 1, pos.line + 1),
        remediation: "Replace new Function() with a statically declared function.",
        severity: "blocking",
      });
    }
    ts.forEachChild(node, visit);
  }

  ts.forEachChild(sourceFile, visit);
  return islands;
}

/**
 * Detect CSS-in-JS patterns (styled-components, emotion template literals).
 */
function detectCssInJs(
  sourceFile: ts.SourceFile,
  relativePath: string,
): ResidualIsland[] {
  const islands: ResidualIsland[] = [];

  function visit(node: ts.Node): void {
    // styled.div`...` or css`...` template tag expressions
    if (ts.isTaggedTemplateExpression(node)) {
      const tag = node.tag.getText(sourceFile);
      if (/^(styled|css|createGlobalStyle|keyframes|injectGlobal)/.test(tag)) {
        const pos = sourceFile.getLineAndCharacterOfPosition(node.getStart());
        islands.push({
          id: `${relativePath}:css-in-js:${pos.line}`,
          kind: "css-in-js-pattern",
          description: `CSS-in-JS pattern detected at line ${pos.line + 1} ("${tag}\`...\`"). Cannot be mapped to design-system tokens.`,
          sourceRef: makeLocalSourceRef(relativePath, pos.line + 1, pos.line + 1),
          remediation: "Migrate to Tailwind or @ghatana/design-system token-based styling.",
          severity: "advisory",
        });
      }
    }
    ts.forEachChild(node, visit);
  }

  ts.forEachChild(sourceFile, visit);
  return islands;
}

/**
 * Detect dynamic component patterns: React.lazy, require().
 */
function detectDynamicComponents(
  sourceFile: ts.SourceFile,
  relativePath: string,
): ResidualIsland[] {
  const islands: ResidualIsland[] = [];

  function visit(node: ts.Node): void {
    if (ts.isCallExpression(node)) {
      const callee = node.expression.getText(sourceFile);
      if (callee === "React.lazy" || callee === "lazy") {
        const pos = sourceFile.getLineAndCharacterOfPosition(node.getStart());
        islands.push({
          id: `${relativePath}:lazy:${pos.line}`,
          kind: "runtime-dynamic",
          description: `React.lazy() at line ${pos.line + 1} — dynamically loaded component cannot be statically resolved in the model.`,
          sourceRef: makeLocalSourceRef(relativePath, pos.line + 1, pos.line + 1),
          remediation: "Add the lazy-loaded component to the decompiler's known module set so it can be statically resolved.",
          severity: "advisory",
        });
      }
      if (callee === "require" || callee.endsWith(".require")) {
        const pos = sourceFile.getLineAndCharacterOfPosition(node.getStart());
        islands.push({
          id: `${relativePath}:require:${pos.line}`,
          kind: "runtime-dynamic",
          description: `CommonJS require() at line ${pos.line + 1} — use ESM static imports for full model coverage.`,
          sourceRef: makeLocalSourceRef(relativePath, pos.line + 1, pos.line + 1),
          remediation: "Replace require() with ESM import declaration.",
          severity: "advisory",
        });
      }
    }
    ts.forEachChild(node, visit);
  }

  ts.forEachChild(sourceFile, visit);
  return islands;
}

// ============================================================================
// MODEL-LEVEL DETECTORS
// ============================================================================

/**
 * Detect nodes whose classification confidence is below threshold.
 * These are surfaced as "unsupported-syntax" residuals.
 */
function detectLowConfidenceNodes(
  nodes: ReadonlyMap<string, ArtifactNode>,
  confidenceThreshold: number,
): ResidualIsland[] {
  const islands: ResidualIsland[] = [];
  for (const node of nodes.values()) {
    if (node.classificationConfidence < confidenceThreshold) {
      islands.push({
        id: `model:low-confidence:${node.id}`,
        kind: "unknown",
        description: `Node "${node.displayName}" has classification confidence ${(node.classificationConfidence * 100).toFixed(0)}% (threshold: ${(confidenceThreshold * 100).toFixed(0)}%).`,
        sourceRef: node.sourceRef,
        remediation: "Review the node manually and adjust its kind/props before compiling.",
        severity: node.classificationConfidence < 0.5 ? "blocking" : "advisory",
      });
    }
  }
  return islands;
}

/**
 * Detect orphan edges — edges pointing to nodes not in the model.
 */
function detectOrphanEdges(model: LogicalArtifactModel): ResidualIsland[] {
  const islands: ResidualIsland[] = [];
  for (const edge of model.edges) {
    if (!(edge.toId in model.nodes) && !(edge.toId.startsWith("."))) {
      // Skip external package edges (they're expected to be absent)
      continue;
    }
    if (
      edge.toId.startsWith(".") &&
      // Normalised toId doesn't resolve to a known node
      !Object.keys(model.nodes).some((id) => id.endsWith(edge.toId.replace(/^\.\//, "")))
    ) {
      islands.push({
        id: `model:orphan-edge:${edge.id}`,
        kind: "unknown",
        description: `Edge "${edge.id}" targets "${edge.toId}" which is not in the model — may be an unresolved relative import.`,
        remediation: "Ensure the target file is included in the decompile input set.",
        severity: "advisory",
      });
    }
  }
  return islands;
}

// ============================================================================
// MAIN DETECTOR
// ============================================================================

/**
 * Produce a ResidualIslandReport for a LogicalArtifactModel.
 *
 * Optionally accepts pre-parsed TypeScript SourceFiles for AST-level
 * detection (eval, CSS-in-JS, dynamic components). When source files are
 * not provided, detection is limited to model-level heuristics.
 *
 * @doc.type function
 * @doc.purpose Detect residual islands in a logical artifact model
 * @doc.layer platform
 * @doc.pattern Utility
 */
export function detectResidualIslands(
  model: LogicalArtifactModel,
  options?: {
    /** Pre-parsed source files for AST-level detection. */
    parsedFiles?: readonly ParsedSourceFile[];
    /**
     * Minimum classification confidence. Nodes below this are flagged.
     * Default: 0.7.
     */
    confidenceThreshold?: number;
  },
): ResidualIslandReport {
  const parsedFiles = options?.parsedFiles ?? [];
  const confidenceThreshold = options?.confidenceThreshold ?? 0.7;

  const islands: ResidualIsland[] = [];

  // Model-level detection
  const nodeMap = new Map<string, ArtifactNode>(
    Object.entries(model.nodes),
  );
  islands.push(...detectLowConfidenceNodes(nodeMap, confidenceThreshold));
  islands.push(...detectOrphanEdges(model));

  // AST-level detection (per parsed source file)
  for (const { relativePath, sourceFile } of parsedFiles) {
    islands.push(...detectEvalUsage(sourceFile, relativePath));
    islands.push(...detectCssInJs(sourceFile, relativePath));
    islands.push(...detectDynamicComponents(sourceFile, relativePath));
  }

  // Deduplicate by ID
  const seenIds = new Set<string>();
  const dedupedIslands: ResidualIsland[] = [];
  for (const island of islands) {
    if (!seenIds.has(island.id)) {
      seenIds.add(island.id);
      dedupedIslands.push(island);
    }
  }

  return createResidualIslandReport(dedupedIslands);
}
