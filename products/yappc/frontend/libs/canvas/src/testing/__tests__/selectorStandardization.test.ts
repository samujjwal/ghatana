/**
 * @jest-environment jsdom
 */
import { describe, it, expect, beforeEach } from 'vitest';

import {
  createSelectorManager,
  generateSelector,
  generateNodeSelector,
  generateEdgeSelector,
  generatePaletteSelector,
  generateToolbarSelector,
  generatePanelSelector,
  generateDropZoneSelector,
  generateMinimapSelector,
  generateControlsSelector,
  validateSelector,
  registerSelector,
  getRegisteredSelector,
  getSelectorsForComponent,
  validateAllSelectors,
  getSelectorStatistics,
  exportSelectorDocumentation,
  parseSelector,
  followsConvention,
  suggestSelectorFix,
  DEFAULT_SELECTOR_RULES,
  type SelectorManagerState,
  type SelectorPattern,
} from '../selectorStandardization';

describe('Selector Standardization', () => {
  let manager: SelectorManagerState;

  beforeEach(() => {
    manager = createSelectorManager();
  });

  describe('Manager Creation', () => {
    it('should create manager with default rules', () => {
      expect(manager.rules).toEqual(DEFAULT_SELECTOR_RULES);
      expect(manager.registeredSelectors.size).toBe(0);
      expect(manager.validationCache.size).toBe(0);
    });

    it('should create manager with custom rules', () => {
      const customManager = createSelectorManager({
        maxLength: 64,
        requiredPrefix: 'test',
      });

      expect(customManager.rules.maxLength).toBe(64);
      expect(customManager.rules.requiredPrefix).toBe('test');
      expect(customManager.rules.separator).toBe('-'); // default
    });

    it('should merge custom rules with defaults', () => {
      const customManager = createSelectorManager({
        enforceLowercase: false,
      });

      expect(customManager.rules.enforceLowercase).toBe(false);
      expect(customManager.rules.maxLength).toBe(128); // default
    });
  });

  describe('Selector Generation', () => {
    it('should generate selector from pattern', () => {
      const pattern: SelectorPattern = {
        prefix: 'canvas',
        component: 'node',
        id: 'test-123',
      };

      const selector = generateSelector(pattern);
      expect(selector).toBe('canvas-node-test-123');
    });

    it('should include action in selector', () => {
      const pattern: SelectorPattern = {
        prefix: 'canvas',
        component: 'button',
        action: 'save',
      };

      const selector = generateSelector(pattern);
      expect(selector).toBe('canvas-button-save');
    });

    it('should include state in selector', () => {
      const pattern: SelectorPattern = {
        prefix: 'canvas',
        component: 'node',
        id: 'node-1',
        state: 'selected',
      };

      const selector = generateSelector(pattern);
      expect(selector).toBe('canvas-node-node-1-selected');
    });

    it('should include all parts in selector', () => {
      const pattern: SelectorPattern = {
        prefix: 'canvas',
        component: 'toolbar',
        id: 'main',
        action: 'add',
        state: 'disabled',
        suffix: 'icon',
      };

      const selector = generateSelector(pattern);
      expect(selector).toBe('canvas-toolbar-main-add-disabled-icon');
    });

    it('should convert to lowercase', () => {
      const pattern: SelectorPattern = {
        prefix: 'CANVAS',
        component: 'node',
        id: 'TestNode',
      };

      const selector = generateSelector(pattern);
      expect(selector).toBe('canvas-node-testnode');
    });
  });

  describe('Node Selectors', () => {
    it('should generate node selector', () => {
      const selector = generateNodeSelector('node-123');
      expect(selector).toBe('canvas-node-node-123');
    });

    it('should generate node selector with state', () => {
      const selector = generateNodeSelector('node-123', 'selected');
      expect(selector).toBe('canvas-node-node-123-selected');
    });

    it('should handle complex node IDs', () => {
      const selector = generateNodeSelector('workflow-step-5');
      expect(selector).toBe('canvas-node-workflow-step-5');
    });
  });

  describe('Edge Selectors', () => {
    it('should generate edge selector', () => {
      const selector = generateEdgeSelector('edge-abc');
      expect(selector).toBe('canvas-edge-edge-abc');
    });

    it('should generate edge selector with state', () => {
      const selector = generateEdgeSelector('edge-abc', 'active');
      expect(selector).toBe('canvas-edge-edge-abc-active');
    });
  });

  describe('Palette Selectors', () => {
    it('should generate palette selector', () => {
      const selector = generatePaletteSelector('shapes', 'rectangle');
      expect(selector).toBe('canvas-palette-shapes-rectangle');
    });

    it('should handle different categories', () => {
      const selector1 = generatePaletteSelector('diagrams', 'flowchart');
      const selector2 = generatePaletteSelector('icons', 'cloud');

      expect(selector1).toBe('canvas-palette-diagrams-flowchart');
      expect(selector2).toBe('canvas-palette-icons-cloud');
    });
  });

  describe('Toolbar Selectors', () => {
    it('should generate toolbar selector with action', () => {
      const selector = generateToolbarSelector('add');
      expect(selector).toBe('canvas-toolbar-add');
    });

    it('should generate toolbar selector with action and state', () => {
      const selector = generateToolbarSelector('delete', 'disabled');
      expect(selector).toBe('canvas-toolbar-delete-disabled');
    });

    it('should handle zoom actions', () => {
      const zoomIn = generateToolbarSelector('zoom-in');
      const zoomOut = generateToolbarSelector('zoom-out');

      expect(zoomIn).toBe('canvas-toolbar-zoom-in');
      expect(zoomOut).toBe('canvas-toolbar-zoom-out');
    });
  });

  describe('Panel Selectors', () => {
    it('should generate panel selector', () => {
      const selector = generatePanelSelector('properties');
      expect(selector).toBe('canvas-panel-properties');
    });

    it('should generate panel selector with section', () => {
      const selector = generatePanelSelector('properties', 'style');
      expect(selector).toBe('canvas-panel-properties-style');
    });

    it('should handle different panel types', () => {
      const layers = generatePanelSelector('layers');
      const history = generatePanelSelector('history');

      expect(layers).toBe('canvas-panel-layers');
      expect(history).toBe('canvas-panel-history');
    });
  });

  describe('Drop Zone Selectors', () => {
    it('should generate drop zone selector', () => {
      const selector = generateDropZoneSelector('main');
      expect(selector).toBe('canvas-viewport-drop-zone-main');
    });

    it('should handle different zones', () => {
      const primary = generateDropZoneSelector('primary');
      const secondary = generateDropZoneSelector('secondary');

      expect(primary).toBe('canvas-viewport-drop-zone-primary');
      expect(secondary).toBe('canvas-viewport-drop-zone-secondary');
    });
  });

  describe('Minimap Selectors', () => {
    it('should generate minimap selector', () => {
      const selector = generateMinimapSelector();
      expect(selector).toBe('canvas-minimap');
    });

    it('should generate minimap selector with element', () => {
      const selector = generateMinimapSelector('viewport');
      expect(selector).toBe('canvas-minimap-viewport');
    });

    it('should handle minimap controls', () => {
      const toggle = generateMinimapSelector('toggle');
      const resize = generateMinimapSelector('resize');

      expect(toggle).toBe('canvas-minimap-toggle');
      expect(resize).toBe('canvas-minimap-resize');
    });
  });

  describe('Controls Selectors', () => {
    it('should generate controls selector', () => {
      const selector = generateControlsSelector('zoom-in');
      expect(selector).toBe('canvas-controls-zoom-in');
    });

    it('should handle different control actions', () => {
      const fitView = generateControlsSelector('fit-view');
      const reset = generateControlsSelector('reset');

      expect(fitView).toBe('canvas-controls-fit-view');
      expect(reset).toBe('canvas-controls-reset');
    });
  });

  describe('Selector Validation', () => {
    it('should validate correct selector', () => {
      const result = validateSelector('canvas-node-test-123');

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should reject empty selector', () => {
      const result = validateSelector('');

      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Selector cannot be empty');
    });

    it('should reject selector without prefix', () => {
      const result = validateSelector('node-test-123');

      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.errors.some((e) => e.includes('prefix'))).toBe(true);
    });

    it('should reject selector exceeding max length', () => {
      const longSelector = `canvas-${  'a'.repeat(150)}`;
      const result = validateSelector(longSelector);

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('maximum length'))).toBe(true);
    });

    it('should reject selector with disallowed characters', () => {
      const result = validateSelector('canvas-node@test#123');

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('disallowed characters'))).toBe(true);
    });

    it('should warn about non-lowercase selectors', () => {
      const result = validateSelector('canvas-node-TestNode');

      expect(result.warnings.some((w) => w.includes('lowercase'))).toBe(true);
    });

    it('should reject consecutive separators', () => {
      const result = validateSelector('canvas--node--test');

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('consecutive'))).toBe(true);
    });

    it('should reject leading separator', () => {
      const result = validateSelector('-canvas-node-test');

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('start or end'))).toBe(true);
    });

    it('should reject trailing separator', () => {
      const result = validateSelector('canvas-node-test-');

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('start or end'))).toBe(true);
    });

    it('should warn about missing separator', () => {
      const result = validateSelector('canvasnode');

      expect(result.warnings.some((w) => w.includes('separator'))).toBe(true);
    });

    it('should validate with custom rules', () => {
      const customRules = { ...DEFAULT_SELECTOR_RULES, maxLength: 15 };
      const result = validateSelector('canvas-node-test-123', customRules); // 20 characters > 15

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('maximum length'))).toBe(true);
    });
  });

  describe('Selector Registration', () => {
    it('should register valid selector', () => {
      const pattern: SelectorPattern = {
        prefix: 'canvas',
        component: 'node',
        id: 'test',
      };

      const newManager = registerSelector(manager, 'canvas-node-test', pattern);

      expect(newManager.registeredSelectors.size).toBe(1);
      expect(newManager.registeredSelectors.get('canvas-node-test')).toEqual(pattern);
    });

    it('should throw on invalid selector registration', () => {
      const pattern: SelectorPattern = {
        prefix: 'canvas',
        component: 'node',
      };

      expect(() => {
        registerSelector(manager, 'invalid@selector', pattern);
      }).toThrow(/Invalid selector/);
    });

    it('should cache validation result', () => {
      const pattern: SelectorPattern = {
        prefix: 'canvas',
        component: 'node',
        id: 'test',
      };

      const newManager = registerSelector(manager, 'canvas-node-test', pattern);

      expect(newManager.validationCache.has('canvas-node-test')).toBe(true);
      expect(newManager.validationCache.get('canvas-node-test')?.valid).toBe(true);
    });

    it('should register multiple selectors', () => {
      let newManager = registerSelector(
        manager,
        'canvas-node-test1',
        { prefix: 'canvas', component: 'node', id: 'test1' }
      );

      newManager = registerSelector(
        newManager,
        'canvas-node-test2',
        { prefix: 'canvas', component: 'node', id: 'test2' }
      );

      expect(newManager.registeredSelectors.size).toBe(2);
    });
  });

  describe('Selector Retrieval', () => {
    beforeEach(() => {
      manager = registerSelector(manager, 'canvas-node-test', {
        prefix: 'canvas',
        component: 'node',
        id: 'test',
      });

      manager = registerSelector(manager, 'canvas-edge-conn', {
        prefix: 'canvas',
        component: 'edge',
        id: 'conn',
      });
    });

    it('should get registered selector', () => {
      const pattern = getRegisteredSelector(manager, 'canvas-node-test');

      expect(pattern).toBeDefined();
      expect(pattern?.component).toBe('node');
      expect(pattern?.id).toBe('test');
    });

    it('should return undefined for non-existent selector', () => {
      const pattern = getRegisteredSelector(manager, 'canvas-missing');

      expect(pattern).toBeUndefined();
    });

    it('should get selectors by component', () => {
      const nodeSelectors = getSelectorsForComponent(manager, 'node');

      expect(nodeSelectors.size).toBe(1);
      expect(nodeSelectors.has('canvas-node-test')).toBe(true);
    });

    it('should return empty map for non-existent component', () => {
      const selectors = getSelectorsForComponent(manager, 'panel');

      expect(selectors.size).toBe(0);
    });

    it('should filter by component correctly', () => {
      const nodeSelectors = getSelectorsForComponent(manager, 'node');
      const edgeSelectors = getSelectorsForComponent(manager, 'edge');

      expect(nodeSelectors.size).toBe(1);
      expect(edgeSelectors.size).toBe(1);
      expect(nodeSelectors.has('canvas-node-test')).toBe(true);
      expect(edgeSelectors.has('canvas-edge-conn')).toBe(true);
    });
  });

  describe('Validation of All Selectors', () => {
    beforeEach(() => {
      manager = registerSelector(manager, 'canvas-node-test1', {
        prefix: 'canvas',
        component: 'node',
        id: 'test1',
      });

      manager = registerSelector(manager, 'canvas-node-test2', {
        prefix: 'canvas',
        component: 'node',
        id: 'test2',
      });
    });

    it('should validate all registered selectors', () => {
      const results = validateAllSelectors(manager);

      expect(results.size).toBe(2);
      expect(results.get('canvas-node-test1')?.valid).toBe(true);
      expect(results.get('canvas-node-test2')?.valid).toBe(true);
    });

    it('should use cached validation results', () => {
      const results1 = validateAllSelectors(manager);
      const results2 = validateAllSelectors(manager);

      expect(results1).toEqual(results2);
    });
  });

  describe('Statistics', () => {
    beforeEach(() => {
      manager = registerSelector(manager, 'canvas-node-test1', {
        prefix: 'canvas',
        component: 'node',
        id: 'test1',
      });

      manager = registerSelector(manager, 'canvas-edge-conn1', {
        prefix: 'canvas',
        component: 'edge',
        id: 'conn1',
      });

      manager = registerSelector(manager, 'canvas-panel-props', {
        prefix: 'canvas',
        component: 'panel',
        id: 'props',
      });
    });

    it('should calculate total selectors', () => {
      const stats = getSelectorStatistics(manager);

      expect(stats.totalSelectors).toBe(3);
    });

    it('should calculate valid selectors', () => {
      const stats = getSelectorStatistics(manager);

      expect(stats.validSelectors).toBe(3);
      expect(stats.invalidSelectors).toBe(0);
    });

    it('should group by component', () => {
      const stats = getSelectorStatistics(manager);

      expect(stats.selectorsByComponent.get('node')).toBe(1);
      expect(stats.selectorsByComponent.get('edge')).toBe(1);
      expect(stats.selectorsByComponent.get('panel')).toBe(1);
    });

    it('should calculate average length', () => {
      const stats = getSelectorStatistics(manager);

      expect(stats.averageLength).toBeGreaterThan(0);
    });

    it('should handle empty manager', () => {
      const emptyManager = createSelectorManager();
      const stats = getSelectorStatistics(emptyManager);

      expect(stats.totalSelectors).toBe(0);
      expect(stats.averageLength).toBe(0);
    });
  });

  describe('Documentation Export', () => {
    beforeEach(() => {
      manager = registerSelector(manager, 'canvas-node-test', {
        prefix: 'canvas',
        component: 'node',
        id: 'test',
      });

      manager = registerSelector(manager, 'canvas-toolbar-add', {
        prefix: 'canvas',
        component: 'toolbar',
        action: 'add',
        state: 'active',
      });
    });

    it('should export markdown documentation', () => {
      const docs = exportSelectorDocumentation(manager);

      expect(docs).toContain('# Canvas Test Selector Reference');
      expect(docs).toContain('## Selector Conventions');
      expect(docs).toContain('canvas-node-test');
      expect(docs).toContain('canvas-toolbar-add');
    });

    it('should include selector rules', () => {
      const docs = exportSelectorDocumentation(manager);

      expect(docs).toContain('**Prefix**: `canvas`');
      expect(docs).toContain('**Separator**: `-`');
      expect(docs).toContain('**Max Length**: 128');
    });

    it('should group by component', () => {
      const docs = exportSelectorDocumentation(manager);

      expect(docs).toContain('## Node Selectors');
      expect(docs).toContain('## Toolbar Selectors');
    });

    it('should include action and state info', () => {
      const docs = exportSelectorDocumentation(manager);

      expect(docs).toContain('Action: add');
      expect(docs).toContain('State: active');
    });

    it('should include statistics', () => {
      const docs = exportSelectorDocumentation(manager);

      expect(docs).toContain('## Statistics');
      expect(docs).toContain('Total Selectors: 2');
    });
  });

  describe('Selector Parsing', () => {
    it('should parse selector into components', () => {
      const parsed = parseSelector('canvas-node-test-123');

      expect(parsed.prefix).toBe('canvas');
      expect(parsed.component).toBe('node');
      expect(parsed.id).toBe('test-123');
      expect(parsed.parts).toEqual(['canvas', 'node', 'test', '123']);
    });

    it('should handle selector with multiple parts', () => {
      const parsed = parseSelector('canvas-toolbar-main-add');

      expect(parsed.prefix).toBe('canvas');
      expect(parsed.component).toBe('toolbar');
      expect(parsed.id).toBe('main-add');
    });

    it('should handle minimal selector', () => {
      const parsed = parseSelector('canvas-node');

      expect(parsed.prefix).toBe('canvas');
      expect(parsed.component).toBe('node');
      expect(parsed.id).toBeUndefined();
    });

    it('should handle single part selector', () => {
      const parsed = parseSelector('canvas');

      expect(parsed.prefix).toBeUndefined();
      expect(parsed.component).toBeUndefined();
      expect(parsed.parts).toEqual(['canvas']);
    });

    it('should use custom separator', () => {
      const parsed = parseSelector('canvas_node_test', '_');

      expect(parsed.prefix).toBe('canvas');
      expect(parsed.component).toBe('node');
      expect(parsed.id).toBe('test');
    });
  });

  describe('Convention Checking', () => {
    it('should check if selector follows convention', () => {
      expect(followsConvention('canvas-node-test')).toBe(true);
    });

    it('should reject selector without prefix', () => {
      expect(followsConvention('node-test')).toBe(false);
    });

    it('should reject selector with uppercase', () => {
      expect(followsConvention('canvas-node-TestNode')).toBe(false);
    });

    it('should reject selector with special characters', () => {
      expect(followsConvention('canvas-node@test')).toBe(false);
    });

    it('should work with custom rules', () => {
      const customRules = { ...DEFAULT_SELECTOR_RULES, enforceLowercase: false };
      expect(followsConvention('canvas-node-TestNode', customRules)).toBe(true);
    });
  });

  describe('Selector Fix Suggestions', () => {
    it('should add missing prefix', () => {
      const fixed = suggestSelectorFix('node-test');

      expect(fixed).toBe('canvas-node-test');
    });

    it('should convert to lowercase', () => {
      const fixed = suggestSelectorFix('canvas-node-TestNode');

      expect(fixed).toBe('canvas-node-testnode');
    });

    it('should remove disallowed characters', () => {
      const fixed = suggestSelectorFix('canvas-node@test#123');

      expect(fixed).toBe('canvas-nodetest123');
    });

    it('should remove consecutive separators', () => {
      const fixed = suggestSelectorFix('canvas--node--test');

      expect(fixed).toBe('canvas-node-test');
    });

    it('should remove leading separator', () => {
      const fixed = suggestSelectorFix('-canvas-node-test');

      expect(fixed).toBe('canvas-node-test');
    });

    it('should remove trailing separator', () => {
      const fixed = suggestSelectorFix('canvas-node-test-');

      expect(fixed).toBe('canvas-node-test');
    });

    it('should truncate long selectors', () => {
      const longSelector = `canvas-${  'a'.repeat(150)}`;
      const fixed = suggestSelectorFix(longSelector);

      expect(fixed.length).toBeLessThanOrEqual(128);
      expect(fixed.endsWith('-')).toBe(false);
    });

    it('should handle multiple fixes', () => {
      const fixed = suggestSelectorFix('--Node@Test##123--');

      expect(fixed).toBe('canvas-nodetest123');
    });

    it('should work with custom rules', () => {
      const customRules = { ...DEFAULT_SELECTOR_RULES, maxLength: 20 };
      const fixed = suggestSelectorFix('canvas-node-very-long-test-id', customRules);

      expect(fixed.length).toBeLessThanOrEqual(20);
    });
  });
});
