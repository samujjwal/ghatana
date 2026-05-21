/**
 * @fileoverview Round-trip source/model/source diff utilities.
 */

import * as ts from "typescript";

import {
  computeFidelityReport,
  createResidualIslandReport,
  type ArtifactEdge,
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
  ) || areAstEquivalent(options.originalContent, options.generatedContent);
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

function normalizeAst(content: string): string | null {
  try {
    const sourceFile = ts.createSourceFile("diff.tsx", content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
    const diagnostics = (sourceFile as ts.SourceFile & { readonly parseDiagnostics?: readonly ts.Diagnostic[] }).parseDiagnostics;
    if (diagnostics && diagnostics.length > 0) {
      return null;
    }
    const printer = ts.createPrinter({ removeComments: true });
    return normalizeSource(printer.printFile(sourceFile));
  } catch {
    return null;
  }
}

function areAstEquivalent(originalContent: string, generatedContent: string): boolean {
  const originalAst = normalizeAst(originalContent);
  const generatedAst = normalizeAst(generatedContent);
  if (originalAst === null || generatedAst === null) {
    return normalizeSource(originalContent) === normalizeSource(generatedContent);
  }

  const originalSignature = buildAstSemanticSignature(originalContent);
  const generatedSignature = buildAstSemanticSignature(generatedContent);

  if (originalSignature !== null && generatedSignature !== null) {
    return originalSignature === generatedSignature;
  }

  return originalAst === generatedAst;
}

function buildAstSemanticSignature(content: string): string | null {
  try {
    const sourceFile = ts.createSourceFile('signature.tsx', content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
    const diagnostics = (sourceFile as ts.SourceFile & { readonly parseDiagnostics?: readonly ts.Diagnostic[] }).parseDiagnostics;
    if (diagnostics && diagnostics.length > 0) {
      return null;
    }

    const importSignatures: string[] = [];
    const exportSignatures: string[] = [];
    const jsxNodeKinds: string[] = [];
    const jsxAttributeNames: string[] = [];
    const callExpressionNames: string[] = [];
    const jsxEventHandlerNames: string[] = [];
    const jsxBindingExpressions: string[] = [];
    const styleReferences: string[] = [];

    const visit = (node: ts.Node): void => {
      if (ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)) {
        const importClause = node.importClause;
        const namedBindings = importClause?.namedBindings;
        let bindingShape = '';
        if (namedBindings && ts.isNamedImports(namedBindings)) {
          bindingShape = namedBindings.elements
            .map((element) => `${element.propertyName?.text ?? element.name.text}->${element.name.text}`)
            .sort((left, right) => left.localeCompare(right))
            .join(',');
        }
        importSignatures.push(`${importClause?.isTypeOnly === true ? 'type' : 'value'}:${node.moduleSpecifier.text}:${bindingShape}`);
      }

      if ((ts.isFunctionDeclaration(node) || ts.isClassDeclaration(node) || ts.isVariableStatement(node)) && node.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword)) {
        exportSignatures.push(node.kind.toString());
      }

      if (ts.isJsxElement(node)) {
        jsxNodeKinds.push(node.openingElement.tagName.getText(sourceFile));
        node.openingElement.attributes.properties.forEach((attribute) => {
          if (ts.isJsxAttribute(attribute)) {
            jsxAttributeNames.push(attribute.name.text);
            if (/^on[A-Z]/.test(attribute.name.text)) {
              jsxEventHandlerNames.push(attribute.name.text);
            }
            if (attribute.initializer && ts.isJsxExpression(attribute.initializer) && attribute.initializer.expression) {
              jsxBindingExpressions.push(`${attribute.name.text}:${normalizeSource(attribute.initializer.expression.getText(sourceFile))}`);
            }
            if (attribute.name.text === 'className' || attribute.name.text === 'style') {
              styleReferences.push(attribute.getText(sourceFile));
            }
          }
        });
      }

      if (ts.isJsxSelfClosingElement(node)) {
        jsxNodeKinds.push(node.tagName.getText(sourceFile));
        node.attributes.properties.forEach((attribute) => {
          if (ts.isJsxAttribute(attribute)) {
            jsxAttributeNames.push(attribute.name.text);
            if (/^on[A-Z]/.test(attribute.name.text)) {
              jsxEventHandlerNames.push(attribute.name.text);
            }
            if (attribute.initializer && ts.isJsxExpression(attribute.initializer) && attribute.initializer.expression) {
              jsxBindingExpressions.push(`${attribute.name.text}:${normalizeSource(attribute.initializer.expression.getText(sourceFile))}`);
            }
            if (attribute.name.text === 'className' || attribute.name.text === 'style') {
              styleReferences.push(attribute.getText(sourceFile));
            }
          }
        });
      }

      if (ts.isJsxExpression(node) && node.expression) {
        jsxBindingExpressions.push(normalizeSource(node.expression.getText(sourceFile)));
      }

      if (ts.isCallExpression(node)) {
        callExpressionNames.push(node.expression.getText(sourceFile));
      }

      if (ts.isStringLiteralLike(node)) {
        const text = node.text;
        if (text.includes('var(--') || /\btoken\b/i.test(text) || /\bclass(Name)?\b/.test(text)) {
          styleReferences.push(text);
        }
      }

      ts.forEachChild(node, visit);
    };

    ts.forEachChild(sourceFile, visit);

    return JSON.stringify({
      imports: importSignatures.sort((left, right) => left.localeCompare(right)),
      exports: exportSignatures.sort((left, right) => left.localeCompare(right)),
      jsxNodes: jsxNodeKinds.sort((left, right) => left.localeCompare(right)),
      jsxAttributes: jsxAttributeNames.sort((left, right) => left.localeCompare(right)),
      calls: callExpressionNames.sort((left, right) => left.localeCompare(right)),
      jsxEventHandlers: jsxEventHandlerNames.sort((left, right) => left.localeCompare(right)),
      jsxBindings: jsxBindingExpressions.sort((left, right) => left.localeCompare(right)),
      styleReferences: styleReferences.sort((left, right) => left.localeCompare(right)),
    });
  } catch {
    return null;
  }
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

  // Shape-level equivalence (existing checks)
  const shapeEquivalent =
    originalNode.kind === reimportedNode.kind &&
    originalNode.displayName === reimportedNode.displayName &&
    JSON.stringify([...originalNode.exportedSymbols].sort()) === JSON.stringify([...reimportedNode.exportedSymbols].sort()) &&
    JSON.stringify(normalizeRecord(originalNode.inferredProps)) === JSON.stringify(normalizeRecord(reimportedNode.inferredProps)) &&
    JSON.stringify(normalizeSourceImportShape(originalNode.sourceImports)) === JSON.stringify(normalizeSourceImportShape(reimportedNode.sourceImports)) &&
    JSON.stringify(nodeEdgeShape(model, nodeId)) === JSON.stringify(nodeEdgeShape(reimportedModel, nodeId));

  if (!shapeEquivalent) return false;

  // Import graph parity (new)
  const importGraphEquivalent = isImportGraphEquivalent(model, reimportedModel, nodeId);
  if (!importGraphEquivalent) return false;

  return true;
}

/**
 * Import graph parity check.
 *
 * Verifies that the import relationships are preserved across round-trip,
 * including both type-only and value imports, as well as other dependency kinds.
 */
function isImportGraphEquivalent(
  originalModel: LogicalArtifactModel,
  reimportedModel: LogicalArtifactModel,
  nodeId: string,
): boolean {
  const originalEdges = originalModel.edges.filter((edge) => edge.fromId === nodeId);
  const reimportedEdges = reimportedModel.edges.filter((edge) => edge.fromId === nodeId);

  if (originalEdges.length !== reimportedEdges.length) {
    return false;
  }

  const normalizeEdge = (edge: ArtifactEdge): string => {
    return `${edge.kind}:${edge.toId}:${edge.importSpecifier ?? ""}`;
  };

  const originalNormalized = originalEdges.map(normalizeEdge).sort();
  const reimportedNormalized = reimportedEdges.map(normalizeEdge).sort();

  return JSON.stringify(originalNormalized) === JSON.stringify(reimportedNormalized);
}

function normalizeRecord(record: Record<string, string> | undefined): Record<string, string> {
  if (record === undefined) return {};
  const entries = Object.entries(record).sort(([left], [right]) => left.localeCompare(right));
  return Object.fromEntries(entries);
}

function normalizeSourceImportShape(sourceImports: LogicalArtifactModel["nodes"][string]["sourceImports"]): readonly string[] {
  if (sourceImports === undefined) return [];
  return sourceImports
    .map((record) => `${record.isTypeOnly ? "type" : "value"}:${record.moduleSpecifier}:${record.importClauseText ?? ""}`)
    .sort((left, right) => left.localeCompare(right));
}

function nodeEdgeShape(model: LogicalArtifactModel, nodeId: string): readonly string[] {
  return model.edges
    .filter((edge) => edge.fromId === nodeId)
    .map((edge) => `${edge.kind}:${edge.toId}:${edge.importSpecifier ?? ""}`)
    .sort((left, right) => left.localeCompare(right));
}
