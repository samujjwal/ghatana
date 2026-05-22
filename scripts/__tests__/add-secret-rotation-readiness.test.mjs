#!/usr/bin/env node

/**
 * Tests for Wave 4 secret rotation readiness addition script
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');
const scriptPath = path.join(repoRoot, 'scripts/add-secret-rotation-readiness.mjs');

describe('add-secret-rotation-readiness', () => {
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

  it('should check secret inventory', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('secret') || expect(output).toContain('inventory');
  });

  it('should check rotation procedures', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('rotation') || expect(output).toContain('procedure');
  });

  it('should check secret versioning', () => {
    const output = execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    expect(output).toContain('version') || expect(output).toContain('secret');
  });

  it('should generate secret rotation readiness report', () => {
    const evidenceDir = path.join(repoRoot, '.kernel', 'evidence', 'secret-rotation');
    
    // Run script
    execSync(`node ${scriptPath}`, { encoding: 'utf-8' });
    
    // Check if evidence directory exists
    expect(existsSync(evidenceDir)).toBe(true);
  });
});
