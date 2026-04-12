/**
 * @fileoverview Canvas AI contract testing helpers.
 *
 * Provides builders, matchers, and assertion utilities for verifying
 * that CanvasAIAdapter implementations satisfy the platform contract —
 * returning well-formed results, emitting the right telemetry events,
 * and handling error cases cleanly.
 *
 * @doc.type utilities
 * @doc.purpose AI contract testing for @ghatana/canvas/testing
 * @doc.layer testing
 */

import type {
  CanvasAIAdapter,
  AISuggestion,
  CanvasAIContext,
  AIResult,
  AILayoutResult,
  AIGenerateElementResult,
} from '../ai/types.js';
import type {
  CanvasTelemetrySink,
  CanvasTelemetryEvent,
  CanvasAIFlowEvent,
} from '../telemetry/events.js';

// ============================================================================
// Context builders
// ============================================================================

/** Minimal valid CanvasAIContext for unit tests. */
export function makeTestAIContext(
  overrides: Partial<CanvasAIContext> = {},
): CanvasAIContext {
  return {
    selectedElementIds: [],
    activeLayer: 'architecture',
    visibleElementIds: [],
    ...overrides,
  };
}

/** Build a minimal AISuggestion for test assertions. */
export function makeTestAISuggestion(
  overrides: Partial<AISuggestion> = {},
): AISuggestion {
  return {
    id: `sug-${Math.random().toString(36).slice(2, 8)}`,
    kind: 'layout',
    title: 'Auto-arrange elements',
    description: 'Arrange selected elements in a grid layout.',
    confidence: 0.9,
    payload: {},
    ...overrides,
  };
}

/** Build a minimal AIResult with kind=suggestions. */
export function makeTestAIResult(): AIResult {
  return { kind: 'suggestions', suggestions: [] };
}

/** Build an AILayoutResult for layout test assertions. */
export function makeTestLayoutResult(
  positions: Record<string, { x: number; y: number }> = {},
): AILayoutResult {
  return { positions };
}

/** Build an AIGenerateElementResult for generate-elements assertions. */
export function makeTestGenerateResult(
  overrides: Partial<AIGenerateElementResult> = {},
): AIGenerateElementResult {
  return {
    elementType: 'ui-component',
    props: { label: 'Generated Element' },
    suggestedPosition: { x: 100, y: 100 },
    ...overrides,
  };
}

// ============================================================================
// AI contract assertion helpers
// ============================================================================

/**
 * Assert that an AISuggestion is well-formed according to the platform contract.
 * Throws descriptively if any field violates the contract.
 */
export function assertSuggestionContract(suggestion: AISuggestion): void {
  if (!suggestion.id || typeof suggestion.id !== 'string') {
    throw new Error(`AISuggestion.id must be a non-empty string; got: ${JSON.stringify(suggestion.id)}`);
  }
  const validKinds: AISuggestion['kind'][] = [
    'layout', 'content', 'connection', 'element', 'label', 'summarize', 'diagram', 'code', 'search',
  ];
  if (!validKinds.includes(suggestion.kind)) {
    throw new Error(`AISuggestion.kind "${suggestion.kind}" is not in allowed set: ${validKinds.join(', ')}`);
  }
  if (typeof suggestion.title !== 'string' || suggestion.title.length === 0) {
    throw new Error(`AISuggestion.title must be a non-empty string`);
  }
  if (
    suggestion.confidence !== undefined &&
    (typeof suggestion.confidence !== 'number' ||
      suggestion.confidence < 0 ||
      suggestion.confidence > 1)
  ) {
    throw new Error(
      `AISuggestion.confidence must be a number in [0, 1]; got: ${suggestion.confidence}`,
    );
  }
}

/**
 * Assert that all suggestions in a list satisfy the platform contract.
 */
export function assertSuggestionsContract(suggestions: AISuggestion[]): void {
  suggestions.forEach((s, i) => {
    try {
      assertSuggestionContract(s);
    } catch (e) {
      throw new Error(`Suggestion at index ${i} failed contract check: ${(e as Error).message}`);
    }
  });
}

/**
 * Assert that an AILayoutResult is well-formed: positions map must contain
 * only numeric x/y pairs.
 */
export function assertLayoutResultContract(result: AILayoutResult): void {
  for (const [id, pos] of Object.entries(result.positions)) {
    if (typeof pos.x !== 'number' || typeof pos.y !== 'number') {
      throw new Error(
        `AILayoutResult.positions["${id}"] must have numeric x and y; got: ${JSON.stringify(pos)}`,
      );
    }
  }
}

/**
 * Assert that an AIGenerateElementResult is well-formed.
 */
export function assertGenerateResultContract(result: AIGenerateElementResult): void {
  if (!result.elementType || typeof result.elementType !== 'string') {
    throw new Error(`AIGenerateElementResult.elementType must be a non-empty string`);
  }
  if (typeof result.props !== 'object' || result.props === null) {
    throw new Error(`AIGenerateElementResult.props must be an object`);
  }
  if (
    result.suggestedPosition !== undefined &&
    (typeof result.suggestedPosition.x !== 'number' || typeof result.suggestedPosition.y !== 'number')
  ) {
    throw new Error(`AIGenerateElementResult.suggestedPosition must have numeric x and y`);
  }
}

// ============================================================================
// Full adapter contract test runner
// ============================================================================

export interface AIAdapterContractResult {
  readonly passed: string[];
  readonly failed: Array<{ check: string; error: string }>;
  readonly ok: boolean;
}

/**
 * Run a standard set of contract checks against any CanvasAIAdapter
 * implementation.  Returns a result object rather than throwing so callers
 * can decide how to surface failures.
 */
export async function runAIAdapterContractChecks(
  adapter: CanvasAIAdapter,
  context: CanvasAIContext = makeTestAIContext(),
): Promise<AIAdapterContractResult> {
  const passed: string[] = [];
  const failed: Array<{ check: string; error: string }> = [];

  async function check(name: string, fn: () => Promise<void>): Promise<void> {
    try {
      await fn();
      passed.push(name);
    } catch (e) {
      failed.push({ check: name, error: (e as Error).message });
    }
  }

  await check('getSuggestions returns array', async () => {
    const result = await adapter.getSuggestions(context);
    if (!Array.isArray(result)) throw new Error(`Expected array, got ${typeof result}`);
    assertSuggestionsContract(result);
  });

  await check('getSuggestions handles empty selection', async () => {
    const emptyCtx = makeTestAIContext({ selectedElementIds: [] });
    const result = await adapter.getSuggestions(emptyCtx);
    if (!Array.isArray(result)) throw new Error(`Expected array, got ${typeof result}`);
  });

  await check('query returns AIResult', async () => {
    const result = await adapter.query(context);
    if (!result || typeof result.kind !== 'string') {
      throw new Error(`Expected AIResult with kind; got: ${JSON.stringify(result)}`);
    }
  });

  await check('autoLayout returns AILayoutResult', async () => {
    const result = await adapter.autoLayout(context);
    if (!result || typeof result.positions !== 'object') {
      throw new Error(`Expected AILayoutResult with positions; got: ${JSON.stringify(result)}`);
    }
    assertLayoutResultContract(result);
  });

  await check('generateElements returns array', async () => {
    const result = await adapter.generateElements('a simple button', context);
    if (!Array.isArray(result)) throw new Error(`Expected array, got ${typeof result}`);
    result.forEach(assertGenerateResultContract);
  });

  await check('acceptSuggestion returns AIResult', async () => {
    const suggestion = makeTestAISuggestion();
    const result = await adapter.acceptSuggestion(suggestion, context);
    if (!result || typeof result.kind !== 'string') {
      throw new Error(`Expected AIResult; got: ${JSON.stringify(result)}`);
    }
  });

  await check('dismissSuggestion resolves without throwing', async () => {
    await adapter.dismissSuggestion('non-existent-id');
  });

  return { passed, failed, ok: failed.length === 0 };
}

// ============================================================================
// Telemetry contract helpers for AI flows
// ============================================================================

export interface TelemetryCapture {
  readonly events: CanvasTelemetryEvent[];
  readonly sink: CanvasTelemetrySink;
}

/** Create a capture sink and assert helpers for use in tests. */
export function createTelemetryCapture(): TelemetryCapture {
  const events: CanvasTelemetryEvent[] = [];
  const sink: CanvasTelemetrySink = {
    emit(event): void {
      events.push(event);
    },
    async flush(): Promise<void> {
      // noop
    },
  };
  return { events, sink };
}

/** Assert a specific AI flow event was emitted at least once. */
export function assertAIFlowEmitted(
  events: CanvasTelemetryEvent[],
  flow: CanvasAIFlowEvent['flow'],
  options: { success?: boolean; minCount?: number } = {},
): void {
  const { success, minCount = 1 } = options;
  const aiEvents = events.filter(
    (e): e is CanvasAIFlowEvent =>
      e.kind === 'canvas-ai-flow' &&
      (e as CanvasAIFlowEvent).flow === flow &&
      (success === undefined || e.success === success),
  );
  if (aiEvents.length < minCount) {
    const desc = success !== undefined ? ` (success=${success})` : '';
    throw new Error(
      `assertAIFlowEmitted: expected ≥${minCount} "${flow}"${desc} event(s), got ${aiEvents.length}`,
    );
  }
}

/** Assert no AI flow errors were emitted. */
export function assertNoAIFlowErrors(events: CanvasTelemetryEvent[]): void {
  const errors = events.filter(
    (e): e is CanvasAIFlowEvent => e.kind === 'canvas-ai-flow' && !e.success,
  );
  if (errors.length > 0) {
    const codes = errors.map((e) => e.errorCode ?? 'UNKNOWN').join(', ');
    throw new Error(`assertNoAIFlowErrors: ${errors.length} AI flow error(s) emitted — codes: ${codes}`);
  }
}
