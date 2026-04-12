/**
 * @fileoverview Tests for the AI contract testing helpers.
 */
import { describe, it, expect } from 'vitest';
import {
  makeTestAIContext,
  makeTestAISuggestion,
  makeTestAIResult,
  makeTestLayoutResult,
  makeTestGenerateResult,
  assertSuggestionContract,
  assertSuggestionsContract,
  assertLayoutResultContract,
  assertGenerateResultContract,
  runAIAdapterContractChecks,
  createTelemetryCapture,
  assertAIFlowEmitted,
  assertNoAIFlowErrors,
} from '../ai-contract-helpers.js';
import { createStubAIAdapter } from '../telemetry-helpers.js';
import type { CanvasAIFlowEvent } from '../../telemetry/events.js';

// ── Context builder ──────────────────────────────────────────────────────────

describe('makeTestAIContext', () => {
  it('returns a valid context with defaults', () => {
    const ctx = makeTestAIContext();
    expect(ctx.selectedElementIds).toEqual([]);
    expect(ctx.activeLayer).toBe('architecture');
    expect(ctx.visibleElementIds).toEqual([]);
  });

  it('merges overrides', () => {
    const ctx = makeTestAIContext({ selectedElementIds: ['a', 'b'], userQuery: 'arrange' });
    expect(ctx.selectedElementIds).toEqual(['a', 'b']);
    expect(ctx.userQuery).toBe('arrange');
  });
});

// ── Suggestion builder ───────────────────────────────────────────────────────

describe('makeTestAISuggestion', () => {
  it('returns a valid suggestion', () => {
    const s = makeTestAISuggestion();
    expect(typeof s.id).toBe('string');
    expect(s.id.length).toBeGreaterThan(0);
    expect(s.kind).toBe('layout');
    expect(typeof s.title).toBe('string');
    expect(s.confidence).toBeGreaterThanOrEqual(0);
    expect(s.confidence).toBeLessThanOrEqual(1);
  });

  it('applies overrides', () => {
    const s = makeTestAISuggestion({ kind: 'code', title: 'Generate button' });
    expect(s.kind).toBe('code');
    expect(s.title).toBe('Generate button');
  });
});

// ── Result builders ──────────────────────────────────────────────────────────

describe('makeTestAIResult', () => {
  it('returns kind=suggestions with empty array', () => {
    const r = makeTestAIResult();
    expect(r.kind).toBe('suggestions');
    if (r.kind === 'suggestions') expect(r.suggestions).toEqual([]);
  });
});

describe('makeTestLayoutResult', () => {
  it('returns empty positions by default', () => {
    const r = makeTestLayoutResult();
    expect(r.positions).toEqual({});
  });

  it('stores given positions', () => {
    const r = makeTestLayoutResult({ 'el-1': { x: 10, y: 20 } });
    expect(r.positions['el-1']).toEqual({ x: 10, y: 20 });
  });
});

describe('makeTestGenerateResult', () => {
  it('returns a valid result', () => {
    const r = makeTestGenerateResult();
    expect(r.elementType).toBe('ui-component');
    expect(r.props).toEqual({ label: 'Generated Element' });
    expect(r.suggestedPosition).toEqual({ x: 100, y: 100 });
  });
});

// ── assertSuggestionContract ─────────────────────────────────────────────────

describe('assertSuggestionContract', () => {
  it('passes for a valid suggestion', () => {
    expect(() => assertSuggestionContract(makeTestAISuggestion())).not.toThrow();
  });

  it('throws for empty id', () => {
    const s = makeTestAISuggestion({ id: '' });
    expect(() => assertSuggestionContract(s)).toThrow('id');
  });

  it('throws for invalid kind', () => {
    const s = makeTestAISuggestion({ kind: 'invalid' as never });
    expect(() => assertSuggestionContract(s)).toThrow('kind');
  });

  it('throws for empty title', () => {
    const s = makeTestAISuggestion({ title: '' });
    expect(() => assertSuggestionContract(s)).toThrow('title');
  });

  it('throws for confidence > 1', () => {
    const s = makeTestAISuggestion({ confidence: 1.5 });
    expect(() => assertSuggestionContract(s)).toThrow('confidence');
  });

  it('throws for confidence < 0', () => {
    const s = makeTestAISuggestion({ confidence: -0.1 });
    expect(() => assertSuggestionContract(s)).toThrow('confidence');
  });
});

describe('assertSuggestionsContract', () => {
  it('passes for an empty array', () => {
    expect(() => assertSuggestionsContract([])).not.toThrow();
  });

  it('passes for all valid suggestions', () => {
    expect(() =>
      assertSuggestionsContract([makeTestAISuggestion(), makeTestAISuggestion({ kind: 'code' })]),
    ).not.toThrow();
  });

  it('throws with index when one is invalid', () => {
    const invalid = makeTestAISuggestion({ id: '' });
    expect(() => assertSuggestionsContract([makeTestAISuggestion(), invalid])).toThrow('index 1');
  });
});

// ── assertLayoutResultContract ───────────────────────────────────────────────

describe('assertLayoutResultContract', () => {
  it('passes for valid positions', () => {
    expect(() =>
      assertLayoutResultContract({ positions: { 'a': { x: 0, y: 0 }, 'b': { x: 100, y: 50 } } }),
    ).not.toThrow();
  });

  it('throws for non-numeric position', () => {
    expect(() =>
      assertLayoutResultContract({ positions: { 'a': { x: 'bad' as unknown as number, y: 0 } } }),
    ).toThrow('"a"');
  });
});

// ── assertGenerateResultContract ─────────────────────────────────────────────

describe('assertGenerateResultContract', () => {
  it('passes for valid result', () => {
    expect(() => assertGenerateResultContract(makeTestGenerateResult())).not.toThrow();
  });

  it('throws for empty elementType', () => {
    const r = makeTestGenerateResult({ elementType: '' as never });
    expect(() => assertGenerateResultContract(r)).toThrow('elementType');
  });

  it('throws for null props', () => {
    const r = makeTestGenerateResult({ props: null as unknown as Record<string, unknown> });
    expect(() => assertGenerateResultContract(r)).toThrow('props');
  });

  it('throws for non-numeric suggestedPosition', () => {
    const r = makeTestGenerateResult({
      suggestedPosition: { x: 'bad' as unknown as number, y: 0 },
    });
    expect(() => assertGenerateResultContract(r)).toThrow('suggestedPosition');
  });
});

// ── runAIAdapterContractChecks ───────────────────────────────────────────────

describe('runAIAdapterContractChecks', () => {
  it('passes all checks for the stub adapter', async () => {
    const stub = createStubAIAdapter();
    const result = await runAIAdapterContractChecks(stub);
    expect(result.ok).toBe(true);
    expect(result.failed).toHaveLength(0);
    expect(result.passed.length).toBeGreaterThan(0);
  });

  it('reports failures for a broken adapter', async () => {
    const broken = {
      ...createStubAIAdapter(),
      getSuggestions: async () => 'not-an-array' as unknown as never[],
    };
    const result = await runAIAdapterContractChecks(broken);
    expect(result.ok).toBe(false);
    expect(result.failed.some((f) => f.check.includes('array'))).toBe(true);
  });
});

// ── Telemetry capture helpers ─────────────────────────────────────────────────

describe('createTelemetryCapture', () => {
  it('captures emitted events', () => {
    const { events, sink } = createTelemetryCapture();
    const event: CanvasAIFlowEvent = {
      kind: 'canvas-ai-flow',
      flow: 'suggestion-requested',
      success: true,
      durationMs: 50,
      canvasId: 'c1',
      sessionId: 'test',
      timestamp: Date.now(),
    };
    sink.emit(event);
    expect(events).toHaveLength(1);
    expect(events[0]).toEqual(event);
  });
});

describe('assertAIFlowEmitted', () => {
  it('passes when the flow was emitted', () => {
    const event: CanvasAIFlowEvent = {
      kind: 'canvas-ai-flow',
      flow: 'suggestion-requested',
      success: true,
      durationMs: 10,
      canvasId: 'c1',
      sessionId: 's',
      timestamp: Date.now(),
    };
    expect(() => assertAIFlowEmitted([event], 'suggestion-requested')).not.toThrow();
  });

  it('throws when flow was not emitted', () => {
    expect(() => assertAIFlowEmitted([], 'autolayout-applied')).toThrow('autolayout-applied');
  });

  it('filters by success', () => {
    const event: CanvasAIFlowEvent = {
      kind: 'canvas-ai-flow',
      flow: 'autolayout-applied',
      success: false,
      durationMs: 10,
      canvasId: 'c1',
      sessionId: 's',
      timestamp: Date.now(),
    };
    expect(() => assertAIFlowEmitted([event], 'autolayout-applied', { success: true })).toThrow();
    expect(() => assertAIFlowEmitted([event], 'autolayout-applied', { success: false })).not.toThrow();
  });
});

describe('assertNoAIFlowErrors', () => {
  it('passes when no errors', () => {
    const event: CanvasAIFlowEvent = {
      kind: 'canvas-ai-flow',
      flow: 'suggestion-accepted',
      success: true,
      durationMs: 10,
      canvasId: 'c1',
      sessionId: 's',
      timestamp: Date.now(),
    };
    expect(() => assertNoAIFlowErrors([event])).not.toThrow();
  });

  it('throws when errors exist', () => {
    const event: CanvasAIFlowEvent = {
      kind: 'canvas-ai-flow',
      flow: 'generate-requested',
      success: false,
      errorCode: 'TIMEOUT',
      durationMs: 5000,
      canvasId: 'c1',
      sessionId: 's',
      timestamp: Date.now(),
    };
    expect(() => assertNoAIFlowErrors([event])).toThrow('TIMEOUT');
  });
});
