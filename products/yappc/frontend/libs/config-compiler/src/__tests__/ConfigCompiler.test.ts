/**
 * Config Compiler Tests
 *
 * Tests for ConfigCompiler class.
 *
 * @packageDocumentation
 */

import { describe, it, expect } from 'vitest';

import { ConfigCompiler } from '../generators/ConfigCompiler';
import { mergeCompilerOptions, type GeneratedArtifact } from '../types';
import type { PageConfig } from 'yappc-config-schema';

function validPageConfig(): PageConfig {
  return {
    id: 'page-1',
    version: '1.0.0',
    intentId: 'intent-1',
    requirementIds: ['req-1'],
    title: 'Test Page',
    description: 'A test page',
    route: '/test',
    layout: 'canvas',
    layoutConfig: {
      template: 'default',
      responsiveBreakpoints: [
        {
          breakpoint: 'mobile',
          config: {},
        },
      ],
    },
    components: [
      {
        type: 'Button',
        props: { label: 'Click me' },
        id: 'ButtonOne',
      },
    ],
    data: {
      sources: [
        {
          id: 'source-1',
          type: 'api',
          config: { url: 'https://api.example.com' },
        },
      ],
      bindings: [],
    },
    actions: [],
    connections: {
      events: [],
      data: [],
      navigation: [],
    },
    contracts: {
      inputs: [],
      outputs: [],
    },
    permissions: {
      view: ['admin'],
      edit: ['admin'],
      delete: ['admin'],
    },
    i18n: {
      defaultLocale: 'en',
      supportedLocales: ['en'],
      translations: {
        en: { title: 'Test Page' },
      },
    },
    createdAt: '2026-05-26T00:00:00Z',
    updatedAt: '2026-05-26T00:00:00Z',
    author: 'test-user',
    tags: ['test'],
  };
}

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

  it('validates emitted PageConfig artifacts with yappc-config-schema', async () => {
    const options = mergeCompilerOptions();
    const result = await compiler.compile(validPageConfig(), options);

    expect(result.success).toBe(true);
    expect(result.artifacts).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          type: 'config',
          path: 'configs/page-1.json',
          metadata: { configKind: 'page' },
        }),
      ]),
    );
  });

  it('blocks compilation when generated config artifact fails schema validation', async () => {
    class InvalidGeneratedConfigCompiler extends ConfigCompiler {
      protected override createSourceConfigArtifact(config: PageConfig): GeneratedArtifact {
        return {
          type: 'config',
          name: config.id,
          content: JSON.stringify({ id: config.id, version: config.version }),
          language: 'json',
          path: `configs/${config.id}.json`,
          metadata: { configKind: 'page' },
        };
      }
    }

    const options = mergeCompilerOptions();
    const result = await new InvalidGeneratedConfigCompiler().compile(validPageConfig(), options);

    expect(result.success).toBe(false);
    expect(result.artifacts).toEqual([]);
    expect(result.context.errors).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'GENERATED_CONFIG_VALIDATION_ERROR',
        }),
      ]),
    );
  });
});
