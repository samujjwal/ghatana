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

import {
  extractComponentsFromSource,
  extractPageFromSource,
  parseCsfSource,
  type ExtractedComponent,
  type ExtractedPage,
  type ExtractedStory,
  type ExtractedCsfData,
} from 'yappc-artifact-compiler';
import * as ts from 'typescript';

export type ImportSourceType = 'tsx' | 'route' | 'storybook' | 'artifact' | 'zip';

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
  const files: ImportedFile[] = [];
  const dependencies: string[] = [];

  try {
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
      default:
        throw new Error(`Unsupported source type: ${sourceType}`);
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
    const content = await fetchTSXContent(source);
    
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
    const extractedComponent = extractedComponents[0]!;
    const componentName = targetComponentName || extractedComponent.name;

    files.push({
      path: `${componentName}.tsx`,
      content,
      type: 'component',
      source,
    });

    if (options?.includeStyles) {
      files.push(...await extractStyleFiles(source));
    }
    if (options?.includeTests) {
      files.push(...await extractTestFiles(source));
    }

    // Extract dependencies from JSX usage
    dependencies.push(...extractedComponent.jsxUsage);

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
    const content = await fetchRouteContent(source);
    
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
      files.push(...await extractStyleFiles(source));
    }
    if (options?.includeTests) {
      files.push(...await extractTestFiles(source));
    }

    // Extract dependencies from components rendered
    dependencies.push(...extractedPage.componentsRendered);

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
    const content = await fetchStorybookStory(source);
    
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
        extractedCsf.componentFilePath ?? inferSiblingComponentImportPath(source)
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
  const artifactData = await fetchArtifact(source);
  const componentName = targetComponentName || artifactData.metadata.name;

  files.push({
    path: `${componentName}.json`,
    content: JSON.stringify(artifactData, null, 2),
    type: 'component',
    source,
  });

  dependencies.push(...(artifactData.dependencies || []));

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
  const zipFiles = await unzipArchive(source);
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

async function fetchTSXContent(source: string): Promise<string> {
  return readTextSource(source);
}

async function fetchRouteContent(source: string): Promise<string> {
  return readTextSource(source);
}

async function fetchStorybookStory(source: string): Promise<string> {
  return readTextSource(source);
}

async function fetchStorybookComponent(storySource: string, componentImportPath?: string): Promise<string | null> {
  if (!componentImportPath) {
    return null;
  }
  const candidates = resolveImportCandidates(storySource, componentImportPath);
  for (const candidate of candidates) {
    try {
      return await readTextSource(candidate);
    } catch {
      // Try the next supported extension/path candidate.
    }
  }
  return null;
}

async function fetchArtifact(source: string): Promise<{ metadata: { name: string }; dependencies?: string[] }> {
  const content = await readTextSource(source);
  const artifactData = JSON.parse(content) as { metadata?: { name?: string }; dependencies?: string[] };
  return {
    metadata: { name: artifactData.metadata?.name ?? extractComponentName(content) },
    dependencies: artifactData.dependencies ?? [],
  };
}

async function unzipArchive(source: string): Promise<{ path: string; content: string }[]> {
  const { default: JSZip } = await import('jszip');
  const archive = await JSZip.loadAsync(await readBinarySource(source));
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
  if (extractedComponents.length > 0) {
    return extractedComponents[0]!.name;
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

async function extractStyleFiles(source: string): Promise<ImportedFile[]> {
  return readSiblingFiles(source, ['.css', '.scss', '.sass', '.less'], 'style');
}

async function extractTestFiles(source: string): Promise<ImportedFile[]> {
  const fileInfo = splitSourcePath(source);
  const candidates = [
    `${fileInfo.directory}/${fileInfo.baseName}.test${fileInfo.extension}`,
    `${fileInfo.directory}/${fileInfo.baseName}.spec${fileInfo.extension}`,
  ];
  return readExistingFiles(candidates, 'test', source);
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

export default {
  importFromSource,
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

function isRemoteSource(source: string): boolean {
  return /^(https?:)?\/\//.test(source);
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

async function readTextSource(source: string): Promise<string> {
  if (source.startsWith('inline:')) {
    return source.slice('inline:'.length);
  }

  if (!isRemoteSource(source)) {
    const localFile = await tryReadLocalFile(source);
    if (localFile != null) {
      return localFile;
    }
  }

  const response = await fetch(source);
  if (!response.ok) {
    throw new Error(`Failed to load source: ${source} (${response.status})`);
  }
  return response.text();
}

async function readBinarySource(source: string): Promise<Uint8Array> {
  if (!isRemoteSource(source)) {
    const localFile = await tryReadLocalBinary(source);
    if (localFile != null) {
      return localFile;
    }
  }

  const response = await fetch(source);
  if (!response.ok) {
    throw new Error(`Failed to load source: ${source} (${response.status})`);
  }
  return new Uint8Array(await response.arrayBuffer());
}

async function tryReadLocalFile(source: string): Promise<string | null> {
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

async function tryReadLocalBinary(source: string): Promise<Uint8Array | null> {
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
  type: ImportedFile['type']
): Promise<ImportedFile[]> {
  const fileInfo = splitSourcePath(source);
  const candidates = extensions.map((extension) => `${fileInfo.directory}/${fileInfo.baseName}${extension}`);
  return readExistingFiles(candidates, type, source);
}

async function readExistingFiles(
  candidates: string[],
  type: ImportedFile['type'],
  source: string
): Promise<ImportedFile[]> {
  const files = await Promise.all(
    candidates.map(async (candidate) => {
      try {
        return {
          path: candidate.split('/').pop() ?? candidate,
          content: await readTextSource(candidate),
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
