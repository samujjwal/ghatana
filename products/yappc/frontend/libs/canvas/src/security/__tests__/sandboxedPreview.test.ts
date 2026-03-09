/**
 * Tests for SandboxedPreviewManager
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  createSandboxedPreviewManager,
  validatePreviewConfig,
  type PreviewConfig,
  type CSPDirectives,

  SandboxedPreviewManager} from '../sandboxedPreview';

describe.skip('SandboxedPreviewManager', () => {
  let manager: SandboxedPreviewManager;
  let container: HTMLElement;

  beforeEach(() => {
    manager = createSandboxedPreviewManager();
    container = document.createElement('div');
    document.body.appendChild(container);
  });

  describe('Preview Creation', () => {
    it('should create preview with default config', () => {
      const result = manager.createPreview(container, '<p>Hello World</p>');

      expect(result.success).toBe(true);
      expect(result.previewId).toBeDefined();
      expect(result.iframe).toBeDefined();
      expect(result.contentSize).toBeGreaterThan(0);
      expect(result.renderTime).toBeGreaterThanOrEqual(0);
    });

    it('should create iframe with sandbox attribute', () => {
      const result = manager.createPreview(container, '<p>Test</p>');

      expect(result.success).toBe(true);
      expect(result.iframe).toBeDefined();
      expect(result.iframe!.getAttribute('sandbox')).toBe('');
    });

    it('should create iframe with custom sandbox', () => {
      const result = manager.createPreview(
        container,
        '<p>Test</p>',
        { sandbox: ['allow-scripts', 'allow-forms'] }
      );

      expect(result.success).toBe(true);
      const sandbox = result.iframe!.getAttribute('sandbox');
      expect(sandbox).toContain('allow-scripts');
      expect(sandbox).toContain('allow-forms');
    });

    it('should reject oversized content', () => {
      const largeContent = 'x'.repeat(2 * 1024 * 1024); // 2MB
      const result = manager.createPreview(
        container,
        largeContent,
        { maxContentSize: 1024 * 1024 }
      );

      expect(result.success).toBe(false);
      expect(result.error).toContain('exceeds maximum');
    });

    it('should set iframe title for accessibility', () => {
      const result = manager.createPreview(
        container,
        '<p>Test</p>',
        { iframeTitle: 'Custom Preview' }
      );

      expect(result.success).toBe(true);
      expect(result.iframe!.title).toBe('Custom Preview');
    });

    it('should track active previews', () => {
      expect(manager.getActivePreviewsCount()).toBe(0);

      manager.createPreview(container, '<p>Test 1</p>');
      expect(manager.getActivePreviewsCount()).toBe(1);

      manager.createPreview(container, '<p>Test 2</p>');
      expect(manager.getActivePreviewsCount()).toBe(2);
    });
  });

  describe('Preview Destruction', () => {
    it('should destroy preview', () => {
      const result = manager.createPreview(container, '<p>Test</p>');
      expect(result.success).toBe(true);

      const destroyed = manager.destroyPreview(result.previewId);
      expect(destroyed).toBe(true);
      expect(manager.getActivePreviewsCount()).toBe(0);
    });

    it('should return false for non-existent preview', () => {
      const destroyed = manager.destroyPreview('non-existent');
      expect(destroyed).toBe(false);
    });

    it('should remove iframe from DOM', () => {
      const result = manager.createPreview(container, '<p>Test</p>');
      expect(container.querySelector('iframe')).toBeDefined();

      manager.destroyPreview(result.previewId);
      expect(container.querySelector('iframe')).toBeNull();
    });

    it('should destroy all previews', () => {
      manager.createPreview(container, '<p>Test 1</p>');
      manager.createPreview(container, '<p>Test 2</p>');
      manager.createPreview(container, '<p>Test 3</p>');

      expect(manager.getActivePreviewsCount()).toBe(3);

      manager.destroyAll();
      expect(manager.getActivePreviewsCount()).toBe(0);
    });
  });

  describe('CSP Configuration', () => {
    it('should build CSP header from directives', () => {
      const directives: CSPDirectives = {
        defaultSrc: ["'none'"],
        scriptSrc: ["'self'"],
        styleSrc: ["'unsafe-inline'"],
        imgSrc: ['data:', 'https:'],
      };

      const header = manager.buildCSPHeader(directives);

      expect(header).toContain("default-src 'none'");
      expect(header).toContain("script-src 'self'");
      expect(header).toContain("style-src 'unsafe-inline'");
      expect(header).toContain('img-src data: https:');
    });

    it('should handle empty directives', () => {
      const header = manager.buildCSPHeader({});
      expect(header).toBe('');
    });

    it('should create preview with custom CSP', () => {
      const csp: CSPDirectives = {
        defaultSrc: ["'self'"],
        imgSrc: ['*'],
      };

      const result = manager.createPreview(
        container,
        '<p>Test</p>',
        { csp }
      );

      expect(result.success).toBe(true);
      const iframe = result.iframe!;
      const cspAttr = iframe.getAttribute('csp');
      expect(cspAttr).toContain("default-src 'self'");
      expect(cspAttr).toContain('img-src *');
    });
  });

  describe('Violation Logging', () => {
    it('should initialize with no violations', () => {
      const violations = manager.getViolations();
      expect(violations).toEqual([]);
    });

    it('should filter violations by preview ID', () => {
      const result1 = manager.createPreview(container, '<p>Test 1</p>');
      const result2 = manager.createPreview(container, '<p>Test 2</p>');

      const violations1 = manager.getViolations(result1.previewId);
      const violations2 = manager.getViolations(result2.previewId);

      expect(violations1).toEqual([]);
      expect(violations2).toEqual([]);
    });

    it('should clear violations for specific preview', () => {
      const result = manager.createPreview(container, '<p>Test</p>');
      
      manager.clearViolations(result.previewId);
      
      const violations = manager.getViolations(result.previewId);
      expect(violations).toEqual([]);
    });

    it('should clear all violations', () => {
      manager.createPreview(container, '<p>Test 1</p>');
      manager.createPreview(container, '<p>Test 2</p>');

      manager.clearViolations();

      const violations = manager.getViolations();
      expect(violations).toEqual([]);
    });
  });

  describe('Security Events', () => {
    it('should log preview creation event', () => {
      const result = manager.createPreview(container, '<p>Test</p>');

      const events = manager.getSecurityEvents({ previewId: result.previewId });
      expect(events.length).toBeGreaterThanOrEqual(1);
      
      const createEvent = events.find(e => e.type === 'preview_created');
      expect(createEvent).toBeDefined();
      expect(createEvent!.severity).toBe('low');
    });

    it('should log preview destruction event', () => {
      const result = manager.createPreview(container, '<p>Test</p>');
      manager.destroyPreview(result.previewId);

      const events = manager.getSecurityEvents({ previewId: result.previewId });
      const destroyEvent = events.find(e => e.type === 'preview_destroyed');
      
      expect(destroyEvent).toBeDefined();
      expect(destroyEvent!.details.lifetime).toBeGreaterThanOrEqual(0);
    });

    it('should log size exceeded event', () => {
      const largeContent = 'x'.repeat(2 * 1024 * 1024);
      const result = manager.createPreview(
        container,
        largeContent,
        { maxContentSize: 1024 * 1024 }
      );

      const events = manager.getSecurityEvents();
      const sizeEvent = events.find(e => e.type === 'size_exceeded');
      
      expect(sizeEvent).toBeDefined();
      expect(sizeEvent!.severity).toBe('medium');
    });

    it('should filter events by type', () => {
      manager.createPreview(container, '<p>Test 1</p>');
      const result2 = manager.createPreview(container, '<p>Test 2</p>');
      manager.destroyPreview(result2.previewId);

      const createEvents = manager.getSecurityEvents({ type: 'preview_created' });
      const destroyEvents = manager.getSecurityEvents({ type: 'preview_destroyed' });

      expect(createEvents.length).toBeGreaterThanOrEqual(2);
      expect(destroyEvents.length).toBeGreaterThanOrEqual(1);
      expect(createEvents.every(e => e.type === 'preview_created')).toBe(true);
      expect(destroyEvents.every(e => e.type === 'preview_destroyed')).toBe(true);
    });

    it('should filter events by severity', () => {
      manager.createPreview(container, '<p>Test</p>');
      
      const lowEvents = manager.getSecurityEvents({ severity: 'low' });
      expect(lowEvents.every(e => e.severity === 'low')).toBe(true);
    });

    it('should filter events by time', () => {
      const before = Date.now();
      manager.createPreview(container, '<p>Test</p>');

      const recentEvents = manager.getSecurityEvents({ since: before });
      expect(recentEvents.length).toBeGreaterThanOrEqual(1);
      expect(recentEvents.every(e => e.timestamp >= before)).toBe(true);
    });

    it('should clear security events', () => {
      manager.createPreview(container, '<p>Test</p>');
      expect(manager.getSecurityEvents().length).toBeGreaterThan(0);

      manager.clearSecurityEvents();
      expect(manager.getSecurityEvents()).toEqual([]);
    });

    it('should notify event subscribers', () => {
      const events: unknown[] = [];
      const unsubscribe = manager.subscribe(event => {
        events.push(event);
      });

      manager.createPreview(container, '<p>Test</p>');

      expect(events.length).toBeGreaterThanOrEqual(1);
      expect(events[0].type).toBe('preview_created');

      unsubscribe();
    });

    it('should unsubscribe from events', () => {
      const events: unknown[] = [];
      const unsubscribe = manager.subscribe(event => {
        events.push(event);
      });

      manager.createPreview(container, '<p>Test 1</p>');
      const count1 = events.length;

      unsubscribe();

      manager.createPreview(container, '<p>Test 2</p>');
      const count2 = events.length;

      expect(count2).toBe(count1);
    });

    it('should support multiple subscribers', () => {
      const events1: unknown[] = [];
      const events2: unknown[] = [];

      manager.subscribe(event => events1.push(event));
      manager.subscribe(event => events2.push(event));

      manager.createPreview(container, '<p>Test</p>');

      expect(events1.length).toBe(events2.length);
      expect(events1.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('Preview State', () => {
    it('should retrieve preview state', () => {
      const result = manager.createPreview(container, '<p>Test</p>');
      const state = manager.getPreview(result.previewId);

      expect(state).toBeDefined();
      expect(state!.id).toBe(result.previewId);
      expect(state!.iframe).toBe(result.iframe);
      expect(state!.violations).toBe(0);
    });

    it('should return undefined for non-existent preview', () => {
      const state = manager.getPreview('non-existent');
      expect(state).toBeUndefined();
    });
  });

  describe('Configuration Validation', () => {
    it('should validate valid config', () => {
      const config: PreviewConfig = {
        maxContentSize: 1024 * 1024,
        timeout: 30000,
        useProxy: false,
      };

      const result = validatePreviewConfig(config);
      expect(result.valid).toBe(true);
      expect(result.errors).toEqual([]);
    });

    it('should reject negative maxContentSize', () => {
      const config: PreviewConfig = {
        maxContentSize: -100,
      };

      const result = validatePreviewConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('maxContentSize must be positive');
    });

    it('should reject negative timeout', () => {
      const config: PreviewConfig = {
        timeout: -1000,
      };

      const result = validatePreviewConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('timeout must be positive');
    });

    it('should require proxyUrl when useProxy is true', () => {
      const config: PreviewConfig = {
        useProxy: true,
      };

      const result = validatePreviewConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('proxyUrl required when useProxy is true');
    });

    it('should accept valid proxy config', () => {
      const config: PreviewConfig = {
        useProxy: true,
        proxyUrl: '/api/proxy',
      };

      const result = validatePreviewConfig(config);
      expect(result.valid).toBe(true);
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty content', () => {
      const result = manager.createPreview(container, '');
      expect(result.success).toBe(true);
    });

    it('should handle special characters in content', () => {
      const content = '<p>Test & "quotes" <script>alert(1)</script></p>';
      const result = manager.createPreview(container, content);
      expect(result.success).toBe(true);
    });

    it('should handle multiple previews in same container', () => {
      const result1 = manager.createPreview(container, '<p>Test 1</p>');
      const result2 = manager.createPreview(container, '<p>Test 2</p>');

      expect(result1.success).toBe(true);
      expect(result2.success).toBe(true);
      expect(container.querySelectorAll('iframe').length).toBe(2);
    });

    it('should handle rapid creation and destruction', () => {
      for (let i = 0; i < 10; i++) {
        const result = manager.createPreview(container, `<p>Test ${i}</p>`);
        expect(result.success).toBe(true);
        manager.destroyPreview(result.previewId);
      }

      expect(manager.getActivePreviewsCount()).toBe(0);
    });

    it('should handle zero maxContentSize gracefully', () => {
      const result = manager.createPreview(
        container,
        '<p>Test</p>',
        { maxContentSize: 0 }
      );

      expect(result.success).toBe(false);
    });
  });
});
