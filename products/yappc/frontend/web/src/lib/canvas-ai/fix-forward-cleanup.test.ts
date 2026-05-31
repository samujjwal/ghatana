/**
 * Tests for Fix-Forward Cleanup (YAPPC-T07)
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { FixForwardCleanup, type CleanupConfig } from './fix-forward-cleanup';
import { mkdirSync, writeFileSync, rmSync } from 'fs';
import { join } from 'path';

describe('FixForwardCleanup', () => {
  const testDir = join(process.cwd(), 'test-cleanup-temp');
  
  beforeEach(() => {
    // Create test directory structure
    mkdirSync(testDir, { recursive: true } as any);
    mkdirSync(join(testDir, 'src'), { recursive: true } as any);
  });

  afterEach(() => {
    // Clean up test directory
    try {
      rmSync(testDir, { recursive: true, force: true } as any);
    } catch {
      // Ignore cleanup errors
    }
  });

  it('identifies obsolete files when generated replacement exists', () => {
    // Create generated file
    writeFileSync(join(testDir, 'src', 'generatedDashboard.ts'), '// generated');
    
    // Create legacy file
    writeFileSync(join(testDir, 'src', 'placeholderDashboard.ts'), '// placeholder');

    const config: CleanupConfig = {
      targetDirectory: testDir,
      generatedFilePattern: /^generated.*\.ts$/,
      legacyFilePattern: /^placeholder.*\.ts$/,
      dryRun: true,
    };

    const cleanup = new FixForwardCleanup();
    const obsolete = cleanup.identifyObsoleteFiles(config);

    expect(obsolete).toHaveLength(1);
    expect(obsolete[0]).toBe('src/placeholderDashboard.ts');
  });

  it('does not identify obsolete files when no generated replacement exists', () => {
    // Create only legacy file
    writeFileSync(join(testDir, 'src', 'placeholderDashboard.ts'), '// placeholder');

    const config: CleanupConfig = {
      targetDirectory: testDir,
      generatedFilePattern: /^generated.*\.ts$/,
      legacyFilePattern: /^placeholder.*\.ts$/,
      dryRun: true,
    };

    const cleanup = new FixForwardCleanup();
    const obsolete = cleanup.identifyObsoleteFiles(config);

    expect(obsolete).toHaveLength(0);
  });

  it('deletes obsolete files in non-dry-run mode', () => {
    // Create generated file
    writeFileSync(join(testDir, 'src', 'generatedDashboard.ts'), '// generated');
    
    // Create legacy file
    const legacyPath = join(testDir, 'src', 'placeholderDashboard.ts');
    writeFileSync(legacyPath, '// placeholder');

    const config: CleanupConfig = {
      targetDirectory: testDir,
      generatedFilePattern: /^generated.*\.ts$/,
      legacyFilePattern: /^placeholder.*\.ts$/,
      dryRun: false,
    };

    const cleanup = new FixForwardCleanup();
    const result = cleanup.cleanup(config);

    expect(result.deletedFiles).toHaveLength(1);
    expect(result.deletedFiles[0]).toBe('src/placeholderDashboard.ts');
  });

  it('skips deletion in dry-run mode', () => {
    // Create generated file
    writeFileSync(join(testDir, 'src', 'generatedDashboard.ts'), '// generated');
    
    // Create legacy file
    const legacyPath = join(testDir, 'src', 'placeholderDashboard.ts');
    writeFileSync(legacyPath, '// placeholder');

    const config: CleanupConfig = {
      targetDirectory: testDir,
      generatedFilePattern: /^generated.*\.ts$/,
      legacyFilePattern: /^placeholder.*\.ts$/,
      dryRun: true,
    };

    const cleanup = new FixForwardCleanup();
    const result = cleanup.cleanup(config);

    expect(result.deletedFiles).toHaveLength(0);
    expect(result.skippedFiles).toHaveLength(1);
    expect(result.skippedFiles[0]).toBe('src/placeholderDashboard.ts');
  });

  it('handles non-existent directory gracefully', () => {
    const config: CleanupConfig = {
      targetDirectory: join(testDir, 'nonexistent'),
      generatedFilePattern: /^generated.*\.ts$/,
      legacyFilePattern: /^placeholder.*\.ts$/,
      dryRun: true,
    };

    const cleanup = new FixForwardCleanup();
    const result = cleanup.cleanup(config);

    expect(result.deletedFiles).toHaveLength(0);
    expect(result.skippedFiles).toHaveLength(0);
    expect(result.errors).toHaveLength(0);
  });

  it('creates product cleanup configuration', () => {
    const config = FixForwardCleanup.createProductCleanupConfig(testDir, true);

    expect(config.targetDirectory).toBe(testDir);
    expect(config.dryRun).toBe(true);
    expect(config.generatedFilePattern).toBeDefined();
    expect(config.legacyFilePattern).toBeDefined();
  });

  it('performs product cleanup', () => {
    // Create generated file
    writeFileSync(join(testDir, 'src', 'GeneratedDashboard.ts'), '// generated');
    
    // Create legacy file
    const legacyPath = join(testDir, 'src', 'legacyDashboard.ts');
    writeFileSync(legacyPath, '// legacy');

    const result = FixForwardCleanup.cleanupProductGenerated(testDir, true);

    expect(result.skippedFiles).toHaveLength(1);
    expect(result.skippedFiles[0]).toBe('src/legacyDashboard.ts');
  });
});
