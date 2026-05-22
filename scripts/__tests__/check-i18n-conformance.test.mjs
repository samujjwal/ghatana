#!/usr/bin/env node

/**
 * Tests for P1-4 i18n conformance script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/check-i18n-conformance.mjs');

describe('check-i18n-conformance', () => {
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
      execSync(`node ${scriptPath} --product=Data`, { encoding: 'utf-8' });
    }).not.toThrow();
  });

  it('should generate evidence report in CI mode', () => {
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'i18n-conformance');
    
    // Run with CI flag
    execSync(`node ${scriptPath} --ci`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });

  it('should check i18n infrastructure', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('i18n');
  });

  it('should check missing keys', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('missing key') || expect(output).toContain('key coverage');
  });

  it('should check date/number/currency/timezone coverage', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('date') || expect(output).toContain('number') || 
    expect(output).toContain('currency') || expect(output).toContain('timezone');
  });

  it('should check RTL readiness', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('RTL') || expect(output).toContain('rtl');
  });

  it('should check pseudo-locale tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('pseudo') || expect(output).toContain('locale');
  });

  it('should check localized validation', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('validation') || expect(output).toContain('error');
  });
});
