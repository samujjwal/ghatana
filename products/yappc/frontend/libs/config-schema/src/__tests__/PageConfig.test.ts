/**
 * PageConfig Schema Tests
 *
 * Tests for PageConfig schema validation and type inference.
 *
 * @packageDocumentation
 */

import { describe, it, expect } from 'vitest';

import {
  PageConfigSchema,
  ComponentInstanceSchema,
  InterfaceDefinitionSchema,
  EventConnectionSchema,
  DataConnectionSchema,
  NavigationConnectionSchema,
} from '../schemas/PageConfig';

describe('PageConfigSchema', () => {
  const validPageConfig = {
    id: 'page-1',
    version: '1.0.0',
    intentId: 'intent-1',
    requirementIds: ['req-1', 'req-2'],
    title: 'Test Page',
    description: 'A test page',
    route: '/test',
    layout: 'canvas' as const,
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
        id: 'button-1',
      },
    ],
    data: {
      sources: [
        {
          id: 'source-1',
          type: 'api' as const,
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
      supportedLocales: ['en', 'es'],
      translations: {
        en: { title: 'Test Page' },
        es: { title: 'Página de Prueba' },
      },
    },
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    author: 'test-user',
    tags: ['test'],
  };

  it('should validate a valid PageConfig', () => {
    const result = PageConfigSchema.safeParse(validPageConfig);
    expect(result.success).toBe(true);
  });

  it('should reject PageConfig without required fields', () => {
    const invalidConfig = { ...validPageConfig, id: '' };
    const result = PageConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should reject PageConfig with invalid layout type', () => {
    const invalidConfig = { ...validPageConfig, layout: 'invalid' };
    const result = PageConfigSchema.safeParse(invalidConfig);
    expect(result.success).toBe(false);
  });

  it('should accept PageConfig with optional fields omitted', () => {
    const minimalConfig = {
      ...validPageConfig,
      intentId: undefined,
      layoutConfig: {
        template: undefined,
        responsiveBreakpoints: [],
      },
    };
    const result = PageConfigSchema.safeParse(minimalConfig);
    expect(result.success).toBe(true);
  });
});

describe('ComponentInstanceSchema', () => {
  const validComponent = {
    type: 'Button',
    props: { label: 'Click me' },
    id: 'button-1',
    condition: 'user.isLoggedIn',
    dataBinding: {
      source: 'user-data',
      path: 'name',
      transform: 'toUpper',
    },
    events: { onClick: 'handleClick' },
  };

  it('should validate a valid ComponentInstance', () => {
    const result = ComponentInstanceSchema.safeParse(validComponent);
    expect(result.success).toBe(true);
  });

  it('should accept ComponentInstance with minimal fields', () => {
    const minimalComponent = { type: 'Button' };
    const result = ComponentInstanceSchema.safeParse(minimalComponent);
    expect(result.success).toBe(true);
  });

  it('should reject ComponentInstance without type', () => {
    const invalidComponent = { ...validComponent, type: '' };
    const result = ComponentInstanceSchema.safeParse(invalidComponent);
    expect(result.success).toBe(false);
  });
});

describe('InterfaceDefinitionSchema', () => {
  const validInterface = {
    id: 'interface-1',
    name: 'UserData',
    type: 'input' as const,
    schema: {
      type: 'object' as const,
      properties: {
        name: {
          type: 'string' as const,
          description: 'User name',
          required: true,
        },
      },
      required: ['name'],
    },
    validation: {
      rules: [
        {
          type: 'min' as const,
          value: 1,
          message: 'Required',
        },
      ],
    },
    description: 'User data interface',
    examples: [{ name: 'John Doe' }],
  };

  it('should validate a valid InterfaceDefinition', () => {
    const result = InterfaceDefinitionSchema.safeParse(validInterface);
    expect(result.success).toBe(true);
  });

  it('should accept InterfaceDefinition with optional schema properties', () => {
    const minimalInterface = {
      ...validInterface,
      schema: {
        type: 'object' as const,
        properties: undefined,
        required: undefined,
      },
    };
    const result = InterfaceDefinitionSchema.safeParse(minimalInterface);
    expect(result.success).toBe(true);
  });
});

describe('EventConnectionSchema', () => {
  const validEventConnection = {
    id: 'event-conn-1',
    sourceComponentId: 'button-1',
    sourceEvent: 'onClick',
    targetComponentId: 'modal-1',
    targetAction: 'open',
    transform: 'mapData',
    condition: 'isValid',
  };

  it('should validate a valid EventConnection', () => {
    const result = EventConnectionSchema.safeParse(validEventConnection);
    expect(result.success).toBe(true);
  });

  it('should accept EventConnection without optional fields', () => {
    const minimalConnection = {
      ...validEventConnection,
      transform: undefined,
      condition: undefined,
    };
    const result = EventConnectionSchema.safeParse(minimalConnection);
    expect(result.success).toBe(true);
  });
});

describe('DataConnectionSchema', () => {
  const validDataConnection = {
    id: 'data-conn-1',
    sourceId: 'source-1',
    sourcePath: 'data.user',
    targetComponentId: 'text-1',
    targetProp: 'value',
    transform: 'format',
    mode: 'one-way' as const,
  };

  it('should validate a valid DataConnection', () => {
    const result = DataConnectionSchema.safeParse(validDataConnection);
    expect(result.success).toBe(true);
  });

  it('should reject DataConnection with invalid mode', () => {
    const invalidConnection = { ...validDataConnection, mode: 'invalid' };
    const result = DataConnectionSchema.safeParse(invalidConnection);
    expect(result.success).toBe(false);
  });
});

describe('NavigationConnectionSchema', () => {
  const validNavConnection = {
    id: 'nav-conn-1',
    sourceComponentId: 'link-1',
    sourceEvent: 'onClick',
    targetPageId: 'page-2',
    targetRoute: '/page-2',
    params: { id: '123' },
  };

  it('should validate a valid NavigationConnection', () => {
    const result = NavigationConnectionSchema.safeParse(validNavConnection);
    expect(result.success).toBe(true);
  });

  it('should accept NavigationConnection without params', () => {
    const minimalConnection = { ...validNavConnection, params: undefined };
    const result = NavigationConnectionSchema.safeParse(minimalConnection);
    expect(result.success).toBe(true);
  });
});
