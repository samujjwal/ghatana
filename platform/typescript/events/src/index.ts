/**
 * @ghatana/events
 *
 * Platform-level event system for Ghatana applications.
 *
 * Provides:
 * - `PlatformEvent<T>` base interface (discriminated by `type`)
 * - `EventDispatcher` for routing events to typed handlers
 * - Serialization / deserialization with versioned envelope (`_v: 1`)
 * - Zod-based runtime validation helpers
 * - Type guards: `isPlatformEvent`, `isEventOfType`
 *
 * @example
 * ```ts
 * import { EventDispatcher, type PlatformEvent } from '@ghatana/events';
 *
 * interface UserLoggedIn extends PlatformEvent<{ userId: string }> {
 *   type: 'user.logged-in';
 * }
 *
 * const dispatcher = new EventDispatcher();
 * const token = dispatcher.subscribe<UserLoggedIn>('user.logged-in', (e) => {
 *   console.log(e.data.userId);
 * });
 * ```
 *
 * @module @ghatana/events
 */

// Core types
export type {
  EventSourceType,
  EventSource,
  PlatformEvent,
  EventHandler,
  EventFilter,
  SubscriptionToken,
} from "./types";

export {
  EventSourceSchema,
  PlatformEventSchema,
  isPlatformEvent,
  isEventOfType,
} from "./types";

// Dispatcher
export { EventDispatcher } from "./dispatcher";

// Serialization
export type { SerializedPlatformEvent } from "./serializer";
export {
  serializeEvent,
  deserializeEvent,
  validateEvent,
  EventDeserializationError,
} from "./serializer";

// Validation
export {
  validatePlatformEvent,
  validatePlatformEventWithData,
  safeParsePlatformEvent,
  EventValidationError,
} from "./validation";
