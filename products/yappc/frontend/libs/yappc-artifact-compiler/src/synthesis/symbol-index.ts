import type { GraphNode } from '../graph/types';

export interface SymbolIndexEntry {
  readonly nodeId: string;
  readonly symbolRef: string | undefined;
  readonly label: string;
  readonly relativePath: string;
  readonly kind: string;
  readonly baseName: string;
}

export type SymbolIndex = Map<string, SymbolIndexEntry[]>;

export interface SymbolResolverOptions {
  readonly pathAliases?: Readonly<Record<string, string>>;
  readonly workspacePackagePrefixes?: readonly string[];
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

export function resolvePathAlias(importPath: string, options?: SymbolResolverOptions): string {
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

export function normalizeWorkspacePackageImport(importPath: string, options?: SymbolResolverOptions): string {
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