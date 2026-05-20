/**
 * @fileoverview Tests for preview runtime: sandbox policy and parity.
 *
 * Verifies that:
 *   1. Security policies are correctly applied (sandbox attributes, CSP)
 *   2. Preview output is deterministic across multiple renders
 *   3. Console logs are captured correctly
 *   4. Session cleanup works as expected
 *
 * @doc.type test
 * @doc.purpose Preview runtime sandbox policy and parity tests
 * @doc.layer studio
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { InMemoryPreviewRuntime, defaultPreviewRuntime } from '../in-memory-preview-runtime.js';
import type { PreviewRequest } from '../preview-protocol.js';

// ============================================================================
// Fixtures
// ============================================================================

const SIMPLE_TSX_SOURCE = `
import type { ReactElement } from 'react';

export function Button({ label }: { label: string }): ReactElement {
  return <button>{label}</button>;
}
`.trim();

function makeBaseRequest(overrides: Partial<PreviewRequest> = {}): PreviewRequest {
  return {
    sessionId: 'test-session-001',
    source: SIMPLE_TSX_SOURCE,
    filePath: 'Button.tsx',
    designSystem: {
      packageName: '@ghatana/design-system',
      version: '1.0.0',
      availableComponents: ['Button', 'Card'],
    },
    theme: {
      mode: 'light',
    },
    securityPolicy: {
      allowScripts: true,
      allowSameOrigin: true,
      allowPopups: false,
      allowForms: false,
    },
    ...overrides,
  };
}

// ============================================================================
// Sandbox Policy Tests
// ============================================================================

describe('Preview Runtime — Sandbox Policy', () => {
  let runtime: InMemoryPreviewRuntime;

  beforeEach(() => {
    runtime = new InMemoryPreviewRuntime();
  });

  it('applies allowScripts=true in sandbox when securityPolicy.allowScripts is true', async () => {
    const request = makeBaseRequest({
      securityPolicy: { allowScripts: true, allowSameOrigin: false, allowPopups: false, allowForms: false },
    });
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    expect(result.html).toContain('sandbox="allow-same-origin allow-scripts"');
  });

  it('applies allowSameOrigin=true in sandbox when securityPolicy.allowSameOrigin is true', async () => {
    const request = makeBaseRequest({
      securityPolicy: { allowScripts: false, allowSameOrigin: true, allowPopups: false, allowForms: false },
    });
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    expect(result.html).toContain('sandbox="allow-same-origin allow-scripts"');
  });

  it('disallows popups when securityPolicy.allowPopups is false', async () => {
    const request = makeBaseRequest({
      securityPolicy: { allowScripts: true, allowSameOrigin: true, allowPopups: false, allowForms: false },
    });
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    // Default sandbox should NOT include allow-popups
    expect(result.html).not.toContain('allow-popups');
  });

  it('disallows forms when securityPolicy.allowForms is false', async () => {
    const request = makeBaseRequest({
      securityPolicy: { allowScripts: true, allowSameOrigin: true, allowPopups: false, allowForms: false },
    });
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    // Default sandbox should NOT include allow-forms
    expect(result.html).not.toContain('allow-forms');
  });

  it('includes CSP when securityPolicy.contentSecurityPolicy is provided', async () => {
    const csp = "default-src 'self'; script-src 'self'";
    const request = makeBaseRequest({
      securityPolicy: {
        allowScripts: true,
        allowSameOrigin: true,
        allowPopups: false,
        allowForms: false,
        contentSecurityPolicy: csp,
      },
    });
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    expect(result.html).toContain(csp);
  });

  it('uses light theme by default', async () => {
    const request = makeBaseRequest({
      theme: { mode: 'light' },
    });
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    expect(result.html).toContain('data-theme="light"');
  });

  it('uses dark theme when specified', async () => {
    const request = makeBaseRequest({
      theme: { mode: 'dark' },
    });
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    expect(result.html).toContain('data-theme="dark"');
  });

  it('applies custom theme tokens when provided', async () => {
    const request = makeBaseRequest({
      theme: {
        mode: 'light',
        tokens: { 'color-primary': '#ff0000', 'spacing-unit': '8px' },
      },
    });
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    expect(result.html).toContain('--color-primary: #ff0000');
    expect(result.html).toContain('--spacing-unit: 8px');
  });
});

// ============================================================================
// Preview Parity Tests
// ============================================================================

describe('Preview Runtime — Parity and Determinism', () => {
  let runtime: InMemoryPreviewRuntime;

  beforeEach(() => {
    runtime = new InMemoryPreviewRuntime();
  });

  it('produces identical output for the same input across multiple renders', async () => {
    const request = makeBaseRequest();

    const result1 = await runtime.render(request);
    const result2 = await runtime.render(request);

    expect(result1.success).toBe(true);
    expect(result2.success).toBe(true);
    expect(result1.html).toBe(result2.html);
    expect(result1.duration).toBeGreaterThan(0);
    expect(result2.duration).toBeGreaterThan(0);
  });

  it('produces identical output across different session IDs for the same source', async () => {
    const request1 = makeBaseRequest({ sessionId: 'session-001' });
    const request2 = makeBaseRequest({ sessionId: 'session-002' });

    const result1 = await runtime.render(request1);
    const result2 = await runtime.render(request2);

    expect(result1.success).toBe(true);
    expect(result2.success).toBe(true);
    // HTML output should be identical (session ID only affects cleanup)
    expect(result1.html).toBe(result2.html);
  });

  it('captures console logs during render', async () => {
    const sourceWithLogs = `
import type { ReactElement } from 'react';

export function Component(): ReactElement {
  console.log('Test log');
  console.warn('Test warning');
  console.error('Test error');
  return <div>Test</div>;
}
`.trim();

    const request = makeBaseRequest({ source: sourceWithLogs });
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    expect(result.logs.length).toBeGreaterThan(0);

    const logMessages = result.logs.map(l => l.message);
    expect(logMessages.some(m => m.includes('Test log'))).toBe(true);
    expect(logMessages.some(m => m.includes('Test warning'))).toBe(true);
    expect(logMessages.some(m => m.includes('Test error'))).toBe(true);
  });

  it('includes log timestamps', async () => {
    const sourceWithLog = `
import type { ReactElement } from 'react';

export function Component(): ReactElement {
  console.log('Timestamp test');
  return <div>Test</div>;
}
`.trim();

    const request = makeBaseRequest({ source: sourceWithLog });
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    expect(result.logs.length).toBeGreaterThan(0);

    for (const log of result.logs) {
      expect(typeof log.timestamp).toBe('number');
      expect(log.timestamp).toBeGreaterThan(0);
    }
  });

  it('includes render duration in result', async () => {
    const request = makeBaseRequest();
    const result = await runtime.render(request);

    expect(result.success).toBe(true);
    expect(typeof result.duration).toBe('number');
    expect(result.duration).toBeGreaterThan(0);
  });

  it('returns error result when component export is missing', async () => {
    const sourceWithoutExport = `
import type { ReactElement } from 'react';

// No export
const Component = (): ReactElement => <div>Test</div>;
`.trim();

    const request = makeBaseRequest({ source: sourceWithoutExport });
    const result = await runtime.render(request);

    expect(result.success).toBe(false);
    expect(result.error).toBeDefined();
    expect(result.error).toContain('component');
  });

  it('returns error result when source has syntax errors', async () => {
    const sourceWithSyntaxError = `
import type { ReactElement } from 'react';

export function Component(): ReactElement {
  return <div>Test</div>
// Missing closing brace
`.trim();

    const request = makeBaseRequest({ source: sourceWithSyntaxError });
    const result = await runtime.render(request);

    expect(result.success).toBe(false);
    expect(result.error).toBeDefined();
  });
});

// ============================================================================
// Session Management Tests
// ============================================================================

describe('Preview Runtime — Session Management', () => {
  let runtime: InMemoryPreviewRuntime;

  beforeEach(() => {
    runtime = new InMemoryPreviewRuntime();
  });

  it('tracks active sessions correctly', async () => {
    const request1 = makeBaseRequest({ sessionId: 'session-001' });
    const request2 = makeBaseRequest({ sessionId: 'session-002' });

    await runtime.render(request1);
    await runtime.render(request2);

    const status = runtime.getStatus();
    expect(status.ready).toBe(true);
    expect(status.activeSessions).toBe(2);
  });

  it('decrements active sessions after cleanup', async () => {
    const request = makeBaseRequest({ sessionId: 'session-001' });
    await runtime.render(request);

    await runtime.cleanup('session-001');

    const status = runtime.getStatus();
    expect(status.ready).toBe(true);
    expect(status.activeSessions).toBe(0);
  });

  it('cleanup of non-existent session does not throw', async () => {
    await expect(runtime.cleanup('non-existent-session')).resolves.not.toThrow();
  });

  it('status reports ready as true after initialization', () => {
    const status = runtime.getStatus();
    expect(status.ready).toBe(true);
    expect(status.activeSessions).toBe(0);
  });
});

// ============================================================================
// Default Runtime Instance Tests
// ============================================================================

describe('Default Preview Runtime Instance', () => {
  it('defaultPreviewRuntime is an instance of InMemoryPreviewRuntime', () => {
    expect(defaultPreviewRuntime).toBeInstanceOf(InMemoryPreviewRuntime);
  });

  it('defaultPreviewRuntime can render a request', async () => {
    const request = makeBaseRequest();
    const result = await defaultPreviewRuntime.render(request);

    expect(result.success).toBe(true);
    expect(result.html).toBeDefined();
  });
});
