/**
 * @fileoverview Event Capture Interface
 *
 * Defines the contract for capturing browser events (tabs, navigation, network, etc.)
 * Implementations should use browser extension APIs only.
 *
 * Event shape follows the PlatformEvent<T> contract defined in @ghatana/realtime/events:
 *   - `id`        — unique event UUID
 *   - `type`      — discriminated event kind
 *   - `timestamp` — Unix millisecond epoch
 *   - `source`    — origin description  ({ type: 'browser', id: ... })
 *   - `data`      — domain-specific payload
 *
 * When @ghatana/realtime is linked into the DCMAAR workspace, replace the local
 * BrowserEventSource/BrowserPlatformEvent definitions below with direct imports from
 * '@ghatana/realtime/events'.
 *
 * @module core/interfaces/EventCapture
 */

// ---------------------------------------------------------------------------
// Platform event contract (mirrors @ghatana/realtime/events PlatformEvent<T>)
// ---------------------------------------------------------------------------

/**
 * Identifies the browser extension as the event source.
 */
export interface BrowserEventSource {
  /** Always 'browser' for extension-captured events. */
  readonly type: 'browser';
  /** Extension or tab instance identifier. */
  readonly id: string;
}

/**
 * Base event shape for all DCMAAR browser-extension events.
 * Structurally compatible with PlatformEvent<T> from @ghatana/realtime/events.
 */
export interface BrowserPlatformEvent<T = unknown> {
  /** Unique event identifier (UUID v4). */
  readonly id: string;
  /** Discriminated event type string (e.g. 'tab.created'). */
  readonly type: string;
  /** Unix millisecond timestamp of event creation. */
  readonly timestamp: number;
  /** Always browser-sourced in this package. */
  readonly source: BrowserEventSource;
  /** Domain-specific payload. */
  readonly data: T;
  /** Optional correlation id for distributed tracing. */
  readonly correlationId?: string;
}

// ---------------------------------------------------------------------------
// Domain payload types
// ---------------------------------------------------------------------------

export interface TabEventData {
  action: 'created' | 'updated' | 'removed' | 'activated' | 'moved' | 'attached' | 'detached';
  tabId: number;
  windowId?: number;
  url?: string;
  title?: string;
  active?: boolean;
}

export interface NavigationEventData {
  action: 'before' | 'committed' | 'completed' | 'error' | 'replaced';
  tabId: number;
  url: string;
  frameId: number;
  transitionType?: string;
}

export interface NetworkEventData {
  action: 'request' | 'response' | 'error' | 'redirect';
  requestId: string;
  url: string;
  method: string;
  tabId?: number;
  statusCode?: number;
  contentType?: string;
  duration?: number;
  error?: string;
}

export interface WebRequestEventData {
  action: 'before' | 'sent' | 'headers' | 'redirect' | 'response' | 'completed' | 'error';
  requestId: string;
  url: string;
  method: string;
  tabId?: number;
  /** e.g. main_frame, sub_frame, stylesheet, script, image */
  resourceType: string;
}

export interface HistoryEventData {
  action: 'visited' | 'deleted';
  url: string;
  title?: string;
  visitTime: number;
}

export interface FlowEventData {
  flowId: string;
  action: 'start' | 'step' | 'complete' | 'abandon' | 'error';
  step?: string;
  url?: string;
  tabId?: number;
  data?: Record<string, unknown>;
}

// ---------------------------------------------------------------------------
// Domain event interfaces (extend BrowserPlatformEvent)
// ---------------------------------------------------------------------------

export interface TabEvent extends BrowserPlatformEvent<TabEventData> {
  readonly type: 'tab';
  readonly source: BrowserEventSource;
}

export interface NavigationEvent extends BrowserPlatformEvent<NavigationEventData> {
  readonly type: 'navigation';
  readonly source: BrowserEventSource;
}

export interface NetworkEvent extends BrowserPlatformEvent<NetworkEventData> {
  readonly type: 'network';
  readonly source: BrowserEventSource;
}

export interface WebRequestEvent extends BrowserPlatformEvent<WebRequestEventData> {
  readonly type: 'webrequest';
  readonly source: BrowserEventSource;
}

export interface HistoryEvent extends BrowserPlatformEvent<HistoryEventData> {
  readonly type: 'history';
  readonly source: BrowserEventSource;
}

export interface FlowEvent extends BrowserPlatformEvent<FlowEventData> {
  readonly type: 'flow';
  readonly source: BrowserEventSource;
}

/**
 * Discriminated union of all browser event types.
 */
export type BrowserEvent =
  | TabEvent
  | NavigationEvent
  | NetworkEvent
  | WebRequestEvent
  | HistoryEvent
  | FlowEvent;

// ---------------------------------------------------------------------------
// Handler / Filter / Capture contracts (unchanged)
// ---------------------------------------------------------------------------

/**
 * Event handler function.
 */
export type EventHandler<T = BrowserEvent> = (event: T) => void | Promise<void>;

/**
 * Event filter options.
 */
export interface EventFilter {
  /** Filter by event types */
  types?: Array<BrowserEvent['type']>;
  /** Filter by URL patterns */
  urlPatterns?: string[];
  /** Filter by tab IDs */
  tabIds?: number[];
  /** Filter by window IDs */
  windowIds?: number[];
}

/**
 * Event Capture Interface
 *
 * Captures browser events using extension APIs and forwards them to handlers.
 * Implementations should be lightweight and non-blocking.
 */
export interface EventCapture {
  /**
   * Start capturing tab events (created, updated, removed, activated)
   *
   * @param filter - Optional filter for tab events
   */
  captureTabEvents(filter?: EventFilter): void;

  /**
   * Start capturing navigation events (before, committed, completed)
   *
   * @param filter - Optional filter for navigation events
   */
  captureNavigationEvents(filter?: EventFilter): void;

  /**
   * Start capturing network request events
   *
   * @param filter - Optional filter for network events
   */
  captureNetworkEvents(filter?: EventFilter): void;

  /**
   * Start capturing web request events (before, sent, response, etc.)
   *
   * @param filter - Optional filter for web request events
   */
  captureWebRequestEvents(filter?: EventFilter): void;

  /**
   * Start capturing history events (visited, deleted)
   *
   * @param filter - Optional filter for history events
   */
  captureHistoryEvents(filter?: EventFilter): void;

  /**
   * Register event handler
   *
   * @param handler - Function to call when events are captured
   */
  onEvent(handler: EventHandler): void;

  /**
   * Remove event handler
   *
   * @param handler - Handler to remove
   */
  offEvent(handler: EventHandler): void;

  /**
   * Stop all event capture and cleanup listeners
   */
  stop(): void;

  /**
   * Get capture status
   *
   * @returns Object with status of each event capture type
   */
  getStatus(): {
    tabs: boolean;
    navigation: boolean;
    network: boolean;
    webrequest: boolean;
    history: boolean;
  };
}

/**
 * Unified event capture for all browser events
 */
export interface UnifiedEventCapture extends EventCapture {
  /**
   * Start capturing all supported events
   *
   * @param filter - Optional global filter
   */
  captureAll(filter?: EventFilter): void;

  /**
   * Get count of captured events by type
   */
  getEventCounts(): Record<BrowserEvent['type'], number>;

  /**
   * Clear event counts
   */
  clearEventCounts(): void;
}
