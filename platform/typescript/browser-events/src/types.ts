import { z } from "zod";

import {
  PlatformEvent,
  EventSource,
  PlatformEventSchema,
} from "@ghatana/events";

// ---------------------------------------------------------------------------
// Browser-specific source
// ---------------------------------------------------------------------------

/**
 * Identifies a browser extension as event source.
 * `type` is always `"browser"` for browser-extension events.
 */
export interface BrowserEventSource extends EventSource {
  readonly type: "browser";
}

// ---------------------------------------------------------------------------
// Domain payload types
// ---------------------------------------------------------------------------

export interface TabEventData {
  readonly action:
    | "created"
    | "updated"
    | "removed"
    | "activated"
    | "moved"
    | "attached"
    | "detached";
  readonly tabId: number;
  readonly windowId?: number;
  readonly url?: string;
  readonly title?: string;
  readonly active?: boolean;
}

export interface NavigationEventData {
  readonly action: "before" | "committed" | "completed" | "error" | "replaced";
  readonly tabId: number;
  readonly url: string;
  readonly frameId?: number;
  readonly errorText?: string;
}

export interface NetworkRequestData {
  readonly requestId: string;
  readonly url: string;
  readonly method: string;
  readonly type: "fetch" | "xhr";
  readonly headers?: Readonly<Record<string, string>>;
  readonly body?: string;
}

export interface NetworkResponseData {
  readonly requestId: string;
  readonly url: string;
  readonly method: string;
  readonly status: number;
  readonly statusText: string;
  readonly duration: number;
  readonly type: "fetch" | "xhr";
  readonly headers?: Readonly<Record<string, string>>;
  readonly responseSize?: number;
}

export interface NetworkErrorData {
  readonly requestId: string;
  readonly url: string;
  readonly method: string;
  readonly duration: number;
  readonly type: "fetch" | "xhr";
  readonly error: string;
}

export type NetworkEventData =
  | ({ readonly eventType: "network-request" } & NetworkRequestData)
  | ({ readonly eventType: "network-response" } & NetworkResponseData)
  | ({ readonly eventType: "network-error" } & NetworkErrorData);

export interface WebRequestEventData {
  readonly action:
    | "before-request"
    | "before-send-headers"
    | "send-headers"
    | "headers-received"
    | "response-started"
    | "completed"
    | "error-occurred";
  readonly requestId: string;
  readonly url: string;
  readonly method: string;
  readonly tabId?: number;
  readonly statusCode?: number;
  readonly error?: string;
}

export interface HistoryEventData {
  readonly action: "visited" | "title-changed" | "deleted" | "all-deleted";
  readonly url?: string;
  readonly title?: string;
  readonly visitId?: string;
  readonly transitionType?: string;
}

// ---------------------------------------------------------------------------
// Concrete event types
// ---------------------------------------------------------------------------

export interface TabEvent extends PlatformEvent<TabEventData> {
  readonly type: "tab.created" | "tab.updated" | "tab.removed" | "tab.activated" | "tab.moved" | "tab.detached";
  readonly source: BrowserEventSource;
}

export interface NavigationEvent extends PlatformEvent<NavigationEventData> {
  readonly type:
    | "navigation.before"
    | "navigation.committed"
    | "navigation.completed"
    | "navigation.error"
    | "navigation.replaced";
  readonly source: BrowserEventSource;
}

export interface NetworkEvent extends PlatformEvent<NetworkEventData> {
  readonly type: "network.request" | "network.response" | "network.error";
  readonly source: BrowserEventSource;
}

export interface WebRequestEvent extends PlatformEvent<WebRequestEventData> {
  readonly type: `webrequest.${"before-request" | "headers-received" | "completed" | "error-occurred"}`;
  readonly source: BrowserEventSource;
}

export interface HistoryEvent extends PlatformEvent<HistoryEventData> {
  readonly type: `history.${"visited" | "title-changed" | "deleted" | "all-deleted"}`;
  readonly source: BrowserEventSource;
}

/**
 * Union of all browser event types.
 */
export type BrowserEvent =
  | TabEvent
  | NavigationEvent
  | NetworkEvent
  | WebRequestEvent
  | HistoryEvent;

// ---------------------------------------------------------------------------
// EventFilter and EventHandler
// ---------------------------------------------------------------------------

export type BrowserEventFilter<T extends BrowserEvent = BrowserEvent> = (
  event: T
) => boolean;

export type BrowserEventHandler<T extends BrowserEvent = BrowserEvent> = (
  event: T
) => void | Promise<void>;

// ---------------------------------------------------------------------------
// Zod schemas for runtime validation
// ---------------------------------------------------------------------------

export const TabEventDataSchema = z.object({
  action: z.enum(["created", "updated", "removed", "activated", "moved", "attached", "detached"]),
  tabId: z.number().int(),
  windowId: z.number().int().optional(),
  url: z.string().optional(),
  title: z.string().optional(),
  active: z.boolean().optional(),
});

export const NavigationEventDataSchema = z.object({
  action: z.enum(["before", "committed", "completed", "error", "replaced"]),
  tabId: z.number().int(),
  url: z.string().url(),
  frameId: z.number().int().optional(),
  errorText: z.string().optional(),
});

export const HistoryEventDataSchema = z.object({
  action: z.enum(["visited", "title-changed", "deleted", "all-deleted"]),
  url: z.string().optional(),
  title: z.string().optional(),
  visitId: z.string().optional(),
  transitionType: z.string().optional(),
});

/**
 * Type guard for any BrowserEvent.
 */
export function isBrowserEvent(event: unknown): event is BrowserEvent {
  if (!PlatformEventSchema.safeParse(event).success) return false;
  const e = event as PlatformEvent;
  return (
    typeof e.source === "object" &&
    e.source !== null &&
    (e.source as EventSource).type === "browser"
  );
}

/**
 * Type guard for a specific browser event type.
 */
export function isBrowserEventOfType<T extends BrowserEvent>(
  event: BrowserEvent,
  type: T["type"]
): event is T {
  return event.type === type;
}

// ---------------------------------------------------------------------------
// Domain URL filtering helpers
// ---------------------------------------------------------------------------

/**
 * Creates a domain filter that accepts only events with URLs matching the given domain(s).
 */
export function createDomainFilter<T extends BrowserEvent>(
  ...domains: string[]
): BrowserEventFilter<T> {
  return (event: T): boolean => {
    const maybeUrl =
      (event.data as { url?: string }).url ??
      (event.data as { url?: string } | undefined)?.url;
    if (!maybeUrl) return false;
    try {
      const { hostname } = new URL(maybeUrl);
      return domains.some(
        (d) => hostname === d || hostname.endsWith(`.${d}`)
      );
    } catch {
      return false;
    }
  };
}

/**
 * Creates a URL pattern filter that accepts events whose URL matches the pattern.
 */
export function createUrlPatternFilter<T extends BrowserEvent>(
  pattern: RegExp
): BrowserEventFilter<T> {
  return (event: T): boolean => {
    const maybeUrl = (event.data as { url?: string }).url;
    if (!maybeUrl) return false;
    return pattern.test(maybeUrl);
  };
}
