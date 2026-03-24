/**
 * @jest-environment node
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  SelectorStandardsManager,
  type SelectorCategory,
  type SelectorEntry,
  type ESLintRuleConfig,
  type NamingConvention,
} from '../selectorStandards';

describe('SelectorStandardsManager', () => {
  let manager: SelectorStandardsManager;

  beforeEach(() => {
    manager = new SelectorStandardsManager();
  });

  describe('Initialization', () => {
    it('should initialize with default configuration', () => {
      const config = manager.getConfig();

      expect(config.namingConventions.size).toBe(9);
      expect(config.eslintRule.severity).toBe('error');
      expect(config.strictMode).toBe(false);
      expect(config.maxSelectorLength).toBe(64);
    });

    it('should accept custom configuration', () => {
      const customManager = new SelectorStandardsManager({
        strictMode: true,
        maxSelectorLength: 32,
      });

      const config = customManager.getConfig();
      expect(config.strictMode).toBe(true);
      expect(config.maxSelectorLength).toBe(32);
    });

    it('should initialize with empty registry', () => {
      const selectors = manager.getAllSelectors();
      expect(selectors).toHaveLength(0);
    });
  });

  describe('Selector Generation', () => {
    it('should generate canvas selector', () => {
      const selector = manager.generateSelector('canvas', 'toolbar');
      expect(selector).toBe('canvas-toolbar');
    });

    it('should generate palette selector', () => {
      const selector = manager.generateSelector('palette', 'nodes');
      expect(selector).toBe('palette-nodes');
    });

    it('should generate node selector', () => {
      const selector = manager.generateSelector('node', 'user-profile');
      expect(selector).toBe('node-user-profile');
    });

    it('should generate edge selector', () => {
      const selector = manager.generateSelector('edge', 'connection');
      expect(selector).toBe('edge-connection');
    });

    it('should generate control selector', () => {
      const selector = manager.generateSelector('control', 'save-button');
      expect(selector).toBe('control-save-button');
    });

    it('should convert camelCase to kebab-case', () => {
      const selector = manager.generateSelector('node', 'userProfileCard');
      expect(selector).toBe('node-user-profile-card');
    });

    it('should handle custom category without prefix', () => {
      const selector = manager.generateSelector('custom', 'special-widget');
      expect(selector).toBe('special-widget');
    });

    it('should normalize spaces to hyphens', () => {
      const selector = manager.generateSelector('dialog', 'confirm delete');
      expect(selector).toBe('dialog-confirm-delete');
    });
  });

  describe('Selector Validation', () => {
    it('should validate correct canvas selector', () => {
      const result = manager.validateSelector('canvas-toolbar');
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should validate correct node selector', () => {
      const result = manager.validateSelector('node-user-profile');
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should reject uppercase characters', () => {
      const result = manager.validateSelector('Canvas-Toolbar');
      expect(result.valid).toBe(false);
      expect(result.errors).toContain(
        'Selector must contain only lowercase letters, numbers, and hyphens'
      );
    });

    it('should reject special characters', () => {
      const result = manager.validateSelector('canvas_toolbar!');
      expect(result.valid).toBe(false);
    });

    it('should reject selectors exceeding max length', () => {
      const longSelector = `canvas-${  'a'.repeat(60)}`;
      const result = manager.validateSelector(longSelector);
      expect(result.valid).toBe(false);
      expect(result.errors![0]).toContain('exceeds max length');
    });

    it('should warn on non-standard format in non-strict mode', () => {
      const result = manager.validateSelector('random-selector-123');
      // Should be valid but with warnings
      expect(result.valid).toBe(true);
      expect(result.warnings).toContain(
        'Selector does not match standard conventions'
      );
    });

    it('should fail on non-standard format in strict mode', () => {
      const strictManager = new SelectorStandardsManager({ strictMode: true });
      const result = strictManager.validateSelector('random-selector');
      expect(result.valid).toBe(false);
      expect(result.errors).toContain(
        'Selector does not match any naming convention'
      );
    });

    it('should provide suggestions for invalid selectors', () => {
      const strictManager = new SelectorStandardsManager({ strictMode: true });
      const result = strictManager.validateSelector('badformat');
      expect(result.suggestions).toBeDefined();
      expect(result.suggestions![0]).toBe('Example formats:');
    });
  });

  describe('Registry Management', () => {
    it('should register selector', () => {
      manager.registerSelector({
        component: 'NodePalette',
        selector: 'palette-nodes',
        category: 'palette',
        description: 'Node palette container',
      });

      const entry = manager.getSelector('NodePalette');
      expect(entry).toBeDefined();
      expect(entry!.selector).toBe('palette-nodes');
    });

    it('should include timestamp on registration', () => {
      manager.registerSelector({
        component: 'CanvasToolbar',
        selector: 'canvas-toolbar',
        category: 'canvas',
      });

      const entry = manager.getSelector('CanvasToolbar');
      expect(entry!.registeredAt).toBeInstanceOf(Date);
    });

    it('should get selector by component name', () => {
      manager.registerSelector({
        component: 'SaveButton',
        selector: 'control-save-button',
        category: 'control',
      });

      const entry = manager.getSelector('SaveButton');
      expect(entry).toBeDefined();
      expect(entry!.component).toBe('SaveButton');
    });

    it('should return undefined for non-existent component', () => {
      const entry = manager.getSelector('NonExistent');
      expect(entry).toBeUndefined();
    });

    it('should get all registered selectors', () => {
      manager.registerSelector({
        component: 'Comp1',
        selector: 'canvas-comp1',
        category: 'canvas',
      });
      manager.registerSelector({
        component: 'Comp2',
        selector: 'node-comp2',
        category: 'node',
      });

      const all = manager.getAllSelectors();
      expect(all).toHaveLength(2);
    });

    it('should filter selectors by category', () => {
      manager.registerSelector({
        component: 'Canvas1',
        selector: 'canvas-1',
        category: 'canvas',
      });
      manager.registerSelector({
        component: 'Node1',
        selector: 'node-1',
        category: 'node',
      });
      manager.registerSelector({
        component: 'Canvas2',
        selector: 'canvas-2',
        category: 'canvas',
      });

      const canvasSelectors = manager.getSelectorsByCategory('canvas');
      expect(canvasSelectors).toHaveLength(2);
      expect(canvasSelectors.every((s) => s.category === 'canvas')).toBe(true);
    });
  });

  describe('ESLint Rule Checking', () => {
    it('should detect missing data-testid on Canvas component', () => {
      const jsxCode = `
        <CanvasToolbar />
      `;

      const violations = manager.checkForMissingSelectors(
        jsxCode,
        'test.tsx'
      );
      expect(violations).toHaveLength(1);
      expect(violations[0].component).toBe('CanvasToolbar');
    });

    it('should not report when data-testid is present', () => {
      const jsxCode = `
        <CanvasToolbar data-testid="canvas-toolbar" />
      `;

      const violations = manager.checkForMissingSelectors(
        jsxCode,
        'test.tsx'
      );
      expect(violations).toHaveLength(0);
    });

    it('should ignore Fragment components', () => {
      const jsxCode = `
        <Fragment>
          <div>Content</div>
        </Fragment>
      `;

      const violations = manager.checkForMissingSelectors(
        jsxCode,
        'test.tsx'
      );
      expect(violations).toHaveLength(0);
    });

    it('should detect multiple missing selectors', () => {
      const jsxCode = `
        <CanvasToolbar />
        <PalettePanel />
        <NodeEditor />
      `;

      const violations = manager.checkForMissingSelectors(
        jsxCode,
        'test.tsx'
      );
      expect(violations.length).toBeGreaterThan(1);
    });

    it('should provide suggested fix when autoFix is enabled', () => {
      const jsxCode = `
        <CanvasToolbar />
      `;

      const violations = manager.checkForMissingSelectors(
        jsxCode,
        'test.tsx'
      );
      expect(violations[0].suggestedFix).toBe(
        'data-testid="canvas-toolbar"'
      );
    });

    it('should not provide fix when autoFix is disabled', () => {
      manager.updateESLintConfig({ autoFix: false });

      const jsxCode = `
        <CanvasToolbar />
      `;

      const violations = manager.checkForMissingSelectors(
        jsxCode,
        'test.tsx'
      );
      expect(violations[0].suggestedFix).toBeUndefined();
    });

    it('should include line and column numbers', () => {
      const jsxCode = `
        <CanvasToolbar />
      `;

      const violations = manager.checkForMissingSelectors(
        jsxCode,
        'test.tsx'
      );
      expect(violations[0].line).toBeGreaterThan(0);
      expect(violations[0].column).toBeGreaterThan(0);
    });

    it('should respect severity configuration', () => {
      manager.updateESLintConfig({ severity: 'warning' });

      const jsxCode = `
        <CanvasToolbar />
      `;

      const violations = manager.checkForMissingSelectors(
        jsxCode,
        'test.tsx'
      );
      expect(violations[0].severity).toBe('warning');
    });
  });

  describe('Violation Management', () => {
    beforeEach(() => {
      // Add some violations
      manager.checkForMissingSelectors(
        '<CanvasToolbar />',
        'file1.tsx'
      );
      manager.checkForMissingSelectors(
        '<PalettePanel />',
        'file2.tsx'
      );
    });

    it('should get all violations', () => {
      const violations = manager.getViolations();
      expect(violations.length).toBeGreaterThan(0);
    });

    it('should filter violations by severity', () => {
      const errors = manager.getViolations({ severity: 'error' });
      expect(errors.every((v) => v.severity === 'error')).toBe(true);
    });

    it('should filter violations by file path', () => {
      const file1Violations = manager.getViolations({
        filePath: 'file1.tsx',
      });
      expect(file1Violations.every((v) => v.filePath === 'file1.tsx')).toBe(
        true
      );
    });

    it('should filter violations by component', () => {
      const violations = manager.getViolations({
        component: 'CanvasToolbar',
      });
      expect(violations.every((v) => v.component === 'CanvasToolbar')).toBe(
        true
      );
    });

    it('should clear violations for specific file', () => {
      manager.clearViolations('file1.tsx');
      const remaining = manager.getViolations({ filePath: 'file1.tsx' });
      expect(remaining).toHaveLength(0);
    });

    it('should clear all violations', () => {
      manager.clearViolations();
      const violations = manager.getViolations();
      expect(violations).toHaveLength(0);
    });
  });

  describe('Naming Convention Management', () => {
    it('should get naming conventions', () => {
      const conventions = manager.getNamingConventions();
      expect(conventions.size).toBeGreaterThan(0);
      expect(conventions.get('canvas')).toBeDefined();
    });

    it('should update naming convention', () => {
      const customConvention: NamingConvention = {
        pattern: /^test-[a-z]+$/,
        description: 'Test pattern',
        example: 'test-example',
      };

      manager.updateNamingConvention('custom', customConvention);

      const conventions = manager.getNamingConventions();
      expect(conventions.get('custom')).toEqual(customConvention);
    });
  });

  describe('ESLint Configuration', () => {
    it('should get ESLint configuration', () => {
      const config = manager.getESLintConfig();
      expect(config.severity).toBe('error');
      expect(config.autoFix).toBe(true);
    });

    it('should update ESLint configuration', () => {
      manager.updateESLintConfig({
        severity: 'warning',
        autoFix: false,
      });

      const config = manager.getESLintConfig();
      expect(config.severity).toBe('warning');
      expect(config.autoFix).toBe(false);
    });

    it('should merge ESLint configuration updates', () => {
      manager.updateESLintConfig({ severity: 'info' });

      const config = manager.getESLintConfig();
      expect(config.severity).toBe('info');
      expect(config.autoFix).toBe(true); // Original value preserved
    });
  });

  describe('Statistics', () => {
    beforeEach(() => {
      manager.registerSelector({
        component: 'Canvas1',
        selector: 'canvas-1',
        category: 'canvas',
      });
      manager.registerSelector({
        component: 'Canvas2',
        selector: 'canvas-2',
        category: 'canvas',
      });
      manager.registerSelector({
        component: 'Node1',
        selector: 'node-1',
        category: 'node',
      });

      manager.checkForMissingSelectors(
        '<CanvasToolbar />',
        'test.tsx'
      );
    });

    it('should calculate total selectors', () => {
      const stats = manager.getStatistics();
      expect(stats.totalSelectors).toBe(3);
    });

    it('should count selectors by category', () => {
      const stats = manager.getStatistics();
      expect(stats.byCategory.get('canvas')).toBe(2);
      expect(stats.byCategory.get('node')).toBe(1);
    });

    it('should count violations', () => {
      const stats = manager.getStatistics();
      expect(stats.violationCount).toBeGreaterThan(0);
    });

    it('should calculate coverage percentage', () => {
      const stats = manager.getStatistics();
      expect(stats.coveragePercentage).toBeGreaterThan(0);
      expect(stats.coveragePercentage).toBeLessThanOrEqual(100);
    });

    it('should handle zero selectors gracefully', () => {
      const emptyManager = new SelectorStandardsManager();
      const stats = emptyManager.getStatistics();
      expect(stats.totalSelectors).toBe(0);
      expect(stats.coveragePercentage).toBe(0);
    });
  });

  describe('Documentation Export', () => {
    beforeEach(() => {
      manager.registerSelector({
        component: 'CanvasToolbar',
        selector: 'canvas-toolbar',
        category: 'canvas',
        description: 'Main toolbar',
      });
      manager.registerSelector({
        component: 'NodePalette',
        selector: 'palette-nodes',
        category: 'palette',
        description: 'Node palette',
      });
    });

    it('should export documentation in Markdown', () => {
      const markdown = manager.exportDocumentation();
      expect(markdown).toContain('# Test Selector Standards');
    });

    it('should include naming conventions', () => {
      const markdown = manager.exportDocumentation();
      expect(markdown).toContain('## Naming Conventions');
      expect(markdown).toContain('### canvas');
    });

    it('should include registered selectors', () => {
      const markdown = manager.exportDocumentation();
      expect(markdown).toContain('## Registered Selectors');
      expect(markdown).toContain('CanvasToolbar');
      expect(markdown).toContain('NodePalette');
    });

    it('should include statistics', () => {
      const markdown = manager.exportDocumentation();
      expect(markdown).toContain('## Statistics');
      expect(markdown).toContain('Total Selectors:');
    });

    it('should format selectors in table', () => {
      const markdown = manager.exportDocumentation();
      expect(markdown).toContain('| Component | Selector | Description |');
      expect(markdown).toContain('`canvas-toolbar`');
    });
  });

  describe('Reset', () => {
    beforeEach(() => {
      manager.registerSelector({
        component: 'Test',
        selector: 'test-selector',
        category: 'custom',
      });
      manager.checkForMissingSelectors(
        '<CanvasToolbar />',
        'test.tsx'
      );
    });

    it('should clear registry', () => {
      manager.reset();
      const selectors = manager.getAllSelectors();
      expect(selectors).toHaveLength(0);
    });

    it('should clear violations', () => {
      manager.reset();
      const violations = manager.getViolations();
      expect(violations).toHaveLength(0);
    });

    it('should preserve configuration', () => {
      const configBefore = manager.getConfig();
      manager.reset();
      const configAfter = manager.getConfig();

      expect(configAfter.strictMode).toBe(configBefore.strictMode);
      expect(configAfter.maxSelectorLength).toBe(
        configBefore.maxSelectorLength
      );
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty JSX code', () => {
      const violations = manager.checkForMissingSelectors('', 'test.tsx');
      expect(violations).toHaveLength(0);
    });

    it('should handle JSX with no components', () => {
      const jsxCode = `
        <div>
          <span>Text</span>
        </div>
      `;

      const violations = manager.checkForMissingSelectors(
        jsxCode,
        'test.tsx'
      );
      expect(violations).toHaveLength(0);
    });

    it('should handle component names with numbers', () => {
      const selector = manager.generateSelector('node', 'Node2Type3');
      expect(selector).toBe('node-node-2-type-3'); // Proper kebab-case with number separation
    });

    it('should handle very long component names', () => {
      const longName = 'VeryLongComponentNameThatExceedsReasonableLength';
      const selector = manager.generateSelector('canvas', longName);
      // Should still generate, validation will catch length
      expect(selector).toContain('canvas-');
    });

    it('should handle pattern wildcards correctly', () => {
      const jsxCode = `
        <NodeEditor />
        <NodePanel />
        <CustomButton />
      `;

      const violations = manager.checkForMissingSelectors(
        jsxCode,
        'test.tsx'
      );

      // Node* components should be detected
      const nodeViolations = violations.filter((v) =>
        v.component.startsWith('Node')
      );
      expect(nodeViolations.length).toBeGreaterThan(0);

      // *Button components should be detected
      const buttonViolations = violations.filter((v) =>
        v.component.endsWith('Button')
      );
      expect(buttonViolations.length).toBeGreaterThan(0);
    });
  });
});
