/**
 * Tests for Export Sanitization
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createSanitizer,
  validatePolicy,
  type SanitizationPolicy,
  type ExportNode,
  type ExportEdge,

  ExportSanitizer} from '../sanitizer';

describe.skip('ExportSanitizer', () => {
  describe('Node Sanitization', () => {
    let sanitizer: ExportSanitizer;

    beforeEach(() => {
      sanitizer = createSanitizer();
    });

    it('should allow allowlisted node properties', () => {
      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: { label: 'Test' },
        style: { color: 'red' },
      };

      const result = sanitizer.sanitizeNode(node);

      expect(result.sanitized).toBe(false);
      expect(result.removed).toHaveLength(0);
      expect(result.data.id).toBe('node1');
      expect(result.data.type).toBe('custom');
    });

    it('should remove disallowed node properties', () => {
      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: { label: 'Test' },
        dangerousField: 'value',
        _private: 'data',
      };

      const result = sanitizer.sanitizeNode(node);

      expect(result.sanitized).toBe(true);
      expect(result.removed).toContain('dangerousField');
      expect(result.removed).toContain('_private');
      expect(result.warnings.length).toBeGreaterThan(0);
    });

    it('should sanitize HTML in node data fields', () => {
      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: {
          label: '<script>alert("xss")</script><p>Safe content</p>',
        },
      };

      const result = sanitizer.sanitizeNode(node);

      const dataLabel = (result.data.data as Record<string, unknown>)?.label as string;
      expect(dataLabel).not.toContain('<script>');
      expect(dataLabel).toContain('<p>');
    });

    it('should handle nodes without data field', () => {
      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
      };

      const result = sanitizer.sanitizeNode(node);

      expect(result.sanitized).toBe(false);
      expect(result.data.id).toBe('node1');
    });

    it('should handle nested metadata', () => {
      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: { label: 'Test' },
        metadata: {
          createdAt: Date.now(),
          tags: ['tag1', 'tag2'],
        },
      };

      const result = sanitizer.sanitizeNode(node);

      expect(result.data.metadata).toBeDefined();
      expect((result.data.metadata as Record<string, unknown>).tags).toEqual(['tag1', 'tag2']);
    });
  });

  describe('Edge Sanitization', () => {
    let sanitizer: ExportSanitizer;

    beforeEach(() => {
      sanitizer = createSanitizer();
    });

    it('should allow allowlisted edge properties', () => {
      const edge = {
        id: 'edge1',
        source: 'node1',
        target: 'node2',
        type: 'custom',
        data: { label: 'Connection' },
      };

      const result = sanitizer.sanitizeEdge(edge);

      expect(result.sanitized).toBe(false);
      expect(result.removed).toHaveLength(0);
      expect(result.data.id).toBe('edge1');
      expect(result.data.source).toBe('node1');
      expect(result.data.target).toBe('node2');
    });

    it('should remove disallowed edge properties', () => {
      const edge = {
        id: 'edge1',
        source: 'node1',
        target: 'node2',
        dangerousField: 'value',
        __internal: {},
      };

      const result = sanitizer.sanitizeEdge(edge);

      expect(result.sanitized).toBe(true);
      expect(result.removed).toContain('dangerousField');
      expect(result.removed).toContain('__internal');
    });

    it('should sanitize HTML in edge data fields', () => {
      const edge = {
        id: 'edge1',
        source: 'node1',
        target: 'node2',
        data: {
          label: '<a href="javascript:void(0)">Link</a>',
        },
      };

      const result = sanitizer.sanitizeEdge(edge);

      const dataLabel = (result.data.data as Record<string, unknown>)?.label as string;
      expect(dataLabel).not.toContain('javascript:');
    });
  });

  describe('Document Sanitization', () => {
    let sanitizer: ExportSanitizer;

    beforeEach(() => {
      sanitizer = createSanitizer();
    });

    it('should sanitize all nodes in document', () => {
      const document = {
        nodes: [
          {
            id: 'node1',
            type: 'custom',
            position: { x: 100, y: 200 },
            data: { label: 'Test' },
            dangerousField: 'value',
          },
          {
            id: 'node2',
            type: 'custom',
            position: { x: 300, y: 400 },
            data: { label: 'Test2' },
          },
        ],
        edges: [],
      };

      const result = sanitizer.sanitizeDocument(document);

      expect(result.data.nodes).toHaveLength(2);
      expect(result.sanitized).toBe(true);
      expect(result.removed.some((r) => r.includes('dangerousField'))).toBe(true);
    });

    it('should sanitize all edges in document', () => {
      const document = {
        nodes: [],
        edges: [
          {
            id: 'edge1',
            source: 'node1',
            target: 'node2',
            dangerousField: 'value',
          },
          {
            id: 'edge2',
            source: 'node2',
            target: 'node3',
          },
        ],
      };

      const result = sanitizer.sanitizeDocument(document);

      expect(result.data.edges).toHaveLength(2);
      expect(result.sanitized).toBe(true);
    });

    it('should handle empty document', () => {
      const document = {
        nodes: [],
        edges: [],
      };

      const result = sanitizer.sanitizeDocument(document);

      expect(result.data.nodes).toHaveLength(0);
      expect(result.data.edges).toHaveLength(0);
      expect(result.sanitized).toBe(false);
    });

    it('should include document metadata', () => {
      const document = {
        nodes: [],
        edges: [],
        metadata: {
          version: '1.0',
          createdAt: Date.now(),
        },
      };

      const result = sanitizer.sanitizeDocument(document);

      expect(result.data.metadata).toBeDefined();
      expect((result.data.metadata as Record<string, unknown>).version).toBe('1.0');
    });

    it('should handle invalid node entries', () => {
      const document = {
        nodes: [
          { id: 'node1', type: 'custom', position: { x: 0, y: 0 }, data: {} },
          null,
          'invalid',
          { id: 'node2', type: 'custom', position: { x: 0, y: 0 }, data: {} },
        ],
        edges: [],
      };

      const result = sanitizer.sanitizeDocument(document);

      expect(result.data.nodes).toHaveLength(2);
    });
  });

  describe('HTML Sanitization', () => {
    let sanitizer: ExportSanitizer;

    beforeEach(() => {
      sanitizer = createSanitizer();
    });

    it('should remove script tags', () => {
      const html = '<p>Safe content</p><script>alert("xss")</script><p>More safe content</p>';

      const result = sanitizer.sanitizeHtml(html);

      expect(result.data).not.toContain('<script>');
      expect(result.data).toContain('<p>');
      expect(result.sanitized).toBe(true);
    });

    it('should remove event handlers', () => {
      const html = '<div onclick="alert(1)">Click me</div>';

      const result = sanitizer.sanitizeHtml(html);

      expect(result.data).not.toContain('onclick');
      expect(result.sanitized).toBe(true);
    });

    it('should remove javascript protocol', () => {
      const html = '<a href="javascript:void(0)">Link</a>';

      const result = sanitizer.sanitizeHtml(html);

      expect(result.data).not.toContain('javascript:');
      expect(result.sanitized).toBe(true);
    });

    it('should allow safe HTML', () => {
      const html = '<p><strong>Bold</strong> and <em>italic</em> text</p>';

      const result = sanitizer.sanitizeHtml(html);

      expect(result.data).toContain('<strong>');
      expect(result.data).toContain('<em>');
    });

    it('should remove disallowed tags', () => {
      const html = '<iframe src="evil.com"></iframe><p>Safe</p>';

      const result = sanitizer.sanitizeHtml(html);

      expect(result.data).not.toContain('<iframe>');
      expect(result.data).toContain('<p>');
      expect(result.sanitized).toBe(true);
    });

    it('should handle plain text', () => {
      const text = 'Plain text without HTML';

      const result = sanitizer.sanitizeHtml(text);

      expect(result.data).toBe(text);
      expect(result.sanitized).toBe(false);
    });

    it('should be disabled if policy disables DOMPurify', () => {
      const sanitizer = createSanitizer({ enableDomPurify: false });
      const html = '<script>alert("xss")</script>';

      const result = sanitizer.sanitizeHtml(html);

      expect(result.data).toBe(html);
      expect(result.sanitized).toBe(false);
    });
  });

  describe('Audit Logging', () => {
    let sanitizer: ExportSanitizer;

    beforeEach(() => {
      sanitizer = createSanitizer();
    });

    it('should log allowed properties', () => {
      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: { label: 'Test' },
      };

      sanitizer.sanitizeNode(node);

      const log = sanitizer.getAuditLog();
      const allowedEntries = log.filter((entry) => entry.action === 'allow');
      expect(allowedEntries.length).toBeGreaterThan(0);
    });

    it('should log removed properties', () => {
      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: { label: 'Test' },
        dangerousField: 'value',
      };

      sanitizer.sanitizeNode(node);

      const log = sanitizer.getAuditLog();
      const removedEntry = log.find((entry) => entry.action === 'remove' && entry.field === 'dangerousField');
      expect(removedEntry).toBeDefined();
      expect(removedEntry?.reason).toContain('not in allowlist');
    });

    it('should log HTML sanitization', () => {
      const html = '<script>alert("xss")</script><p>Safe</p>';

      sanitizer.sanitizeHtml(html);

      const log = sanitizer.getAuditLog();
      const sanitizeEntry = log.find((entry) => entry.action === 'sanitize');
      expect(sanitizeEntry).toBeDefined();
      expect(sanitizeEntry?.type).toBe('html');
    });

    it('should clear audit log', () => {
      sanitizer.sanitizeHtml('<script>alert("xss")</script>');
      expect(sanitizer.getAuditLog().length).toBeGreaterThan(0);

      sanitizer.clearAuditLog();
      expect(sanitizer.getAuditLog()).toHaveLength(0);
    });

    it('should be disabled if policy disables audit log', () => {
      const sanitizer = createSanitizer({ enableAuditLog: false });
      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: { label: 'Test' },
        dangerousField: 'value',
      };

      sanitizer.sanitizeNode(node);

      expect(sanitizer.getAuditLog()).toHaveLength(0);
    });

    it('should truncate long original values', () => {
      const longHtml = `<script>${  'x'.repeat(200)  }</script>`;

      sanitizer.sanitizeHtml(longHtml);

      const log = sanitizer.getAuditLog();
      const entry = log.find((e) => e.originalValue);
      expect(entry?.originalValue?.length).toBeLessThanOrEqual(100);
    });
  });

  describe('Policy Management', () => {
    it('should use custom policy', () => {
      const sanitizer = createSanitizer({
        allowedNodeProps: new Set(['id', 'type', 'position', 'data']),
      });

      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: { label: 'Test' },
        style: { color: 'red' },
      };

      const result = sanitizer.sanitizeNode(node);

      expect(result.removed).toContain('style');
    });

    it('should get current policy', () => {
      const sanitizer = createSanitizer();
      const policy = sanitizer.getPolicy();

      expect(policy.allowedNodeProps).toBeInstanceOf(Set);
      expect(policy.allowedEdgeProps).toBeInstanceOf(Set);
      expect(Array.isArray(policy.allowedHtmlTags)).toBe(true);
    });

    it('should update policy', () => {
      const sanitizer = createSanitizer();

      sanitizer.updatePolicy({
        allowedNodeProps: new Set(['id', 'type', 'position']),
      });

      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: { label: 'Test' },
      };

      const result = sanitizer.sanitizeNode(node);

      expect(result.removed).toContain('data');
    });

    it('should merge policy updates', () => {
      const sanitizer = createSanitizer({
        allowedHtmlTags: ['p', 'br'],
      });

      const policy = sanitizer.getPolicy();
      expect(policy.allowedHtmlTags).toEqual(['p', 'br']);
      expect(policy.enableDomPurify).toBe(true); // Default value preserved
    });
  });

  describe('Policy Validation', () => {
    it('should validate valid policy', () => {
      const policy: SanitizationPolicy = {
        allowedNodeProps: new Set(['id', 'type']),
        allowedEdgeProps: new Set(['id', 'source', 'target']),
        allowedHtmlTags: ['p', 'br'],
        allowedHtmlAttrs: ['href'],
        enableDomPurify: true,
        enableAuditLog: true,
      };

      expect(validatePolicy(policy)).toBe(true);
    });

    it('should reject invalid policy', () => {
      expect(validatePolicy(null)).toBe(false);
      expect(validatePolicy({})).toBe(false);
      expect(
        validatePolicy({
          allowedNodeProps: ['id'], // Should be Set
          allowedEdgeProps: new Set(),
          allowedHtmlTags: [],
          allowedHtmlAttrs: [],
          enableDomPurify: true,
          enableAuditLog: true,
        })
      ).toBe(false);
    });
  });

  describe('Edge Cases', () => {
    let sanitizer: ExportSanitizer;

    beforeEach(() => {
      sanitizer = createSanitizer();
    });

    it('should handle empty objects', () => {
      const result = sanitizer.sanitizeNode({});
      expect(result.removed).toHaveLength(0);
    });

    it('should handle null values', () => {
      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: null,
      };

      const result = sanitizer.sanitizeNode(node);
      expect(result.data.data).toBeNull();
    });

    it('should handle nested HTML in data', () => {
      const node = {
        id: 'node1',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: {
          description: '<p>Nested <script>alert(1)</script> content</p>',
          title: 'Plain title',
        },
      };

      const result = sanitizer.sanitizeNode(node);
      const data = result.data.data as Record<string, unknown>;
      expect((data.description as string)).not.toContain('<script>');
      expect(data.title).toBe('Plain title');
    });

    it('should handle very large documents', () => {
      const nodes = Array.from({ length: 100 }, (_, i) => ({
        id: `node${i}`,
        type: 'custom',
        position: { x: i * 100, y: i * 100 },
        data: { label: `Node ${i}` },
      }));

      const document = { nodes, edges: [] };
      const result = sanitizer.sanitizeDocument(document);

      expect(result.data.nodes).toHaveLength(100);
    });
  });
});
