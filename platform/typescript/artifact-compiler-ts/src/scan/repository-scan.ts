/**
 * @fileoverview Repository-scale scan facade for TypeScript/TSX source sets.
 *
 * Builds a contract-level ScanResult from many source entries by inventorying
 * files, parsing supported TypeScript sources, decompiling them into one
 * LogicalArtifactModel, and attaching fidelity/residual evidence.
 */

import * as ts from "typescript";
import type {
  ArtifactEdge,
  FileScanResult,
  LogicalArtifactModel,
  ScanResult,
  SourceFile,
  SourceFileKind,
} from "@ghatana/artifact-contracts";
import { createLogicalArtifactModel, createPerfectFidelityReport } from "@ghatana/artifact-contracts";
import { decompileTsx, type DecompileSourceFile } from "../decompile/tsx.js";
import { detectResidualIslands, type ParsedSourceFile } from "../residual/residual-islands.js";

export interface RepositoryScanSourceEntry {
  readonly relativePath: string;
  readonly content: string;
  readonly contentType?: string;
  readonly sizeBytes?: number;
}

export interface RepositoryScanOptions {
  readonly scanJobId: string;
  readonly modelId: string;
  readonly label: string;
  readonly designSystemComponentNames?: ReadonlySet<string>;
}

export interface RepositoryScanOutput {
  readonly result: ScanResult;
  readonly model: LogicalArtifactModel;
}

interface PackageManifestIntelligence {
  readonly workspacePackageCount: number;
  readonly workspaceDependencyCount: number;
  readonly workspaceScriptCount: number;
}

const SUPPORTED_EXTENSIONS = new Set([".ts", ".tsx", ".js", ".jsx"]);

export function scanRepositorySources(
  sources: readonly RepositoryScanSourceEntry[],
  options: RepositoryScanOptions,
): RepositoryScanOutput {
  const startedAt = Date.now();
  const fileResults: FileScanResult[] = [];
  const decompileFiles: DecompileSourceFile[] = [];
  const parsedFiles: ParsedSourceFile[] = [];
  const importStatements: ImportStatement[] = [];
  const intelligenceRecords: RepositoryIntelligenceRecord[] = [];
  let packageManifestIntelligence: PackageManifestIntelligence = {
    workspacePackageCount: 0,
    workspaceDependencyCount: 0,
    workspaceScriptCount: 0,
  };

  for (const source of sources) {
    packageManifestIntelligence = mergePackageManifestIntelligence(
      packageManifestIntelligence,
      collectPackageManifestIntelligence(source),
    );
    const sourceFile = toSourceFile(source);
    const parseStartedAt = Date.now();
    const isSupported = isSupportedSourcePath(source.relativePath);

    if (!isSupported) {
      fileResults.push({
        sourceFile,
        parsed: false,
        parseError: `Unsupported source extension for "${source.relativePath}".`,
        parseDurationMs: Date.now() - parseStartedAt,
      });
      continue;
    }

    const parsed = ts.createSourceFile(
      source.relativePath,
      source.content,
      ts.ScriptTarget.Latest,
      true,
      source.relativePath.endsWith(".tsx") || source.relativePath.endsWith(".jsx")
        ? ts.ScriptKind.TSX
        : ts.ScriptKind.TS,
    );
    const parseDiagnostics = (parsed as ts.SourceFile & {
      readonly parseDiagnostics?: readonly ts.Diagnostic[];
    }).parseDiagnostics ?? [];
    const parseError = parseDiagnostics[0];

    if (parseError !== undefined) {
      fileResults.push({
        sourceFile,
        parsed: false,
        parseError: ts.flattenDiagnosticMessageText(parseError.messageText, "\n"),
        parseDurationMs: Date.now() - parseStartedAt,
      });
      continue;
    }

    fileResults.push({
      sourceFile,
      parsed: true,
      parseDurationMs: Date.now() - parseStartedAt,
    });
    decompileFiles.push({
      relativePath: source.relativePath,
      content: source.content,
    });
    parsedFiles.push({
      relativePath: source.relativePath,
      sourceFile: parsed,
    });
    importStatements.push(...collectImportStatements(source.relativePath, parsed));
    intelligenceRecords.push(collectRepositoryIntelligence(parsed));
  }

  const decompiled = decompileFiles.length > 0
    ? decompileTsx({
        label: options.label,
        modelId: options.modelId,
        files: decompileFiles,
        designSystemComponentNames: options.designSystemComponentNames,
      })
    : {
        model: createLogicalArtifactModel(options.modelId, options.label),
        fidelityReport: {
          ...createPerfectFidelityReport(options.modelId),
          scope: "pipeline" as const,
        },
      };

  const residuals = detectResidualIslands(decompiled.model, { parsedFiles });
  const graph = buildRepositoryImportGraph({
    model: decompiled.model,
    sources,
    imports: importStatements,
  });
  const intelligence = aggregateRepositoryIntelligence(intelligenceRecords);

  const modelWithGraph: LogicalArtifactModel = {
    ...decompiled.model,
    edges: mergeUniqueEdges(decompiled.model.edges, graph.edges),
    metadata: {
      ...decompiled.model.metadata,
      repositoryGraph: {
        resolvedImportCount: graph.resolvedImportCount,
        unresolvedImportCount: graph.unresolvedImportCount,
        routeDeclarationCount: intelligence.routeDeclarationCount,
        componentUsageCount: intelligence.componentUsageCount,
        apiCallCount: intelligence.apiCallCount,
        designTokenReferenceCount: intelligence.designTokenReferenceCount,
        routeConfigObjectCount: intelligence.routeConfigObjectCount,
        routeConfigMaxDepth: intelligence.routeConfigMaxDepth,
        workspacePackageCount: packageManifestIntelligence.workspacePackageCount,
        workspaceDependencyCount: packageManifestIntelligence.workspaceDependencyCount,
        workspaceScriptCount: packageManifestIntelligence.workspaceScriptCount,
      },
    },
  };

  const result: ScanResult = {
    scanJobId: options.scanJobId,
    modelId: options.modelId,
    files: fileResults,
    fidelity: decompiled.fidelityReport,
    residuals,
    completedAt: new Date().toISOString(),
    durationMs: Math.max(1, Date.now() - startedAt),
  };

  return {
    result,
    model: modelWithGraph,
  };
}

interface ImportStatement {
  readonly fromPath: string;
  readonly moduleSpecifier: string;
  readonly isTypeOnly: boolean;
}

interface RepositoryIntelligenceRecord {
  readonly routeDeclarationCount: number;
  readonly componentUsageCount: number;
  readonly apiCallCount: number;
  readonly designTokenReferenceCount: number;
  readonly routeConfigObjectCount: number;
  readonly routeConfigMaxDepth: number;
}

function collectImportStatements(relativePath: string, sourceFile: ts.SourceFile): ImportStatement[] {
  const imports: ImportStatement[] = [];
  for (const statement of sourceFile.statements) {
    if (!ts.isImportDeclaration(statement) || !ts.isStringLiteral(statement.moduleSpecifier)) {
      continue;
    }
    imports.push({
      fromPath: relativePath,
      moduleSpecifier: statement.moduleSpecifier.text,
      isTypeOnly: statement.importClause?.isTypeOnly ?? false,
    });
  }
  return imports;
}

function collectRepositoryIntelligence(sourceFile: ts.SourceFile): RepositoryIntelligenceRecord {
  let routeDeclarationCount = 0;
  let componentUsageCount = 0;
  let apiCallCount = 0;
  let designTokenReferenceCount = 0;
  let routeConfigObjectCount = 0;
  let routeConfigMaxDepth = 0;

  const visit = (node: ts.Node): void => {
    if (ts.isJsxOpeningLikeElement(node)) {
      const tag = node.tagName.getText(sourceFile);
      if (tag === 'Route') {
        routeDeclarationCount += 1;
      }
      if (/^[A-Z]/.test(tag)) {
        componentUsageCount += 1;
      }
    }

    if (ts.isVariableDeclaration(node) && isRouteConfigIdentifier(node.name.getText(sourceFile))) {
      const initializer = node.initializer;
      if (initializer && (ts.isArrayLiteralExpression(initializer) || ts.isObjectLiteralExpression(initializer))) {
        routeConfigObjectCount += 1;
        routeConfigMaxDepth = Math.max(routeConfigMaxDepth, objectGraphDepth(initializer));
      }
    }

    if (ts.isCallExpression(node)) {
      if (ts.isIdentifier(node.expression) && node.expression.text === 'fetch') {
        apiCallCount += 1;
      }

      if (ts.isIdentifier(node.expression) && isRouteConfigFactory(node.expression.text)) {
        const firstArgument = node.arguments[0];
        if (firstArgument && (ts.isArrayLiteralExpression(firstArgument) || ts.isObjectLiteralExpression(firstArgument))) {
          routeConfigObjectCount += 1;
          routeConfigMaxDepth = Math.max(routeConfigMaxDepth, objectGraphDepth(firstArgument));
        }
      }

      if (ts.isPropertyAccessExpression(node.expression)) {
        const objectText = node.expression.expression.getText(sourceFile);
        const methodText = node.expression.name.getText(sourceFile);
        if (objectText === 'axios' || objectText.endsWith('Client') || objectText.endsWith('Api')) {
          if (['get', 'post', 'put', 'patch', 'delete', 'request'].includes(methodText)) {
            apiCallCount += 1;
          }
        }
      }
    }

    if (ts.isStringLiteralLike(node)) {
      const text = node.text;
      if (/\btoken\b/i.test(text) || /var\(--/i.test(text) || /theme\./i.test(text)) {
        designTokenReferenceCount += 1;
      }
    }

    ts.forEachChild(node, visit);
  };

  ts.forEachChild(sourceFile, visit);

  return {
    routeDeclarationCount,
    componentUsageCount,
    apiCallCount,
    designTokenReferenceCount,
    routeConfigObjectCount,
    routeConfigMaxDepth,
  };
}

function aggregateRepositoryIntelligence(records: readonly RepositoryIntelligenceRecord[]): RepositoryIntelligenceRecord {
  return records.reduce<RepositoryIntelligenceRecord>(
    (acc, record) => ({
      routeDeclarationCount: acc.routeDeclarationCount + record.routeDeclarationCount,
      componentUsageCount: acc.componentUsageCount + record.componentUsageCount,
      apiCallCount: acc.apiCallCount + record.apiCallCount,
      designTokenReferenceCount: acc.designTokenReferenceCount + record.designTokenReferenceCount,
      routeConfigObjectCount: acc.routeConfigObjectCount + record.routeConfigObjectCount,
      routeConfigMaxDepth: Math.max(acc.routeConfigMaxDepth, record.routeConfigMaxDepth),
    }),
    {
      routeDeclarationCount: 0,
      componentUsageCount: 0,
      apiCallCount: 0,
      designTokenReferenceCount: 0,
      routeConfigObjectCount: 0,
      routeConfigMaxDepth: 0,
    },
  );
}

function isRouteConfigFactory(identifier: string): boolean {
  return identifier === 'createBrowserRouter' || identifier === 'createHashRouter' || identifier === 'createMemoryRouter';
}

function isRouteConfigIdentifier(identifier: string): boolean {
  return /route/i.test(identifier);
}

function objectGraphDepth(node: ts.ObjectLiteralExpression | ts.ArrayLiteralExpression): number {
  const depth = (current: ts.Node): number => {
    if (ts.isObjectLiteralExpression(current)) {
      const childDepths = current.properties.flatMap((property) => {
        if (!ts.isPropertyAssignment(property)) return [1];
        return [1 + depth(property.initializer)];
      });
      return childDepths.length > 0 ? Math.max(...childDepths) : 1;
    }

    if (ts.isArrayLiteralExpression(current)) {
      const childDepths = current.elements.map((element) => 1 + depth(element));
      return childDepths.length > 0 ? Math.max(...childDepths) : 1;
    }

    return 0;
  };

  return depth(node);
}

function collectPackageManifestIntelligence(source: RepositoryScanSourceEntry): PackageManifestIntelligence {
  const normalized = normalizeRelativePath(source.relativePath).toLowerCase();
  if (!normalized.endsWith('/package.json') && normalized !== 'package.json') {
    return {
      workspacePackageCount: 0,
      workspaceDependencyCount: 0,
      workspaceScriptCount: 0,
    };
  }

  try {
    const parsed = JSON.parse(source.content) as {
      readonly dependencies?: Record<string, string>;
      readonly devDependencies?: Record<string, string>;
      readonly peerDependencies?: Record<string, string>;
      readonly optionalDependencies?: Record<string, string>;
      readonly scripts?: Record<string, string>;
    };

    const dependencyCount =
      Object.keys(parsed.dependencies ?? {}).length +
      Object.keys(parsed.devDependencies ?? {}).length +
      Object.keys(parsed.peerDependencies ?? {}).length +
      Object.keys(parsed.optionalDependencies ?? {}).length;

    return {
      workspacePackageCount: 1,
      workspaceDependencyCount: dependencyCount,
      workspaceScriptCount: Object.keys(parsed.scripts ?? {}).length,
    };
  } catch {
    return {
      workspacePackageCount: 0,
      workspaceDependencyCount: 0,
      workspaceScriptCount: 0,
    };
  }
}

function mergePackageManifestIntelligence(
  left: PackageManifestIntelligence,
  right: PackageManifestIntelligence,
): PackageManifestIntelligence {
  return {
    workspacePackageCount: left.workspacePackageCount + right.workspacePackageCount,
    workspaceDependencyCount: left.workspaceDependencyCount + right.workspaceDependencyCount,
    workspaceScriptCount: left.workspaceScriptCount + right.workspaceScriptCount,
  };
}

function buildRepositoryImportGraph(options: {
  readonly model: LogicalArtifactModel;
  readonly sources: readonly RepositoryScanSourceEntry[];
  readonly imports: readonly ImportStatement[];
}): { readonly edges: readonly ArtifactEdge[]; readonly resolvedImportCount: number; readonly unresolvedImportCount: number } {
  const nodeIds = new Set(Object.keys(options.model.nodes));
  const absoluteSourcePaths = new Set(options.sources.map((source) => toAbsolutePath(source.relativePath)));
  let resolvedImportCount = 0;
  let unresolvedImportCount = 0;

  const moduleResolutionHost: ts.ModuleResolutionHost = {
    fileExists: (fileName: string): boolean => absoluteSourcePaths.has(normalizeAbsolutePath(fileName)),
    readFile: (): string | undefined => undefined,
    directoryExists: (): boolean => true,
    realpath: (fileName: string): string => normalizeAbsolutePath(fileName),
    getCurrentDirectory: (): string => "/",
  };

  const compilerOptions: ts.CompilerOptions = {
    allowJs: true,
    moduleResolution: ts.ModuleResolutionKind.Bundler,
  };

  const edges: ArtifactEdge[] = [];
  for (const importStatement of options.imports) {
    const resolvedPath = resolveImportSpecifier(
      importStatement,
      compilerOptions,
      moduleResolutionHost,
      absoluteSourcePaths,
    );
    if (resolvedPath === null || !nodeIds.has(resolvedPath)) {
      unresolvedImportCount += 1;
      continue;
    }

    resolvedImportCount += 1;
    edges.push({
      id: `${importStatement.fromPath}->${resolvedPath}:${importStatement.moduleSpecifier}:${importStatement.isTypeOnly ? "type" : "value"}`,
      fromId: importStatement.fromPath,
      toId: resolvedPath,
      kind: importStatement.isTypeOnly ? "type-only" : "import",
      importSpecifier: importStatement.moduleSpecifier,
    });
  }

  return { edges, resolvedImportCount, unresolvedImportCount };
}

function resolveImportSpecifier(
  importStatement: ImportStatement,
  compilerOptions: ts.CompilerOptions,
  moduleResolutionHost: ts.ModuleResolutionHost,
  absoluteSourcePaths: ReadonlySet<string>,
): string | null {
  const containingFile = toAbsolutePath(importStatement.fromPath);
  const tsResolved = ts.resolveModuleName(
    importStatement.moduleSpecifier,
    containingFile,
    compilerOptions,
    moduleResolutionHost,
  ).resolvedModule?.resolvedFileName;

  const tsRelative = fromAbsolutePath(tsResolved);
  if (tsRelative !== null && absoluteSourcePaths.has(toAbsolutePath(tsRelative))) {
    return tsRelative;
  }

  if (!importStatement.moduleSpecifier.startsWith(".")) {
    return null;
  }

  const containingDir = normalizeRelativePath(importStatement.fromPath).split('/').slice(0, -1).join('/') || '/';
  const candidateBase = normalizeRelativePath(`${containingDir}/${importStatement.moduleSpecifier}`);
  const manualCandidates = [
    candidateBase,
    `${candidateBase}.ts`,
    `${candidateBase}.tsx`,
    `${candidateBase}.js`,
    `${candidateBase}.jsx`,
    `${candidateBase}/index.ts`,
    `${candidateBase}/index.tsx`,
    `${candidateBase}/index.js`,
    `${candidateBase}/index.jsx`,
  ];

  for (const candidate of manualCandidates) {
    if (absoluteSourcePaths.has(toAbsolutePath(candidate))) {
      return normalizeRelativePath(candidate);
    }
  }

  return null;
}

function normalizeRelativePath(value: string): string {
  return value.replace(/^\/+/, "").replace(/\\/g, "/");
}

function normalizeAbsolutePath(value: string): string {
  const withForwardSlashes = value.replace(/\\/g, "/");
  if (withForwardSlashes.startsWith("/")) {
    return withForwardSlashes;
  }
  return `/${withForwardSlashes}`;
}

function toAbsolutePath(relativePath: string): string {
  return normalizeAbsolutePath(normalizeRelativePath(relativePath));
}

function fromAbsolutePath(value: string | undefined): string | null {
  if (value === undefined) return null;
  return normalizeRelativePath(value.replace(/^(file:\/\/)?\/+/, ""));
}

function mergeUniqueEdges(existing: readonly ArtifactEdge[], discovered: readonly ArtifactEdge[]): ArtifactEdge[] {
  const merged = new Map<string, ArtifactEdge>();
  for (const edge of existing) {
    merged.set(edge.id, edge);
  }
  for (const edge of discovered) {
    merged.set(edge.id, edge);
  }
  return [...merged.values()];
}

function toSourceFile(source: RepositoryScanSourceEntry): SourceFile {
  return {
    relativePath: source.relativePath,
    contentType: source.contentType ?? inferContentType(source.relativePath),
    kind: inferSourceFileKind(source.relativePath),
    sizeBytes: source.sizeBytes ?? new TextEncoder().encode(source.content).byteLength,
    language: inferLanguage(source.relativePath),
  };
}

function isSupportedSourcePath(relativePath: string): boolean {
  return [...SUPPORTED_EXTENSIONS].some((extension) => relativePath.endsWith(extension));
}

function inferContentType(relativePath: string): string {
  if (relativePath.endsWith(".tsx") || relativePath.endsWith(".ts")) return "text/typescript";
  if (relativePath.endsWith(".jsx") || relativePath.endsWith(".js")) return "text/javascript";
  if (relativePath.endsWith(".css")) return "text/css";
  if (relativePath.endsWith(".json")) return "application/json";
  return "text/plain";
}

function inferLanguage(relativePath: string): string | undefined {
  if (relativePath.endsWith(".tsx") || relativePath.endsWith(".ts")) return "typescript";
  if (relativePath.endsWith(".jsx") || relativePath.endsWith(".js")) return "javascript";
  if (relativePath.endsWith(".css")) return "css";
  if (relativePath.endsWith(".json")) return "json";
  return undefined;
}

function inferSourceFileKind(relativePath: string): SourceFileKind {
  const lower = relativePath.toLowerCase();
  if (lower.includes(".test.") || lower.includes(".spec.")) return "test";
  if (lower.includes(".stories.")) return "story";
  if (lower.endsWith(".css") || lower.endsWith(".scss")) return "style";
  if (lower.endsWith(".json")) return "schema";
  if (lower.endsWith(".config.ts") || lower.endsWith(".config.js")) return "config";
  if (/\/(pages?|routes?|views?)\//i.test(lower)) return "page";
  if (/\/hooks?\//i.test(lower)) return "hook";
  if (/\/types?\//i.test(lower) || lower.endsWith(".types.ts") || lower.endsWith(".d.ts")) return "type";
  if (/\/(utils?|helpers?|lib|services?|store)\//i.test(lower)) return "utility";
  if (lower.endsWith(".tsx") || lower.endsWith(".jsx")) return "component";
  return "unknown";
}
