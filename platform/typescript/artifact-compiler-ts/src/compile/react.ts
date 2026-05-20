/**
 * @fileoverview React/TSX compiler — LogicalArtifactModel → React TSX source.
 *
 * Generates TypeScript/React source from a LogicalArtifactModel. Each
 * ArtifactNode becomes one emitted file. Nodes with low classification
 * confidence or critical loss points are emitted as stubs with a
 * `// RESIDUAL:` marker so the UI can surface them for human review.
 *
 * @doc.type module
 * @doc.purpose Compile LogicalArtifactModel to React TSX source files
 * @doc.layer platform
 * @doc.pattern Adapter
 */

import type { ArtifactNode, LogicalArtifactModel } from "@ghatana/artifact-contracts";
import type { FidelityReport, LossPoint } from "@ghatana/artifact-contracts";
import { computeFidelityReport } from "@ghatana/artifact-contracts";

// ============================================================================
// INPUT / OUTPUT CONTRACTS
// ============================================================================

/**
 * Options that control how the compiler emits React/TSX code.
 */
export interface CompileReactOptions {
  /**
   * Minimum classification confidence below which a node is emitted as a
   * residual stub instead of a full component. Default: 0.7.
   */
  readonly confidenceThreshold?: number;
  /**
   * Design-system package specifier used in import statements.
   * Default: `"@ghatana/design-system"`.
   */
  readonly designSystemPackage?: string;
  /**
   * Whether to emit `export default` in addition to a named export.
   * Default: false.
   */
  readonly emitDefaultExport?: boolean;
}

/**
 * A single emitted source file from the compiler.
 */
export interface EmittedFile {
  /** Relative path for the file within a target workspace. */
  readonly relativePath: string;
  /** Generated source content. */
  readonly content: string;
  /** Whether this file is a residual stub requiring human review. */
  readonly isResidualStub: boolean;
  /** Fidelity report for this specific file. */
  readonly fidelity: FidelityReport;
}

/**
 * Result of a compile run.
 */
export interface CompileReactResult {
  readonly emittedFiles: readonly EmittedFile[];
  /** Aggregate fidelity across all emitted files. */
  readonly overallFidelity: FidelityReport;
}

// ============================================================================
// INTERNAL EMITTERS
// ============================================================================

const DEFAULT_OPTIONS: Required<CompileReactOptions> = {
  confidenceThreshold: 0.7,
  designSystemPackage: "@ghatana/design-system",
  emitDefaultExport: false,
};

/**
 * Emit a residual stub for nodes below the confidence threshold or with
 * unsupported patterns.
 */
function emitResidualStub(node: ArtifactNode, opts: Required<CompileReactOptions>): string {
  const exportedName = node.exportedSymbols[0] ?? node.displayName;
  const lines: string[] = [
    `// RESIDUAL: "${node.displayName}" — classification confidence ${(node.classificationConfidence * 100).toFixed(0)}%`,
    `// Review and complete this component before using in production.`,
    `import type { ReactElement } from "react";`,
    ``,
    `/**`,
    ` * @residual This component was emitted as a stub by the artifact compiler.`,
    ` * Classification confidence was below threshold.`,
    ` * Source: ${node.sourceRef?.file.relativePath ?? "unknown"}`,
    ` */`,
    `export function ${exportedName}(): ReactElement {`,
    `  return (`,
    `    <div`,
    `      data-residual-stub="${node.id}"`,
    `      style={{ border: "2px dashed #f59e0b", padding: 16, fontFamily: "monospace" }}`,
    `    >`,
    `      <strong>[Residual Stub]</strong> {" "}`,
    `      <span>{${JSON.stringify(node.displayName)}}</span>`,
    `      <pre style={{ fontSize: 12 }}>{${JSON.stringify(JSON.stringify(node.inferredProps, null, 2))}}</pre>`,
    `    </div>`,
    `  );`,
    `}`,
  ];
  if (opts.emitDefaultExport) {
    lines.push(``, `export default ${exportedName};`);
  }
  return lines.join("\n");
}

/**
 * Emit a full React component for a well-classified ArtifactNode.
 */
function emitComponentFile(node: ArtifactNode, opts: Required<CompileReactOptions>): string {
  const exportedName = node.exportedSymbols[0] ?? node.displayName;
  const propsInterfaceName = `${exportedName}Props`;

  // Build props interface
  const propEntries = Object.entries(node.inferredProps);
  const propsInterface =
    propEntries.length > 0
      ? [
          `export interface ${propsInterfaceName} {`,
          ...propEntries.map(([name, type]) => `  readonly ${name}: ${type};`),
          `}`,
        ].join("\n")
      : `export type ${propsInterfaceName} = Record<string, never>;`;

  // Determine imports
  const dsImport = node.usesDesignSystem
    ? `// Design-system component imports are intentionally omitted until the artifact mapping resolves exact symbols from "${opts.designSystemPackage}".\n`
    : "";

  const lines: string[] = [
    `import type { ReactElement } from "react";`,
    dsImport.trim() ? dsImport : "",
    ``,
    propsInterface,
    ``,
    `/**`,
    ` * @doc.type component`,
    ` * @doc.purpose ${node.displayName}`,
    ` * @doc.layer product`,
    ` * @doc.pattern Component`,
    ` */`,
    `export function ${exportedName}(props: ${propsInterfaceName}): ReactElement {`,
    `  return (`,
    `    <div>`,
    `      <span data-artifact-component="${node.id}">{${JSON.stringify(node.displayName)}}</span>`,
    `    </div>`,
    `  );`,
    `}`,
  ].filter((line) => line !== "");

  if (opts.emitDefaultExport) {
    lines.push(``, `export default ${exportedName};`);
  }
  return lines.join("\n");
}

/**
 * Emit a generic utility/hook/service module stub.
 */
function emitGenericStub(node: ArtifactNode): string {
  const lines: string[] = [
    `/**`,
    ` * @doc.type module`,
    ` * @doc.purpose ${node.displayName}`,
    ` * @doc.layer platform`,
    ` * @doc.pattern Utility`,
    ` */`,
  ];
  for (const sym of node.exportedSymbols) {
    lines.push(
      ``,
      `// Artifact compiler emitted this symbol without executable logic because its source kind is outside the React component target.`,
      `export function ${sym}(): never {`,
      `  throw new Error("Unsupported artifact compiler target symbol: ${sym}");`,
      `}`,
    );
  }
  if (node.exportedSymbols.length === 0) {
    lines.push(``, `// No exports were inferred for ${node.displayName}.`);
  }
  return lines.join("\n");
}

/**
 * Derive a target relative path from the node ID.
 * The node ID is the original relativePath, so we preserve it.
 */
function targetPath(node: ArtifactNode): string {
  // Use sourceRef if available; fall back to node ID as path.
  return node.sourceRef?.file.relativePath ?? `${node.id}.tsx`;
}

// ============================================================================
// MAIN COMPILER
// ============================================================================

/**
 * Compile a LogicalArtifactModel into React/TypeScript source files.
 *
 * Nodes with classification confidence below `options.confidenceThreshold`
 * are emitted as residual stubs marked with `// RESIDUAL:` comments, so
 * Studio can surface them in the residual review queue.
 *
 * @doc.type function
 * @doc.purpose Compile LogicalArtifactModel → React TSX source
 * @doc.layer platform
 * @doc.pattern Adapter
 */
export function compileReact(
  model: LogicalArtifactModel,
  options?: CompileReactOptions,
): CompileReactResult {
  const opts: Required<CompileReactOptions> = {
    ...DEFAULT_OPTIONS,
    ...options,
  };

  const emittedFiles: EmittedFile[] = [];
  const pipelineLossPoints: LossPoint[] = [];

  for (const node of Object.values(model.nodes)) {
    const lossPoints: LossPoint[] = [];
    let content: string;
    let isResidualStub = false;

    if (node.classificationConfidence < opts.confidenceThreshold) {
      // Low confidence → residual stub
      content = emitResidualStub(node, opts);
      isResidualStub = true;
      lossPoints.push({
        code: "low-confidence",
        description: `Classification confidence ${(node.classificationConfidence * 100).toFixed(0)}% is below threshold — emitted as residual stub.`,
        severity: "warning",
        sourceRef: node.sourceRef,
        confidenceImpact: 1 - node.classificationConfidence,
      });
    } else if (
      node.kind === "component" ||
      node.kind === "page" ||
      node.kind === "layout"
    ) {
      content = emitComponentFile(node, opts);
    } else if (node.kind === "type") {
      // Type-only nodes emit minimal type exports
      content = `// Type declarations for ${node.displayName}\n\nexport type { };\n`;
    } else if (node.kind === "asset" || node.kind === "config") {
      // Non-compilable node kinds
      lossPoints.push({
        code: "non-compilable-kind",
        description: `Artifact kind "${node.kind}" cannot be compiled to React/TSX.`,
        severity: "info",
        sourceRef: node.sourceRef,
        confidenceImpact: 0,
      });
      // Emit empty file
      content = `// ${node.displayName} — kind "${node.kind}" is not compilable.\n`;
    } else {
      content = emitGenericStub(node);
    }

    const fidelity = computeFidelityReport(lossPoints, node.id, "file");
    emittedFiles.push({
      relativePath: targetPath(node),
      content,
      isResidualStub,
      fidelity,
    });
    pipelineLossPoints.push(...lossPoints);
  }

  const overallFidelity = computeFidelityReport(
    pipelineLossPoints,
    model.modelId,
    "pipeline",
  );

  return { emittedFiles, overallFidelity };
}
