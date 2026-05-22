#!/usr/bin/env node

/**
 * Tests for Wave 1 failure-injection reports storage script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/store-failure-injection-reports.mjs');

describe('store-failure-injection-reports', () => {
  beforeEach(() => {
    // Setup test fixtures if needed
  });

  afterEach(() => {
    // Cleanup test fixtures if needed
  });

  it('should run without errors', () => {
    expect(() => {
      execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    }).not.toThrow();
  });

  it('should accept --release flag', () => {
    expect(() => {
      execSync(`node ${scriptPath} --release=v1.0.0`, { encoding: 'utf-8' });
    }).not.toThrow();
  });

  it('should create release artifacts directory', () => {
    const releaseArtifactsDir = path.join(repoRoot, '.kernel', 'release-artifacts');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if release artifacts directory exists
    expect(existsSync(releaseArtifactsDir)).toBe(true);
  });

  it('should generate failure-injection report JSON', () => {
    const releaseArtifactsDir = path.join(repoRoot, '.kernel', 'release-artifacts', 'latest');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if report file exists
    const reportFile = path.join(releaseArtifactsDir, 'failure-injection-report-latest.json');
    expect(existsSync(reportFile)).toBe(true);
  });

  it('should generate summary markdown', () => {
    const releaseArtifactsDir = path.join(repoRoot, '.kernel', 'release-artifacts', 'latest');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if summary file exists
    const summaryFile = path.join(releaseArtifactsDir, 'failure-injection-summary-latest.md');
    expect(existsSync(summaryFile)).toBe(true);
  });

  it('should aggregate evidence from all categories', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('Total Reports');
    expect(output).toContain('Total Violations');
    expect(output).toContain('Total Warnings');
    expect(output).toContain('Total Evidence');
  });
});
