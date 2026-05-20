/**
 * @fileoverview Round-trip source/model/source diff utilities.
 */

import {
  computeFidelityReport,
  createResidualIslandReport,
  type DiffHunk,
  type DiffRecord,
  type FidelityReport,
  type LogicalArtifactModel,
  type ResidualIslandReport,
  type RoundTripDiffReport,
} from "@ghatana/artifact-contracts";

export interface RoundTripDiffSourceFile {
  readonly relativePath: string;
  readonly content: string;
}

export interface BuildRoundTripDiffReportOptions {
  readonly reportId: string;
  readonly model: LogicalArtifactModel;
  readonly originalSources: readonly RoundTripDiffSourceFile[];
  readonly generatedSources: readonly RoundTripDiffSourceFile[];
  readonly reimportedModel?: LogicalArtifactModel;
  readonly fidelity?: FidelityReport;
  readonly residuals?: ResidualIslandReport;
}

export function buildRoundTripDiffReport(
  options: BuildRoundTripDiffReportOptions,
): RoundTripDiffReport {
  const originalByPath = new Map(options.originalSources.map((source) => [source.relativePath, source]));
  const diffs = options.generatedSources.map((generated) => {
    const original = originalByPath.get(generated.relativePath);
    return diffSourcePair({
      model: options.model,
      reimportedModel: options.reimportedModel,
      originalPath: original?.relativePath ?? generated.relativePath,
      generatedPath: generated.relativePath,
      originalContent: original?.content ?? "",
      generatedContent: generated.content,
    });
  });

  return {
    reportId: options.reportId,
    modelId: options.model.modelId,
    diffs,
    fidelity: options.fidelity ?? computeFidelityReport([], options.model.modelId, "pipeline"),
    residuals: options.residuals ?? createResidualIslandReport([]),
    isLossless: diffs.every((diff) => diff.semanticallyEquivalent && diff.hunks.length === 0),
    generatedAt: new Date().toISOString(),
  };
}

function diffSourcePair(options: {
  readonly model: LogicalArtifactModel;
  readonly reimportedModel?: LogicalArtifactModel;
  readonly originalPath: string;
  readonly generatedPath: string;
  readonly originalContent: string;
  readonly generatedContent: string;
}): DiffRecord {
  const originalLines = splitLines(options.originalContent);
  const generatedLines = splitLines(options.generatedContent);
  const unchangedLines = countUnchangedPrefix(originalLines, generatedLines);
  const semanticallyEquivalent = isSemanticallyEquivalent(
    options.model,
    options.reimportedModel,
    options.originalPath,
  ) || normalizeSource(options.originalContent) === normalizeSource(options.generatedContent);
  const hunk = buildSingleSummaryHunk(originalLines, generatedLines, unchangedLines);

  return {
    diffId: `${options.originalPath}->${options.generatedPath}`,
    originalPath: options.originalPath,
    generatedPath: options.generatedPath,
    semanticallyEquivalent,
    hunks: hunk ? [hunk] : [],
    addedLines: Math.max(0, generatedLines.length - unchangedLines),
    removedLines: Math.max(0, originalLines.length - unchangedLines),
    unchangedLines,
    diffedAt: new Date().toISOString(),
  };
}

function splitLines(content: string): string[] {
  if (content.length === 0) return [];
  return content.split(/\r?\n/);
}

function normalizeSource(content: string): string {
  return content.replace(/\s+/g, " ").trim();
}

function countUnchangedPrefix(originalLines: readonly string[], generatedLines: readonly string[]): number {
  const max = Math.min(originalLines.length, generatedLines.length);
  let count = 0;
  for (let i = 0; i < max; i += 1) {
    if (originalLines[i] !== generatedLines[i]) break;
    count += 1;
  }
  return count;
}

function buildSingleSummaryHunk(
  originalLines: readonly string[],
  generatedLines: readonly string[],
  unchangedLines: number,
): DiffHunk | null {
  if (
    originalLines.length === generatedLines.length &&
    unchangedLines === originalLines.length
  ) {
    return null;
  }

  return {
    kind: originalLines.length === 0 ? "added" : generatedLines.length === 0 ? "removed" : "changed",
    originalStart: originalLines.length > 0 ? unchangedLines + 1 : undefined,
    generatedStart: generatedLines.length > 0 ? unchangedLines + 1 : undefined,
    lineCount: Math.max(1, Math.max(originalLines.length, generatedLines.length) - unchangedLines),
    originalSnippet: originalLines.slice(unchangedLines, unchangedLines + 12).join("\n").slice(0, 500),
    generatedSnippet: generatedLines.slice(unchangedLines, unchangedLines + 12).join("\n").slice(0, 500),
  };
}

function isSemanticallyEquivalent(
  model: LogicalArtifactModel,
  reimportedModel: LogicalArtifactModel | undefined,
  nodeId: string,
): boolean {
  if (reimportedModel === undefined) return false;
  const originalNode = model.nodes[nodeId];
  const reimportedNode = reimportedModel.nodes[nodeId];
  if (originalNode === undefined || reimportedNode === undefined) return false;

  return (
    originalNode.kind === reimportedNode.kind &&
    originalNode.displayName === reimportedNode.displayName &&
    JSON.stringify(originalNode.exportedSymbols) === JSON.stringify(reimportedNode.exportedSymbols) &&
    JSON.stringify(originalNode.inferredProps) === JSON.stringify(reimportedNode.inferredProps)
  );
}
