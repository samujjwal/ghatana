#!/usr/bin/env node

/**
 * Tests for Wave 3 i18n missing-key extraction script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/extract-i18n-missing-keys.mjs');

describe('extract-i18n-missing-keys', () => {
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

  it('should check missing key extraction', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('i18n') || expect(output).toContain('extraction');
  });

  it('should check pseudo-locale tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('pseudo') || expect(output).toContain('locale');
  });

  it('should check localized validation', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('validation') || expect(output).toContain('error');
  });

  it('should generate i18n extraction report', () => {
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'i18n-extraction');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });
});
