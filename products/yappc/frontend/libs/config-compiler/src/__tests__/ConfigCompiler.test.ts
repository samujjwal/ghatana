/**
 * Config Compiler Tests
 *
 * Tests for ConfigCompiler class.
 *
 * @packageDocumentation
 */

import { describe, it, expect } from 'vitest';

import { ConfigCompiler } from '../generators/ConfigCompiler';
import { mergeCompilerOptions } from '../types';

describe('ConfigCompiler', () => {
  const compiler = new ConfigCompiler();

  it('should have correct name and version', () => {
    expect(compiler.name).toBe('ConfigCompiler');
    expect(compiler.version).toBe('1.0.0');
  });

  it('should return supported config types', () => {
    const types = compiler.getSupportedTypes();
    expect(types).toContain('PageConfig');
    expect(types).toContain('IntentConfig');
    expect(types).toContain('RequirementConfig');
  });

  it('should validate valid config', () => {
    const validConfig = {
      id: 'test-1',
      version: '1.0.0',
    };

    const result = compiler.validate(validConfig);
    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
  });

  it('should reject config without id', () => {
    const invalidConfig = {
      version: '1.0.0',
    };

    const result = compiler.validate(invalidConfig);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should reject config without version', () => {
    const invalidConfig = {
      id: 'test-1',
    };

    const result = compiler.validate(invalidConfig);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should reject non-object config', () => {
    const result = compiler.validate(null);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should compile valid config', async () => {
    const config = {
      id: 'test-1',
      version: '1.0.0',
    };

    const options = mergeCompilerOptions();
    const result = await compiler.compile(config, options);

    expect(result.success).toBe(true);
    expect(result.artifacts).toBeDefined();
  });

  it('should fail to compile invalid config', async () => {
    const config = {
      id: 'test-1',
    };

    const options = mergeCompilerOptions();
    const result = await compiler.compile(config, options);

    expect(result.success).toBe(false);
    expect(result.context.errors.length).toBeGreaterThan(0);
  });
});
