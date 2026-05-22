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
  type RoundTripParitySection,
  type ValidationPipelineResult,
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
  readonly validation?: ValidationPipelineResult;
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
  const paritySections = buildRoundTripParitySections({
    model: options.model,
    reimportedModel: options.reimportedModel,
    originalSources: options.originalSources,
    generatedSources: options.generatedSources,
    diffs,
    validation: options.validation,
  });

  return {
    reportId: options.reportId,
    modelId: options.model.modelId,
    diffs,
    fidelity: options.fidelity ?? computeFidelityReport([], options.model.modelId, "pipeline"),
    residuals: options.residuals ?? createResidualIslandReport([]),
    ...(options.validation === undefined ? {} : { validation: options.validation }),
    paritySections,
    isLossless: diffs.every((diff) => diff.semanticallyEquivalent && diff.hunks.length === 0) &&
      (options.validation?.passed ?? true),
    generatedAt: new Date().toISOString(),
  };
}

export function createNotRunValidationPipelineResult(params: {
  readonly targetId: string;
  readonly reason: string;
}): ValidationPipelineResult {
  return {
    targetId: params.targetId,
    passed: false,
    findings: [
      {
        code: 'validation/not-run',
        message: params.reason,
        severity: 'warning',
        category: 'other',
        suggestion: 'Run generated project install, typecheck, lint, test, and build gates before marking the round-trip production-ready.',
      },
    ],
    errorCount: 0,
    warningCount: 1,
    infoCount: 0,
    validatedAt: new Date().toISOString(),
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

function buildRoundTripParitySections(options: {
  readonly model: LogicalArtifactModel;
  readonly reimportedModel?: LogicalArtifactModel;
  readonly originalSources: readonly RoundTripDiffSourceFile[];
  readonly generatedSources: readonly RoundTripDiffSourceFile[];
  readonly diffs: readonly DiffRecord[];
  readonly validation?: ValidationPipelineResult;
}): RoundTripParitySection[] {
  const semanticFailures = options.diffs
    .filter((diff) => !diff.semanticallyEquivalent)
    .map((diff) => `${diff.originalPath} -> ${diff.generatedPath}`);
  const importGraphFailures = buildImportGraphFindings(options.model, options.reimportedModel);
  const componentFindings = buildComponentParityFindings(options.model, options.reimportedModel);
  const apiFindings = buildApiParityFindings(options.model, options.reimportedModel);
  const designTokenFindings = buildDesignTokenParityFindings(options.originalSources, options.generatedSources);

  return [
    {
      kind: 'ast-semantic',
      status: semanticFailures.length === 0 ? 'passed' : 'failed',
      summary: semanticFailures.length === 0
        ? 'AST semantic signatures are equivalent for all generated files.'
        : 'AST semantic signature drift was detected.',
      findings: [...semanticFailures],
    },
    {
      kind: 'import-graph',
      status: options.reimportedModel === undefined ? 'not-run' : importGraphFailures.length === 0 ? 'passed' : 'failed',
      summary: options.reimportedModel === undefined
        ? 'Import graph parity requires a re-imported model.'
        : importGraphFailures.length === 0
          ? 'Import graph edges are preserved across re-import.'
          : 'Import graph drift was detected across re-import.',
      findings: [...importGraphFailures],
    },
    {
      kind: 'component',
      status: options.reimportedModel === undefined ? 'not-run' : componentFindings.length === 0 ? 'passed' : 'failed',
      summary: options.reimportedModel === undefined
        ? 'Component parity requires a re-imported model.'
        : componentFindings.length === 0
          ? 'Component node identities and display names are preserved.'
          : 'Component node drift was detected.',
      findings: [...componentFindings],
    },
    {
      kind: 'api',
      status: options.reimportedModel === undefined ? 'not-run' : apiFindings.length === 0 ? 'passed' : 'failed',
      summary: options.reimportedModel === undefined
        ? 'API parity requires a re-imported model.'
        : apiFindings.length === 0
          ? 'Exported symbols and inferred prop contracts are preserved.'
          : 'API contract drift was detected.',
      findings: [...apiFindings],
    },
    {
      kind: 'design-token',
      status: designTokenFindings.length === 0 ? 'passed' : 'warning',
      summary: designTokenFindings.length === 0
        ? 'Design token and CSS variable references are preserved.'
        : 'Design token or CSS variable reference drift was detected.',
      findings: [...designTokenFindings],
    },
    {
      kind: 'validation',
      status: options.validation === undefined
        ? 'not-run'
        : options.validation.passed
          ? 'passed'
          : options.validation.errorCount > 0
            ? 'failed'
            : 'warning',
      summary: options.validation === undefined
        ? 'Generated artifact validation was not run.'
        : options.validation.passed
          ? 'Generated artifact validation passed.'
          : 'Generated artifact validation reported findings.',
      findings: options.validation?.findings.map((finding) => `${finding.severity}:${finding.code}:${finding.message}`) ?? [],
    },
  ];
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
            const attributeName = jsxAttributeNameText(attribute.name, sourceFile);
            jsxAttributeNames.push(attributeName);
            if (/^on[A-Z]/.test(attributeName)) {
              jsxEventHandlerNames.push(attributeName);
            }
            if (attribute.initializer && ts.isJsxExpression(attribute.initializer) && attribute.initializer.expression) {
              jsxBindingExpressions.push(`${attributeName}:${normalizeSource(attribute.initializer.expression.getText(sourceFile))}`);
            }
            if (attributeName === 'className' || attributeName === 'style') {
              styleReferences.push(attribute.getText(sourceFile));
            }
          }
        });
      }

      if (ts.isJsxSelfClosingElement(node)) {
        jsxNodeKinds.push(node.tagName.getText(sourceFile));
        node.attributes.properties.forEach((attribute) => {
          if (ts.isJsxAttribute(attribute)) {
            const attributeName = jsxAttributeNameText(attribute.name, sourceFile);
            jsxAttributeNames.push(attributeName);
            if (/^on[A-Z]/.test(attributeName)) {
              jsxEventHandlerNames.push(attributeName);
            }
            if (attribute.initializer && ts.isJsxExpression(attribute.initializer) && attribute.initializer.expression) {
              jsxBindingExpressions.push(`${attributeName}:${normalizeSource(attribute.initializer.expression.getText(sourceFile))}`);
            }
            if (attributeName === 'className' || attributeName === 'style') {
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

function buildImportGraphFindings(
  originalModel: LogicalArtifactModel,
  reimportedModel: LogicalArtifactModel | undefined,
): readonly string[] {
  if (reimportedModel === undefined) return [];
  const nodeIds = Object.keys(originalModel.nodes);
  const mismatches = nodeIds
    .filter((nodeId) => !isImportGraphEquivalent(originalModel, reimportedModel, nodeId));

  const originalEdgeCount = originalModel.edges.length;
  const reimportedEdgeCount = reimportedModel.edges.length;
  const findings = [
    `Import graph parity summary: checked ${nodeIds.length} node(s), ${mismatches.length} mismatch(es), original edges ${originalEdgeCount}, re-imported edges ${reimportedEdgeCount}.`,
  ];

  findings.push(...mismatches.map((nodeId) => `Import graph differs for node "${nodeId}".`));
  return findings;
}

function buildComponentParityFindings(
  originalModel: LogicalArtifactModel,
  reimportedModel: LogicalArtifactModel | undefined,
): readonly string[] {
  if (reimportedModel === undefined) return [];
  const findings: string[] = [];
  const originalNodeIds = Object.keys(originalModel.nodes).sort((left, right) => left.localeCompare(right));
  const reimportedNodeIds = Object.keys(reimportedModel.nodes).sort((left, right) => left.localeCompare(right));
  for (const nodeId of originalNodeIds) {
    const originalNode = originalModel.nodes[nodeId];
    const reimportedNode = reimportedModel.nodes[nodeId];
    if (originalNode === undefined) continue;
    if (reimportedNode === undefined) {
      findings.push(`Component node "${nodeId}" is missing after re-import.`);
      continue;
    }
    if (originalNode.kind !== reimportedNode.kind) {
      findings.push(`Component node "${nodeId}" kind changed from "${originalNode.kind}" to "${reimportedNode.kind}".`);
    }
    if (originalNode.displayName !== reimportedNode.displayName) {
      findings.push(`Component node "${nodeId}" displayName changed from "${originalNode.displayName}" to "${reimportedNode.displayName}".`);
    }
  }
  for (const nodeId of reimportedNodeIds) {
    if (originalModel.nodes[nodeId] === undefined) {
      findings.push(`Component node "${nodeId}" was introduced during re-import.`);
    }
  }
  return findings;
}

function buildApiParityFindings(
  originalModel: LogicalArtifactModel,
  reimportedModel: LogicalArtifactModel | undefined,
): readonly string[] {
  if (reimportedModel === undefined) return [];
  const findings: string[] = [];
  for (const nodeId of Object.keys(originalModel.nodes).sort((left, right) => left.localeCompare(right))) {
    const originalNode = originalModel.nodes[nodeId];
    const reimportedNode = reimportedModel.nodes[nodeId];
    if (originalNode === undefined || reimportedNode === undefined) continue;
    const originalExports = JSON.stringify([...originalNode.exportedSymbols].sort());
    const reimportedExports = JSON.stringify([...reimportedNode.exportedSymbols].sort());
    if (originalExports !== reimportedExports) {
      findings.push(`Exported symbols differ for node "${nodeId}".`);
    }
    if (JSON.stringify(normalizeRecord(originalNode.inferredProps)) !== JSON.stringify(normalizeRecord(reimportedNode.inferredProps))) {
      findings.push(`Inferred prop contract differs for node "${nodeId}".`);
    }
    if (JSON.stringify(normalizeSourceImportShape(originalNode.sourceImports)) !== JSON.stringify(normalizeSourceImportShape(reimportedNode.sourceImports))) {
      findings.push(`Source import contract differs for node "${nodeId}".`);
    }
  }
  return findings;
}

function buildDesignTokenParityFindings(
  originalSources: readonly RoundTripDiffSourceFile[],
  generatedSources: readonly RoundTripDiffSourceFile[],
): readonly string[] {
  const originalTokens = collectDesignTokenReferences(originalSources);
  const generatedTokens = collectDesignTokenReferences(generatedSources);
  const findings: string[] = [];
  for (const token of originalTokens) {
    if (!generatedTokens.has(token)) {
      findings.push(`Design token reference "${token}" is missing from generated sources.`);
    }
  }
  for (const token of generatedTokens) {
    if (!originalTokens.has(token)) {
      findings.push(`Design token reference "${token}" was introduced in generated sources.`);
    }
  }
  return findings;
}

function collectDesignTokenReferences(sources: readonly RoundTripDiffSourceFile[]): ReadonlySet<string> {
  const tokens = new Set<string>();
  const tokenPattern = /\b(?:token-[a-z0-9_-]+|var\(--[a-z0-9_-]+\))/gi;
  for (const source of sources) {
    const matches = source.content.matchAll(tokenPattern);
    for (const match of matches) {
      tokens.add(match[0]);
    }
  }
  return tokens;
}

function normalizeRecord(record: Record<string, string> | undefined): Record<string, string> {
  if (record === undefined) return {};
  const entries = Object.entries(record).sort(([left], [right]) => left.localeCompare(right));
  return Object.fromEntries(entries);
}

function jsxAttributeNameText(name: ts.JsxAttributeName, sourceFile: ts.SourceFile): string {
  return ts.isIdentifier(name) ? name.text : name.getText(sourceFile);
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
