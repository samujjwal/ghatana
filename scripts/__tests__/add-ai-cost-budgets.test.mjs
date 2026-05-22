#!/usr/bin/env node

/**
 * Tests for Wave 4 AI cost budgets addition script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/add-ai-cost-budgets.mjs');

describe('add-ai-cost-budgets', () => {
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

  it('should check cost budget enforcement', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('cost') || expect(output).toContain('budget');
  });

  it('should check model quality thresholds', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('quality') || expect(output).toContain('threshold');
  });

  it('should check cost tracking', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('track') || expect(output).toContain('cost');
  });

  it('should generate AI cost budget report', () => {
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'ai-cost-budgets');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });
});
