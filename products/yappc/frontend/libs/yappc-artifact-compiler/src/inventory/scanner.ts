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
  type PackageBoundary,
  type SkippedArtifact,
} from './types';
import { buildDeterministicNodeId, type SnapshotRef } from '../graph/types';

/**
 * ============================================================================
 * WORKER-LOCAL REPOSITORY INVENTORY SCANNER
 * ============================================================================
 * 
 * P1: This is a worker-local TypeScript implementation of repository inventory scanning.
 * It aligns with the Java canonical RepositoryInventoryScanner contract but is intended
 * for use within TypeScript workers only (e.g., ts-extractor-worker).
 * 
 * Alignment with Java RepositoryInventoryScanner:
 * - Stable sorted walk for deterministic ordering ✓
 * - .gitignore pattern matching (simplified, same limitations as Java)
 * - Include/exclude rule support ✓
 * - Skip reasons: GITIGNORE, BINARY_FILE, VENDOR_DIRECTORY, GENERATED_FILE, FILE_TOO_LARGE, PACKAGE_BOUNDARY ✓
 * - File type classification: SOURCE, CONFIG, DOCS, TEST, ASSETS, BUILD, UNKNOWN (mapped to ArtifactKind)
 * - SHA-256 checksum computation ✓
 * - Package boundary detection ✓
 * 
 * Differences from Java canonical scanner:
 * - Uses TypeScript/Node.js filesystem APIs instead of Java NIO
 * - Additional framework detection (React, Next.js, etc.) for TS-specific use cases
 * - Import/export summary extraction (TS-specific, not in Java scanner)
 * - Extractor eligibility determination (TS-specific, not in Java scanner)
 * 
 * Production note: Like the Java scanner, .gitignore matching is simplified.
 * Full spec compliance would require integrating a library like ignore-git.
 * 
 * This scanner should NOT be used as a general-purpose inventory scanner outside of
 * TypeScript worker contexts. For Java-side inventory, use RepositoryInventoryScanner.
 */

export interface ScannerConfig {
  readonly rootPath: string;
  readonly includeGlobs: readonly string[];
  readonly excludeGlobs: readonly string[];
  readonly maxFileSizeBytes: number;
  readonly followSymlinks: boolean;
  /**
   * Snapshot reference for deterministic artifact ID generation.
   * When supplied, every artifact ID is a stable URN across repeated scans of the same commit.
   */
  readonly snapshotRef?: SnapshotRef;
  /**
   * P0: Allowed file list for scoped scanning.
   * When supplied (e.g., from snapshot.files), only files in this list are scanned.
   * This ensures the pipeline respects the inventory boundary when running from a snapshot.
   * File paths should be relative to rootPath with forward slashes.
   */
  readonly allowedFiles?: readonly string[];
  /**
   * Whether to parse .gitignore files found in the repository and honour them.
   * Defaults to true.
   */
  readonly respectGitignore?: boolean;
  /**
   * P1-7: Maximum number of concurrent file operations (reads, checksums).
   * Prevents overwhelming the filesystem with too many parallel operations.
   * Defaults to 50.
   */
  readonly concurrency?: number;
  /**
   * P1-7: Deterministic scan mode ensures consistent ordering and behavior across runs.
   * When enabled, entries are sorted by name before processing, and results are returned in a stable order.
   * Defaults to true.
   */
  readonly deterministic?: boolean;
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
    '**/.turbo/**',
    '**/.cache/**',
    '**/__pycache__/**',
    '**/vendor/**',
  ],
  maxFileSizeBytes: 10 * 1024 * 1024, // 10MB
  followSymlinks: false,
  respectGitignore: true,
  concurrency: 50,
  deterministic: true,
};

// ============================================================================
// Binary / Generated file classification
// ============================================================================

const BINARY_EXTENSIONS = new Set([
  '.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.svg',
  '.woff', '.woff2', '.ttf', '.otf', '.eot',
  '.mp3', '.mp4', '.wav', '.ogg', '.avi', '.mov', '.webm',
  '.zip', '.tar', '.gz', '.bz2', '.7z', '.rar',
  '.pdf', '.doc', '.docx', '.xls', '.xlsx',
  '.exe', '.dll', '.so', '.dylib', '.class', '.jar',
  '.bin', '.dat', '.db', '.sqlite',
]);

/**
 * P1: Returns true if the file should be treated as binary (not parsed for AST/imports).
 * 
 * This aligns with Java RepositoryInventoryScanner's BINARY_EXTENSIONS set.
 */
function isBinaryFile(filePath: string): boolean {
  return BINARY_EXTENSIONS.has(extname(filePath).toLowerCase());
}

/**
 * P1: Returns true if the file is auto-generated and should not be reverse-engineered.
 * 
 * This aligns with Java RepositoryInventoryScanner's GENERATED_FILE skip reason.
 * Heuristics: .d.ts files, generated comment markers, lockfile extensions, build outputs.
 */
function isGeneratedFile(filePath: string, firstBytes: string): boolean {
  const name = basename(filePath);
  const ext = extname(filePath).toLowerCase();
  const dir = filePath.replace(/\\/g, '/');

  // Declaration files emitted by tsc
  if (name.endsWith('.d.ts') || name.endsWith('.d.mts') || name.endsWith('.d.cts')) return true;

  // Lockfiles
  if (name === 'package-lock.json' || name === 'yarn.lock' || name === 'pnpm-lock.yaml' ||
      name === 'Cargo.lock' || name === 'Gemfile.lock' || name === 'go.sum') return true;

  // Build artifact directories (belt-and-suspenders beyond excludeGlobs)
  if (dir.includes('/.next/') || dir.includes('/dist/') || dir.includes('/out/') ||
      dir.includes('/build/') || dir.includes('/coverage/') || dir.includes('/__generated__/') ||
      dir.includes('/generated/') || dir.includes('/stubs/')) return true;

  // Map files
  if (ext === '.map') return true;

  // Explicit generated comment markers in first 512 bytes
  const header = firstBytes.slice(0, 512);
  if (
    header.includes('// @generated') ||
    header.includes('/* @generated */') ||
    header.includes('// DO NOT EDIT') ||
    header.includes('// Code generated') ||
    header.includes('// GENERATED CODE') ||
    header.includes('// AUTO-GENERATED') ||
    header.includes('# AUTO-GENERATED') ||
    header.includes('# DO NOT EDIT')
  ) return true;

  return false;
}

// ============================================================================
// Package / Workspace boundary detection
// ============================================================================

/**
 * Manifest file name -> package system mapping.
 */
const MANIFEST_SYSTEM_MAP: ReadonlyMap<string, PackageBoundary['system']> = new Map([
  ['package.json', 'npm'],
  ['pnpm-workspace.yaml', 'pnpm'],
  ['build.gradle', 'gradle'],
  ['build.gradle.kts', 'gradle'],
  ['pom.xml', 'maven'],
  ['Cargo.toml', 'cargo'],
]);

const WORKSPACE_MANIFESTS = new Set(['pnpm-workspace.yaml', 'nx.json', 'turbo.json', 'lerna.json']);

/**
 * P1: Scans the repository root once to collect all package and workspace boundary manifests.
 * Returns two lists: package boundaries and workspace boundaries.
 * 
 * This aligns with Java RepositoryInventoryScanner's package boundary detection.
 */
async function detectPackageBoundaries(
  rootPath: string,
  maxDepth: number = 8,
): Promise<{ packageBoundaries: PackageBoundary[]; workspaceBoundaries: PackageBoundary[] }> {
  const packageBoundaries: PackageBoundary[] = [];
  const workspaceBoundaries: PackageBoundary[] = [];

  async function walk(dir: string, depth: number): Promise<void> {
    if (depth > maxDepth) return;
    let entries: import('fs').Dirent[];
    try {
      entries = (await readdir(dir, { withFileTypes: true })) as import('fs').Dirent[];
    } catch {
      return;
    }
    for (const entry of entries) {
      const entryName = entry.name as string;
      if (entry.isDirectory()) {
        if (entryName === 'node_modules' || entryName === '.git' || entryName === 'dist' ||
            entryName === 'build' || entryName === 'target' || entryName === '.next') continue;
        await walk(join(dir, entryName), depth + 1);
      } else if (entry.isFile()) {
        const system = MANIFEST_SYSTEM_MAP.get(entryName);
        if (system) {
          const absoluteManifest = join(dir, entryName);
          const relManifest = relative(rootPath, absoluteManifest).replace(/\\/g, '/');
          const relDir = relative(rootPath, dir).replace(/\\/g, '/') || '.';
          let pkgName = relDir;
          if (entryName === 'package.json') {
            try {
              const raw = await readFile(absoluteManifest, 'utf-8');
              const parsed = JSON.parse(raw) as { name?: string };
              if (parsed.name) pkgName = parsed.name;
            } catch { /* keep relDir */ }
          } else if (entryName === 'build.gradle' || entryName === 'build.gradle.kts' || entryName === 'Cargo.toml') {
            try {
              const raw = await readFile(absoluteManifest, 'utf-8');
              const nameMatch = /^name\s*=\s*["']?([\w-]+)["']?/m.exec(raw);
              if (nameMatch?.[1]) pkgName = nameMatch[1];
            } catch { /* keep relDir */ }
          }
          const boundary: PackageBoundary = {
            name: pkgName,
            relativePath: relDir,
            system: entryName === 'package.json' ? await detectNpmSystem(dir) : system,
            manifestFile: relManifest,
          };
          if (WORKSPACE_MANIFESTS.has(entryName)) {
            workspaceBoundaries.push(boundary);
          } else {
            packageBoundaries.push(boundary);
          }
        }
      }
    }
  }

  await walk(rootPath, 0);
  return { packageBoundaries, workspaceBoundaries };
}

async function detectNpmSystem(dir: string): Promise<PackageBoundary['system']> {
  const { existsSync } = await import('fs');
  const { resolve } = await import('path');

  // Check for lockfile markers in the current directory and ancestors
  let currentDir = dir;
  const root = resolve(dir, '/');
  const maxDepth = 10;
  let depth = 0;

  while (currentDir !== root && depth < maxDepth) {
    // Check for pnpm-workspace.yaml (indicates pnpm workspace)
    if (existsSync(resolve(currentDir, 'pnpm-workspace.yaml'))) {
      return 'pnpm';
    }
    // Check for pnpm-lock.yaml (indicates pnpm package)
    if (existsSync(resolve(currentDir, 'pnpm-lock.yaml'))) {
      return 'pnpm';
    }
    // Check for yarn.lock (indicates yarn)
    if (existsSync(resolve(currentDir, 'yarn.lock'))) {
      return 'yarn';
    }
    // Check for package-lock.json (indicates npm)
    if (existsSync(resolve(currentDir, 'package-lock.json'))) {
      return 'npm';
    }

    currentDir = resolve(currentDir, '..');
    depth++;
  }

  // Default to npm if no lockfile found
  return 'npm';
}

// ============================================================================
// .gitignore parsing
// ============================================================================

/**
 * P1: Parses a .gitignore file into a list of RegExp matchers.
 * 
 * This is a simplified implementation for P1 completion, matching the limitations
 * of the Java RepositoryInventoryScanner. Full spec compliance would require
 * integrating a library like ignore-git.
 * 
 * Supports: negation (!), directory anchors, wildcards (*, ?), globstar (**).
 * 
 * Current limitations (same as Java scanner):
 * - Does not handle character classes [abc]
 * - Does not handle escaped special characters correctly in all cases
 * - Does not handle trailing-slash-only directory patterns correctly
 */
function parseGitignorePatterns(content: string, baseRelPath: string): Array<{ pattern: RegExp; negated: boolean }> {
  const results: Array<{ pattern: RegExp; negated: boolean }> = [];
  const base = baseRelPath ? baseRelPath + '/' : '';

  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;

    const negated = line.startsWith('!');
    const pat = negated ? line.slice(1) : line;
    if (!pat) continue;

    // Convert gitignore glob to regex
    const anchored = pat.startsWith('/');
    const dirOnly = pat.endsWith('/');
    const clean = pat.replace(/^\//,'').replace(/\/$/,'');

    let regexStr = base + (anchored ? '' : '(?:.*/)?' );
    regexStr += clean
      .replace(/[.+^${}()|[\]\\]/g, '\\$&')
      .replace(/\*\*\//g, '(?:.*/)?')
      .replace(/\*\*/g, '.*')
      .replace(/\*/g, '[^/]*')
      .replace(/\?/g, '[^/]');
    if (dirOnly) {
      regexStr += '(?:/.*)?';
    } else {
      regexStr += '(?:/.*)?';
    }

    try {
      results.push({ pattern: new RegExp(`^${regexStr}$`), negated });
    } catch {
      // Skip malformed patterns
    }
  }

  return results;
}

/**
 * P1: Collects all .gitignore files in the repository and returns a combined matcher function.
 * The matcher returns true if a relative path should be ignored.
 * 
 * This aligns with Java RepositoryInventoryScanner's gitignore matching logic.
 */
async function buildGitignoreMatcher(
  rootPath: string,
): Promise<(relativePath: string) => boolean> {
  const allPatterns: Array<{ pattern: RegExp; negated: boolean }> = [];

  async function collect(dir: string): Promise<void> {
    const gitignorePath = join(dir, '.gitignore');
    const relDir = relative(rootPath, dir).replace(/\\/g, '/');
    try {
      const content = await readFile(gitignorePath, 'utf-8');
      const patterns = parseGitignorePatterns(content, relDir);
      allPatterns.push(...patterns);
    } catch {
      // No .gitignore in this directory — fine
    }
    let collectEntries: import('fs').Dirent[];
    try {
      collectEntries = (await readdir(dir, { withFileTypes: true })) as import('fs').Dirent[];
    } catch {
      return;
    }
    for (const entry of collectEntries) {
      if (entry.isDirectory()) {
        const n = entry.name as string;
        if (n === '.git' || n === 'node_modules' || n === 'dist' || n === 'build' || n === 'target') continue;
        await collect(join(dir, n));
      }
    }
  }

  await collect(rootPath);

  return (relativePath: string): boolean => {
    const normalized = relativePath.replace(/\\/g, '/');
    let ignored = false;
    for (const { pattern, negated } of allPatterns) {
      if (pattern.test(normalized)) {
        ignored = !negated;
      }
    }
    return ignored;
  };
}

// ============================================================================
// Deterministic artifact ID
// ============================================================================

function buildArtifactId(snapshotRef: SnapshotRef | undefined, relativePath: string): string {
  return buildDeterministicNodeId(snapshotRef, relativePath, 'file', relativePath);
}

// ============================================================================
// Resolve package boundary for a given relative path
// ============================================================================

function resolvePackageBoundary(
  relativePath: string,
  packageBoundaries: PackageBoundary[],
): PackageBoundary | undefined {
  const normalized = relativePath.replace(/\\/g, '/');
  // Find the deepest (longest path) matching package boundary
  let best: PackageBoundary | undefined;
  let bestLen = -1;
  for (const boundary of packageBoundaries) {
    const bp = boundary.relativePath === '.' ? '' : boundary.relativePath + '/';
    if (bp === '' || normalized.startsWith(bp) || boundary.relativePath === '.' ) {
      const len = bp.length;
      if (len > bestLen) {
        bestLen = len;
        best = boundary;
      }
    }
  }
  return best;
}

// ============================================================================
// Language Detection (Worker-Local Extension)
// ============================================================================
/**
 * P1: Language detection is a worker-local extension for TS-specific use cases.
 * The Java canonical scanner uses FileType classification instead.
 * 
 * Mapping to Java RepositoryInventoryScanner.FileType:
 * - typescript, javascript, java, python, go, rust → SOURCE
 * - yaml, json, xml, properties → CONFIG
 * - markdown, txt, rst → DOCS
 * - (no direct mapping for test files - uses filename patterns)
 * - css, scss, less → ASSETS
 * - (no direct mapping for BUILD - uses filename patterns)
 */

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
// Framework Detection (Worker-Local Extension)
// ============================================================================
/**
 * P1: Framework detection is a worker-local extension for TS-specific use cases.
 * The Java canonical scanner does not include framework detection.
 * 
 * This is used to determine ArtifactKind more precisely in TS contexts.
 */

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
// Artifact Kind Classification (Worker-Local Extension)
// ============================================================================
/**
 * P1: ArtifactKind classification is a worker-local extension for TS-specific use cases.
 * 
 * This provides more granular classification than Java RepositoryInventoryScanner.FileType.
 * The Java scanner uses broader categories (SOURCE, CONFIG, DOCS, TEST, ASSETS, BUILD, UNKNOWN).
 * 
 * This classification is used by TypeScript extractors to determine which extractor
 * should process a given artifact.
 */

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
// Import/Export Summary Extraction (Worker-Local Extension)
// ============================================================================
/**
 * P1: Import/export summary extraction is a worker-local extension for TS-specific use cases.
 * The Java canonical scanner does not include import/export analysis.
 * 
 * This is used by TypeScript extractors to build dependency graphs.
 */

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
// Extractor Eligibility (Worker-Local Extension)
// ============================================================================
/**
 * P1: Extractor eligibility determination is a worker-local extension for TS-specific use cases.
 * The Java canonical scanner does not include extractor eligibility logic.
 * 
 * This is used to determine which TypeScript extractors can process a given artifact.
 */

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
/**
 * P1: SHA-256 checksum computation aligns with Java RepositoryInventoryScanner.
 * Both use SHA-256 for content integrity verification.
 */

function computeChecksum(content: string): string {
  return createHash('sha256').update(content, 'utf-8').digest('hex');
}

function globToRegExp(pattern: string): RegExp {
  const escaped = pattern
    .replace(/[.+^${}()|[\]\\]/g, '\\$&')
    .replace(/\*\*\//g, '(?:.*/)?')
    .replace(/\*\*/g, '.*')
    .replace(/\*/g, '[^/]*');
  return new RegExp(`^${escaped}$`);
}

function matchesAnyGlob(relativePath: string, patterns: readonly string[]): boolean {
  return patterns.some(pattern => globToRegExp(pattern).test(relativePath));
}

function findFirstMatchingGlob(relativePath: string, patterns: readonly string[]): string | undefined {
  return patterns.find(pattern => globToRegExp(pattern).test(relativePath));
}

function createSkippedArtifact(
  relativePath: string,
  source: SkippedArtifact['source'],
  reason: string,
  options: {
    readonly matchedPattern?: string;
    readonly sizeBytes?: number;
  } = {},
): SkippedArtifact {
  const base = {
    relativePath,
    reason,
    source,
    detectedAt: new Date().toISOString(),
  };

  if (options.matchedPattern !== undefined && options.sizeBytes !== undefined) {
    return { ...base, matchedPattern: options.matchedPattern, sizeBytes: options.sizeBytes };
  }
  if (options.matchedPattern !== undefined) {
    return { ...base, matchedPattern: options.matchedPattern };
  }
  if (options.sizeBytes !== undefined) {
    return { ...base, sizeBytes: options.sizeBytes };
  }
  return base;
}

// ============================================================================
// File Walker
// ============================================================================
/**
 * P1: File walker with deterministic sorting aligns with Java RepositoryInventoryScanner.
 * 
 * Both scanners use sorted walks for deterministic ordering across OS/filesystem.
 * The Java scanner uses Files.walk().sorted(), this uses readdir with sort().
 */

async function* walkDirectory(
  dir: string,
  root: string,
  config: ScannerConfig,
  gitignoreMatcher: (rel: string) => boolean,
): AsyncGenerator<
  | { kind: 'file'; relativePath: string; absolutePath: string }
  | { kind: 'skip'; skippedArtifact: SkippedArtifact }
> {
  let entries: import('fs').Dirent[];
  try {
    entries = (await readdir(dir, { withFileTypes: true })) as import('fs').Dirent[];
  } catch {
    return;
  }

  // P1-7: Sort entries by name for deterministic ordering when deterministic mode is enabled
  if (config.deterministic !== false) {
    entries.sort((a, b) => a.name.localeCompare(b.name));
  }

  // P0: Build allowed files set for efficient lookup when provided
  const allowedFiles = config.allowedFiles
    ? config.allowedFiles.map(p => p.replace(/\\/g, '/'))
    : null;
  const allowedFilesSet = allowedFiles
    ? new Set(allowedFiles)
    : null;

  for (const entry of entries) {
    const entryName = entry.name as string;
    const absolutePath = join(dir, entryName);
    const relativePath = relative(root, absolutePath).replace(/\\/g, '/');
    
    // P0: Skip files not in allowedFiles when scoped scanning is enabled
    if (allowedFilesSet !== null) {
      if (entry.isDirectory()) {
        const directoryPrefix = `${relativePath}/`;
        const shouldDescend = allowedFiles !== null && allowedFiles.some(
          path => path === relativePath || path.startsWith(directoryPrefix),
        );
        if (!shouldDescend) {
          continue;
        }
      } else if (!allowedFilesSet.has(relativePath)) {
        // When allowedFiles is set, non-listed files are silently skipped.
        continue;
      }
    }
    
    if (relativePath.includes('/vendor/') || relativePath.startsWith('vendor/')) {
      yield {
        kind: 'skip',
        skippedArtifact: createSkippedArtifact(
          relativePath,
          'vendor',
          'Skipped vendor-managed source path.',
        ),
      };
      continue;
    }
    const excludePattern = findFirstMatchingGlob(relativePath, config.excludeGlobs);
    const ignoredByGitignore = config.respectGitignore !== false && gitignoreMatcher(relativePath);

    if (entry.isDirectory()) {
      if (excludePattern) {
        yield {
          kind: 'skip',
          skippedArtifact: createSkippedArtifact(
            relativePath,
            'excludeGlobs',
            'Excluded by configured glob pattern.',
            { matchedPattern: excludePattern },
          ),
        };
        continue;
      }
      if (ignoredByGitignore) {
        yield {
          kind: 'skip',
          skippedArtifact: createSkippedArtifact(
            relativePath,
            'gitignore',
            'Excluded by .gitignore rule.',
          ),
        };
        continue;
      }
      yield* walkDirectory(absolutePath, root, config, gitignoreMatcher);
    } else if (entry.isSymbolicLink() && !config.followSymlinks) {
      yield {
        kind: 'skip',
        skippedArtifact: createSkippedArtifact(
          relativePath,
          'symlink',
          'Skipped symbolic link because followSymlinks is disabled.',
        ),
      };
    } else if (entry.isFile() || (entry.isSymbolicLink() && config.followSymlinks)) {
      const isIncluded = matchesAnyGlob(relativePath, config.includeGlobs);
      if (excludePattern) {
        yield {
          kind: 'skip',
          skippedArtifact: createSkippedArtifact(
            relativePath,
            'excludeGlobs',
            'Excluded by configured glob pattern.',
            { matchedPattern: excludePattern },
          ),
        };
      } else if (ignoredByGitignore) {
        yield {
          kind: 'skip',
          skippedArtifact: createSkippedArtifact(
            relativePath,
            'gitignore',
            'Excluded by .gitignore rule.',
          ),
        };
      } else if (isIncluded) {
        yield { kind: 'file', relativePath, absolutePath };
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
  packageBoundaries: PackageBoundary[],
): Promise<{ record: ArtifactRecord | null; skippedArtifact?: SkippedArtifact }> {
  try {
    const stats = await stat(absolutePath);
    if (!stats.isFile()) {
      return {
        record: null,
        skippedArtifact: createSkippedArtifact(
          relativePath,
          'readError',
          'Path could not be scanned as a regular file.',
        ),
      };
    }

    // P1-7: Use new skip source for large files
    if (stats.size > config.maxFileSizeBytes) {
      return {
        record: null,
        skippedArtifact: createSkippedArtifact(
          relativePath,
          'maxFileSize',
          'Skipped file because it exceeds the configured size limit.',
          { sizeBytes: stats.size },
        ),
      };
    }

    const binary = isBinaryFile(absolutePath);

    // P1-7: Use new skip source for binary files
    if (binary) {
      // Compute SHA-256 of binary content for accurate checksum
      const binaryContent = await readFile(absolutePath);
      const binaryChecksum = createHash('sha256').update(binaryContent).digest('hex');

      return { record: {
        id: buildArtifactId(config.snapshotRef, relativePath),
        relativePath,
        absolutePath,
        kind: 'unknown-manual',
        language: 'unknown',
        framework: 'unknown',
        isGenerated: false,
        isBinary: true,
        packageBoundary: resolvePackageBoundary(relativePath, packageBoundaries),
        extractorEligibility: [],
        importExportSummary: { imports: [], exports: [] },
        checksum: binaryChecksum,
        sizeBytes: stats.size,
        lastModifiedAt: stats.mtime.toISOString(),
        // P1-6: Add new optional fields
        sourceFileRef: absolutePath,
        contentChecksum: binaryChecksum,
        classificationConfidence: 1.0,
      } };
    }

    const content = await readFile(absolutePath, 'utf-8');
    const generated = isGeneratedFile(absolutePath, content);
    const language = detectLanguage(absolutePath);
    const framework = detectFramework(absolutePath, content, language);
    const kind = classifyArtifact(absolutePath, content, language, framework);
    // Skip import/export extraction for generated files to avoid polluting the graph
    const importExport = generated
      ? { imports: [], exports: [] }
      : extractImportExportSummary(content, language);
    const eligibility = generated ? [] : determineExtractorEligibility(kind, language, framework);
    const checksum = computeChecksum(content);

    return { record: {
      id: buildArtifactId(config.snapshotRef, relativePath),
      relativePath,
      absolutePath,
      kind,
      language,
      framework,
      isGenerated: generated,
      isBinary: false,
      packageBoundary: resolvePackageBoundary(relativePath, packageBoundaries),
      extractorEligibility: eligibility,
      importExportSummary: importExport,
      checksum,
      sizeBytes: stats.size,
      lastModifiedAt: stats.mtime.toISOString(),
      // P1-6: Add new optional fields
      sourceFileRef: absolutePath,
      contentChecksum: checksum,
      classificationConfidence: 0.9, // Default confidence for classified files
    } };
  } catch (_err: unknown) {
    return {
      record: null,
      skippedArtifact: createSkippedArtifact(
        relativePath,
        'readError',
        'Skipped file because it could not be read.',
      ),
    };
  }
}

// ============================================================================
// Public API: Scan Repository
// ============================================================================

export async function scanRepository(
  config: Partial<ScannerConfig> = {},
): Promise<ArtifactInventory> {
  const mergedConfig: ScannerConfig = { ...DEFAULT_SCANNER_CONFIG, ...config };
  const artifacts: ArtifactRecord[] = [];
  const skippedArtifacts: SkippedArtifact[] = [];
  let ignoredFiles = 0;

  // Build .gitignore matcher once before walking
  const gitignoreMatcher = mergedConfig.respectGitignore !== false
    ? await buildGitignoreMatcher(mergedConfig.rootPath)
    : (_rel: string) => false;

  // Detect package + workspace boundaries once before walking
  const { packageBoundaries, workspaceBoundaries } = await detectPackageBoundaries(
    mergedConfig.rootPath,
  );

  // P1-7: Bounded concurrency for file scanning with constant-size active worker set.
  const concurrency = Math.max(1, mergedConfig.concurrency ?? 50);
  const activeWorkers = new Set<Promise<void>>();

  for await (const file of walkDirectory(
    mergedConfig.rootPath,
    mergedConfig.rootPath,
    mergedConfig,
    gitignoreMatcher,
  )) {
    if (file.kind === 'skip') {
      skippedArtifacts.push(file.skippedArtifact);
      ignoredFiles++;
      continue;
    }

    const worker = (async () => {
      const record = await scanFile(
        file.relativePath,
        file.absolutePath,
        mergedConfig,
        packageBoundaries,
      );
      if (record.record) {
        artifacts.push(record.record);
      } else {
        if (record.skippedArtifact) {
          skippedArtifacts.push(record.skippedArtifact);
        }
        ignoredFiles++;
      }
    })().finally(() => {
      activeWorkers.delete(worker);
    });

    activeWorkers.add(worker);
    if (activeWorkers.size >= concurrency) {
      await Promise.race(activeWorkers);
    }
  }

  // Wait for all remaining scans to complete
  await Promise.all(activeWorkers);

  // Compute summary
  const byKind: Record<string, number> = {};
  const byLanguage: Record<string, number> = {};
  const byFramework: Record<string, number> = {};
  let eligibleForExtraction = 0;
  let generatedFiles = 0;
  let binaryFiles = 0;

  for (const artifact of artifacts) {
    byKind[artifact.kind] = (byKind[artifact.kind] ?? 0) + 1;
    byLanguage[artifact.language] = (byLanguage[artifact.language] ?? 0) + 1;
    byFramework[artifact.framework] = (byFramework[artifact.framework] ?? 0) + 1;
    if (artifact.extractorEligibility.some((e: { eligible: boolean }) => e.eligible)) {
      eligibleForExtraction++;
    }
    if (artifact.isGenerated) generatedFiles++;
    if (artifact.isBinary) binaryFiles++;
  }

  // P1-7: Sort artifacts and skippedArtifacts for deterministic output when deterministic mode is enabled
  if (mergedConfig.deterministic !== false) {
    artifacts.sort((a, b) => a.relativePath.localeCompare(b.relativePath));
    skippedArtifacts.sort((a, b) => a.relativePath.localeCompare(b.relativePath));
  }

  return {
    repositoryRoot: mergedConfig.rootPath,
    scannedAt: new Date().toISOString(),
    snapshotRef: mergedConfig.snapshotRef,
    artifacts,
    skippedArtifacts,
    packageBoundaries,
    workspaceBoundaries,
    summary: {
      totalFiles: artifacts.length,
      byKind,
      byLanguage,
      byFramework,
      eligibleForExtraction,
      generatedFiles,
      binaryFiles,
      ignoredFiles,
    },
  };
}
