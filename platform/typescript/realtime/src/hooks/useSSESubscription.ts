/**
 * @doc.type hook
 * @doc.purpose Generic React hook for managing a subscription that returns a { close() } handle
 * @doc.layer platform
 * @doc.pattern Hook
 *
 * Encapsulates the boilerplate of:
 *   useEffect(() => {
 *     const sub = subscribe();
 *     return () => sub.close();
 *   }, deps);
 *
 * The mutable callbacks pattern (via useRef) ensures that the subscription is
 * only torn down and re-created when the explicit deps change, not on every
 * render, while still calling the latest handlers on each message.
 */

import { DependencyList, useEffect, useRef } from "react";

/** Any object that can be explicitly closed / unsubscribed. */
export interface Closeable {
  close(): void;
}

/**
 * useSSESubscription — minimal lifecycle manager for subscriptions that return
 * a `{ close() }` handle (SSE, WebSocket, polling intervals, etc.).
 *
 * @param subscribe  Factory called inside an effect. Must return a `Closeable`.
 *                   Use `useCallback` or `useRef`-stable functions for inner
 *                   callbacks to avoid unnecessary re-subscribes.
 * @param deps       React dependency list — subscription restarts when these change.
 *
 * @example
 * // Stable onMessage ref avoids re-subscribing on every render.
 * const onMessageRef = useRef(onMessage);
 * onMessageRef.current = onMessage;
 *
 * useSSESubscription(
 *   () => subscribeToAepStream(tenantId, (...args) => onMessageRef.current(...args)),
 *   [tenantId],
 * );
 */
export function useSSESubscription(
  subscribe: () => Closeable,
  deps: DependencyList,
): void {
  // Keep the factory stable in the ref so we always call the latest version
  // even if the caller passes an inline closure.
  const subscribeRef = useRef(subscribe);
  subscribeRef.current = subscribe;

  useEffect(() => {
    const sub = subscribeRef.current();
    return () => sub.close();
    // The deps are passed by the caller — disable the hook-specific exhaustive-deps
    // lint rule because that rule cannot know about the subscribeRef indirection.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}
