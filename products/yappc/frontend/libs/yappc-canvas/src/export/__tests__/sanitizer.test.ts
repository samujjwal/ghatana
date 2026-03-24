import { createTestDOMPurify } from '@ghatana/yappc-test-helpers';

import { ContentSanitizer, sanitizeUserContent, sanitizeExportContent, auditExportSecurity } from '../sanitizer';

describe('ContentSanitizer (shared mock)', () => {
  test('sanitizeHTML removes script tags and dangerous attributes', () => {
    const cs = new ContentSanitizer({}, createTestDOMPurify());
    const input = `<div onclick="alert('x')">safe</div><script>alert('x')</script>`;
    const result = cs.sanitizeHTML(input);
    expect(result.sanitized).toContain('<div');
    expect(result.sanitized).not.toContain('onclick');
    expect(result.sanitized).not.toContain('<script>');
    expect(result.safe).toBe(true);
  });

  test('sanitizeCSS removes dangerous CSS properties', () => {
    const cs = new ContentSanitizer({}, createTestDOMPurify());
    const input = `body { color: red; expression: something; }`;
    const result = cs.sanitizeCSS(input);
    expect(result.sanitized).not.toContain('expression');
    expect(result.removed.some(r => r.element === 'expression')).toBe(true);
  });

  test('sanitizeSVG keeps allowed svg tags', () => {
    const cs = new ContentSanitizer({}, createTestDOMPurify());
    const svg = `<svg><g><path d="M0 0" /></g><script>alert(1)</script></svg>`;
    const result = cs.sanitizeSVG(svg);
    expect(result.sanitized).toContain('<svg');
    expect(result.sanitized).not.toContain('<script>');
  });

  test('auditExportSecurity detects javascript: URLs', () => {
    const audit = auditExportSecurity('<a href="javascript:alert(1)">x</a>');
    expect(audit.findings.length).toBeGreaterThan(0);
    expect(audit.riskLevel).toBe('high');
  });
});

describe('shared test DOMPurify is available', () => {
  test('createTestDOMPurify exists and is usable', () => {
    const mock = createTestDOMPurify();
    const sanitized = mock.sanitize('<div onclick="x">a</div>');
    expect(sanitized).toContain('<div');
    expect(sanitized).not.toContain('onclick');
  });
});
