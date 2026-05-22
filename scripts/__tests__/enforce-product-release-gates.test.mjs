#!/usr/bin/env node

/**
 * Tests for Wave 2 product release gates enforcement script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/enforce-product-release-gates.mjs');

describe('enforce-product-release-gates', () => {
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

  it('should accept --tag flag', () => {
    expect(() => {
      execSync(`node ${scriptPath} --tag=v1.0.0`, { encoding: 'utf-8' });
    }).not.toThrow();
  });

  it('should check atomic workflow gate', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('atomic') || expect(output).toContain('workflow');
  });

  it('should check runtime dependency gate', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('runtime') || expect(output).toContain('dependency');
  });

  it('should check AI governance gate', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('AI') || expect(output).toContain('governance');
  });

  it('should check i18n conformance gate', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('i18n') || expect(output).toContain('conformance');
  });

  it('should check a11y behavioral gate', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('a11y') || expect(output).toContain('behavioral');
  });

  it('should check OpenAPI quality gate', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('OpenAPI') || expect(output).toContain('quality');
  });

  it('should check security gate', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('security') || expect(output).toContain('gate');
  });

  it('should generate gate report', () => {
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'product-release-gates');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });

  it('should report gate pass/fail status', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('Passed') || expect(output).toContain('Failed');
  });
});
