/**
 * @fileoverview @ghatana/canvas/testing — render, interaction, telemetry, and AI contract helpers.
 *
 * Import from this subpath in test files only. Never import in production code.
 */

export * from './test-utils.js';

export {
  TelemetrySpy,
  AIAdapterSpy,
  createStubAIAdapter,
  assertOperationTelemetry,
  assertAIFlowTelemetry,
  type AIAdapterCall,
} from './telemetry-helpers.js';

export {
  makePointerEvent,
  dispatchPointerEvent,
  simulateClick,
  simulateDrag,
  dispatchKeyEvent,
  simulateKeyPress,
  makeTestViewport,
  assertViewportEqual,
  assertRenderedRole,
  assertNotRenderedRole,
  assertHasClass,
  assertAriaAttribute,
  type PointerPosition,
  type SimulatedPointerEvent,
  type SimulatedKeyEvent,
  type ViewportState as TestViewportState,
} from './interaction-helpers.js';

export {
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
  type AIAdapterContractResult,
  type TelemetryCapture,
} from './ai-contract-helpers.js';
