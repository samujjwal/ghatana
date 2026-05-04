import { describe, expect, it } from 'vitest';

import {
  applyComponentPolicy,
  assessComponentSafety,
  getDefaultComponentPolicy,
  getTrustedComponentPolicy,
} from '../UnsafeComponentHandler';

describe('UnsafeComponentHandler', () => {
  it('flags real unsafe APIs through AST analysis', () => {
    const assessment = assessComponentSafety(
      [
        'export function DangerousWidget() {',
        '  eval("alert(1)");',
        '  const ws = new WebSocket("wss://example.com");',
        '  return <script>alert(1)</script>;',
        '}',
      ].join('\n'),
      'dangerous-widget'
    );

    expect(assessment.safetyLevel).toBe('unsafe');
    expect(assessment.recommendedAction).toBe('block');
    expect(assessment.riskFactors).toEqual(
      expect.arrayContaining([
        'Uses dangerous function: eval',
        'Network access detected: WebSocket',
        'Inline script tag detected',
      ])
    );
  });

  it('does not flag comments or strings that merely mention unsafe APIs', () => {
    const assessment = assessComponentSafety(
      [
        'export function SafeWidget() {',
        '  // localStorage should not be used here',
        '  const docs = "document.cookie and eval are forbidden";',
        '  return <div>{docs}</div>;',
        '}',
      ].join('\n'),
      'safe-widget'
    );

    expect(assessment.safetyLevel).toBe('safe');
    expect(assessment.riskFactors).toHaveLength(0);
  });

  it('adds blocking guards and rewrites direct eval usage under the default policy', () => {
    const transformed = applyComponentPolicy(
      [
        'export function Widget() {',
        '  window.fetch("/api/data");',
        '  localStorage.getItem("token");',
        '  eval("console.log(1)");',
        '  return <div />;',
        '}',
      ].join('\n'),
      getDefaultComponentPolicy()
    );

    expect(transformed).toContain('const __yappcBlockApi');
    expect(transformed).toContain('const window = new Proxy');
    expect(transformed).toContain('const localStorage = new Proxy');
    expect(transformed).toContain('Blocked unsafe API: eval');
    expect(transformed).not.toContain('eval("console.log(1)")');
  });

  it('respects the trusted policy for allowed browser APIs while still blocking eval', () => {
    const transformed = applyComponentPolicy(
      [
        'export function Widget() {',
        '  window.fetch("/api/data");',
        '  new Function("return 1");',
        '  return <div />;',
        '}',
      ].join('\n'),
      getTrustedComponentPolicy()
    );

    expect(transformed).not.toContain('const window = new Proxy');
    expect(transformed).toContain('Blocked unsafe API: Function');
  });
});
