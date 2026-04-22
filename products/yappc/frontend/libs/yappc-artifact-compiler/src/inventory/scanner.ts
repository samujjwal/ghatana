import { createHash } from 'crypto';
import { readdir, readFile, stat } from 'fs/promises';
import { join, relative, extname, basename } from 'path';
import {
  type ArtifactRecord,
  type ArtifactInventory,
  type ArtifactKind,
  type ArtifactLanguage,
  type ArtifactFramework,
  type ExtractorEligibility,
  type ImportExportSummary,
} from './types';

// ============================================================================
// Scanner Configuration
// ============================================================================

export interface ScannerConfig {
  readonly rootPath: string;
  readonly includeGlobs: readonly string[];
  readonly excludeGlobs: readonly string[];
  readonly maxFileSizeBytes: number;
  readonly followSymlinks: boolean;
}

export const DEFAULT_SCANNER_CONFIG: ScannerConfig = {
  rootPath: process.cwd(),
  includeGlobs: ['**/*'],
  excludeGlobs: [
    '**/node_modules/**',
    '**/dist/**',
    '**/.git/**',
    '**/coverage/**',
    '**/.next/**',
    '**/build/**',
    '**/*.lock',
    '**/*.log',
    '**/target/**',
    '**/.gradle/**',
    '**/out/**',
  ],
  maxFileSizeBytes: 10 * 1024 * 1024, // 10MB
  followSymlinks: false,
};

// ============================================================================
// Language Detection
// ============================================================================

function detectLanguage(filePath: string): ArtifactLanguage {
  const ext = extname(filePath).toLowerCase();
  switch (ext) {
    case '.ts':
      return 'typescript';
    case '.tsx':
      return 'tsx';
    case '.js':
      return 'javascript';
    case '.jsx':
      return 'jsx';
    case '.java':
      return 'java';
    case '.sql':
      return 'sql';
    case '.prisma':
      return 'prisma';
    case '.css':
      return 'css';
    case '.scss':
    case '.sass':
      return 'scss';
    case '.html':
    case '.htm':
      return 'html';
    case '.yaml':
    case '.yml':
      return 'yaml';
    case '.json':
      return 'json';
    case '.xml':
      return 'xml';
    case '.md':
    case '.mdx':
      return 'markdown';
    case '.sh':
    case '.bash':
      return 'shell';
    case '.py':
      return 'python';
    case '.rs':
      return 'rust';
    case '.go':
      return 'go';
    default:
      return 'unknown';
  }
}

// ============================================================================
// Framework Detection
// ============================================================================

function detectFramework(
  filePath: string,
  content: string,
  language: ArtifactLanguage,
): ArtifactFramework {
  const name = basename(filePath).toLowerCase();

  // Next.js patterns
  if (name.includes('page') || name.includes('layout') || name.includes('loading') || name.includes('error')) {
    if (filePath.includes('/app/') || filePath.includes('/pages/')) {
      return 'nextjs';
    }
  }

  // React detection
  if (language === 'tsx' || language === 'jsx') {
    if (content.includes('from \'react\'') || content.includes('from "react"') ||
        content.includes('import React') || content.includes('React.FC') ||
        content.includes('useState') || content.includes('useEffect')) {
      if (content.includes('next/') || content.includes('next.js')) {
        return 'nextjs';
      }
      return 'react';
    }
  }

  // Prisma
  if (language === 'prisma') {
    return 'prisma';
  }

  // Storybook
  if (name.includes('.stories.') || name.includes('.story.')) {
    return 'storybook';
  }

  // Tailwind
  if (content.includes('tailwind') || content.includes('className=')) {
    return 'tailwind';
  }

  // Spring Boot / Java
  if (language === 'java') {
    if (content.includes('@SpringBootApplication') || content.includes('@RestController') ||
        content.includes('@Entity') || content.includes('springframework')) {
      return 'spring-boot';
    }
  }

  // Express
  if (language === 'typescript' || language === 'javascript') {
    if (content.includes('express') || content.includes('app.get(') || content.includes('app.post(')) {
      return 'express';
    }
    if (content.includes('@nestjs')) {
      return 'nest';
    }
  }

  return 'none';
}

// ============================================================================
// Artifact Kind Classification
// ============================================================================

function classifyArtifact(
  filePath: string,
  _content: string,
  language: ArtifactLanguage,
  framework: ArtifactFramework,
): ArtifactKind {
  const name = basename(filePath).toLowerCase();
  const dir = filePath.toLowerCase();

  // Stories
  if (name.includes('.stories.') || name.includes('.story.')) {
    return 'story-example';
  }

  // Routes / Pages
  if (framework === 'nextjs' || framework === 'react') {
    if (dir.includes('/app/') || dir.includes('/pages/')) {
      if (name === 'page.tsx' || name === 'page.jsx' || name === 'page.ts' || name === 'page.js') {
        return 'page-route';
      }
      if (name === 'layout.tsx' || name === 'layout.jsx' || name === 'layout.ts' || name === 'layout.js') {
        return 'page-route'; // Layout is part of page/route structure
      }
    }
  }

  // Components
  if ((language === 'tsx' || language === 'jsx') && !name.includes('.test.') && !name.includes('.spec.')) {
    const isPascalCase = name.charAt(0) === name.charAt(0).toUpperCase();
    const hasComponentPattern = _content.includes('export default') || _content.includes('export function') ||
      _content.includes('export const') || _content.includes('React.FC') || _content.includes('=>');
    if (isPascalCase || hasComponentPattern) {
      return 'component-implementation';
    }
  }

  // Styles / Tokens
  if (language === 'css' || language === 'scss') {
    if (dir.includes('/tokens/') || dir.includes('/theme/') || dir.includes('/design-system/')) {
      return 'token-theme-style';
    }
    if (name.includes('token') || name.includes('theme') || name.includes('color') || name.includes('palette')) {
      return 'token-theme-style';
    }
    return 'token-theme-style';
  }

  // Prisma / DB
  if (language === 'prisma') {
    return 'db-schema-migration';
  }
  if (language === 'sql') {
    if (dir.includes('/migrations/') || dir.includes('/migration/')) {
      return 'db-schema-migration';
    }
    return 'db-schema-migration';
  }

  // API schemas
  if (name.includes('openapi') || name.includes('swagger') || name.includes('api.')) {
    if (language === 'yaml' || language === 'json') {
      return 'api-schema';
    }
  }

  // Configuration / Build
  if (name === 'package.json' || name === 'tsconfig.json' || name === 'dockerfile' ||
      name.endsWith('.config.') || name === 'dockerfile' || name.includes('makefile')) {
    return 'configuration-build';
  }

  // CI/CD
  if (dir.includes('/.github/workflows/') || dir.includes('/.gitlab-ci/') || dir.includes('/jenkins/')) {
    return 'workflow-ci-cd';
  }

  // State management
  if ((language === 'typescript' || language === 'javascript') &&
      (name.includes('store') || name.includes('slice') || name.includes('reducer') ||
       name.includes('context') || name.includes('provider'))) {
    return 'state-management';
  }

  // Domain / Service code
  if (language === 'java' || language === 'typescript' || language === 'javascript') {
    if (dir.includes('/service/') || dir.includes('/domain/') || dir.includes('/controller/') ||
        dir.includes('/repository/') || dir.includes('/usecase/') || dir.includes('/handler/')) {
      return 'domain-service-code';
    }
  }

  // Scripts
  if (language === 'shell' || language === 'python' || (language === 'javascript' && name.includes('.cli.'))) {
    return 'script-utility';
  }

  return 'unknown-manual';
}

// ============================================================================
// Import/Export Summary Extraction
// ============================================================================

function extractImportExportSummary(content: string, language: ArtifactLanguage): ImportExportSummary {
  const imports: ImportExportSummary['imports'] = [];
  const exports: ImportExportSummary['exports'] = [];

  if (language === 'typescript' || language === 'tsx' || language === 'javascript' || language === 'jsx') {
    // ES module imports
    const importRe = /import\s+(?:(\{[^}]*\})|(\*\s+as\s+\w+)|(\w+))?\s*from\s+['"]([^'"]+)['"]/g;
    let match: RegExpExecArray | null;
    while ((match = importRe.exec(content)) !== null) {
      const source = match[4] as string;
      const specifiers: string[] = [];
      if (match[1]) {
        const named = match[1].replace(/[{}]/g, '').split(',').map(s => s.trim()).filter(Boolean);
        specifiers.push(...named);
      }
      if (match[2]) specifiers.push('*');
      if (match[3]) specifiers.push(match[3]);

      imports.push({
        source,
        specifiers,
        isRelative: source.startsWith('.') || source.startsWith('/'),
      });
    }

    // Exports
    const exportRe = /export\s+(?:default\s+(?:function|class|const|let|var)?\s*(\w+)?|(?:const|let|var|function|class)\s+(\w+)|\{([^}]*)\})/g;
    while ((match = exportRe.exec(content)) !== null) {
      if (match[0].includes('default')) {
        exports.push({ name: match[1] ?? 'default', kind: 'default' });
      } else if (match[3]) {
        const names = match[3].split(',').map(s => s.trim()).filter(Boolean);
        for (const n of names) {
          exports.push({ name: n, kind: 'named' });
        }
      } else if (match[2]) {
        exports.push({ name: match[2], kind: 'named' });
      }
    }
  }

  return { imports, exports };
}

// ============================================================================
// Extractor Eligibility
// ============================================================================

function determineExtractorEligibility(
  kind: ArtifactKind,
  language: ArtifactLanguage,
  framework: ArtifactFramework,
): ExtractorEligibility[] {
  const eligibility: ExtractorEligibility[] = [];

  const add = (id: string, eligible: boolean, reason?: string) =>
    eligibility.push({ extractorId: id, eligible, reason });

  switch (kind) {
    case 'component-implementation':
      add('typescript-component', language === 'tsx' || language === 'jsx', 'Requires TSX/JSX');
      add('storybook-csf', false, 'Not a story file');
      break;
    case 'story-example':
      add('storybook-csf', true);
      add('typescript-component', true);
      break;
    case 'page-route':
      add('typescript-page', language === 'tsx' || language === 'typescript', 'Requires TypeScript');
      add('typescript-route', framework === 'nextjs' || framework === 'react', 'Requires React/Next.js');
      break;
    case 'db-schema-migration':
      add('prisma-schema', language === 'prisma', 'Requires Prisma schema');
      add('sql-migration', language === 'sql', 'Requires SQL');
      break;
    case 'token-theme-style':
      add('style-token', language === 'css' || language === 'scss', 'Requires CSS/SCSS');
      break;
    case 'state-management':
      add('state-store', language === 'typescript' || language === 'javascript', 'Requires JS/TS');
      break;
    case 'api-schema':
      add('openapi-schema', language === 'yaml' || language === 'json', 'Requires YAML/JSON');
      break;
    case 'workflow-ci-cd':
      add('ci-cd-workflow', language === 'yaml', 'Requires YAML');
      break;
    default:
      add('generic-ast', true);
      break;
  }

  return eligibility;
}

// ============================================================================
// Checksum
// ============================================================================

function computeChecksum(content: string): string {
  return createHash('sha256').update(content, 'utf-8').digest('hex');
}

// ============================================================================
// File Walker
// ============================================================================

async function* walkDirectory(
  dir: string,
  root: string,
  config: ScannerConfig,
): AsyncGenerator<{ relativePath: string; absolutePath: string }> {
  const entries = await readdir(dir, { withFileTypes: true });

  for (const entry of entries) {
    const absolutePath = join(dir, entry.name);
    const relativePath = relative(root, absolutePath);

    if (entry.isDirectory()) {
      // Check exclusion patterns
      const isExcluded = config.excludeGlobs.some(pattern =>
        new RegExp(pattern.replace(/\*\*/g, '.*').replace(/\*/g, '[^/]*').replace(/\//g, '\\/')).test(relativePath)
      );
      if (isExcluded) continue;

      yield* walkDirectory(absolutePath, root, config);
    } else if (entry.isFile() || (entry.isSymbolicLink() && config.followSymlinks)) {
      const isExcluded = config.excludeGlobs.some(pattern =>
        new RegExp(pattern.replace(/\*\*/g, '.*').replace(/\*/g, '[^/]*').replace(/\//g, '\\/')).test(relativePath)
      );
      if (!isExcluded) {
        yield { relativePath, absolutePath };
      }
    }
  }
}

// ============================================================================
// Scan Single File
// ============================================================================

async function scanFile(
  relativePath: string,
  absolutePath: string,
  config: ScannerConfig,
): Promise<ArtifactRecord | null> {
  try {
    const stats = await stat(absolutePath);
    if (!stats.isFile()) return null;
    if (stats.size > config.maxFileSizeBytes) return null;

    const content = await readFile(absolutePath, 'utf-8');
    const language = detectLanguage(absolutePath);
    const framework = detectFramework(absolutePath, content, language);
    const kind = classifyArtifact(absolutePath, content, language, framework);
    const importExport = extractImportExportSummary(content, language);
    const eligibility = determineExtractorEligibility(kind, language, framework);
    const checksum = computeChecksum(content);

    return {
      id: crypto.randomUUID(),
      relativePath,
      absolutePath,
      kind,
      language,
      framework,
      extractorEligibility: eligibility,
      importExportSummary: importExport,
      checksum,
      sizeBytes: stats.size,
      lastModifiedAt: stats.mtime.toISOString(),
    };
  } catch (_err: unknown) {
    return null;
  }
}

// ============================================================================
// Public API: Scan Repository
// ============================================================================

export async function scanRepository(
  config: Partial<ScannerConfig> = {},
): Promise<ArtifactInventory> {
  const mergedConfig = { ...DEFAULT_SCANNER_CONFIG, ...config };
  const artifacts: ArtifactRecord[] = [];

  for await (const file of walkDirectory(mergedConfig.rootPath, mergedConfig.rootPath, mergedConfig)) {
    const record = await scanFile(file.relativePath, file.absolutePath, mergedConfig);
    if (record) {
      artifacts.push(record);
    }
  }

  // Compute summary
  const byKind: Record<string, number> = {};
  const byLanguage: Record<string, number> = {};
  const byFramework: Record<string, number> = {};
  let eligibleForExtraction = 0;

  for (const artifact of artifacts) {
    byKind[artifact.kind] = (byKind[artifact.kind] ?? 0) + 1;
    byLanguage[artifact.language] = (byLanguage[artifact.language] ?? 0) + 1;
    byFramework[artifact.framework] = (byFramework[artifact.framework] ?? 0) + 1;
    if (artifact.extractorEligibility.some((e: { eligible: boolean }) => e.eligible)) {
      eligibleForExtraction++;
    }
  }

  return {
    repositoryRoot: mergedConfig.rootPath,
    scannedAt: new Date().toISOString(),
    artifacts,
    summary: {
      totalFiles: artifacts.length,
      byKind,
      byLanguage,
      byFramework,
      eligibleForExtraction,
    },
  };
}

