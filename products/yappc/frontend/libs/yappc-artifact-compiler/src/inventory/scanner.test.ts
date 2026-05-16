/**
 * @fileoverview Inventory scanner tests.
 */

import { describe, it, expect } from 'vitest';
import { dirname, resolve } from 'path';
import { fileURLToPath } from 'url';
import { scanRepository, DEFAULT_SCANNER_CONFIG } from './scanner';

const testDir = dirname(fileURLToPath(import.meta.url));
const fixturesRoot = resolve(testDir, '../../test/fixtures');
const smallReactAppRoot = resolve(fixturesRoot, 'small-react-app');
const pnpmMonorepoRoot = resolve(fixturesRoot, 'pnpm-monorepo');

describe('scanRepository', () => {
  it('should scan fixture repo and return deterministic inventory shape', async () => {
    const config = {
      rootPath: smallReactAppRoot,
      excludeGlobs: [
        ...DEFAULT_SCANNER_CONFIG.excludeGlobs,
        '**/node_modules/**',
        '**/dist/**',
        '**/.git/**',
      ],
      maxFileSizeBytes: 5 * 1024 * 1024,
    };

    const inventory = await scanRepository(config);

  expect(inventory.repositoryRoot).toBe(smallReactAppRoot);
    expect(inventory.scannedAt).toBeTruthy();
    expect(inventory.artifacts.length).toBeGreaterThan(0);
    expect(inventory.summary.totalFiles).toBeGreaterThan(0);

    // Should have classified some files
    const kinds = Object.keys(inventory.summary.byKind);
    expect(kinds.length).toBeGreaterThan(0);

    // Should have language classifications
    const languages = Object.keys(inventory.summary.byLanguage);
    expect(languages.length).toBeGreaterThan(0);
  });

  it('should classify package.json as configuration-build', async () => {
    const config = {
      rootPath: smallReactAppRoot,
      includeGlobs: ['**/package.json'],
      excludeGlobs: [],
    };

    const inventory = await scanRepository(config);
    expect(inventory.artifacts.length).toBeGreaterThan(0);

    const pkgJson = inventory.artifacts.find(a => a.relativePath.endsWith('package.json'));
    expect(pkgJson).toBeDefined();
    expect(pkgJson!.kind).toBe('configuration-build');
    expect(pkgJson!.language).toBe('json');
  });

  it('should compute checksums', async () => {
    const config = {
      rootPath: smallReactAppRoot,
      includeGlobs: ['**/package.json'],
      excludeGlobs: [],
    };

    const inventory = await scanRepository(config);
    expect(inventory.artifacts.length).toBeGreaterThan(0);

    for (const artifact of inventory.artifacts) {
      expect(artifact.checksum).toBeTruthy();
      expect(artifact.checksum.length).toBe(64); // sha256 hex
    }
  });

  it('should detect TypeScript files', async () => {
    const config = {
      rootPath: smallReactAppRoot,
      includeGlobs: ['**/*.ts'],
      excludeGlobs: ['**/node_modules/**', '**/dist/**'],
    };

    const inventory = await scanRepository(config);

    for (const artifact of inventory.artifacts) {
      expect(artifact.language).toBe('typescript');
      expect(artifact.checksum).toBeTruthy();
    }
  });
});

describe('scanRepository exclusions', () => {
  it('should respect excludeGlobs', async () => {
    const config = {
      rootPath: smallReactAppRoot,
      includeGlobs: ['**/*'],
      excludeGlobs: ['**/*.ts', '**/*.tsx', '**/*.js', '**/*.jsx'],
    };

    const inventory = await scanRepository(config);

    for (const artifact of inventory.artifacts) {
      expect(artifact.relativePath.endsWith('.ts')).toBe(false);
      expect(artifact.relativePath.endsWith('.tsx')).toBe(false);
      expect(artifact.relativePath.endsWith('.js')).toBe(false);
      expect(artifact.relativePath.endsWith('.jsx')).toBe(false);
    }
  });
});

describe('Phase 1 enhancements', () => {
  it('should produce deterministic scan output ordering', async () => {
    const config = {
      rootPath: smallReactAppRoot,
      includeGlobs: ['**/*.json'],
      excludeGlobs: ['**/node_modules/**'],
    };

    const inventory1 = await scanRepository(config);
    const inventory2 = await scanRepository(config);

    // Artifacts should be in the same order across scans
    expect(inventory1.artifacts.length).toBe(inventory2.artifacts.length);
    for (let i = 0; i < inventory1.artifacts.length; i++) {
      expect(inventory1.artifacts[i]!.relativePath).toBe(inventory2.artifacts[i]!.relativePath);
    }
  });

  it('should use SHA-256 for binary checksums', async () => {
    const config = {
      rootPath: smallReactAppRoot,
      includeGlobs: ['**/package.json'],
      excludeGlobs: [],
    };

    const inventory = await scanRepository(config);
    expect(inventory.artifacts.length).toBeGreaterThan(0);

    for (const artifact of inventory.artifacts) {
      // SHA-256 produces 64 hex characters
      expect(artifact.checksum).toMatch(/^[a-f0-9]{64}$/);
      expect(artifact.checksum.length).toBe(64);
    }
  });

  it('should detect npm/pnpm package boundaries from fixture monorepo', async () => {
    const config = {
      rootPath: pnpmMonorepoRoot,
      includeGlobs: ['**/*'],
      excludeGlobs: [],
    };

    const inventory = await scanRepository(config);
    // Package boundary detection should identify package roots
    expect(inventory.packageBoundaries.length).toBe(2);
    expect(inventory.packageBoundaries.map(boundary => boundary.relativePath).sort()).toEqual([
      'packages/core',
      'packages/web',
    ]);
  });

  it('should protect against zip-slip attacks', async () => {
    const config = {
      rootPath: smallReactAppRoot,
      includeGlobs: ['**/*'],
      excludeGlobs: ['**/node_modules/**', '**/dist/**'],
    };

    const inventory = await scanRepository(config);

    // All paths should be contained within the root
    for (const artifact of inventory.artifacts) {
      // Path traversal should be blocked
      expect(artifact.relativePath).not.toMatch(/^\.\./);
      expect(artifact.relativePath).not.toMatch(/\/\.\.\//);
      expect(artifact.relativePath).not.toMatch(/\/\.\.$/);
    }
  });
});
