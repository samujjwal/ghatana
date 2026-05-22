#!/usr/bin/env node

/**
 * Tests for P1-6 OpenAPI release quality script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/check-openapi-release-quality.mjs');

describe('check-openapi-release-quality', () => {
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
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'openapi-release-quality');
    
    // Run with CI flag
    execSync(`node ${scriptPath} --ci`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });

  it('should check OpenAPI specs', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('OpenAPI') || expect(output).toContain('swagger');
  });

  it('should check method-level parity', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('method') || expect(output).toContain('parity');
  });

  it('should check route schema specificity', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('schema') || expect(output).toContain('specificity');
  });

  it('should check typed error envelopes', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('error') || expect(output).toContain('envelope');
  });

  it('should check typed examples', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('example') || expect(output).toContain('typed');
  });

  it('should check idempotency headers', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('idempotency') || expect(output).toContain('header');
  });

  it('should check backward compatibility', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('compatibility') || expect(output).toContain('version');
  });

  it('should check SDK generated tests', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('SDK') || expect(output).toContain('contract');
  });
});
