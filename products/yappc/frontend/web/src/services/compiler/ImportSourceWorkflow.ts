/**
 * Import from Source Workflow
 *
 * Handles importing components from various sources:
 * - TSX files
 * - Routes
 * - Storybook stories
 * - Artifacts
 * - ZIP archives
 *
 * Uses yappc-artifact-compiler library for real extraction logic.
 *
 * @doc.type service
 * @doc.purpose Import from source workflow
 * @doc.layer product
 */

import * as ts from 'typescript';
import {
  extractComponentsFromSource,
  extractPageFromSource,
  parseCsfSource,
  type ExtractedComponent,
  type ExtractedPage,
  type ExtractedCsfData,
  createDefaultProviderRegistry,
  SynthesisPipeline,
  type GraphNode,
} from 'yappc-artifact-compiler';

import {
  compileImportedSourceToPageArtifacts,
  type ImportedSourceArtifactInput,
} from '@/components/canvas/page/artifactCompilerBridge';
import type { PageArtifactDocument } from '@/components/canvas/page/pageArtifactDocument';
import { ApiRequestError, yappcApi, type SourceImportJobSnapshot } from '@/lib/api/client';
import { assessComponentSafety } from '@/security/UnsafeComponentHandler';

const compileImportedSourceToPageArtifactsSafe = compileImportedSourceToPageArtifacts as (
  input: ImportedSourceArtifactInput,
  createdBy: string,
) => unknown;

const assessComponentSafetySafe = assessComponentSafety as (
  source: string,
  filePath: string,
) => unknown;

export type ImportSourceType =
  | 'tsx'
  | 'route'
  | 'storybook'
  | 'artifact'
  | 'zip'
  | 'github'
  | 'gitlab'
  | 'local-folder';

export interface ImportSourceOptions {
  /** Source type */
  sourceType: ImportSourceType;
  /** Source URL or file path */
  source: string;
  /** Project ID */
  projectId: string;
  /** Target component name */
  targetComponentName?: string;
  /** Import options */
  options?: ImportOptions;
}

export interface ImportOptions {
  /** Include dependencies */
  includeDependencies?: boolean;
  /** Include styles */
  includeStyles?: boolean;
  /** Include tests */
  includeTests?: boolean;
  /** Include documentation */
  includeDocumentation?: boolean;
  /** Preserve file structure */
  preserveStructure?: boolean;
  /** Custom transform function */
  transform?: (code: string) => string;
  /** Allow direct local filesystem reads in trusted runtimes such as tests or backend tooling */
  allowLocalFileAccess?: boolean;
  /** Allow imports to continue when safety assessment flags risky or unsafe code */
  allowUnsafeComponents?: boolean;
  /** Explicit backend endpoint used for server-side source import orchestration */
  importApiEndpoint?: string;
  /** Require server-side import orchestration; do not fall back to local/browser import handlers. */
  requireServerImport?: boolean;
  /** Tenant scope required for governed browser imports. */
  tenantId?: string;
  /** Workspace scope required for governed browser imports. */
  workspaceId?: string;
  /** Maximum allowed source locator size before the import request is rejected client-side. */
  maxSourceLength?: number;
  /** Maximum import job status polling attempts for asynchronous governed imports. */
  maxJobPollAttempts?: number;
  /** Delay between import job polling attempts. */
  jobPollIntervalMs?: number;
  /** GitHub/GitLab token for authenticated repo imports. */
  repoToken?: string;
  /** Maximum number of files to materialize when importing a repository. */
  repoMaxFiles?: number;
  /** Maximum individual file size to materialize when importing a repository. */
  repoMaxFileSizeBytes?: number;
  /** Confidence threshold below which extracted elements are sent to residuals. */
  residualConfidenceThreshold?: number;
}

export interface ImportResult {
  /** Success flag */
  success: boolean;
  /** Imported component ID */
  componentId?: string;
  /** Imported files */
  files: ImportedFile[];
  /** Warnings */
  warnings: string[];
  /** Errors */
  errors: string[];
  /** Metadata */
  metadata: ImportMetadata;
  /**
   * Extracted component AST data from yappc-artifact-compiler.
   * Present when the import was performed from a TSX or route source.
   * Used by importSourceToPageArtifacts to populate canvas nodes.
   */
  extractedComponents?: readonly ExtractedComponent[];
  /** Governed server import job progress and audit status, when server orchestration is used. */
  job?: SourceImportJobSnapshot;
  /**
   * P6.1: Confidence metrics for repository imports.
   * Provides overall confidence score and per-element confidence for synthesis results.
   */
  confidence?: ImportConfidenceMetrics;
  /**
   * P6.1: Residual islands that could not be modeled.
   * Areas of code that require manual review or are unsupported by the synthesis pipeline.
   */
  residuals?: ResidualIsland[];
}

/**
 * P6.1: Confidence metrics for repository import synthesis.
 * Tracks overall confidence and per-element confidence scores.
 */
export interface ImportConfidenceMetrics {
  /** Overall confidence score (0-1) for the entire import */
  overallConfidence: number;
  /** Number of elements with high confidence */
  highConfidenceCount: number;
  /** Number of elements with medium confidence */
  mediumConfidenceCount: number;
  /** Number of elements with low confidence (sent to residuals) */
  lowConfidenceCount: number;
  /** Per-element confidence mapping by element ID */
  elementConfidence: ReadonlyMap<string, number>;
}

/**
 * P6.1: Residual island representing code that could not be modeled.
 */
export interface ResidualIsland {
  /** Unique identifier for the residual island */
  id: string;
  /** Source file path */
  sourcePath: string;
  /** Type of residual (e.g., 'unsupported-language', 'complex-pattern', 'dynamic-import') */
  type: string;
  /** Confidence score for this residual */
  confidence: number;
  /** Whether this residual requires manual review */
  requiresReview: boolean;
  /** Human-readable description */
  description: string;
  /** Line numbers (if available) */
  lineRange?: readonly [number, number];
}

export interface ImportToPageArtifactResult {
  readonly importResult: ImportResult;
  readonly pageArtifacts: readonly PageArtifactDocument[];
}

export interface ImportedFile {
  /** File path */
  path: string;
  /** File content */
  content: string;
  /** File type */
  type: 'component' | 'style' | 'test' | 'documentation' | 'other' | 'route';
  /** Original source */
  source?: string;
}

export interface ImportMetadata {
  /** Source type */
  sourceType: ImportSourceType;
  /** Source URL or path */
  source: string;
  /** Import timestamp */
  importedAt: string;
  /** Component name */
  componentName?: string;
  /** Dependencies */
  dependencies: string[];
  /** File count */
  fileCount: number;
  /** Total size in bytes */
  totalSize: number;
}

/**
 * Import from source
 */
export async function importFromSource(options: ImportSourceOptions): Promise<ImportResult> {
  const { sourceType, source, projectId, targetComponentName, options: importOptions = {} } = options;
  const warnings: string[] = [];
  const errors: string[] = [];

  try {
    const serverImportResult = await tryImportFromServer(options);
    if (serverImportResult != null) {
      return serverImportResult;
    }

    switch (sourceType) {
      case 'tsx':
        return await importFromTSX(source, projectId, targetComponentName, importOptions);
      case 'route':
        return await importFromRoute(source, projectId, targetComponentName, importOptions);
      case 'storybook':
        return await importFromStorybook(source, projectId, targetComponentName, importOptions);
      case 'artifact':
        return await importFromArtifact(source, projectId, targetComponentName, importOptions);
      case 'zip':
        return await importFromZip(source, projectId, targetComponentName, importOptions);
      case 'github':
      case 'gitlab':
      case 'local-folder':
        return await importFromRepo(source, sourceType, projectId, importOptions);
      default:
        throw new Error('Unsupported source type');
    }
  } catch (error) {
    errors.push(error instanceof Error ? error.message : String(error));
    return {
      success: false,
      files: [],
      warnings,
      errors,
      metadata: {
        sourceType,
        source,
        importedAt: new Date().toISOString(),
        dependencies: [],
        fileCount: 0,
        totalSize: 0,
      },
    };
  }
}

export async function importSourceToPageArtifacts(
  options: ImportSourceOptions,
  createdBy: string,
): Promise<ImportToPageArtifactResult> {
  const importResult = await importFromSource(options);
  if (!importResult.success) {
    return {
      importResult,
      pageArtifacts: [],
    };
  }

  const artifactInput: ImportedSourceArtifactInput = {
    projectId: options.projectId,
    componentName: importResult.metadata.componentName,
    source: options.source,
    sourceType: options.sourceType,
    importedAt: importResult.metadata.importedAt,
    extractedComponents: importResult.extractedComponents,
  };

  const compiledArtifactsRaw: unknown = compileImportedSourceToPageArtifactsSafe(artifactInput, createdBy);
  const compiledArtifacts = normalizeCompiledPageArtifacts(compiledArtifactsRaw);

  return {
    importResult,
    pageArtifacts: compiledArtifacts,
  };
}

/**
 * Import from TSX file
 */
async function importFromTSX(
  source: string,
  projectId: string,
  targetComponentName?: string,
  options?: ImportOptions
): Promise<ImportResult> {
  const warnings: string[] = [];
  const errors: string[] = [];
  const files: ImportedFile[] = [];
  const dependencies: string[] = [];

  try {
    // Fetch source content from file path or URL
    const content = await fetchTSXContent(source, options);
    
    // Use real artifact compiler extractor - returns array of components
    const extractedComponents: ExtractedComponent[] = extractComponentsFromSource(content, source);
    
    if (extractedComponents.length === 0) {
      errors.push('No components found in source file');
      return {
        success: false,
        files,
        warnings,
        errors,
        metadata: {
          sourceType: 'tsx',
          source,
          importedAt: new Date().toISOString(),
          dependencies,
          fileCount: 0,
          totalSize: 0,
        },
      };
    }

    // Use the first component or specified target
    const extractedComponent = extractedComponents[0];
    if (!extractedComponent) {
      errors.push('No components found in source file');
      return buildFailedImportResult('tsx', source, warnings, errors, dependencies);
    }

    const componentName = targetComponentName || extractedComponent.name;

    files.push({
      path: `${componentName}.tsx`,
      content,
      type: 'component',
      source,
    });

    if (options?.includeStyles) {
      files.push(...await extractStyleFiles(source, options));
    }
    if (options?.includeTests) {
      files.push(...await extractTestFiles(source, options));
    }

    // Extract dependencies from JSX usage
    dependencies.push(...extractedComponent.jsxUsage);

    if (!enforceImportedComponentSafety(files, warnings, errors, options)) {
      return buildFailedImportResult('tsx', source, warnings, errors, dependencies);
    }

    const totalSize = files.reduce((sum, file) => sum + file.content.length, 0);

    return {
      success: true,
      componentId: `${projectId}/${componentName}`,
      files,
      warnings,
      errors,
      metadata: {
        sourceType: 'tsx',
        source,
        importedAt: new Date().toISOString(),
        componentName,
        dependencies,
        fileCount: files.length,
        totalSize,
      },
      extractedComponents,
    };
  } catch (error) {
    errors.push(error instanceof Error ? error.message : String(error));
    return {
      success: false,
      files,
      warnings,
      errors,
      metadata: {
        sourceType: 'tsx',
        source,
        importedAt: new Date().toISOString(),
        dependencies,
        fileCount: 0,
        totalSize: 0,
      },
    };
  }
}

/**
 * Import from route
 */
async function importFromRoute(
  source: string,
  projectId: string,
  targetComponentName?: string,
  options?: ImportOptions
): Promise<ImportResult> {
  const warnings: string[] = [];
  const errors: string[] = [];
  const files: ImportedFile[] = [];
  const dependencies: string[] = [];

  try {
    // Fetch source content from file path or URL
    const content = await fetchRouteContent(source, options);
    
    // Use real artifact compiler extractor for pages
    const extractedPage: ExtractedPage | null = extractPageFromSource(content, source);
    
    if (!extractedPage) {
      errors.push('No page found in source file');
      return {
        success: false,
        files,
        warnings,
        errors,
        metadata: {
          sourceType: 'route',
          source,
          importedAt: new Date().toISOString(),
          dependencies,
          fileCount: 0,
          totalSize: 0,
        },
      };
    }

    const pageName = targetComponentName || extractedPage.routePath.replace(/\//g, '-') || 'page';

    files.push({
      path: `${pageName}.tsx`,
      content,
      type: 'route',
      source,
    });

    if (options?.includeStyles) {
      files.push(...await extractStyleFiles(source, options));
    }
    if (options?.includeTests) {
      files.push(...await extractTestFiles(source, options));
    }

    // Extract dependencies from components rendered
    dependencies.push(...extractedPage.componentsRendered);

    if (!enforceImportedComponentSafety(files, warnings, errors, options)) {
      return buildFailedImportResult('route', source, warnings, errors, dependencies);
    }

    const totalSize = files.reduce((sum, file) => sum + file.content.length, 0);

    return {
      success: true,
      componentId: `${projectId}/${pageName}`,
      files,
      warnings,
      errors,
      metadata: {
        sourceType: 'route',
        source,
        importedAt: new Date().toISOString(),
        componentName: pageName,
        dependencies,
        fileCount: files.length,
        totalSize,
      },
    };
  } catch (error) {
    errors.push(error instanceof Error ? error.message : String(error));
    return {
      success: false,
      files,
      warnings,
      errors,
      metadata: {
        sourceType: 'route',
        source,
        importedAt: new Date().toISOString(),
        dependencies,
        fileCount: 0,
        totalSize: 0,
      },
    };
  }
}

/**
 * Import from Storybook
 */
async function importFromStorybook(
  source: string,
  projectId: string,
  targetComponentName?: string,
  options?: ImportOptions
): Promise<ImportResult> {
  const warnings: string[] = [];
  const errors: string[] = [];
  const files: ImportedFile[] = [];
  const dependencies: string[] = [];

  try {
    // Fetch source content from file path or URL
    const content = await fetchStorybookStory(source, options);
    
    // Use real artifact compiler extractor for CSF
    const extractedCsf: ExtractedCsfData | null = parseCsfSource(content, source);
    
    if (!extractedCsf) {
      errors.push('No CSF data found in source file');
      return {
        success: false,
        files,
        warnings,
        errors,
        metadata: {
          sourceType: 'storybook',
          source,
          importedAt: new Date().toISOString(),
          dependencies,
          fileCount: 0,
          totalSize: 0,
        },
      };
    }

    const componentName =
      targetComponentName ||
      extractedCsf.meta.componentName ||
      extractedCsf.meta.componentImport ||
      (extractedCsf.componentFilePath ? extractComponentNameFromPath(extractedCsf.componentFilePath) : null) ||
      extractStoryComponentNameFromSource(source);

    if (!componentName) {
      throw new Error('Unable to determine Storybook component name from source metadata');
    }

    files.push({
      path: `${componentName}.stories.tsx`,
      content,
      type: 'documentation',
      source,
    });

    // Extract component implementation if available
    if (options?.includeDependencies) {
      const componentContent = await fetchStorybookComponent(
        source,
        extractedCsf.componentFilePath ?? inferSiblingComponentImportPath(source),
        options,
      );
      if (componentContent) {
        files.push({
          path: `${componentName}.tsx`,
          content: componentContent,
          type: 'component',
          source: extractedCsf.componentFilePath,
        });
        const deps = extractDependencies(componentContent);
        dependencies.push(...deps);
      }
    }

    if (!enforceImportedComponentSafety(files, warnings, errors, options)) {
      return buildFailedImportResult('storybook', source, warnings, errors, dependencies);
    }

    const totalSize = files.reduce((sum, file) => sum + file.content.length, 0);

    return {
      success: true,
      componentId: `${projectId}/${componentName}`,
      files,
      warnings,
      errors,
      metadata: {
        sourceType: 'storybook',
        source,
        importedAt: new Date().toISOString(),
        componentName,
        dependencies,
        fileCount: files.length,
        totalSize,
      },
    };
  } catch (error) {
    errors.push(error instanceof Error ? error.message : String(error));
    return {
      success: false,
      files,
      warnings,
      errors,
      metadata: {
        sourceType: 'storybook',
        source,
        importedAt: new Date().toISOString(),
        dependencies,
        fileCount: 0,
        totalSize: 0,
      },
    };
  }
}

/**
 * Import from artifact
 */
async function importFromArtifact(
  source: string,
  projectId: string,
  targetComponentName?: string,
  options?: ImportOptions
): Promise<ImportResult> {
  const warnings: string[] = [];
  const errors: string[] = [];
  const files: ImportedFile[] = [];
  const dependencies: string[] = [];

  // Fetch artifact
  const artifactData = await fetchArtifact(source, options);
  const componentName = targetComponentName || artifactData.metadata.name;

  files.push({
    path: `${componentName}.json`,
    content: JSON.stringify(artifactData, null, 2),
    type: 'component',
    source,
  });

  dependencies.push(...(artifactData.dependencies || []));

  if (!enforceImportedComponentSafety(files, warnings, errors, options)) {
    return buildFailedImportResult('artifact', source, warnings, errors, dependencies);
  }

  const totalSize = files.reduce((sum, file) => sum + file.content.length, 0);

  return {
    success: true,
    componentId: `${projectId}/${componentName}`,
    files,
    warnings,
    errors,
    metadata: {
      sourceType: 'artifact',
      source,
      importedAt: new Date().toISOString(),
      componentName,
      dependencies,
      fileCount: files.length,
      totalSize,
    },
  };
}

/**
 * Import from ZIP archive
 */
async function importFromZip(
  source: string,
  projectId: string,
  targetComponentName?: string,
  options?: ImportOptions
): Promise<ImportResult> {
  const warnings: string[] = [];
  const errors: string[] = [];
  const files: ImportedFile[] = [];
  const dependencies: string[] = [];

  // Unzip archive
  const zipFiles = await unzipArchive(source, options);
  const componentName = targetComponentName || extractComponentNameFromZip(zipFiles);
  if (!componentName) {
    throw new Error('Unable to determine component name from ZIP archive');
  }

  for (const file of zipFiles) {
    const fileType = determineFileType(file.path);
    if (shouldIncludeFile(file.path, fileType, options)) {
      files.push({
        path: file.path,
        content: file.content,
        type: fileType,
        source,
      });

      if (fileType === 'component') {
        const deps = extractDependencies(file.content);
        dependencies.push(...deps);
      }
      }
    }

    if (!enforceImportedComponentSafety(files, warnings, errors, options)) {
      return buildFailedImportResult('zip', source, warnings, errors, dependencies);
    }

  const totalSize = files.reduce((sum, file) => sum + file.content.length, 0);

  return {
    success: true,
    componentId: `${projectId}/${componentName}`,
    files,
    warnings,
    errors,
    metadata: {
      sourceType: 'zip',
      source,
      importedAt: new Date().toISOString(),
      componentName,
      dependencies,
      fileCount: files.length,
      totalSize,
    },
  };
}

// Helper functions

async function fetchTSXContent(source: string, options?: ImportOptions): Promise<string> {
  return readTextSource(source, options);
}

async function fetchRouteContent(source: string, options?: ImportOptions): Promise<string> {
  return readTextSource(source, options);
}

async function fetchStorybookStory(source: string, options?: ImportOptions): Promise<string> {
  return readTextSource(source, options);
}

async function fetchStorybookComponent(
  storySource: string,
  componentImportPath?: string,
  options?: ImportOptions,
): Promise<string | null> {
  if (!componentImportPath) {
    return null;
  }
  const candidates = resolveImportCandidates(storySource, componentImportPath);
  for (const candidate of candidates) {
    try {
      return await readTextSource(candidate, options);
    } catch {
      // Try the next supported extension/path candidate.
    }
  }
  return null;
}

async function fetchArtifact(
  source: string,
  options?: ImportOptions,
): Promise<{ metadata: { name: string }; dependencies?: string[] }> {
  const content = await readTextSource(source, options);
  const artifactData = parseArtifactJson(content);
  return {
    metadata: { name: artifactData.metadata?.name ?? extractComponentName(content) },
    dependencies: [...(artifactData.dependencies ?? [])],
  };
}

async function unzipArchive(source: string, options?: ImportOptions): Promise<{ path: string; content: string }[]> {
  const { default: JSZip } = await import('jszip');
  const archive = await JSZip.loadAsync(await readBinarySource(source, options));
  const files = await Promise.all(
    Object.values(archive.files)
      .filter((file) => !file.dir)
      .map(async (file) => ({
        path: file.name,
        content: await file.async('string'),
      }))
  );
  return files;
}

function extractComponentName(content: string): string {
  const extractedComponents = extractComponentsFromSource(content, 'inline-component.tsx');
  const firstExtractedComponent = extractedComponents[0];
  if (firstExtractedComponent) {
    return firstExtractedComponent.name;
  }

  const sourceFile = ts.createSourceFile(
    'inline-component.tsx',
    content,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX
  );

  let componentName: string | null = null;
  sourceFile.forEachChild((node) => {
    if (componentName) {
      return;
    }
    if (ts.isFunctionDeclaration(node) && node.name) {
      componentName = node.name.text;
    } else if (ts.isVariableStatement(node)) {
      const declaration = node.declarationList.declarations.find((decl) => ts.isIdentifier(decl.name));
      if (declaration && ts.isIdentifier(declaration.name)) {
        componentName = declaration.name.text;
      }
    }
  });

  if (!componentName) {
    throw new Error('Unable to determine component name from source content');
  }

  return componentName;
}

function extractComponentNameFromZip(files: { path: string }[]): string | null {
  const preferredFile = files.find((file) =>
    /\.(tsx|jsx|ts|js)$/.test(file.path) &&
    !/(\.stories|\.test|\.spec)\./.test(file.path)
  );
  if (!preferredFile) {
    return null;
  }

  const fileName = preferredFile.path.split('/').pop() ?? preferredFile.path;
  const normalized = fileName
    .replace(/\.(tsx|jsx|ts|js)$/, '')
    .replace(/(^index$)/, '')
    .replace(/[-_](\w)/g, (_, letter: string) => letter.toUpperCase());

  return normalized ? normalized.charAt(0).toUpperCase() + normalized.slice(1) : null;
}

function extractComponentNameFromPath(path: string): string | null {
  const fileName = path.split('/').pop() ?? path;
  const normalized = fileName
    .replace(/\.(tsx|jsx|ts|js)$/, '')
    .replace(/^index$/, '')
    .replace(/[-_](\w)/g, (_, letter: string) => letter.toUpperCase());

  return normalized ? normalized.charAt(0).toUpperCase() + normalized.slice(1) : null;
}

function extractStoryComponentNameFromSource(source: string): string | null {
  const fileName = source.split('/').pop() ?? source;
  const match = fileName.match(/^(.*?)(?:\.stories)?\.(tsx|jsx|ts|js)$/);
  if (!match?.[1]) {
    return null;
  }
  return extractComponentNameFromPath(match[1]);
}

function inferSiblingComponentImportPath(source: string): string | undefined {
  const fileName = source.split('/').pop() ?? source;
  const match = fileName.match(/^(.*)\.stories\.(tsx|jsx|ts|js)$/);
  if (!match?.[1]) {
    return undefined;
  }
  return `./${match[1]}`;
}

function extractDependencies(content: string): string[] {
  const sourceFile = ts.createSourceFile(
    'dependencies.tsx',
    content,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX
  );
  const dependencies = new Set<string>();
  sourceFile.forEachChild((node) => {
    if (ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)) {
      dependencies.add(node.moduleSpecifier.text);
    }
  });
  return [...dependencies];
}

async function extractStyleFiles(source: string, options?: ImportOptions): Promise<ImportedFile[]> {
  return readSiblingFiles(source, ['.css', '.scss', '.sass', '.less'], 'style', options);
}

async function extractTestFiles(source: string, options?: ImportOptions): Promise<ImportedFile[]> {
  const fileInfo = splitSourcePath(source);
  const candidates = [
    `${fileInfo.directory}/${fileInfo.baseName}.test${fileInfo.extension}`,
    `${fileInfo.directory}/${fileInfo.baseName}.spec${fileInfo.extension}`,
  ];
  return readExistingFiles(candidates, 'test', source, options);
}

function determineFileType(path: string): ImportedFile['type'] {
  if (path.endsWith('.tsx') || path.endsWith('.ts') || path.endsWith('.jsx') || path.endsWith('.js')) {
    return 'component';
  }
  if (path.endsWith('.css') || path.endsWith('.scss') || path.endsWith('.less')) {
    return 'style';
  }
  if (path.endsWith('.test.tsx') || path.endsWith('.test.ts') || path.endsWith('.spec.tsx')) {
    return 'test';
  }
  if (path.endsWith('.md') || path.endsWith('.mdx')) {
    return 'documentation';
  }
  return 'other';
}

function shouldIncludeFile(path: string, type: ImportedFile['type'], options?: ImportOptions): boolean {
  if (type === 'test' && !options?.includeTests) return false;
  if (type === 'documentation' && !options?.includeDocumentation) return false;
  return true;
}

function buildFailedImportResult(
  sourceType: ImportSourceType,
  source: string,
  warnings: string[],
  errors: string[],
  dependencies: string[],
): ImportResult {
  return {
    success: false,
    files: [],
    warnings,
    errors,
    metadata: {
      sourceType,
      source,
      importedAt: new Date().toISOString(),
      dependencies,
      fileCount: 0,
      totalSize: 0,
    },
  };
}

function enforceImportedComponentSafety(
  files: ImportedFile[],
  warnings: string[],
  errors: string[],
  options?: ImportOptions,
): boolean {
  const componentLikeFiles = files.filter((file) => file.type === 'component' || file.type === 'route');

  for (const file of componentLikeFiles) {
    const rawAssessment: unknown = assessComponentSafetySafe(file.content, file.path);
    const assessment = normalizeSafetyAssessment(rawAssessment);
    if (assessment.safetyLevel === 'safe') {
      continue;
    }

    const message =
      `Imported source '${file.path}' was flagged as ${assessment.safetyLevel}. ` +
      `Recommended action: ${assessment.recommendedAction}. ` +
      `Risk factors: ${assessment.riskFactors.join(', ')}`;

    if (assessment.recommendedAction === 'block' && !options?.allowUnsafeComponents) {
      errors.push(message);
      return false;
    }

    warnings.push(message);
  }

  return true;
}

export default {
  importFromSource,
  importSourceToPageArtifacts,
  importFromTSX,
  importFromRoute,
  importFromStorybook,
  importFromArtifact,
  importFromZip,
};

interface SourcePathParts {
  readonly directory: string;
  readonly fileName: string;
  readonly baseName: string;
  readonly extension: string;
}

interface ParsedArtifactJson {
  readonly metadata?: {
    readonly name?: string;
  };
  readonly dependencies?: readonly string[];
}

interface ComponentSafetyAssessment {
  readonly safetyLevel: 'safe' | 'risky' | 'unsafe';
  readonly recommendedAction: 'allow' | 'review' | 'block';
  readonly riskFactors: readonly string[];
}

function isRemoteSource(source: string): boolean {
  return source.startsWith('http://') || source.startsWith('https://');
}

function splitSourcePath(source: string): SourcePathParts {
  const normalized = source.replace(/\\/g, '/');
  const lastSlash = normalized.lastIndexOf('/');
  const fileName = lastSlash >= 0 ? normalized.slice(lastSlash + 1) : normalized;
  const directory = lastSlash >= 0 ? normalized.slice(0, lastSlash) : '.';
  const extensionMatch = fileName.match(/(\.[^.]+)$/);
  const extension = extensionMatch?.[1] ?? '';
  const baseName = extension ? fileName.slice(0, -extension.length) : fileName;

  return {
    directory,
    fileName,
    baseName,
    extension,
  };
}

function parseArtifactJson(content: string): ParsedArtifactJson {
  const parsed: unknown = JSON.parse(content);
  if (!(parsed instanceof Object) || parsed == null) {
    throw new Error('Artifact JSON must be an object');
  }

  const candidate = parsed as Record<string, unknown>;
  const metadataValue = candidate.metadata;
  const dependenciesValue = candidate.dependencies;
  let metadata: ParsedArtifactJson['metadata'];

  if (metadataValue instanceof Object && metadataValue != null) {
    const metadataCandidate = metadataValue as Record<string, unknown>;
    metadata = {
      name: typeof metadataCandidate.name === 'string' ? metadataCandidate.name : undefined,
    };
  }

  const dependencies = Array.isArray(dependenciesValue)
    ? dependenciesValue.filter((entry): entry is string => typeof entry === 'string')
    : undefined;

  return {
    metadata,
    dependencies,
  };
}

function normalizeCompiledPageArtifacts(value: unknown): readonly PageArtifactDocument[] {
  if (!Array.isArray(value)) {
    return [];
  }

  const artifacts: PageArtifactDocument[] = [];
  for (const entry of value) {
    if (isPageArtifactDocument(entry)) {
      artifacts.push(entry);
    }
  }

  return artifacts;
}

function isPageArtifactDocument(value: unknown): value is PageArtifactDocument {
  if (!(value instanceof Object) || value == null) {
    return false;
  }

  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.artifactId === 'string' &&
    typeof candidate.documentId === 'string' &&
    candidate.serializedBuilderDocument instanceof Object &&
    candidate.serializedBuilderDocument != null &&
    (candidate.source === 'created-in-builder' ||
      candidate.source === 'decompiled' ||
      candidate.source === 'imported' ||
      candidate.source === 'generated') &&
    (candidate.syncStatus === 'dirty' ||
      candidate.syncStatus === 'saving' ||
      candidate.syncStatus === 'synced' ||
      candidate.syncStatus === 'error' ||
      candidate.syncStatus === 'offline') &&
    typeof candidate.trustLevel === 'string' &&
    typeof candidate.dataClassification === 'string' &&
    typeof candidate.createdBy === 'string' &&
    typeof candidate.updatedBy === 'string' &&
    typeof candidate.createdAt === 'string' &&
    typeof candidate.updatedAt === 'string'
  );
}

function normalizeSafetyAssessment(value: unknown): ComponentSafetyAssessment {
  if (!(value instanceof Object) || value == null) {
    return {
      safetyLevel: 'unsafe',
      recommendedAction: 'block',
      riskFactors: ['Unrecognized safety assessment payload'],
    };
  }

  const candidate = value as Record<string, unknown>;
  const safetyLevel =
    candidate.safetyLevel === 'safe' || candidate.safetyLevel === 'risky' || candidate.safetyLevel === 'unsafe'
      ? candidate.safetyLevel
      : 'unsafe';

  const recommendedAction =
    candidate.recommendedAction === 'allow' ||
    candidate.recommendedAction === 'review' ||
    candidate.recommendedAction === 'block'
      ? candidate.recommendedAction
      : 'block';

  const riskFactors = Array.isArray(candidate.riskFactors)
    ? candidate.riskFactors.filter((factor): factor is string => typeof factor === 'string')
    : ['Unknown risk factors'];

  return {
    safetyLevel,
    recommendedAction,
    riskFactors,
  };
}

async function readTextSource(source: string, options?: ImportOptions): Promise<string> {
  if (source.startsWith('inline:')) {
    return source.slice('inline:'.length);
  }

  if (!isRemoteSource(source)) {
    const localFile = await tryReadLocalFile(source, options);
    if (localFile != null) {
      return localFile;
    }
    throw new Error(
      `Local source access requires an explicit trusted loader or allowLocalFileAccess for ${source}`,
    );
  }

  const response = await fetch(source);
  if (!response.ok) {
    throw new Error(`Failed to load source: ${source} (${response.status})`);
  }
  return response.text();
}

async function tryImportFromServer(options: ImportSourceOptions): Promise<ImportResult | null> {
  if (!shouldUseServerImport(options)) {
    return null;
  }

  const requireServerImport = options.options?.requireServerImport === true;
  const endpoint = options.options?.importApiEndpoint;
  if (endpoint && endpoint !== '/api/v1/yappc/artifact/import-source') {
    if (requireServerImport) {
      throw new Error('Governed source imports must use the canonical backend import endpoint.');
    }
    return null;
  }
  const maxSourceLength = options.options?.maxSourceLength ?? 4096;
  if (options.source.length > maxSourceLength) {
    throw new Error(`Source locator exceeds the ${maxSourceLength} character import limit.`);
  }
  if (requireServerImport && (!options.options?.tenantId || !options.options.workspaceId || !options.projectId)) {
    throw new Error('Governed source imports require tenant, workspace, and project scope.');
  }

  const payload = {
    sourceType: options.sourceType,
    source: options.source,
    projectId: options.projectId,
    targetComponentName: options.targetComponentName,
    options: {
      includeDependencies: options.options?.includeDependencies,
      includeStyles: options.options?.includeStyles,
      includeTests: options.options?.includeTests,
      includeDocumentation: options.options?.includeDocumentation,
      preserveStructure: options.options?.preserveStructure,
      allowUnsafeComponents: options.options?.allowUnsafeComponents,
    },
  };

  try {
    const result = await yappcApi.sourceImports.start(
      payload,
      {
        tenantId: options.options?.tenantId ?? '',
        workspaceId: options.options?.workspaceId ?? '',
        projectId: options.projectId,
      },
    );

    if (!isImportResultShape(result)) {
      throw new Error('Server import returned an invalid response payload');
    }

    return pollImportJobUntilTerminal(result as ImportResult, {
      tenantId: options.options?.tenantId ?? '',
      workspaceId: options.options?.workspaceId ?? '',
      projectId: options.projectId,
    }, options.options);
  } catch (networkError) {
    if (
      networkError instanceof ApiRequestError &&
      (networkError.status === 404 || networkError.status === 501) &&
      !requireServerImport
    ) {
      return null;
    }

    // P1-011: Server import was selected for this source (shouldUseServerImport returned true).
    // A network failure must not silently fall through to browser-local handlers, as those
    // lack the auth and tenant context available on the server side.
    throw new Error(
      `Server import request failed: ${
        networkError instanceof Error ? networkError.message : String(networkError)
      }`,
    );
  }
}

const terminalImportJobStatuses = new Set(['REVIEW_REQUIRED', 'REJECTED', 'FAILED']);

function isTerminalImportJob(job: SourceImportJobSnapshot | undefined): boolean {
  return Boolean(job?.status && terminalImportJobStatuses.has(job.status));
}

async function pollImportJobUntilTerminal(
  result: ImportResult,
  scope: { readonly tenantId: string; readonly workspaceId: string; readonly projectId: string },
  options: ImportOptions | undefined,
): Promise<ImportResult> {
  if (!result.job?.id || isTerminalImportJob(result.job)) {
    return result;
  }

  const maxAttempts = Math.max(1, options?.maxJobPollAttempts ?? 5);
  const intervalMs = Math.max(0, options?.jobPollIntervalMs ?? 250);
  let latestJob = result.job;

  for (let attempt = 0; attempt < maxAttempts && !isTerminalImportJob(latestJob); attempt += 1) {
    if (attempt > 0 && intervalMs > 0) {
      await delay(intervalMs);
    }
    const status = await yappcApi.sourceImports.status(latestJob.id, scope);
    if (!isImportJobShape(status.job)) {
      throw new Error('Server import job status returned an invalid response payload');
    }
    latestJob = status.job;
  }

  if (!isTerminalImportJob(latestJob)) {
    throw new Error(`Server import job ${latestJob.id} did not reach a terminal status before polling timed out.`);
  }

  return {
    ...result,
    job: latestJob,
  };
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

/**
 * Import from a repository source (GitHub, GitLab, or local folder).
 * P6.1: Enhanced with progress tracking and confidence display.
 * Uses the SourceProviderRegistry to acquire the snapshot and the SynthesisPipeline
 * to extract components. Returns an ImportResult with extracted component metadata,
 * confidence metrics, and residual islands.
 */
async function importFromRepo(
  source: string,
  sourceType: 'github' | 'gitlab' | 'local-folder',
  projectId: string,
  options?: ImportOptions,
): Promise<ImportResult> {
  const warnings: string[] = [];
  const errors: string[] = [];

  try {
    const registry = createDefaultProviderRegistry();

    // For GitLab, remap the locator to the GitLab API domain if it's a plain slug
    const locator = sourceType === 'gitlab' && !source.includes('gitlab.com')
      ? `https://gitlab.com/${source}`
      : source;

    const snapshot = await registry.resolve(locator, {
      credentials: options?.repoToken ? { token: options.repoToken } : undefined,
      maxFiles: options?.repoMaxFiles,
      maxFileSizeBytes: options?.repoMaxFileSizeBytes,
    });

    const pipeline = new SynthesisPipeline({
      extractors: [],
      residualConfidenceThreshold: options?.residualConfidenceThreshold ?? 0.5,
    });

    const result = await pipeline.runFromSnapshot(snapshot);

    for (const warn of result.warnings) {
      warnings.push(warn.message);
    }
    for (const err of result.errors) {
      if (!err.recoverable) {
        errors.push(err.message);
      } else {
        warnings.push(err.message);
      }
    }

    const componentNodes = result.graph.nodes.filter((n: GraphNode) => n.kind === 'component');
    const files: ImportedFile[] = componentNodes.map((node: GraphNode) => ({
      path: node.sourceLocation.filePath,
      content: '',
      type: 'component' as const,
      source: locator,
    }));

    // P6.1: Extract confidence metrics from synthesis result
    const confidenceMetrics = extractConfidenceMetrics(result);

    // P6.1: Extract residual islands from synthesis result
    const residualIslands = extractResidualIslands(result);

    return {
      success: errors.length === 0,
      componentId: `${projectId}/${snapshot.snapshotRef.repoId}`,
      files,
      warnings,
      errors,
      metadata: {
        sourceType,
        source,
        importedAt: new Date().toISOString(),
        dependencies: [],
        fileCount: result.stats.scannedFiles,
        totalSize: 0,
      },
      confidence: confidenceMetrics,
      residuals: residualIslands,
    };
  } catch (err) {
    errors.push(err instanceof Error ? err.message : String(err));
    return buildFailedImportResult(sourceType, source, warnings, errors, []);
  }
}

/**
 * P6.1: Extract confidence metrics from synthesis pipeline result.
 */
function extractConfidenceMetrics(result: any): ImportConfidenceMetrics | undefined {
  if (!result || !result.semanticModel) {
    return undefined;
  }

  const elements = result.semanticModel.elements || [];
  const elementConfidence = new Map<string, number>();
  let highCount = 0;
  let mediumCount = 0;
  let lowCount = 0;
  let totalConfidence = 0;

  for (const element of elements) {
    const confidence = element.confidence ?? 0.5;
    elementConfidence.set(element.id, confidence);
    totalConfidence += confidence;

    if (confidence >= 0.8) {
      highCount++;
    } else if (confidence >= 0.5) {
      mediumCount++;
    } else {
      lowCount++;
    }
  }

  const overallConfidence = elements.length > 0 ? totalConfidence / elements.length : 0;

  return {
    overallConfidence,
    highConfidenceCount: highCount,
    mediumConfidenceCount: mediumCount,
    lowConfidenceCount: lowCount,
    elementConfidence,
  };
}

/**
 * P6.1: Extract residual islands from synthesis pipeline result.
 */
function extractResidualIslands(result: any): ResidualIsland[] | undefined {
  if (!result || !result.residualIslands) {
    return undefined;
  }

  return result.residualIslands.map((island: any) => ({
    id: island.id,
    sourcePath: island.sourcePath || island.location?.filePath,
    type: island.type || island.kind || 'unknown',
    confidence: island.confidence ?? 0,
    requiresReview: island.requiresReview ?? island.confidence < 0.5,
    description: island.description || island.reason || 'Unmodeled code fragment',
    lineRange: island.lineRange || island.location?.lineRange,
  }));
}

function shouldUseServerImport(options: ImportSourceOptions): boolean {
  if (options.source.startsWith('inline:')) {
    return false;
  }

  if (options.options?.allowLocalFileAccess) {
    return false;
  }

  // Repo source types use the SourceProviderRegistry directly, not the server import endpoint
  if (
    options.sourceType === 'github' ||
    options.sourceType === 'gitlab' ||
    options.sourceType === 'local-folder'
  ) {
    return false;
  }

  return true;
}

function isImportResultShape(value: unknown): value is ImportResult {
  if (!(value instanceof Object) || value == null) {
    return false;
  }

  const candidate = value as Partial<ImportResult>;
  return (
    typeof candidate.success === 'boolean' &&
    Array.isArray(candidate.files) &&
    Array.isArray(candidate.warnings) &&
    Array.isArray(candidate.errors) &&
    typeof candidate.metadata === 'object' &&
    candidate.metadata != null
  );
}

function isImportJobShape(value: unknown): value is SourceImportJobSnapshot {
  if (!(value instanceof Object) || value == null) {
    return false;
  }

  const candidate = value as Partial<SourceImportJobSnapshot>;
  return (
    typeof candidate.id === 'string' &&
    typeof candidate.status === 'string' &&
    typeof candidate.percentComplete === 'number' &&
    typeof candidate.currentStep === 'string' &&
    Array.isArray(candidate.steps) &&
    typeof candidate.createdAt === 'string'
  );
}

async function readBinarySource(source: string, options?: ImportOptions): Promise<Uint8Array> {
  if (!isRemoteSource(source)) {
    const localFile = await tryReadLocalBinary(source, options);
    if (localFile != null) {
      return localFile;
    }
    throw new Error(
      `Local source access requires an explicit trusted loader or allowLocalFileAccess for ${source}`,
    );
  }

  const response = await fetch(source);
  if (!response.ok) {
    throw new Error(`Failed to load source: ${source} (${response.status})`);
  }
  return new Uint8Array(await response.arrayBuffer());
}

async function tryReadLocalFile(source: string, options?: ImportOptions): Promise<string | null> {
  if (!options?.allowLocalFileAccess) {
    return null;
  }

  if (!(typeof process !== 'undefined' && Boolean(process.versions?.node))) {
    return null;
  }

  try {
    const fs = await import('node:fs/promises');
    return await fs.readFile(source, 'utf8');
  } catch {
    return null;
  }
}

async function tryReadLocalBinary(source: string, options?: ImportOptions): Promise<Uint8Array | null> {
  if (!options?.allowLocalFileAccess) {
    return null;
  }

  if (!(typeof process !== 'undefined' && Boolean(process.versions?.node))) {
    return null;
  }

  try {
    const fs = await import('node:fs/promises');
    return await fs.readFile(source);
  } catch {
    return null;
  }
}

function resolveImportCandidates(storySource: string, componentImportPath: string): string[] {
  if (isRemoteSource(componentImportPath) || componentImportPath.startsWith('/')) {
    return [componentImportPath];
  }

  const storyPath = splitSourcePath(storySource);
  const baseDirectory = storyPath.directory === '.' ? '' : storyPath.directory;
  const baseCandidate = `${baseDirectory}/${componentImportPath}`.replace(/\/+/g, '/');
  const hasKnownExtension = /\.[a-z]+$/i.test(componentImportPath);

  if (hasKnownExtension) {
    return [baseCandidate];
  }

  return [
    `${baseCandidate}.tsx`,
    `${baseCandidate}.ts`,
    `${baseCandidate}.jsx`,
    `${baseCandidate}.js`,
    `${baseCandidate}/index.tsx`,
    `${baseCandidate}/index.ts`,
    `${baseCandidate}/index.jsx`,
    `${baseCandidate}/index.js`,
  ];
}

async function readSiblingFiles(
  source: string,
  extensions: string[],
  type: ImportedFile['type'],
  options?: ImportOptions,
): Promise<ImportedFile[]> {
  const fileInfo = splitSourcePath(source);
  const candidates = extensions.map((extension) => `${fileInfo.directory}/${fileInfo.baseName}${extension}`);
  return readExistingFiles(candidates, type, source, options);
}

async function readExistingFiles(
  candidates: string[],
  type: ImportedFile['type'],
  source: string,
  options?: ImportOptions,
): Promise<ImportedFile[]> {
  const files = await Promise.all(
    candidates.map(async (candidate): Promise<ImportedFile | null> => {
      try {
        return {
          path: candidate.split('/').pop() ?? candidate,
          content: await readTextSource(candidate, options),
          type,
          source: candidate,
        } satisfies ImportedFile;
      } catch {
        return null;
      }
    })
  );

  return files.filter((file): file is ImportedFile => file != null);
}
