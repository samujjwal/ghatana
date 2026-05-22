#!/usr/bin/env node

/**
 * Tests for Wave 3 a11y behavioral tests addition script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/add-a11y-behavioral-tests.mjs');

describe('add-a11y-behavioral-tests', () => {
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

  it('should check keyboard journey tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('keyboard') || expect(output).toContain('journey');
  });

  it('should check screen-reader tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('screen') || expect(output).toContain('reader');
  });

  it('should check table accessibility tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('table') || expect(output).toContain('accessibility');
  });

  it('should check chart accessibility tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('chart') || expect(output).toContain('visualization');
  });

  it('should check modal accessibility tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('modal') || expect(output).toContain('dialog');
  });

  it('should generate a11y behavioral test report', () => {
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'a11y-behavioral-tests');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });
});
