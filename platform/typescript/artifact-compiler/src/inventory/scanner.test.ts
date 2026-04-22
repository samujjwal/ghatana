/**
 * @fileoverview Inventory scanner tests.
 */

import { describe, it, expect } from 'vitest';
import { scanRepository, DEFAULT_SCANNER_CONFIG } from './scanner';

describe('scanRepository', () => {
  it('should scan the current repo and return an inventory', async () => {
    const config = {
      rootPath: process.cwd(),
      excludeGlobs: [
        ...DEFAULT_SCANNER_CONFIG.excludeGlobs,
        '**/node_modules/**',
        '**/dist/**',
        '**/.git/**',
      ],
      maxFileSizeBytes: 5 * 1024 * 1024,
    };

    const inventory = await scanRepository(config);

    expect(inventory.repositoryRoot).toBe(process.cwd());
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
      rootPath: process.cwd(),
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
      rootPath: process.cwd(),
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
      rootPath: process.cwd(),
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
      rootPath: process.cwd(),
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
