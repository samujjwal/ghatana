/**
 * Template Manager Tests (Feature 2.12)
 *
 * Test coverage for template gallery, parameters, and version updates
 */

import { describe, it, expect } from 'vitest';

import {
  createGalleryState,
  addToGallery,
  removeFromGallery,
  setCategory,
  getTemplatesByCategory,
  getFeaturedTemplates,
  setFeatured,
  recordUsage,
  getRecentlyUsed,
  getPopularTemplates,
  getTopRatedTemplates,
  rateTemplate,
  applyParameters,
  getParameterDefaults,
  checkForUpdates,
  getAvailableUpdates,
  clearUpdate,
  searchTemplates,
  exportGallery,
  importGallery,
  getGalleryStats,
  type GalleryTemplate,
  type TemplateCategory,
  type TemplateParameter,
} from '../templateManager';

describe('templateManager', () => {
  describe('Gallery State', () => {
    it('should create empty gallery state', () => {
      const state = createGalleryState();

      expect(state.templates.size).toBe(0);
      expect(state.categories.size).toBe(0);
      expect(state.featured).toEqual([]);
      expect(state.recentlyUsed).toEqual([]);
      expect(state.updates.size).toBe(0);
    });

    it('should add template to gallery', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Login Flow',
        state: { nodes: [], edges: [] },
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);

      expect(state.templates.size).toBe(1);
      expect(state.templates.get('tpl-1')).toEqual({
        ...template,
        usageCount: 0,
        rating: 0,
      });
    });

    it('should remove template from gallery', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Test',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);
      state = removeFromGallery(state, 'tpl-1');

      expect(state.templates.size).toBe(0);
    });

    it('should remove template from featured and recently used lists', () => {
      let state = createGalleryState();
      state = { ...state, featured: ['tpl-1'], recentlyUsed: ['tpl-1'] };

      state = removeFromGallery(state, 'tpl-1');

      expect(state.featured).toEqual([]);
      expect(state.recentlyUsed).toEqual([]);
    });
  });

  describe('Categories', () => {
    it('should add category', () => {
      let state = createGalleryState();

      const category: TemplateCategory = {
        id: 'cat-1',
        name: 'Flowcharts',
        description: 'Flow diagram templates',
        order: 1,
      };

      state = setCategory(state, category);

      expect(state.categories.size).toBe(1);
      expect(state.categories.get('cat-1')).toEqual(category);
    });

    it('should get templates by category', () => {
      let state = createGalleryState();

      const category: TemplateCategory = {
        id: 'cat-1',
        name: 'Flowcharts',
        description: 'Flow diagrams',
        order: 1,
      };

      state = setCategory(state, category);

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template 1',
        category: 'cat-1',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Template 2',
        category: 'cat-2',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const templates = getTemplatesByCategory(state, 'cat-1');

      expect(templates.length).toBe(1);
      expect(templates[0].id).toBe('tpl-1');
    });

    it('should include subcategories when requested', () => {
      let state = createGalleryState();

      const parent: TemplateCategory = {
        id: 'parent',
        name: 'Parent',
        description: 'Parent category',
        order: 1,
        subcategories: [
          {
            id: 'child',
            name: 'Child',
            description: 'Child category',
            order: 1,
          },
        ],
      };

      state = setCategory(state, parent);

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Parent Template',
        category: 'parent',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Child Template',
        category: 'child',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const templates = getTemplatesByCategory(state, 'parent', true);

      expect(templates.length).toBe(2);
    });
  });

  describe('Featured Templates', () => {
    it('should mark template as featured', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Featured Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);
      state = setFeatured(state, 'tpl-1', true);

      expect(state.featured).toContain('tpl-1');
      expect(state.templates.get('tpl-1')?.featured).toBe(true);
    });

    it('should unmark template as featured', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);
      state = setFeatured(state, 'tpl-1', true);
      state = setFeatured(state, 'tpl-1', false);

      expect(state.featured).not.toContain('tpl-1');
      expect(state.templates.get('tpl-1')?.featured).toBe(false);
    });

    it('should get featured templates', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Featured 1',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Featured 2',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);
      state = setFeatured(state, 'tpl-1', true);
      state = setFeatured(state, 'tpl-2', true);

      const featured = getFeaturedTemplates(state);

      expect(featured.length).toBe(2);
    });
  });

  describe('Usage Tracking', () => {
    it('should record template usage', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);
      state = recordUsage(state, 'tpl-1');

      const updated = state.templates.get('tpl-1');
      expect(updated?.usageCount).toBe(1);
      expect(updated?.lastUsed).toBeDefined();
    });

    it('should update recently used list', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template 1',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Template 2',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);
      state = recordUsage(state, 'tpl-1');
      state = recordUsage(state, 'tpl-2');

      expect(state.recentlyUsed[0]).toBe('tpl-2');
      expect(state.recentlyUsed[1]).toBe('tpl-1');
    });

    it('should limit recently used list to 10', () => {
      let state = createGalleryState();

      for (let i = 1; i <= 15; i++) {
        const template: GalleryTemplate = {
          id: `tpl-${i}`,
          name: `Template ${i}`,
          state: {},
          createdAt: Date.now(),
          updatedAt: Date.now(),
        };
        state = addToGallery(state, template);
        state = recordUsage(state, `tpl-${i}`);
      }

      expect(state.recentlyUsed.length).toBe(10);
    });

    it('should get recently used templates', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);
      state = recordUsage(state, 'tpl-1');

      const recent = getRecentlyUsed(state);

      expect(recent.length).toBe(1);
      expect(recent[0].id).toBe('tpl-1');
    });

    it('should get popular templates', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Popular',
        usageCount: 100,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Less Popular',
        usageCount: 10,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const popular = getPopularTemplates(state, 2);

      expect(popular[0].id).toBe('tpl-1');
      expect(popular[1].id).toBe('tpl-2');
    });
  });

  describe('Ratings', () => {
    it('should rate template', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);
      state = rateTemplate(state, 'tpl-1', 4.5);

      expect(state.templates.get('tpl-1')?.rating).toBe(4.5);
    });

    it('should clamp rating to 0-5', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);
      state = rateTemplate(state, 'tpl-1', 10);

      expect(state.templates.get('tpl-1')?.rating).toBe(5);

      state = rateTemplate(state, 'tpl-1', -5);

      expect(state.templates.get('tpl-1')?.rating).toBe(0);
    });

    it('should get top-rated templates', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Best',
        rating: 5,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Good',
        rating: 4,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const topRated = getTopRatedTemplates(state, 2);

      expect(topRated[0].id).toBe('tpl-1');
      expect(topRated[1].id).toBe('tpl-2');
    });
  });

  describe('Parameters', () => {
    it('should apply parameters to template', () => {
      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
        parameters: [
          {
            id: 'color',
            name: 'Primary Color',
            description: 'Main color',
            type: 'color',
            defaultValue: '#blue',
          },
        ],
      };

      const result = applyParameters(template, { color: '#red' });

      expect(result.parameterValues).toEqual({ color: '#red' });
    });

    it('should validate required parameters', () => {
      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
        parameters: [
          {
            id: 'name',
            name: 'Name',
            description: 'Template name',
            type: 'string',
            required: true,
          },
        ],
      };

      expect(() => applyParameters(template, {})).toThrow('Parameter "Name" is required');
    });

    it('should validate parameter types', () => {
      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
        parameters: [
          {
            id: 'count',
            name: 'Count',
            description: 'Number of items',
            type: 'number',
          },
        ],
      };

      expect(() => applyParameters(template, { count: 'not-a-number' as unknown })).toThrow(
        'Parameter "Count" must be a number'
      );
    });

    it('should validate number ranges', () => {
      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
        parameters: [
          {
            id: 'count',
            name: 'Count',
            description: 'Number of items',
            type: 'number',
            validation: {
              min: 1,
              max: 10,
            },
          },
        ],
      };

      expect(() => applyParameters(template, { count: 0 })).toThrow(
        'Parameter "Count" must be at least 1'
      );

      expect(() => applyParameters(template, { count: 20 })).toThrow(
        'Parameter "Count" must be at most 10'
      );
    });

    it('should validate string patterns', () => {
      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
        parameters: [
          {
            id: 'email',
            name: 'Email',
            description: 'Email address',
            type: 'string',
            validation: {
              pattern: '^[^@]+@[^@]+\\.[^@]+$',
            },
          },
        ],
      };

      expect(() => applyParameters(template, { email: 'invalid' })).toThrow(
        'Parameter "Email" has invalid format'
      );

      const result = applyParameters(template, { email: 'test@example.com' });
      expect(result.parameterValues?.email).toBe('test@example.com');
    });

    it('should validate select options', () => {
      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
        parameters: [
          {
            id: 'size',
            name: 'Size',
            description: 'Template size',
            type: 'select',
            options: ['small', 'medium', 'large'],
          },
        ],
      };

      expect(() => applyParameters(template, { size: 'huge' })).toThrow(
        'Parameter "Size" must be one of: small, medium, large'
      );
    });

    it('should get parameter defaults', () => {
      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
        parameters: [
          {
            id: 'color',
            name: 'Color',
            description: 'Color',
            type: 'color',
            defaultValue: '#blue',
          },
          {
            id: 'size',
            name: 'Size',
            description: 'Size',
            type: 'number',
            defaultValue: 10,
          },
        ],
      };

      const defaults = getParameterDefaults(template);

      expect(defaults).toEqual({
        color: '#blue',
        size: 10,
      });
    });
  });

  describe('Version Updates', () => {
    it('should check for template updates', () => {
      let state = createGalleryState();

      const local: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        tags: ['v1.0.0'],
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const remote: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        tags: ['v2.0.0'],
        description: 'Major update',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, local);
      state = checkForUpdates(state, [remote]);

      expect(state.updates.size).toBe(1);
      const update = state.updates.get('tpl-1');
      expect(update?.currentVersion).toBe('1.0.0');
      expect(update?.latestVersion).toBe('2.0.0');
      expect(update?.breaking).toBe(true);
    });

    it('should not report update for same version', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        tags: ['v1.0.0'],
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);
      state = checkForUpdates(state, [template]);

      expect(state.updates.size).toBe(0);
    });

    it('should get available updates', () => {
      let state = createGalleryState();

      const local: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        tags: ['v1.0.0'],
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const remote: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        tags: ['v1.1.0'],
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, local);
      state = checkForUpdates(state, [remote]);

      const updates = getAvailableUpdates(state);

      expect(updates.length).toBe(1);
    });

    it('should clear update notification', () => {
      let state = createGalleryState();

      const local: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        tags: ['v1.0.0'],
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const remote: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        tags: ['v2.0.0'],
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, local);
      state = checkForUpdates(state, [remote]);
      state = clearUpdate(state, 'tpl-1');

      expect(state.updates.size).toBe(0);
    });
  });

  describe('Search', () => {
    it('should search templates by text', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Login Flow',
        description: 'User authentication',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Dashboard',
        description: 'Main dashboard',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const results = searchTemplates(state, { text: 'login' });

      expect(results.length).toBe(1);
      expect(results[0].id).toBe('tpl-1');
    });

    it('should search by category', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template 1',
        category: 'flowchart',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Template 2',
        category: 'uml',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const results = searchTemplates(state, { category: 'flowchart' });

      expect(results.length).toBe(1);
      expect(results[0].id).toBe('tpl-1');
    });

    it('should search by tags (AND logic)', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template 1',
        tags: ['auth', 'flow', 'best-practice'],
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Template 2',
        tags: ['auth', 'diagram'],
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const results = searchTemplates(state, { tags: ['auth', 'flow'] });

      expect(results.length).toBe(1);
      expect(results[0].id).toBe('tpl-1');
    });

    it('should filter by featured status', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Featured',
        featured: true,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Regular',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const results = searchTemplates(state, { featured: true });

      expect(results.length).toBe(1);
      expect(results[0].id).toBe('tpl-1');
    });

    it('should filter by minimum rating', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'High Rated',
        rating: 4.5,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Low Rated',
        rating: 2.0,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const results = searchTemplates(state, { minRating: 4.0 });

      expect(results.length).toBe(1);
      expect(results[0].id).toBe('tpl-1');
    });

    it('should sort by name', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Zebra',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Apple',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const results = searchTemplates(state, { sortBy: 'name' });

      expect(results[0].name).toBe('Apple');
      expect(results[1].name).toBe('Zebra');
    });

    it('should sort by usage count', () => {
      let state = createGalleryState();

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Less Used',
        usageCount: 5,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'More Used',
        usageCount: 50,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template1);
      state = addToGallery(state, template2);

      const results = searchTemplates(state, { sortBy: 'usage', sortOrder: 'desc' });

      expect(results[0].id).toBe('tpl-2');
    });
  });

  describe('Export/Import', () => {
    it('should export gallery to JSON', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);

      const json = exportGallery(state);
      const parsed = JSON.parse(json);

      expect(parsed.templates).toBeDefined();
      expect(parsed.categories).toBeDefined();
    });

    it('should import gallery from JSON', () => {
      let state = createGalleryState();

      const template: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template',
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = addToGallery(state, template);

      const json = exportGallery(state);
      const imported = importGallery(json);

      expect(imported.templates.size).toBe(1);
      expect(imported.templates.get('tpl-1')?.name).toBe('Template');
    });
  });

  describe('Statistics', () => {
    it('should get gallery statistics', () => {
      let state = createGalleryState();

      const category: TemplateCategory = {
        id: 'cat-1',
        name: 'Category 1',
        description: 'Test category',
        order: 1,
      };

      const template1: GalleryTemplate = {
        id: 'tpl-1',
        name: 'Template 1',
        category: 'cat-1',
        rating: 4,
        usageCount: 10,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const template2: GalleryTemplate = {
        id: 'tpl-2',
        name: 'Template 2',
        category: 'cat-1',
        rating: 5,
        usageCount: 20,
        state: {},
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      state = setCategory(state, category);
      state = addToGallery(state, template1);
      state = addToGallery(state, template2);
      state = setFeatured(state, 'tpl-1', true);

      const stats = getGalleryStats(state);

      expect(stats.totalTemplates).toBe(2);
      expect(stats.totalCategories).toBe(1);
      expect(stats.featuredCount).toBe(1);
      expect(stats.totalUsage).toBe(30);
      expect(stats.averageRating).toBe(4.5);
    });
  });
});
