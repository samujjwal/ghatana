/**
 * @fileoverview Event types barrel export.
 */

export type {
  CorrelationId,
  SessionId,
  EventSource,
  PlatformEvent,
} from './base';

export {
  createCorrelationId,
  createSessionId,
  isValidCorrelationId,
  isValidSessionId,
  createPlatformEvent,
} from './base';

export type { CanvasEventPayloads, CanvasEventName } from './canvas-events';
export { CanvasEvents, ALL_CANVAS_EVENT_NAMES } from './canvas-events';

export type { BuilderEventPayloads, BuilderEventName } from './builder-events';
export { BuilderEvents, ALL_BUILDER_EVENT_NAMES } from './builder-events';

export type { DesignSystemEventPayloads, DesignSystemEventName } from './design-system-events';
export { DesignSystemEvents, ALL_DESIGN_SYSTEM_EVENT_NAMES } from './design-system-events';
