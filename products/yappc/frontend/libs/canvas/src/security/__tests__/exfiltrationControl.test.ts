/**
 * Tests for ExfiltrationControl
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createExfiltrationControl,
  type ExfiltrationControlConfig,

  ExfiltrationControl} from '../exfiltrationControl';

describe.skip('ExfiltrationControl', () => {
  let control: ExfiltrationControl;

  beforeEach(() => {
    control = createExfiltrationControl();
  });

  describe('URL Validation', () => {
    it('should allow valid HTTPS URLs', () => {
      const result = control.validateURL('https://example.com/path');
      
      expect(result.allowed).toBe(true);
      expect(result.sanitizedUrl).toBeDefined();
    });

    it('should allow HTTP URLs by default', () => {
      const result = control.validateURL('http://example.com');
      
      expect(result.allowed).toBe(true);
    });

    it('should block disallowed protocols', () => {
      const customControl = createExfiltrationControl({
        allowedProtocols: ['https:'],
      });

      const result = customControl.validateURL('ftp://example.com');
      
      expect(result.allowed).toBe(false);
      expect(result.category).toBe('protocol');
      expect(result.reason).toContain('not allowed');
    });

    it('should sanitize URLs with credentials', () => {
      const result = control.validateURL('https://user:pass@example.com/path');
      
      expect(result.allowed).toBe(true);
      expect(result.sanitizedUrl).not.toContain('user:pass');
    });

    it('should enforce domain allowlist', () => {
      const customControl = createExfiltrationControl({
        domainAllowlist: ['trusted.com', 'safe.com'],
      });

      const result1 = customControl.validateURL('https://trusted.com');
      expect(result1.allowed).toBe(true);

      const result2 = customControl.validateURL('https://untrusted.com');
      expect(result2.allowed).toBe(false);
      expect(result2.category).toBe('domain');
    });

    it('should support wildcard domains in allowlist', () => {
      const customControl = createExfiltrationControl({
        domainAllowlist: ['*.example.com'],
      });

      const result1 = customControl.validateURL('https://sub.example.com');
      expect(result1.allowed).toBe(true);

      const result2 = customControl.validateURL('https://example.com');
      expect(result2.allowed).toBe(true);

      const result3 = customControl.validateURL('https://other.com');
      expect(result3.allowed).toBe(false);
    });

    it('should enforce domain blocklist', () => {
      const customControl = createExfiltrationControl({
        domainBlocklist: ['evil.com', 'malicious.com'],
      });

      const result1 = customControl.validateURL('https://evil.com');
      expect(result1.allowed).toBe(false);
      expect(result1.category).toBe('domain');

      const result2 = customControl.validateURL('https://safe.com');
      expect(result2.allowed).toBe(true);
    });

    it('should support wildcard domains in blocklist', () => {
      const customControl = createExfiltrationControl({
        domainBlocklist: ['*.spam.com'],
      });

      const result = customControl.validateURL('https://sub.spam.com');
      expect(result.allowed).toBe(false);
    });

    it('should block URLs matching patterns', () => {
      const customControl = createExfiltrationControl({
        blockedPatterns: [/\/admin/, /\.exe$/],
      });

      const result1 = customControl.validateURL('https://example.com/admin');
      expect(result1.allowed).toBe(false);
      expect(result1.category).toBe('pattern');

      const result2 = customControl.validateURL('https://example.com/file.exe');
      expect(result2.allowed).toBe(false);
    });

    it('should reject invalid URL format', () => {
      const result = control.validateURL('not-a-url');
      
      expect(result.allowed).toBe(false);
      expect(result.category).toBe('protocol');
    });
  });

  describe('Payload Size Limits', () => {
    it('should allow payloads within limits', () => {
      const payload = { data: 'x'.repeat(1000) };
      const result = control.checkPayloadSize(payload, 'collaboration');
      
      expect(result.allowed).toBe(true);
      expect(result.size).toBeGreaterThan(0);
      expect(result.size).toBeLessThan(result.limit);
    });

    it('should block oversized payloads', () => {
      const payload = { data: 'x'.repeat(200 * 1024) }; // >100KB
      const result = control.checkPayloadSize(payload, 'collaboration');
      
      expect(result.allowed).toBe(false);
      expect(result.reason).toContain('exceeds limit');
    });

    it('should enforce different limits per operation type', () => {
      const largePayload = { data: 'x'.repeat(80 * 1024) }; // 80KB+

      // Should pass for collaboration (100KB limit)
      const collabResult = control.checkPayloadSize(largePayload, 'collaboration');
      expect(collabResult.allowed).toBe(true);

      // Should pass for embed (1MB limit)
      const embedResult = control.checkPayloadSize(largePayload, 'embed');
      expect(embedResult.allowed).toBe(true);

      // Create payload that exceeds collaboration limit
      const hugePayload = { data: 'x'.repeat(150 * 1024) };
      const collabResult2 = control.checkPayloadSize(hugePayload, 'collaboration');
      expect(collabResult2.allowed).toBe(false);
    });

    it('should accept string payloads', () => {
      const payload = 'test string';
      const result = control.checkPayloadSize(payload, 'collaboration');
      
      expect(result.allowed).toBe(true);
      expect(result.size).toBeGreaterThan(0);
    });

    it('should support custom size limits', () => {
      const customControl = createExfiltrationControl({
        sizeLimits: { collaboration: 1024 }, // 1KB
      });

      const payload = { data: 'x'.repeat(2000) };
      const result = customControl.checkPayloadSize(payload, 'collaboration');
      
      expect(result.allowed).toBe(false);
    });
  });

  describe('Script Detection', () => {
    it('should detect script tags', () => {
      const content = '<p>Hello</p><script>alert(1)</script>';
      const result = control.detectScripts(content);
      
      expect(result.safe).toBe(true); // Sanitized in non-strict mode
      expect(result.threats.length).toBeGreaterThan(0);
      expect(result.sanitized).not.toContain('<script>');
    });

    it('should detect event handlers', () => {
      const content = '<div onclick="alert(1)">Click</div>';
      const result = control.detectScripts(content);
      
      expect(result.threats.length).toBeGreaterThan(0);
      expect(result.sanitized).not.toContain('onclick');
    });

    it('should detect javascript: protocol', () => {
      const content = '<a href="javascript:alert(1)">Link</a>';
      const result = control.detectScripts(content);
      
      expect(result.threats.length).toBeGreaterThan(0);
    });

    it('should detect data URI with HTML', () => {
      const content = '<img src="data:text/html,<script>alert(1)</script>">';
      const result = control.detectScripts(content);
      
      expect(result.threats.length).toBeGreaterThan(0);
    });

    it('should detect iframe tags', () => {
      const content = '<iframe src="evil.com"></iframe>';
      const result = control.detectScripts(content);
      
      expect(result.threats.length).toBeGreaterThan(0);
    });

    it('should detect eval usage', () => {
      const content = 'eval("malicious code")';
      const result = control.detectScripts(content);
      
      expect(result.threats.length).toBeGreaterThan(0);
      expect(result.severity).toBe('critical');
    });

    it('should calculate threat severity', () => {
      const lowThreat = '<div onclick="test()">Test</div>';
      const lowResult = control.detectScripts(lowThreat);
      expect(lowResult.severity).toBe('low');

      const highThreat = '<script>alert(1)</script>'.repeat(6);
      const highResult = control.detectScripts(highThreat);
      expect(highResult.severity).toBe('high');
    });

    it('should block in strict mode', () => {
      const strictControl = createExfiltrationControl({ strictMode: true });
      
      const content = '<script>alert(1)</script>';
      const result = strictControl.detectScripts(content);
      
      expect(result.safe).toBe(false);
      expect(result.sanitized).toBeUndefined();
    });

    it('should allow safe content', () => {
      const content = '<p>This is <strong>safe</strong> content</p>';
      const result = control.detectScripts(content);
      
      expect(result.safe).toBe(true);
      expect(result.threats).toEqual([]);
    });

    it('should be disabled when detectScripts is false', () => {
      const customControl = createExfiltrationControl({ detectScripts: false });
      
      const content = '<script>alert(1)</script>';
      const result = customControl.detectScripts(content);
      
      expect(result.safe).toBe(true);
      expect(result.threats).toEqual([]);
    });
  });

  describe('Rate Limiting', () => {
    it('should allow requests within rate limit', () => {
      const userId = 'user-1';
      
      for (let i = 0; i < 10; i++) {
        const result = control.validateURL('https://example.com', userId);
        expect(result.allowed).toBe(true);
      }
    });

    it('should block requests exceeding rate limit', () => {
      const customControl = createExfiltrationControl({ rateLimitPerMinute: 5 });
      const userId = 'user-1';
      
      // First 5 should succeed
      for (let i = 0; i < 5; i++) {
        const result = customControl.validateURL('https://example.com', userId);
        expect(result.allowed).toBe(true);
      }

      // 6th should fail
      const result = customControl.validateURL('https://example.com', userId);
      expect(result.allowed).toBe(false);
      expect(result.category).toBe('rate_limit');
    });

    it('should track rate limits per user', () => {
      const customControl = createExfiltrationControl({ rateLimitPerMinute: 5 });
      
      // User 1 exhausts limit
      for (let i = 0; i < 5; i++) {
        customControl.validateURL('https://example.com', 'user-1');
      }

      // User 2 should still be allowed
      const result = customControl.validateURL('https://example.com', 'user-2');
      expect(result.allowed).toBe(true);
    });
  });

  describe('Complete Operation Validation', () => {
    it('should validate all aspects of an operation', () => {
      const result = control.validateOperation({
        url: 'https://example.com',
        payload: { data: 'test' },
        content: '<p>Safe content</p>',
        operationType: 'export',
      });
      
      expect(result.allowed).toBe(true);
      expect(result.violations).toEqual([]);
    });

    it('should collect all violations', () => {
      const customControl = createExfiltrationControl({
        domainBlocklist: ['evil.com'],
        sizeLimits: { export: 100 },
        strictMode: true,
      });

      const result = customControl.validateOperation({
        url: 'https://evil.com',
        payload: { data: 'x'.repeat(1000) },
        content: '<script>alert(1)</script>',
        operationType: 'export',
      });
      
      expect(result.allowed).toBe(false);
      expect(result.violations.length).toBe(3);
    });

    it('should provide sanitized data', () => {
      const result = control.validateOperation({
        url: 'https://user:pass@example.com',
        content: '<p>Test</p><script>bad()</script>',
        operationType: 'share',
      });
      
      expect(result.sanitized?.url).not.toContain('user:pass');
      expect(result.sanitized?.content).not.toContain('<script>');
    });
  });

  describe('Violation Logging', () => {
    it('should log violations', () => {
      const customControl = createExfiltrationControl({
        domainBlocklist: ['evil.com'],
      });

      customControl.validateURL('https://evil.com', 'user-1');
      
      const violations = customControl.getViolations();
      expect(violations.length).toBeGreaterThan(0);
      expect(violations[0].type).toBe('url');
      expect(violations[0].userId).toBe('user-1');
    });

    it('should filter violations by user', () => {
      const customControl = createExfiltrationControl({
        domainBlocklist: ['evil.com'],
      });

      customControl.validateURL('https://evil.com', 'user-1');
      customControl.validateURL('https://evil.com', 'user-2');

      const user1Violations = customControl.getViolations({ userId: 'user-1' });
      expect(user1Violations.every(v => v.userId === 'user-1')).toBe(true);
    });

    it('should filter violations by type', () => {
      const content = '<script>alert(1)</script>';
      control.detectScripts(content, 'user-1');

      const scriptViolations = control.getViolations({ type: 'script' });
      expect(scriptViolations.every(v => v.type === 'script')).toBe(true);
    });

    it('should filter violations by severity', () => {
      const highThreat = '<script>eval("bad")</script>';
      control.detectScripts(highThreat, 'user-1');

      const criticalViolations = control.getViolations({ severity: 'critical' });
      expect(criticalViolations.every(v => v.severity === 'critical')).toBe(true);
    });

    it('should filter violations by time', () => {
      const before = Date.now();
      
      const customControl = createExfiltrationControl({
        domainBlocklist: ['evil.com'],
      });
      customControl.validateURL('https://evil.com');

      const recentViolations = customControl.getViolations({ since: before });
      expect(recentViolations.every(v => v.timestamp >= before)).toBe(true);
    });

    it('should clear violations', () => {
      const customControl = createExfiltrationControl({
        domainBlocklist: ['evil.com'],
      });

      customControl.validateURL('https://evil.com');
      expect(customControl.getViolations().length).toBeGreaterThan(0);

      customControl.clearViolations();
      expect(customControl.getViolations()).toEqual([]);
    });

    it('should not log when disabled', () => {
      const customControl = createExfiltrationControl({
        logViolations: false,
        domainBlocklist: ['evil.com'],
      });

      customControl.validateURL('https://evil.com');
      expect(customControl.getViolations()).toEqual([]);
    });
  });

  describe('Statistics', () => {
    beforeEach(() => {
      const customControl = createExfiltrationControl({
        domainBlocklist: ['evil.com'],
      });

      // Generate some violations
      customControl.validateURL('https://evil.com', 'user-1');
      customControl.detectScripts('<script>alert(1)</script>', 'user-2');
      customControl.checkPayloadSize('x'.repeat(200 * 1024), 'collaboration', 'user-1');

      control = customControl;
    });

    it('should count total violations', () => {
      const stats = control.getStatistics();
      expect(stats.totalViolations).toBeGreaterThan(0);
    });

    it('should count violations by type', () => {
      const stats = control.getStatistics();
      expect(stats.byType.url).toBeGreaterThan(0);
      expect(stats.byType.script).toBeGreaterThan(0);
      expect(stats.byType.size).toBeGreaterThan(0);
    });

    it('should count violations by severity', () => {
      const stats = control.getStatistics();
      expect(Object.values(stats.bySeverity).some(count => count > 0)).toBe(true);
    });

    it('should count unique users', () => {
      const stats = control.getStatistics();
      expect(stats.uniqueUsers).toBe(2);
    });
  });

  describe('Configuration', () => {
    it('should get current configuration', () => {
      const config = control.getConfig();
      
      expect(config.allowedProtocols).toBeDefined();
      expect(config.detectScripts).toBeDefined();
    });

    it('should update configuration', () => {
      control.updateConfig({ strictMode: true });
      
      const config = control.getConfig();
      expect(config.strictMode).toBe(true);
    });

    it('should merge updates with existing config', () => {
      const originalProtocols = control.getConfig().allowedProtocols;
      
      control.updateConfig({ rateLimitPerMinute: 30 });
      
      const config = control.getConfig();
      expect(config.rateLimitPerMinute).toBe(30);
      expect(config.allowedProtocols).toEqual(originalProtocols);
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty URL', () => {
      const result = control.validateURL('');
      expect(result.allowed).toBe(false);
    });

    it('should handle empty payload', () => {
      const result = control.checkPayloadSize('', 'collaboration');
      expect(result.allowed).toBe(true);
    });

    it('should handle empty content', () => {
      const result = control.detectScripts('');
      expect(result.safe).toBe(true);
      expect(result.threats).toEqual([]);
    });

    it('should handle operation with no data', () => {
      const result = control.validateOperation({
        operationType: 'export',
      });
      
      expect(result.allowed).toBe(true);
    });

    it('should handle multiple script patterns', () => {
      const content = `
        <script>alert(1)</script>
        <div onclick="bad()">Test</div>
        <a href="javascript:void(0)">Link</a>
      `;
      const result = control.detectScripts(content);
      
      expect(result.threats.length).toBeGreaterThan(2);
    });
  });
});
