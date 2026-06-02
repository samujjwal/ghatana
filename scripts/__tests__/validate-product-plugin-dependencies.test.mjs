#!/usr/bin/env node

/**
 * Tests for validate-product-plugin-dependencies.mjs
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { readFileSync, writeFileSync, unlinkSync, existsSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const TEST_DIR = join(__dirname, '.test-plugin-deps');
const SCRIPT_PATH = join(__dirname, '..', 'validate-product-plugin-dependencies.mjs');

describe('validate-product-plugin-dependencies', () => {
  beforeEach(() => {
    // Create test directory
    if (!existsSync(TEST_DIR)) {
      mkdirSync(TEST_DIR, { recursive: true });
    }
  });

  afterEach(() => {
    // Cleanup test directory
    if (existsSync(TEST_DIR)) {
      try {
        execSync(`rm -rf "${TEST_DIR}"`, { stdio: 'ignore' });
      } catch (e) {
        // Ignore cleanup errors
      }
    }
  });

  it('should pass with valid plugin declarations', () => {
    const validConfig = `
plugins:
  - pluginId: kernel-session-context-resolver
    kind: platform-plugin
  - pluginId: kernel-security
    kind: platform-plugin
  - pluginId: kernel-phi-policy
    kind: platform-plugin
`;

    const configPath = join(TEST_DIR, 'kernel-product.yaml');
    writeFileSync(configPath, validConfig, 'utf-8');

    const result = execSync(`node "${SCRIPT_PATH}" "${TEST_DIR}"`, {
      encoding: 'utf-8',
      stdio: 'pipe',
    });

    expect(result).toContain('✅ All 3 declared plugins are valid and available.');
  });

  it('should fail with missing plugin', () => {
    const invalidConfig = `
plugins:
  - pluginId: kernel-session-context-resolver
    kind: platform-plugin
  - pluginId: kernel-nonexistent-plugin
    kind: platform-plugin
`;

    const configPath = join(TEST_DIR, 'kernel-product.yaml');
    writeFileSync(configPath, invalidConfig, 'utf-8');

    expect(() => {
      execSync(`node "${SCRIPT_PATH}" "${TEST_DIR}"`, {
        encoding: 'utf-8',
        stdio: 'pipe',
      });
    }).toThrow();
  });

  it('should fail with invalid plugin ID format', () => {
    const invalidConfig = `
plugins:
  - pluginId: InvalidPluginName
    kind: platform-plugin
`;

    const configPath = join(TEST_DIR, 'kernel-product.yaml');
    writeFileSync(configPath, invalidConfig, 'utf-8');

    expect(() => {
      execSync(`node "${SCRIPT_PATH}" "${TEST_DIR}"`, {
        encoding: 'utf-8',
        stdio: 'pipe',
      });
    }).toThrow();
  });

  it('should handle plugins without kernel- prefix', () => {
    const validConfig = `
plugins:
  - pluginId: audit-trail
    kind: platform-plugin
  - pluginId: consent
    kind: platform-plugin
`;

    const configPath = join(TEST_DIR, 'kernel-product.yaml');
    writeFileSync(configPath, validConfig, 'utf-8');

    const result = execSync(`node "${SCRIPT_PATH}" "${TEST_DIR}"`, {
      encoding: 'utf-8',
      stdio: 'pipe',
    });

    expect(result).toContain('✅ All 2 declared plugins are valid and available.');
  });

  it('should fail when kernel-product.yaml does not exist', () => {
    const emptyDir = join(TEST_DIR, 'empty');
    mkdirSync(emptyDir, { recursive: true });

    expect(() => {
      execSync(`node "${SCRIPT_PATH}" "${emptyDir}"`, {
        encoding: 'utf-8',
        stdio: 'pipe',
      });
    }).toThrow();
  });
});
