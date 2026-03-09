import { describe, test, expect, beforeEach } from 'vitest';

import { ComponentRegistry } from './ComponentRegistry';

import type { ComponentDefinition } from './types';

describe('ComponentRegistry', () => {
  beforeEach(() => {
    ComponentRegistry.clear();
  });

  const mockComponent: ComponentDefinition = {
    id: 'test.button',
    type: 'button',
    category: 'UI',
    label: 'Test Button',
    description: 'A test button',
    icon: '🔘',
    version: '1.0.0',
    defaultData: { text: 'Click' },
    tags: ['test', 'button'],
  };

  describe('register', () => {
    test('should register a component', () => {
      ComponentRegistry.register(mockComponent);
      
      const retrieved = ComponentRegistry.get('button');
      expect(retrieved).toBeTruthy();
      expect(retrieved?.label).toBe('Test Button');
    });

    test('should throw error for duplicate registration', () => {
      ComponentRegistry.register(mockComponent);
      
      expect(() => {
        ComponentRegistry.register(mockComponent);
      }).toThrow('Component already registered');
    });

    test('should update category index', () => {
      ComponentRegistry.register(mockComponent);
      
      const categories = ComponentRegistry.listCategories();
      expect(categories).toContain('UI');
    });
  });

  describe('get', () => {
    test('should get component by type', () => {
      ComponentRegistry.register(mockComponent);
      
      const component = ComponentRegistry.get('button');
      expect(component).toBeTruthy();
      expect(component?.type).toBe('button');
    });

    test('should return null for non-existent component', () => {
      const component = ComponentRegistry.get('nonexistent');
      expect(component).toBeNull();
    });
  });

  describe('getVersion', () => {
    test('should get specific version', () => {
      ComponentRegistry.register(mockComponent);
      
      const component = ComponentRegistry.getVersion('button', '1.0.0');
      expect(component).toBeTruthy();
      expect(component?.version).toBe('1.0.0');
    });

    test('should return null for wrong version', () => {
      ComponentRegistry.register(mockComponent);
      
      const component = ComponentRegistry.getVersion('button', '2.0.0');
      expect(component).toBeNull();
    });
  });

  describe('list', () => {
    test('should list all components', () => {
      ComponentRegistry.register(mockComponent);
      ComponentRegistry.register({
        ...mockComponent,
        id: 'test.card',
        type: 'card',
        label: 'Test Card',
      });
      
      const components = ComponentRegistry.list();
      expect(components).toHaveLength(2);
    });

    test('should filter components', () => {
      ComponentRegistry.register(mockComponent);
      ComponentRegistry.register({
        ...mockComponent,
        id: 'test.card',
        type: 'card',
        category: 'Layout',
      });
      
      const uiComponents = ComponentRegistry.list((c) => c.category === 'UI');
      expect(uiComponents).toHaveLength(1);
    });
  });

  describe('listByCategory', () => {
    test('should list components by category', () => {
      ComponentRegistry.register(mockComponent);
      ComponentRegistry.register({
        ...mockComponent,
        id: 'test.card',
        type: 'card',
      });
      
      const uiComponents = ComponentRegistry.listByCategory('UI');
      expect(uiComponents).toHaveLength(2);
    });

    test('should return empty array for non-existent category', () => {
      const components = ComponentRegistry.listByCategory('NonExistent');
      expect(components).toHaveLength(0);
    });
  });

  describe('search', () => {
    test('should search by label', () => {
      ComponentRegistry.register(mockComponent);
      
      const results = ComponentRegistry.search('button');
      expect(results).toHaveLength(1);
      expect(results[0].label).toBe('Test Button');
    });

    test('should search by tag', () => {
      ComponentRegistry.register(mockComponent);
      
      const results = ComponentRegistry.search('test');
      expect(results).toHaveLength(1);
    });

    test('should be case insensitive', () => {
      ComponentRegistry.register(mockComponent);
      
      const results = ComponentRegistry.search('BUTTON');
      expect(results).toHaveLength(1);
    });
  });

  describe('has', () => {
    test('should check if component exists', () => {
      ComponentRegistry.register(mockComponent);
      
      expect(ComponentRegistry.has('button')).toBe(true);
      expect(ComponentRegistry.has('nonexistent')).toBe(false);
    });

    test('should check specific version', () => {
      ComponentRegistry.register(mockComponent);
      
      expect(ComponentRegistry.has('button', '1.0.0')).toBe(true);
      expect(ComponentRegistry.has('button', '2.0.0')).toBe(false);
    });
  });

  describe('remove', () => {
    test('should remove component', () => {
      ComponentRegistry.register(mockComponent);
      
      const removed = ComponentRegistry.remove('button', '1.0.0');
      expect(removed).toBe(true);
      expect(ComponentRegistry.has('button')).toBe(false);
    });

    test('should return false for non-existent component', () => {
      const removed = ComponentRegistry.remove('nonexistent', '1.0.0');
      expect(removed).toBe(false);
    });
  });

  describe('stats', () => {
    test('should return registry statistics', () => {
      ComponentRegistry.register(mockComponent);
      ComponentRegistry.register({
        ...mockComponent,
        id: 'test.deprecated',
        type: 'deprecated',
        deprecated: true,
      });
      
      const stats = ComponentRegistry.stats();
      expect(stats.total).toBe(2);
      expect(stats.categories).toBe(1);
      expect(stats.deprecated).toBe(1);
    });
  });
});
