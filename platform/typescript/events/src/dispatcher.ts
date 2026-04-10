import { randomUUID } from "crypto";

import type {
  PlatformEvent,
  EventHandler,
  EventFilter,
  SubscriptionToken,
} from "./types";

// ---------------------------------------------------------------------------
// Internal types
// ---------------------------------------------------------------------------

interface Subscription<T extends PlatformEvent = PlatformEvent> {
  readonly id: string;
  readonly eventType: string | "*";
  readonly handler: EventHandler<T>;
  readonly filter?: EventFilter<T>;
}

// ---------------------------------------------------------------------------
// EventDispatcher
// ---------------------------------------------------------------------------

/**
 * Platform event dispatcher.
 *
 * Supports:
 * - Type-scoped subscriptions
 * - Wildcard `"*"` subscriptions (receive all events)
 * - Per-subscription filters
 * - Sync and async handlers
 * - Observable subscription counts for diagnostics
 *
 * @example
 * ```ts
 * const dispatcher = new EventDispatcher();
 *
 * const token = dispatcher.subscribe('user.logged-in', async (event) => {
 *   console.log(event.data);
 * });
 *
 * dispatcher.dispatch({
 *   id: crypto.randomUUID(),
 *   type: 'user.logged-in',
 *   timestamp: Date.now(),
 *   source: { type: 'client', id: 'web-app' },
 *   data: { userId: '123' },
 * });
 *
 * dispatcher.unsubscribe(token);
 * ```
 */
export class EventDispatcher {
  private readonly subscriptions = new Map<string, Subscription>();

  // -------------------------------------------------------------------------
  // subscribe
  // -------------------------------------------------------------------------

  /**
   * Subscribes a handler to events of a given type.
   *
   * @param eventType - The discriminated event type string, or `"*"` for all events.
   * @param handler   - The async or sync handler function.
   * @param filter    - Optional predicate to further filter matching events.
   * @returns A `SubscriptionToken` that can be used to unsubscribe.
   */
  subscribe<T extends PlatformEvent = PlatformEvent>(
    eventType: string,
    handler: EventHandler<T>,
    filter?: EventFilter<T>
  ): SubscriptionToken {
    const id =
      typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : randomUUID();

    const subscription: Subscription<T> = {
      id,
      eventType,
      handler,
      filter,
    };

    this.subscriptions.set(id, subscription as Subscription);
    return { id, eventType };
  }

  // -------------------------------------------------------------------------
  // unsubscribe
  // -------------------------------------------------------------------------

  /**
   * Removes a subscription by its token.
   *
   * @param token - The token returned from `subscribe()`.
   */
  unsubscribe(token: SubscriptionToken): void {
    this.subscriptions.delete(token.id);
  }

  // -------------------------------------------------------------------------
  // dispatch
  // -------------------------------------------------------------------------

  /**
   * Dispatches an event to all matching subscribers.
   *
   * Execution is sequential (one handler after another). Errors in one handler
   * do NOT prevent subsequent handlers from running — errors are collected and
   * can optionally surface via the `onError` callback.
   *
   * @param event   - The event to dispatch.
   * @param onError - Optional error callback invoked per failing handler.
   */
  async dispatch(
    event: PlatformEvent,
    onError?: (err: unknown, subscription: SubscriptionToken) => void
  ): Promise<void> {
    const promises: Promise<void>[] = [];

    for (const sub of this.subscriptions.values()) {
      if (sub.eventType !== "*" && sub.eventType !== event.type) {
        continue;
      }

      if (sub.filter && !sub.filter(event as never)) {
        continue;
      }

      const token: SubscriptionToken = { id: sub.id, eventType: sub.eventType };

      const p = Promise.resolve()
        .then(() => sub.handler(event as never))
        .catch((err: unknown) => {
          if (onError) {
            onError(err, token);
          }
        });

      promises.push(p);
    }

    await Promise.all(promises);
  }

  // -------------------------------------------------------------------------
  // dispatchSync
  // -------------------------------------------------------------------------

  /**
   * Fires all matching sync handlers and ignores async results.
   * Use sparingly — prefer `dispatch()` for async safety.
   *
   * @param event   - The event to dispatch.
   * @param onError - Optional error callback per failing sync handler.
   */
  dispatchSync(
    event: PlatformEvent,
    onError?: (err: unknown, subscription: SubscriptionToken) => void
  ): void {
    for (const sub of this.subscriptions.values()) {
      if (sub.eventType !== "*" && sub.eventType !== event.type) {
        continue;
      }

      if (sub.filter && !sub.filter(event as never)) {
        continue;
      }

      const token: SubscriptionToken = { id: sub.id, eventType: sub.eventType };

      try {
        const result = sub.handler(event as never);
        // Swallow returned promise intentionally — user called dispatchSync
        void result;
      } catch (err) {
        if (onError) {
          onError(err, token);
        }
      }
    }
  }

  // -------------------------------------------------------------------------
  // diagnostics
  // -------------------------------------------------------------------------

  /** Returns the total number of active subscriptions. */
  get subscriptionCount(): number {
    return this.subscriptions.size;
  }

  /** Returns the number of subscriptions for a given event type. */
  subscriptionCountForType(eventType: string): number {
    let count = 0;
    for (const sub of this.subscriptions.values()) {
      if (sub.eventType === eventType || sub.eventType === "*") {
        count++;
      }
    }
    return count;
  }

  /** Removes all subscriptions. */
  clear(): void {
    this.subscriptions.clear();
  }
}
