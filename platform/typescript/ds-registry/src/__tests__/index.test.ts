/**
 * @ghatana/ds-registry test suite
 * Tests for component, token, theme, and pattern registration and compatibility checks
 */

import { describe, it, expect, beforeEach } from 'vitest';
import {
  createRegistryStore,
  resetRegistryStore,
  type ComponentEntry,
  type TokenSetEntry,
  type ThemeEntry,
  type PatternEntry,
} from '../index';

describe('@ghatana/ds-registry', () => {
  let store: ReturnType<typeof createRegistryStore>;

  beforeEach(() => {
    resetRegistryStore();
    store = createRegistryStore();
  });

  describe('Token Registration', () => {
    it('should register token sets', () => {
      const entry: Omit<TokenSetEntry, 'registeredAt' | 'updatedAt'> = {
        id: 'tokens-1',
        name: 'Test Tokens',
        tokens: {},
        source: 'test-source',
        version: '1.0.0',
      };

      const registered = store.registerTokenSet(entry);
      expect(registered).toBeDefined();
      expect(registered.id).toBe('tokens-1');
      expect(registered.name).toBe('Test Tokens');
    });

    it('should retrieve registered tokens', () => {
      const entry: Omit<TokenSetEntry, 'registeredAt' | 'updatedAt'> = {
        id: 'tokens-1',
        name: 'Test Tokens',
        tokens: {},
        source: 'test-source',
        version: '1.0.0',
      };

      store.registerTokenSet(entry);
      const retrieved = store.getTokenSet('tokens-1');

      expect(retrieved).toBeDefined();
      expect(retrieved?.id).toBe('tokens-1');
    });

    it('should update token sets', () => {
      const entry: Omit<TokenSetEntry, 'registeredAt' | 'updatedAt'> = {
        id: 'tokens-1',
        name: 'Test Tokens',
        tokens: {},
        source: 'test-source',
        version: '1.0.0',
      };

      store.registerTokenSet(entry);
      const updated = store.updateTokenSet('tokens-1', { color: { primary: '#000000' } });

      expect(updated).toBeDefined();
      expect(updated?.tokens).toEqual({ color: { primary: '#000000' } });
    });

    it('should unregister token sets', () => {
      const entry: Omit<TokenSetEntry, 'registeredAt' | 'updatedAt'> = {
        id: 'tokens-1',
        name: 'Test Tokens',
        tokens: {},
        source: 'test-source',
        version: '1.0.0',
      };

      store.registerTokenSet(entry);
      const unregistered = store.unregisterTokenSet('tokens-1');

      expect(unregistered).toBe(true);
      expect(store.getTokenSet('tokens-1')).toBeUndefined();
    });
  });

  describe('Component Registration', () => {
    it('should register component contracts', () => {
      const entry: Omit<ComponentEntry, 'registeredAt' | 'updatedAt'> = {
        id: 'component-1',
        contract: {
          name: 'Button',
          version: '1.0.0',
          props: [],
          slots: [],
          events: [],
          styles: {},
          metadata: {
            category: 'input',
            status: 'stable' as const,
            platforms: ['web' as const],
          },
        },
        hash: 'hash-123',
        source: 'test-source',
        version: '1.0.0',
      };

      const registered = store.registerComponent(entry);
      expect(registered).toBeDefined();
      expect(registered.id).toBe('component-1');
      expect(registered.contract.name).toBe('Button');
    });

    it('should retrieve component contracts', () => {
      const entry: Omit<ComponentEntry, 'registeredAt' | 'updatedAt'> = {
        id: 'component-1',
        contract: {
          name: 'Button',
          version: '1.0.0',
          props: [],
          slots: [],
          events: [],
          styles: {},
          metadata: {
            category: 'input',
            status: 'stable' as const,
            platforms: ['web' as const],
          },
        },
        hash: 'hash-123',
        source: 'test-source',
        version: '1.0.0',
      };

      store.registerComponent(entry);
      const retrieved = store.getComponent('component-1');

      expect(retrieved).toBeDefined();
      expect(retrieved?.id).toBe('component-1');
    });

    it('should retrieve component by name', () => {
      const entry: Omit<ComponentEntry, 'registeredAt' | 'updatedAt'> = {
        id: 'component-1',
        contract: {
          name: 'Button',
          version: '1.0.0',
          props: [],
          slots: [],
          events: [],
          styles: {},
          metadata: {
            category: 'input',
            status: 'stable' as const,
            platforms: ['web' as const],
          },
        },
        hash: 'hash-123',
        source: 'test-source',
        version: '1.0.0',
      };

      store.registerComponent(entry);
      const retrieved = store.getComponentByName('Button');

      expect(retrieved).toBeDefined();
      expect(retrieved?.contract.name).toBe('Button');
    });

    it('should find components by category', () => {
      const entry: Omit<ComponentEntry, 'registeredAt' | 'updatedAt'> = {
        id: 'component-1',
        contract: {
          name: 'Button',
          version: '1.0.0',
          props: [],
          slots: [],
          events: [],
          styles: {},
          metadata: {
            category: 'input',
            status: 'stable' as const,
            platforms: ['web' as const],
          },
        },
        hash: 'hash-123',
        source: 'test-source',
        version: '1.0.0',
      };

      store.registerComponent(entry);
      const components = store.findComponentsByCategory('input');

      expect(components).toHaveLength(1);
      expect(components[0].contract.name).toBe('Button');
    });
  });

  describe('Theme Registration', () => {
    it('should register themes', () => {
      const entry: Omit<ThemeEntry, 'registeredAt' | 'updatedAt'> = {
        id: 'theme-1',
        name: 'Dark Theme',
        tokenSetIds: ['tokens-1'],
        overrides: {},
      };

      const registered = store.registerTheme(entry);
      expect(registered).toBeDefined();
      expect(registered.id).toBe('theme-1');
      expect(registered.name).toBe('Dark Theme');
    });

    it('should retrieve themes', () => {
      const entry: Omit<ThemeEntry, 'registeredAt' | 'updatedAt'> = {
        id: 'theme-1',
        name: 'Dark Theme',
        tokenSetIds: ['tokens-1'],
        overrides: {},
      };

      store.registerTheme(entry);
      const retrieved = store.getTheme('theme-1');

      expect(retrieved).toBeDefined();
      expect(retrieved?.id).toBe('theme-1');
    });
  });

  describe('Pattern Registration', () => {
    it('should register patterns', () => {
      const entry: Omit<PatternEntry, 'registeredAt'> = {
        id: 'pattern-1',
        name: 'Card Pattern',
        description: 'A card layout pattern',
        componentIds: ['component-1'],
        category: 'layout',
      };

      const registered = store.registerPattern(entry);
      expect(registered).toBeDefined();
      expect(registered.id).toBe('pattern-1');
      expect(registered.name).toBe('Card Pattern');
    });

    it('should retrieve patterns', () => {
      const entry: Omit<PatternEntry, 'registeredAt'> = {
        id: 'pattern-1',
        name: 'Card Pattern',
        description: 'A card layout pattern',
        componentIds: ['component-1'],
        category: 'layout',
      };

      store.registerPattern(entry);
      const retrieved = store.getPattern('pattern-1');

      expect(retrieved).toBeDefined();
      expect(retrieved?.id).toBe('pattern-1');
    });

    it('should find patterns by component', () => {
      const entry: Omit<PatternEntry, 'registeredAt'> = {
        id: 'pattern-1',
        name: 'Card Pattern',
        description: 'A card layout pattern',
        componentIds: ['component-1'],
        category: 'layout',
      };

      store.registerPattern(entry);
      const patterns = store.findPatternsByComponent('component-1');

      expect(patterns).toHaveLength(1);
      expect(patterns[0].id).toBe('pattern-1');
    });
  });
});
