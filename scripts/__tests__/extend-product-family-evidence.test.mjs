#!/usr/bin/env node

/**
 * Tests for Wave 2 product family evidence extension script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/extend-product-family-evidence.mjs');

describe('extend-product-family-evidence', () => {
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

  it('should check atomic workflow tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('atomic') || expect(output).toContain('workflow');
  });

  it('should check runtime dependency tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('runtime') || expect(output).toContain('dependency');
  });

  it('should check AI governance tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('AI') || expect(output).toContain('governance');
  });

  it('should check release evidence directory', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('evidence') || expect(output).toContain('directory');
  });

  it('should check release summary', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('summary') || expect(output).toContain('RELEASE');
  });

  it('should generate evidence report', () => {
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'product-family-evidence');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });
});
