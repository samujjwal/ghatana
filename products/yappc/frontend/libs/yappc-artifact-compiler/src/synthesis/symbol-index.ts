import type { GraphNode } from '../graph/types';
import type { PackageBoundary } from '../inventory/types';

export interface SymbolIndexEntry {
  readonly nodeId: string;
  readonly symbolRef: string | undefined;
  readonly label: string;
  readonly relativePath: string;
  readonly kind: string;
  readonly baseName: string;
}

export type SymbolIndex = Map<string, SymbolIndexEntry[]>;

/**
 * P1-10: Extended options for symbol resolution with package boundary and workspace metadata.
 */
export interface SymbolResolverOptions {
  readonly pathAliases?: Readonly<Record<string, string>>;
  readonly workspacePackagePrefixes?: readonly string[];
  /**
   * P1-10: Package boundaries from scanner to derive package aliases.
   * Enables resolution like "@my-package/component" to "packages/my-package/src/component".
   */
  readonly packageBoundaries?: readonly PackageBoundary[];
  /**
   * P1-10: Workspace boundaries from scanner to derive workspace aliases.
   * Enables resolution of workspace package imports.
   */
  readonly workspaceBoundaries?: readonly PackageBoundary[];
  /**
   * P1-10: tsconfig paths mappings for TypeScript path alias resolution.
   * Enables resolution like "@/components" to "src/components".
   */
  readonly tsconfigPaths?: Readonly<Record<string, string>>;
}

export function buildSymbolIndex(nodes: readonly GraphNode[]): SymbolIndex {
  const index: SymbolIndex = new Map();

  const add = (key: string, entry: SymbolIndexEntry): void => {
    const normalized = key.trim();
    if (!normalized) {
      return;
    }
    const existing = index.get(normalized);
    if (existing) {
      existing.push(entry);
    } else {
      index.set(normalized, [entry]);
    }
  };

  for (const node of nodes) {
    const filePath = node.sourceLocation.filePath;
    const base = filePath.split('/').pop() ?? '';
    const nameNoExt = base.includes('.') ? base.slice(0, base.lastIndexOf('.')) : base;

    const entry: SymbolIndexEntry = {
      nodeId: node.id,
      symbolRef: node.symbolRef,
      label: node.label,
      relativePath: filePath,
      kind: node.kind,
      baseName: nameNoExt,
    };

    if (node.symbolRef) {
      add(node.symbolRef, entry);
    }
    add(node.label, entry);
    add(filePath, entry);

    if (nameNoExt && nameNoExt !== node.label) {
      add(nameNoExt, entry);
    }

    if (!filePath.endsWith('.ts')) {
      add(filePath + '.ts', entry);
    }
    if (!filePath.endsWith('.tsx')) {
      add(filePath + '.tsx', entry);
    }
    if (!filePath.endsWith('.js')) {
      add(filePath + '.js', entry);
    }
    if (!filePath.endsWith('.jsx')) {
      add(filePath + '.jsx', entry);
    }

    const lastSlash = filePath.lastIndexOf('/');
    const dirPath = lastSlash > 0 ? filePath.slice(0, lastSlash) : '';
    if (dirPath && (nameNoExt === 'index' || nameNoExt === 'Index')) {
      add(dirPath, entry);
      add(dirPath + '/index', entry);
      add(dirPath + '/index.ts', entry);
      add(dirPath + '/index.tsx', entry);
      add(dirPath + '/index.js', entry);
      add(dirPath + '/index.jsx', entry);
    }
  }

  return index;
}

export function resolveRelativePath(importPath: string, sourcePath: string): string {
  if (!importPath.startsWith('./') && !importPath.startsWith('../')) {
    return importPath;
  }

  const sourceDir = sourcePath.slice(0, sourcePath.lastIndexOf('/'));
  const segments = sourceDir.split('/').filter(segment => segment.length > 0);
  const importSegments = importPath.split('/').filter(segment => segment !== '.');

  for (const segment of importSegments) {
    if (segment === '..') {
      segments.pop();
      continue;
    }
    segments.push(segment);
  }

  return segments.join('/');
}

/**
 * P1-10: Resolve path aliases using tsconfig paths, package boundaries, and workspace metadata.
 * Priority: tsconfig paths > package boundaries > workspace boundaries > hardcoded aliases.
 */
export function resolvePathAlias(importPath: string, options?: SymbolResolverOptions): string {
  // P1-10: Try tsconfig paths first
  if (options?.tsconfigPaths) {
    const tsconfigEntries = Object.entries(options.tsconfigPaths);
    for (const [pattern, target] of tsconfigEntries) {
      if (!pattern.endsWith('/*') || !target.endsWith('/*')) {
        continue;
      }
      const patternBase = pattern.slice(0, -2);
      const targetBase = target.slice(0, -2);
      if (importPath === patternBase || importPath.startsWith(patternBase + '/')) {
        const suffix = importPath.slice(patternBase.length);
        return `${targetBase}${suffix}`;
      }
    }
  }

  // P1-10: Try package boundaries for package alias resolution
  if (options?.packageBoundaries) {
    for (const boundary of options.packageBoundaries) {
      const packageName = boundary.name;
      const packagePath = boundary.relativePath === '.' ? '' : boundary.relativePath;
      const alias = `@${packageName}`;
      if (importPath === alias || importPath.startsWith(alias + '/')) {
        const suffix = importPath.slice(alias.length);
        const srcPath = packagePath ? `${packagePath}/src${suffix}` : `src${suffix}`;
        return srcPath.startsWith('/') ? srcPath.slice(1) : srcPath;
      }
    }
  }

  // P1-10: Try workspace boundaries for workspace package resolution
  if (options?.workspaceBoundaries) {
    for (const boundary of options.workspaceBoundaries) {
      const workspaceName = boundary.name;
      const workspacePath = boundary.relativePath === '.' ? '' : boundary.relativePath;
      if (importPath.startsWith(`${workspaceName}/`)) {
        const suffix = importPath.slice(workspaceName.length + 1);
        const srcPath = workspacePath ? `${workspacePath}/src/${suffix}` : `src/${suffix}`;
        return srcPath.startsWith('/') ? srcPath.slice(1) : srcPath;
      }
    }
  }

  // Fallback to legacy alias resolution
  const aliasEntries = Object.entries(options?.pathAliases ?? {});
  for (const [aliasPrefix, resolvedPrefix] of aliasEntries) {
    if (!aliasPrefix.endsWith('/*') || !resolvedPrefix.endsWith('/*')) {
      continue;
    }
    const aliasBase = aliasPrefix.slice(0, -2);
    const resolvedBase = resolvedPrefix.slice(0, -2);
    if (importPath === aliasBase || importPath.startsWith(aliasBase + '/')) {
      const suffix = importPath.slice(aliasBase.length);
      return `${resolvedBase}${suffix}`;
    }
  }

  if (importPath.startsWith('@/')) {
    return 'src/' + importPath.slice(2);
  }
  if (importPath.startsWith('~@/')) {
    return 'src/' + importPath.slice(3);
  }
  if (importPath.startsWith('#/')) {
    return 'src/' + importPath.slice(2);
  }
  if (importPath.startsWith('~/')) {
    return importPath.slice(2);
  }

  return importPath;
}

/**
 * P1-10: Normalize workspace package imports using workspace boundaries and package metadata.
 * Resolves imports like "my-package/lib/utils" to "packages/my-package/src/lib/utils".
 */
export function normalizeWorkspacePackageImport(importPath: string, options?: SymbolResolverOptions): string {
  // P1-10: Try workspace boundaries first for more accurate resolution
  if (options?.workspaceBoundaries) {
    for (const boundary of options.workspaceBoundaries) {
      const workspaceName = boundary.name;
      if (importPath === workspaceName || importPath.startsWith(workspaceName + '/')) {
        const suffix = importPath === workspaceName ? '' : importPath.slice(workspaceName.length + 1);
        const workspacePath = boundary.relativePath === '.' ? '' : boundary.relativePath;
        const srcPath = suffix ? `${workspacePath}/src/${suffix}` : `${workspacePath}/src`;
        return srcPath.startsWith('/') ? srcPath.slice(1) : srcPath;
      }
    }
  }

  // P1-10: Try package boundaries for package-specific resolution
  if (options?.packageBoundaries) {
    for (const boundary of options.packageBoundaries) {
      const packageName = boundary.name;
      if (importPath === packageName || importPath.startsWith(packageName + '/')) {
        const suffix = importPath === packageName ? '' : importPath.slice(packageName.length + 1);
        const packagePath = boundary.relativePath === '.' ? '' : boundary.relativePath;
        const srcPath = suffix ? `${packagePath}/src/${suffix}` : `${packagePath}/src`;
        return srcPath.startsWith('/') ? srcPath.slice(1) : srcPath;
      }
    }
  }

  // Fallback to legacy workspace package prefix resolution
  const packagePrefixes = options?.workspacePackagePrefixes ?? [];
  for (const prefix of packagePrefixes) {
    if (!importPath.startsWith(prefix + '/')) {
      continue;
    }
    const packagePath = importPath.slice(prefix.length + 1);
    const packageSegments = packagePath.split('/').filter(segment => segment.length > 0);
    if (packageSegments.length < 2) {
      continue;
    }
    const packageName = packageSegments[0]!;
    const subPath = packageSegments.slice(1).join('/');
    return `packages/${packageName}/src/${subPath}`;
  }
  return importPath;
}