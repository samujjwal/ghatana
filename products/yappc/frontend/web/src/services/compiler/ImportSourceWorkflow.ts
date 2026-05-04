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

    const componentName = targetComponentName || extractedCsf.meta.componentName || 'Component';

    files.push({
      path: `${componentName}.stories.tsx`,
      content,
      type: 'documentation',
      source,
    });

    // Extract component implementation if available
    if (options?.includeDependencies && extractedCsf.componentFilePath) {
      const componentContent = await fetchStorybookComponent(extractedCsf.componentFilePath);
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

// Helper functions (placeholder implementations)

async function fetchTSXContent(source: string): Promise<string> {
  // Placeholder: fetch TSX content from source
  return '';
}

async function fetchRouteContent(source: string): Promise<string> {
  // Placeholder: fetch route content from source
  return '';
}

async function fetchStorybookStory(source: string): Promise<string> {
  // Placeholder: fetch Storybook story from source
  return '';
}

async function fetchStorybookComponent(source: string): Promise<string | null> {
  // Placeholder: fetch component from Storybook
  return null;
}

async function fetchArtifact(source: string): Promise<{ metadata: { name: string }; dependencies?: string[] }> {
  // Placeholder: fetch artifact from source
  return { metadata: { name: '' } };
}

async function unzipArchive(source: string): Promise<{ path: string; content: string }[]> {
  // Placeholder: unzip archive
  return [];
}

function extractComponentName(content: string): string {
  // Placeholder: extract component name from content
  return 'Component';
}

function extractComponentNameFromZip(files: { path: string }[]): string {
  // Placeholder: extract component name from zip files
  return 'Component';
}

function extractDependencies(content: string): string[] {
  // Placeholder: extract dependencies from content
  return [];
}

async function extractStyleFiles(source: string): Promise<ImportedFile[]> {
  // Placeholder: extract style files
  return [];
}

async function extractTestFiles(source: string): Promise<ImportedFile[]> {
  // Placeholder: extract test files
  return [];
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
