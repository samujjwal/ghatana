#!/usr/bin/env node

/**
 * Tests for Wave 2 product release summaries generation script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/generate-product-release-summaries.mjs');

describe('generate-product-release-summaries', () => {
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

  it('should accept --product flag', () => {
    expect(() => {
      execSync(`node ${scriptPath} --product=Data`, { encoding: 'utf-8' });
    }).not.toThrow();
  });

  it('should accept --version flag', () => {
    expect(() => {
      execSync(`node ${scriptPath} --version=2.0.0`, { encoding: 'utf-8' });
    }).not.toThrow();
  });

  it('should generate release summary for Data Cloud', () => {
    const summaryPath = path.join(repoRoot, 'products/data-cloud/delivery/launcher', 'RELEASE_SUMMARY.md');
    
    // Run script
    execSync(`node ${scriptPath} --product=Data`, { encoding: 'utf-8' });
    
    // Check if summary file exists
    expect(existsSync(summaryPath)).toBe(true);
  });

  it('should include required sections in summary', () => {
    const summaryPath = path.join(repoRoot, 'products/data-cloud/delivery/launcher', 'RELEASE_SUMMARY.md');
    
    // Run script
    execSync(`node ${scriptPath} --product=Data`, { encoding: 'utf-8' });
    
    // Read summary and check for required sections
    const content = readFileSync(summaryPath, 'utf-8');
    expect(content).toContain('Features');
    expect(content).toContain('Bug Fixes');
    expect(content).toContain('Breaking Changes');
    expect(content).toContain('Upgrade Notes');
    expect(content).toContain('Performance Metrics');
    expect(content).toContain('Security Considerations');
  });

  it('should include version in summary', () => {
    const summaryPath = path.join(repoRoot, 'products/data-cloud/delivery/launcher', 'RELEASE_SUMMARY.md');
    
    // Run script with specific version
    execSync(`node ${scriptPath} --product=Data --version=1.2.3`, { encoding: 'utf-8' });
    
    // Read summary and check for version
    const content = readFileSync(summaryPath, 'utf-8');
    expect(content).toContain('1.2.3');
  });
});
