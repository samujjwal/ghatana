/**
 * @fileoverview Repository-scale scan facade for TypeScript/TSX source sets.
 *
 * Builds a contract-level ScanResult from many source entries by inventorying
 * files, parsing supported TypeScript sources, decompiling them into one
 * LogicalArtifactModel, and attaching fidelity/residual evidence.
 */

import * as ts from "typescript";
import type {
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

const SUPPORTED_EXTENSIONS = new Set([".ts", ".tsx", ".js", ".jsx"]);

export function scanRepositorySources(
  sources: readonly RepositoryScanSourceEntry[],
  options: RepositoryScanOptions,
): RepositoryScanOutput {
  const startedAt = Date.now();
  const fileResults: FileScanResult[] = [];
  const decompileFiles: DecompileSourceFile[] = [];
  const parsedFiles: ParsedSourceFile[] = [];

  for (const source of sources) {
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
    model: decompiled.model,
  };
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
