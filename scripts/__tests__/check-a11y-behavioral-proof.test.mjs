#!/usr/bin/env node

/**
 * Tests for P1-5 a11y behavioral proof script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/check-a11y-behavioral-proof.mjs');

describe('check-a11y-behavioral-proof', () => {
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

  it('should accept --ci flag', () => {
    expect(() => {
      execSync(`node ${scriptPath} --ci`, { encoding: 'utf-8' });
    }).not.toThrow();
  });

  it('should accept --product flag', () => {
    expect(() => {
      execSync(`node ${scriptPath} --product=Studio`, { encoding: 'utf-8' });
    }).not.toThrow();
  });

  it('should generate evidence report in CI mode', () => {
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'a11y-behavioral-proof');
    
    // Run with CI flag
    execSync(`node ${scriptPath} --ci`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });

  it('should check a11y test infrastructure', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('a11y') || expect(output).toContain('accessibility');
  });

  it('should check keyboard journey tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('keyboard') || expect(output).toContain('tab');
  });

  it('should check focus trap tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('focus') || expect(output).toContain('trap');
  });

  it('should check screen reader tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('landmark') || expect(output).toContain('label') || 
    expect(output).toContain('aria');
  });

  it('should check table/grid accessibility', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('table') || expect(output).toContain('grid');
  });

  it('should check chart/visualization accessibility', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('chart') || expect(output).toContain('visualization');
  });

  it('should check modal/toast/error accessibility', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('modal') || expect(output).toContain('toast') || 
    expect(output).toContain('error');
  });
});
