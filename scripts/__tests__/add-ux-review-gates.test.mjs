#!/usr/bin/env node

/**
 * Tests for Wave 3 UX review gates addition script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/add-ux-review-gates.mjs');

describe('add-ux-review-gates', () => {
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
      execSync(`node ${scriptPath} --product=Studio`, { encoding: 'utf-8' });
    }).not.toThrow();
  });

  it('should check UX review documentation', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('UX') || expect(output).toContain('review');
  });

  it('should check UX review gates in OpenAPI', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('OpenAPI') || expect(output).toContain('gate');
  });

  it('should check UX review tracking', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('tracking') || expect(output).toContain('UX');
  });

  it('should generate UX review gate report', () => {
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'ux-review-gates');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });
});
