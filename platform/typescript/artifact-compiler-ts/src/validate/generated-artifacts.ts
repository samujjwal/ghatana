/**
 * @fileoverview Generated artifact validation pipeline.
 *
 * Runs a deterministic TypeScript syntax/type validation pass over generated
 * sources using an in-memory compiler host. This is intentionally side-effect
 * free so Studio can execute it in the browser, while backend runners can add
 * install/lint/test/build stages to the same ValidationPipelineResult contract.
 */

import ts from "typescript";
import type {
  SourceRef,
  ValidationFinding,
  ValidationPipelineResult,
} from "@ghatana/artifact-contracts";

export interface GeneratedArtifactValidationSource {
  readonly relativePath: string;
  readonly content: string;
}

export interface ValidateGeneratedArtifactsOptions {
  readonly targetId: string;
  readonly generatedSources: readonly GeneratedArtifactValidationSource[];
  readonly now?: () => Date;
}

const AMBIENT_FILE = "/__ghatana_validation__/ambient.d.ts";

const AMBIENT_TYPES = `
interface Array<T> { readonly length: number; [n: number]: T; }
interface Boolean {}
interface CallableFunction extends Function {}
interface Function {}
interface IArguments {}
interface NewableFunction extends Function {}
interface Number {}
interface Object {}
interface RegExp {}
interface String {}
interface ReadonlyArray<T> { readonly length: number; readonly [n: number]: T; }
type Record<K extends keyof any, T> = { [P in K]: T };
declare module "react" {
  export interface ReactElement {}
  export type ReactNode = unknown;
}
declare namespace JSX {
  interface IntrinsicElements {
    readonly [elementName: string]: unknown;
  }
  type Element = import("react").ReactElement;
}
`.trim();

const COMPILER_OPTIONS: ts.CompilerOptions = {
  allowJs: false,
  esModuleInterop: true,
  isolatedModules: false,
  jsx: ts.JsxEmit.Preserve,
  module: ts.ModuleKind.ESNext,
  moduleResolution: ts.ModuleResolutionKind.Bundler,
  noEmit: true,
  noLib: true,
  noResolve: false,
  skipLibCheck: true,
  strict: true,
  target: ts.ScriptTarget.ES2022,
};

export function validateGeneratedArtifacts(
  options: ValidateGeneratedArtifactsOptions,
): ValidationPipelineResult {
  const startedAt = Date.now();
  const now = options.now ?? (() => new Date());
  const files = new Map<string, string>();
  files.set(AMBIENT_FILE, AMBIENT_TYPES);

  for (const source of options.generatedSources) {
    files.set(normalizeCompilerPath(source.relativePath), source.content);
  }

  const host = createVirtualCompilerHost(files);
  const rootNames = [...files.keys()];
  const program = ts.createProgram(rootNames, COMPILER_OPTIONS, host);
  const diagnostics = [
    ...program.getOptionsDiagnostics(),
    ...program.getSyntacticDiagnostics(),
    ...program.getSemanticDiagnostics(),
  ];

  const findings = diagnostics
    .filter((diagnostic) => diagnostic.file?.fileName !== AMBIENT_FILE)
    .map(diagnosticToFinding);

  const errorCount = findings.filter((finding) => finding.severity === "error").length;
  const warningCount = findings.filter((finding) => finding.severity === "warning").length;
  const infoCount = findings.filter((finding) => finding.severity === "info").length;

  return {
    targetId: options.targetId,
    passed: errorCount === 0,
    findings,
    errorCount,
    warningCount,
    infoCount,
    validatedAt: now().toISOString(),
    durationMs: Date.now() - startedAt,
  };
}

function createVirtualCompilerHost(files: ReadonlyMap<string, string>): ts.CompilerHost {
  const fileExists = (fileName: string): boolean => files.has(normalizeCompilerPath(fileName));
  const readFile = (fileName: string): string | undefined => files.get(normalizeCompilerPath(fileName));

  return {
    fileExists,
    getCanonicalFileName: (fileName) => normalizeCompilerPath(fileName),
    getCurrentDirectory: () => "/",
    getDefaultLibFileName: () => AMBIENT_FILE,
    getDirectories: () => [],
    getNewLine: () => "\n",
    getSourceFile: (fileName, languageVersion) => {
      const normalized = normalizeCompilerPath(fileName);
      const content = files.get(normalized);
      if (content === undefined) {
        return undefined;
      }
      return ts.createSourceFile(normalized, content, languageVersion, true, scriptKindForPath(normalized));
    },
    readFile,
    resolveModuleNames: (moduleNames, containingFile) => moduleNames.map((moduleName) => {
      if (moduleName === "react") {
        return {
          resolvedFileName: AMBIENT_FILE,
          extension: ts.Extension.Dts,
          isExternalLibraryImport: true,
        };
      }

      const resolved = resolveRelativeModule(moduleName, containingFile, files);
      return resolved
        ? { resolvedFileName: resolved, extension: scriptExtensionForPath(resolved) }
        : undefined;
    }),
    useCaseSensitiveFileNames: () => true,
    writeFile: () => undefined,
  };
}

function resolveRelativeModule(
  moduleName: string,
  containingFile: string,
  files: ReadonlyMap<string, string>,
): string | undefined {
  if (!moduleName.startsWith(".")) {
    return undefined;
  }
  const containingDir = normalizeCompilerPath(containingFile).split("/").slice(0, -1).join("/");
  const base = normalizeCompilerPath(`${containingDir}/${moduleName}`);
  const candidates = [
    base,
    `${base}.ts`,
    `${base}.tsx`,
    `${base}.d.ts`,
    `${base}/index.ts`,
    `${base}/index.tsx`,
    `${base}/index.d.ts`,
  ];
  return candidates.find((candidate) => files.has(candidate));
}

function diagnosticToFinding(diagnostic: ts.Diagnostic): ValidationFinding {
  const sourceRef = diagnostic.file
    ? sourceRefForDiagnostic(diagnostic.file, diagnostic.start, diagnostic.length)
    : undefined;

  return {
    code: `TS${diagnostic.code}`,
    message: ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n"),
    severity: diagnostic.category === ts.DiagnosticCategory.Error ? "error" : "warning",
    category: "typescript",
    ...(sourceRef === undefined ? {} : { sourceRef }),
  };
}

function sourceRefForDiagnostic(
  file: ts.SourceFile,
  start: number | undefined,
  length: number | undefined,
): SourceRef {
  const startOffset = start ?? 0;
  const endOffset = startOffset + (length ?? 0);
  const startPosition = file.getLineAndCharacterOfPosition(startOffset);
  const endPosition = file.getLineAndCharacterOfPosition(endOffset);
  const relativePath = stripLeadingSlash(file.fileName);

  return {
    repositoryUri: "generated-artifact://studio",
    commitRef: "generated",
    file: {
      relativePath,
      contentType: contentTypeForPath(relativePath),
      kind: relativePath.endsWith(".d.ts") ? "type" : "component",
      language: relativePath.endsWith(".tsx") ? "tsx" : "typescript",
    },
    span: {
      startOffset,
      endOffset,
      startLine: startPosition.line + 1,
      startColumn: startPosition.character + 1,
      endLine: endPosition.line + 1,
      endColumn: endPosition.character + 1,
    },
  };
}

function normalizeCompilerPath(fileName: string): string {
  const normalized = fileName.replace(/\\/g, "/").replace(/^\/+/, "");
  return `/${normalized}`.replace(/\/+/g, "/");
}

function stripLeadingSlash(fileName: string): string {
  return fileName.replace(/^\/+/, "");
}

function scriptKindForPath(fileName: string): ts.ScriptKind {
  if (fileName.endsWith(".tsx")) return ts.ScriptKind.TSX;
  if (fileName.endsWith(".jsx")) return ts.ScriptKind.JSX;
  return ts.ScriptKind.TS;
}

function scriptExtensionForPath(fileName: string): ts.Extension {
  if (fileName.endsWith(".tsx")) return ts.Extension.Tsx;
  if (fileName.endsWith(".d.ts")) return ts.Extension.Dts;
  return ts.Extension.Ts;
}

function contentTypeForPath(fileName: string): string {
  if (fileName.endsWith(".tsx")) return "text/tsx";
  if (fileName.endsWith(".ts")) return "text/typescript";
  return "text/plain";
}
