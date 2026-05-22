#!/usr/bin/env node

/**
 * Tests for Wave 2 per-product evidence collection script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/collect-per-product-evidence.mjs');

describe('collect-per-product-evidence', () => {
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

  it('should check a11y evidence', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('a11y') || expect(output).toContain('accessibility');
  });

  it('should check i18n evidence', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('i18n') || expect(output).toContain('locale');
  });

  it('should check AI governance evidence', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('AI') || expect(output).toContain('governance');
  });

  it('should check security evidence', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('security') || expect(output).toContain('auth');
  });

  it('should check release evidence', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('release') || expect(output).toContain('summary');
  });

  it('should generate evidence report', () => {
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'per-product-evidence');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });

  it('should aggregate evidence across all products', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('Summary');
    expect(output).toContain('Total Products');
  });
});
